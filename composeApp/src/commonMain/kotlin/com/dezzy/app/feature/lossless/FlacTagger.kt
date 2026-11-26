/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.lossless

/** Bloque de metadatos FLAC: [type] (0 = STREAMINFO, 1 = PADDING, 3 = SEEKTABLE…) y su contenido. */
class FlacBlock(val type: Int, val data: ByteArray)

/** Resultado de etiquetar: [head] sustituye a los primeros [oldHeadLength] bytes del archivo. */
class FlacRewrite(val head: ByteArray, val oldHeadLength: Int)

/**
 * Escritor de etiquetas FLAC en Kotlin puro: bloques VORBIS_COMMENT (4) y PICTURE (6).
 * Trabaja sobre la cabecera (todo lo anterior a las tramas de audio), no sobre el archivo entero.
 */
object FlacTagger {
    const val TYPE_STREAMINFO = 0
    const val TYPE_PADDING = 1
    const val TYPE_VORBIS_COMMENT = 4
    const val TYPE_PICTURE = 6

    private const val PICTURE_FRONT_COVER = 3
    private const val MAX_BLOCK = 0xFFFFFF

    /** Cuánto de la cabecera se lee del archivo para etiquetarlo. */
    const val MAX_HEAD_BYTES = 8 * 1024 * 1024

    /**
     * Devuelve la cabecera con los comentarios de [tags] puestos (sustituyen a los existentes con la misma clave,
     * sin distinguir mayúsculas) y, si hay [coverJpeg], con la portada frontal reemplazada. Null si [head]
     * no es un FLAC válido o la cabecera no cabe entera en [head].
     */
    fun apply(head: ByteArray, tags: Map<String, String>, coverJpeg: ByteArray?): FlacRewrite? {
        val parsed = parse(head) ?: return null
        val blocks = parsed.first
        if (blocks.isEmpty() || blocks[0].type != TYPE_STREAMINFO) return null

        var vendor = "Dezzy"
        val comments = ArrayList<Pair<String, String>>()
        val kept = ArrayList<FlacBlock>()
        for (b in blocks) {
            when (b.type) {
                TYPE_PADDING -> Unit
                TYPE_VORBIS_COMMENT -> readVorbis(b.data)?.let { (v, list) ->
                    if (v.isNotEmpty()) vendor = v
                    comments.addAll(list)
                }
                TYPE_PICTURE -> if (coverJpeg == null || pictureType(b.data) != PICTURE_FRONT_COVER) kept.add(b)
                else -> kept.add(b)
            }
        }
        val replaced = tags.keys.map { it.uppercase() }.toSet()
        val finalComments = comments.filter { it.first.uppercase() !in replaced } +
            tags.filter { it.value.isNotBlank() }.map { it.key.uppercase() to it.value }

        val out = ArrayList<FlacBlock>(kept.size + 2)
        out.addAll(kept)
        out.add(FlacBlock(TYPE_VORBIS_COMMENT, buildVorbis(vendor, finalComments)))
        if (coverJpeg != null) {
            val pic = buildPicture(coverJpeg)
            if (pic.size <= MAX_BLOCK) out.add(FlacBlock(TYPE_PICTURE, pic))
        }
        if (out.any { it.data.size > MAX_BLOCK }) return null
        return FlacRewrite(serialize(out), parsed.second)
    }

    /** Bloques de una cabecera FLAC, o null si no es válida o está cortada. */
    fun readBlocks(head: ByteArray): List<FlacBlock>? = parse(head)?.first

    /** Comentarios Vorbis (clave en mayúsculas → valor; el primero de cada clave) de una cabecera FLAC. */
    fun readComments(head: ByteArray): Map<String, String>? {
        val blocks = readBlocks(head) ?: return null
        val block = blocks.firstOrNull { it.type == TYPE_VORBIS_COMMENT } ?: return emptyMap()
        val list = readVorbis(block.data)?.second ?: return emptyMap()
        val map = LinkedHashMap<String, String>()
        for ((k, v) in list) map.getOrPut(k.uppercase()) { v }
        return map
    }

    private fun parse(head: ByteArray): Pair<List<FlacBlock>, Int>? {
        if (head.size < 8) return null
        if (head[0] != 'f'.code.toByte() || head[1] != 'L'.code.toByte() ||
            head[2] != 'a'.code.toByte() || head[3] != 'C'.code.toByte()
        ) return null
        val blocks = ArrayList<FlacBlock>()
        var pos = 4
        while (true) {
            if (pos + 4 > head.size) return null
            val h = head[pos].toInt() and 0xFF
            val last = h and 0x80 != 0
            val type = h and 0x7F
            val len = ((head[pos + 1].toInt() and 0xFF) shl 16) or
                ((head[pos + 2].toInt() and 0xFF) shl 8) or
                (head[pos + 3].toInt() and 0xFF)
            pos += 4
            if (type == 127 || pos + len > head.size) return null
            blocks.add(FlacBlock(type, head.copyOfRange(pos, pos + len)))
            pos += len
            if (last) return blocks to pos
        }
    }

    private fun serialize(blocks: List<FlacBlock>): ByteArray {
        val total = 4 + blocks.sumOf { 4 + it.data.size }
        val out = ByteArray(total)
        out[0] = 'f'.code.toByte(); out[1] = 'L'.code.toByte(); out[2] = 'a'.code.toByte(); out[3] = 'C'.code.toByte()
        var pos = 4
        blocks.forEachIndexed { i, b ->
            val last = if (i == blocks.lastIndex) 0x80 else 0
            out[pos] = (last or b.type).toByte()
            out[pos + 1] = (b.data.size shr 16).toByte()
            out[pos + 2] = (b.data.size shr 8).toByte()
            out[pos + 3] = b.data.size.toByte()
            b.data.copyInto(out, pos + 4)
            pos += 4 + b.data.size
        }
        return out
    }

    // ---- VORBIS_COMMENT (little endian) ----

    private fun buildVorbis(vendor: String, comments: List<Pair<String, String>>): ByteArray {
        val out = ByteSink()
        val v = vendor.encodeToByteArray()
        out.u32le(v.size); out.bytes(v)
        out.u32le(comments.size)
        for ((k, value) in comments) {
            val c = "$k=$value".encodeToByteArray()
            out.u32le(c.size); out.bytes(c)
        }
        return out.toByteArray()
    }

    private fun readVorbis(data: ByteArray): Pair<String, List<Pair<String, String>>>? {
        var pos = 0
        fun u32(): Int? {
            if (pos + 4 > data.size) return null
            val v = (data[pos].toInt() and 0xFF) or ((data[pos + 1].toInt() and 0xFF) shl 8) or
                ((data[pos + 2].toInt() and 0xFF) shl 16) or ((data[pos + 3].toInt() and 0xFF) shl 24)
            pos += 4
            return v
        }
        val vlen = u32() ?: return null
        if (vlen < 0 || pos + vlen > data.size) return null
        val vendor = data.decodeToString(pos, pos + vlen)
        pos += vlen
        val count = u32() ?: return vendor to emptyList()
        val list = ArrayList<Pair<String, String>>()
        repeat(count.coerceAtMost(100_000)) {
            val len = u32() ?: return vendor to list
            if (len < 0 || pos + len > data.size) return vendor to list
            val s = data.decodeToString(pos, pos + len)
            pos += len
            val eq = s.indexOf('=')
            if (eq > 0) list.add(s.substring(0, eq) to s.substring(eq + 1))
        }
        return vendor to list
    }

    // ---- PICTURE (big endian) ----

    private fun pictureType(data: ByteArray): Int =
        if (data.size < 4) -1
        else ((data[0].toInt() and 0xFF) shl 24) or ((data[1].toInt() and 0xFF) shl 16) or
            ((data[2].toInt() and 0xFF) shl 8) or (data[3].toInt() and 0xFF)

    private fun buildPicture(jpeg: ByteArray): ByteArray {
        val (w, h) = jpegSize(jpeg)
        val mime = "image/jpeg".encodeToByteArray()
        val out = ByteSink()
        out.u32be(PICTURE_FRONT_COVER)
        out.u32be(mime.size); out.bytes(mime)
        out.u32be(0) // descripción vacía
        out.u32be(w); out.u32be(h)
        out.u32be(24) // profundidad de color
        out.u32be(0) // colores indexados
        out.u32be(jpeg.size); out.bytes(jpeg)
        return out.toByteArray()
    }

    /** Ancho y alto leídos del marcador SOF del JPEG; (0, 0) si no se encuentran. */
    private fun jpegSize(d: ByteArray): Pair<Int, Int> {
        var i = 2
        while (i + 9 < d.size) {
            if (d[i] != 0xFF.toByte()) { i++; continue }
            val m = d[i + 1].toInt() and 0xFF
            if (m == 0xFF) { i++; continue }
            if (m in 0xC0..0xCF && m != 0xC4 && m != 0xC8 && m != 0xCC) {
                val h = ((d[i + 5].toInt() and 0xFF) shl 8) or (d[i + 6].toInt() and 0xFF)
                val w = ((d[i + 7].toInt() and 0xFF) shl 8) or (d[i + 8].toInt() and 0xFF)
                return w to h
            }
            if (m == 0xD8 || m == 0x01 || m in 0xD0..0xD7) { i += 2; continue }
            val len = ((d[i + 2].toInt() and 0xFF) shl 8) or (d[i + 3].toInt() and 0xFF)
            if (len < 2) return 0 to 0
            i += 2 + len
        }
        return 0 to 0
    }

    private class ByteSink {
        private var buf = ByteArray(256)
        private var size = 0

        private fun ensure(extra: Int) {
            if (size + extra > buf.size) buf = buf.copyOf(maxOf(buf.size * 2, size + extra))
        }

        fun bytes(b: ByteArray) {
            ensure(b.size)
            b.copyInto(buf, size)
            size += b.size
        }

        fun u32le(v: Int) {
            ensure(4)
            buf[size++] = v.toByte(); buf[size++] = (v shr 8).toByte()
            buf[size++] = (v shr 16).toByte(); buf[size++] = (v shr 24).toByte()
        }

        fun u32be(v: Int) {
            ensure(4)
            buf[size++] = (v shr 24).toByte(); buf[size++] = (v shr 16).toByte()
            buf[size++] = (v shr 8).toByte(); buf[size++] = v.toByte()
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }
}
