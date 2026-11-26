/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.player

import com.dezzy.app.core.model.QueueItem
import com.dezzy.app.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlin.random.Random

/** Modo de repetición de la cola. */
@Serializable
enum class RepeatMode { OFF, ALL, ONE }

/** Estado observable de la cola. */
@Serializable
data class QueueState(
    val items: List<QueueItem> = emptyList(),
    val index: Int = -1,
    val shuffled: Boolean = false,
    val repeat: RepeatMode = RepeatMode.OFF,
    val radio: Boolean = false,
    val source: String? = null,
) {
    val current: QueueItem? get() = items.getOrNull(index)
}

/** Qué hacer cuando termina la canción actual. */
sealed interface EndedAction {
    data object RepeatOne : EndedAction
    data class Load(val index: Int) : EndedAction
    data object Stop : EndedAction
}

/** Qué hacer al pulsar «anterior». */
sealed interface PreviousAction {
    data object Restart : PreviousAction
    data class Load(val index: Int) : PreviousAction
}

/** Copia persistible de la cola (tramo de como mucho 300 items). */
@Serializable
data class QueueSnapshot(
    val items: List<QueueItem>,
    val index: Int,
    val manualIndices: List<Int>,
    val repeat: RepeatMode,
    val radio: Boolean,
    val source: String?,
    val positionMs: Long,
)

/**
 * Cola del reproductor (lógica pura, portada del comportamiento de Dezzy Desktop).
 *
 * NO es thread-safe: debe usarse desde un solo hilo (el principal). [state] se actualiza
 * tras cada operación que cambie algo.
 */
class PlayerQueue(private val random: Random = Random.Default) {

    private val items = mutableListOf<QueueItem>()
    private var index = -1

    /** No nulo ⇔ aleatorio activo; guarda el orden original. */
    private var original: MutableList<QueueItem>? = null

    /** queueEntryId de las canciones añadidas a mano. */
    private val manual = mutableSetOf<String>()
    private var repeat = RepeatMode.OFF
    private var radio = false
    private var source: String? = null
    private var counter = 0

    private val _state = MutableStateFlow(QueueState())
    val state: StateFlow<QueueState> = _state.asStateFlow()

    private fun publish() {
        _state.value = QueueState(
            items = items.toList(),
            index = index,
            shuffled = original != null,
            repeat = repeat,
            radio = radio,
            source = source,
        )
    }

    private fun wrap(track: Track): QueueItem {
        counter++
        return QueueItem(queueEntryId = "q${counter}_${random.nextInt(100000, 999999)}", track = track)
    }

    /** Reemplaza la cola por [tracks]. Devuelve false (sin cambios) si está vacía. */
    fun load(tracks: List<Track>, start: Int = 0, source: String? = null): Boolean {
        if (tracks.isEmpty()) return false
        items.clear()
        items.addAll(tracks.map { wrap(it) })
        index = start.coerceIn(0, items.size - 1)
        original = null
        radio = false
        manual.clear()
        this.source = source
        publish()
        return true
    }

    fun loadRadio(track: Track, source: String) {
        items.clear()
        items.add(wrap(track))
        index = 0
        original = null
        radio = true
        manual.clear()
        this.source = source
        publish()
    }

    fun setIndex(i: Int) {
        if (i in items.indices) {
            index = i
            publish()
        }
    }

    fun playNext(track: Track) {
        insert(track, index + 1)
    }

    /** Detrás de lo ya añadido a mano, delante del resto. */
    fun addToQueue(track: Track) {
        var at = index + 1
        while (at < items.size && items[at].queueEntryId in manual) at++
        insert(track, at)
    }

    private fun insert(track: Track, at: Int) {
        val item = wrap(track)
        manual.add(item.queueEntryId)
        if (index < 0) {
            items.clear()
            items.add(item)
            index = 0
        } else {
            items.add(at.coerceIn(0, items.size), item)
            original?.add(item)
        }
        publish()
    }

    fun removeAt(i: Int) {
        if (i == index || i !in items.indices) return
        val removed = items.removeAt(i)
        manual.remove(removed.queueEntryId)
        original?.removeAll { it.queueEntryId == removed.queueEntryId }
        if (i < index) index--
        publish()
    }

    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices || from == to) return
        val item = items.removeAt(from)
        items.add(to, item)
        index = when {
            from == index -> to
            from < index && to >= index -> index - 1
            from > index && to <= index -> index + 1
            else -> index
        }
        publish()
    }

    /** Deja sólo la canción actual (o todo vacío si no hay). */
    fun clear() {
        val cur = items.getOrNull(index)
        items.clear()
        if (cur != null) {
            items.add(cur)
            index = 0
        } else {
            index = -1
        }
        original = null
        manual.clear()
        publish()
    }

    /** Sustituye todo lo que va detrás de la actual por [tracks] (no manuales). */
    fun replaceUpcoming(tracks: List<Track>) {
        val head = if (index >= 0) items.take(index + 1) else emptyList()
        items.clear()
        items.addAll(head)
        items.addAll(tracks.map { wrap(it) })
        original = null
        manual.retainAll(items.map { it.queueEntryId }.toSet())
        radio = false
        publish()
    }

    fun toggleShuffle() {
        val orig = original
        if (orig != null) {
            val currentId = items.getOrNull(index)?.queueEntryId
            val idx = orig.indexOfFirst { it.queueEntryId == currentId }
            items.clear()
            items.addAll(orig)
            original = null
            index = if (idx >= 0) idx else 0
        } else {
            val cur = items.getOrNull(index) ?: return
            val rest = items.filterIndexed { i, _ -> i != index }.toMutableList()
            original = items.toMutableList()
            for (i in rest.lastIndex downTo 1) {
                val j = random.nextInt(i + 1)
                val tmp = rest[i]; rest[i] = rest[j]; rest[j] = tmp
            }
            items.clear()
            items.add(cur)
            items.addAll(rest)
            index = 0
        }
        publish()
    }

    fun cycleRepeat() {
        repeat = when (repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        publish()
    }

    /** Qué hacer al acabar la canción. No cambia index: quien llama hace setIndex al cargar. */
    fun onEnded(): EndedAction = when {
        repeat == RepeatMode.ONE -> EndedAction.RepeatOne
        index + 1 < items.size -> EndedAction.Load(index + 1)
        repeat == RepeatMode.ALL && items.isNotEmpty() -> EndedAction.Load(0)
        else -> EndedAction.Stop
    }

    /** Siguiente al pulsar ⏭ (no mira ONE). */
    fun nextIndex(): Int? = when {
        index + 1 < items.size -> index + 1
        repeat == RepeatMode.ALL && items.isNotEmpty() -> 0
        else -> null
    }

    /** Siguiente tras un error de reproducción: nunca da la vuelta. */
    fun nextIndexAfterError(): Int? = if (index + 1 < items.size) index + 1 else null

    fun previous(positionMs: Long): PreviousAction =
        if (positionMs > 3000 || index <= 0) PreviousAction.Restart else PreviousAction.Load(index - 1)

    fun upcoming(count: Int = 2): List<QueueItem> {
        if (index < 0) return emptyList()
        return items.drop(index + 1).take(count)
    }

    fun needsRadioExtension(): Boolean = radio && index >= items.size - 2

    /** Añade al final los tracks con videoId no nulo que aún no estén en la cola. */
    fun appendRadio(tracks: List<Track>) {
        val seen = items.mapNotNullTo(mutableSetOf()) { it.track.videoId }
        var changed = false
        for (t in tracks) {
            val id = t.videoId ?: continue
            if (!seen.add(id)) continue
            val item = wrap(t)
            items.add(item)
            original?.add(item)
            changed = true
        }
        if (changed) publish()
    }

    /** Tramo de como mucho 300 items desde max(0, index - 20). El aleatorio no se guarda. */
    fun snapshot(positionMs: Long): QueueSnapshot {
        val start = maxOf(0, index - 20)
        val end = minOf(items.size, start + 300)
        val slice = if (start < end) items.subList(start, end).toList() else emptyList()
        val manualIdx = slice.mapIndexedNotNull { i, it -> if (it.queueEntryId in manual) i else null }
        return QueueSnapshot(
            items = slice,
            index = if (index < 0) -1 else index - start,
            manualIndices = manualIdx,
            repeat = repeat,
            radio = radio,
            source = source,
            positionMs = positionMs,
        )
    }

    fun restore(s: QueueSnapshot) {
        items.clear()
        items.addAll(s.items)
        index = if (items.isEmpty()) -1 else s.index.coerceIn(0, items.size - 1)
        original = null
        manual.clear()
        s.manualIndices.forEach { i -> items.getOrNull(i)?.let { manual.add(it.queueEntryId) } }
        repeat = s.repeat
        radio = s.radio
        source = s.source
        // Evita choques de ids: el contador sube por encima del mayor número tras "q".
        val maxN = items.maxOfOrNull { it.queueEntryId.removePrefix("q").substringBefore('_').toIntOrNull() ?: 0 } ?: 0
        if (maxN > counter) counter = maxN
        publish()
    }
}
