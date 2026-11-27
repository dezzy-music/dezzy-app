/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.dj

import com.dezzy.app.core.model.Track
import com.dezzy.app.feature.dj.TasteProfile.Companion.soft
import com.dezzy.app.source.youtube.YouTubeEndpoints
import com.dezzy.app.source.youtube.YouTubeMusicClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.random.Random

/** De dónde sale cada canción de la sesión. */
enum class DjKind { REQUEST, FAVORITES, THROWBACK, ARTIST_NEW, DISCOVERY }

/** Canción elegida por el DJ. */
class DjPick(
    val track: Track,
    val kind: DjKind,
    val seed: String? = null,
    /** Motivo concreto para el DJ («la pones mucho a esta hora», «suena en la radio de 3 de tus favoritas»…). */
    val reason: String? = null,
) {
    val id: String get() = track.videoId.orEmpty()
    fun copy(kind: DjKind = this.kind, seed: String? = this.seed, reason: String? = this.reason) =
        DjPick(track, kind, seed, reason)
}

/** Tramo de la cola con un mismo motivo. [start] es el índice de su primera canción. */
data class DjBlock(val kind: DjKind, val start: Int, val size: Int, val seed: String?, val reason: String? = null)

/** Sesión del DJ: canciones elegidas y sus bloques. */
class DjSet(val picks: List<DjPick>) {
    val tracks: List<Track> = picks.map { it.track }
    val blocks: List<DjBlock> = blocksOf(picks)
    fun blockStartingAt(index: Int): DjBlock? = blocks.firstOrNull { it.start == index }
    fun blockAt(index: Int): DjBlock? = blocks.lastOrNull { it.start <= index }

    companion object {
        val EMPTY = DjSet(emptyList())

        /** Junta canciones seguidas del mismo motivo en bloques; los sueltos se unen al anterior. */
        private fun blocksOf(picks: List<DjPick>): List<DjBlock> {
            val blocks = ArrayList<DjBlock>()
            picks.forEachIndexed { index, pick ->
                val last = blocks.lastOrNull()
                val sameRun = last != null && last.kind == pick.kind && (pick.kind != DjKind.DISCOVERY || last.seed == pick.seed)
                val orphan = last != null && picks.getOrNull(index + 1)?.let { it.kind != pick.kind } ?: true
                if (last != null && (sameRun || orphan)) {
                    blocks[blocks.lastIndex] = last.copy(size = last.size + 1)
                } else {
                    blocks += DjBlock(pick.kind, index, 1, pick.seed, pick.reason)
                }
            }
            return blocks
        }
    }
}

/**
 * Arma las sesiones del DJ a partir de tus gustos reales, al estilo del DJ de Spotify.
 *
 * 1. Perfil ([TasteProfile]): todo tu historial del último año, con más peso a lo reciente y a lo
 *    que sueles oír a esta hora; me gusta suman, saltos restan.
 * 2. Candidatas: en repeat, recuerdos (lo que ponías mucho y ya no), nuevas de tus artistas y
 *    descubrimientos (la radio de varias favoritas, puntuada y sin relleno).
 * 3. Orden con sentido: abre con algo que te encanta ahora; cada favorita va seguida de su racimo
 *    de descubrimientos; lo nuevo se dosifica según cuánto descubres tú.
 * 4. Variedad: un artista no vuelve hasta [MIN_GAP] canciones después y como mucho
 *    [MAX_PER_ARTIST] por sesión.
 *
 * Cada canción lleva su [DjPick.reason]: el DJ cuenta por qué viene con datos reales.
 */
class DjSetBuilder(private val client: YouTubeMusicClient) {

    // ── Catálogo ────────────────────────────────────────────────────────────────────────────────

    private suspend fun radioOf(id: String): List<Track> =
        djCatching { client.getWatchNext(id).getOrNull()?.items.orEmpty() }
            .getOrDefault(emptyList())
            .filter { !it.videoId.isNullOrBlank() }

    // ── Puntuación ──────────────────────────────────────────────────────────────────────────────

    /** Lo que sale de la radio de una semilla, con su posición (cuanto antes, más parecida). */
    private class Candidate(val song: Track) {
        var hits = 0
        var bestPos = Int.MAX_VALUE
        val seeds = LinkedHashMap<String, Int>()
    }

    /**
     * Junta las radios de varias semillas y puntúa cada canción contra el perfil. Devuelve sólo
     * lo que pasa los filtros, de mejor a peor. [exclude]: ids que ya están en la sesión.
     */
    private fun scoreRadios(
        radios: List<Pair<Track, List<Track>>>,
        profile: TasteProfile?,
        exclude: Set<String>,
        newOnly: Boolean,
    ): List<DjPick> {
        val byId = LinkedHashMap<String, Candidate>()
        for ((seed, list) in radios) {
            val seedTitle = seed.title
            list.forEachIndexed { pos, song ->
                val id = song.videoId ?: return@forEachIndexed
                if (id == seed.videoId || id in exclude) return@forEachIndexed
                val c = byId.getOrPut(id) { Candidate(song) }
                c.hits++
                c.bestPos = minOf(c.bestPos, pos)
                c.seeds[seedTitle] = minOf(c.seeds[seedTitle] ?: Int.MAX_VALUE, pos)
            }
        }
        val scored = byId.values.mapNotNull { c ->
            val id = c.song.videoId.orEmpty()
            val keys = DjText.artistKeys(c.song.artists)
            val variant = DjText.variantOf(c.song.title)
            if (profile == null) {
                if (variant != null) return@mapNotNull null
                return@mapNotNull c to (c.hits * 1.5 + 1.0 / (1 + c.bestPos / 6.0))
            }
            // Ya la conoces (aunque sea en otro vídeo): no es un descubrimiento.
            if (newOnly && ((profile.songScore[id] ?: 0.0) > 0.3 || profile.knows(c.song.title, c.song.artists))) return@mapNotNull null
            if (profile.songSkips(id) >= 2) return@mapNotNull null
            val skipRatio = profile.artistSkipRatio(keys)
            if (skipRatio > 0.6) return@mapNotNull null
            var score = 1.6 * (c.hits - 1) + 1.0 / (1 + c.bestPos / 6.0) + 1.1 * soft(profile.artistAffinity(keys))
            score -= 4.0 * skipRatio + 1.5 * profile.songSkips(id)
            if (variant != null && !profile.likesVariant(variant)) score -= 4.0
            val seconds = c.song.durationSeconds ?: 200L
            if (seconds < 75 || seconds > 480) score -= 1.5
            if (score < -1.0) null else c to score
        }
        return scored.sortedByDescending { it.second }.map { (c, _) ->
            val keys = DjText.artistKeys(c.song.artists)
            val seed = c.seeds.minByOrNull { it.value }?.key
            val knownArtist = profile != null && profile.artistAffinity(keys) >= 1.0
            if (knownArtist && profile != null) {
                val name = c.song.artists.substringBefore(',').trim().ifBlank { c.song.artists }
                val plays = profile.topArtistOf(keys)?.let { profile.artistPlays[it] } ?: 0
                DjPick(
                    c.song, DjKind.ARTIST_NEW, seed,
                    "Es de $name, a quien escucha mucho" + (if (plays >= 5) " ($plays veces este año)" else "") + ", y todavía no conoce esta.",
                )
            } else {
                val why = when {
                    c.hits >= 3 -> "Aparece en la radio de ${c.hits} de sus favoritas a la vez: encaja con su gusto."
                    c.hits == 2 -> "Sale en la radio de dos de sus favoritas" + (seed?.let { ", entre ellas «$it»" } ?: "") + "."
                    else -> seed?.let { "Suena parecida a «$it», que le encanta." }
                }
                DjPick(c.song, DjKind.DISCOVERY, seed, why)
            }
        }
    }

    /** Orden «con ruido»: respeta la puntuación pero cada sesión sale distinta. */
    private fun <T> noisy(items: List<T>, score: (T) -> Double): List<T> =
        items.map { it to score(it) * Random.nextDouble(0.75, 1.25) }.sortedByDescending { it.second }.map { it.first }

    // ── Variedad ────────────────────────────────────────────────────────────────────────────────

    /**
     * Reordena y recorta [picks] para que ningún artista se repita antes de [minGap] canciones
     * ni pase de [maxPerArtist]. Mantiene el orden de entrada todo lo posible: si una canción no
     * cabe aún, se adelanta la siguiente que sí, y la otra espera su turno. Lo que no cabe se
     * descarta (mejor una sesión más corta que repetir).
     */
    fun diversify(
        picks: List<DjPick>,
        minGap: Int = MIN_GAP,
        maxPerArtist: Int = MAX_PER_ARTIST,
        focus: Set<String> = emptySet(),
        limit: Int = SESSION_SIZE,
    ): List<DjPick> {
        val pool = picks.distinctBy { it.id }.toMutableList()
        val out = ArrayList<DjPick>()
        val count = HashMap<String, Int>()
        val titles = HashSet<String>()
        while (out.size < limit && pool.isNotEmpty()) {
            val recent = out.takeLast(minGap).flatMapTo(HashSet()) { artistKeys(it.track) - focus }
            val lastFocus = out.lastOrNull()?.let { artistKeys(it.track) intersect focus }.orEmpty()
            val index = pool.indexOfFirst { pick ->
                val keys = artistKeys(pick.track)
                (keys - focus).none { it in recent } &&
                    (keys intersect focus).none { it in lastFocus } &&
                    keys.all { (count[it] ?: 0) < if (it in focus) MAX_FOCUS else maxPerArtist }
            }
            if (index < 0) break
            val pick = pool.removeAt(index)
            // La misma canción en otro vídeo (audio, videoclip, lyric video) no se repite.
            if (!titles.add(DjText.songKey(pick.track.title, pick.track.artists))) continue
            artistKeys(pick.track).forEach { count[it] = (count[it] ?: 0) + 1 }
            out += pick
        }
        return out
    }

    // ── Sesión personal ─────────────────────────────────────────────────────────────────────────

    /**
     * Sesión personal sin petición. Vacía si todavía no hay historial (entonces el DJ usa la
     * búsqueda de siempre).
     */
    suspend fun buildSession(profile: TasteProfile): DjSet = withContext(Dispatchers.Default) {
        if (profile.isEmpty) return@withContext DjSet.EMPTY

        // En repeat: reciente + esta hora; nada de lo que sonó en las últimas 3 horas ni lo que saltas.
        val heavyIds = profile.songs.keys.filter { id ->
            profile.songSkips(id) < 2 && profile.daysSince(id) > 0.125 &&
                ((profile.recentScore[id] ?: 0.0) >= 1.0 || (profile.hourScore[id] ?: 0.0) >= 1.5 || id in profile.liked)
        }
        val heavy = noisy(heavyIds) { id ->
            (profile.recentScore[id] ?: 0.0) + 0.8 * (profile.hourScore[id] ?: 0.0) + 0.3 * (profile.songScore[id] ?: 0.0)
        }.take(30).mapNotNull { id ->
            val song = profile.songs[id] ?: return@mapNotNull null
            val recent = (profile.recentScore[id] ?: 0.0).roundToInt()
            val atThisHour = (profile.hourScore[id] ?: 0.0) >= 2.0
            val why = when {
                atThisHour -> "Es de las que más pone a esta hora del día."
                recent >= 5 -> "La ha puesto $recent veces en las últimas dos semanas."
                id in profile.liked -> "Le dio me gusta y la sigue escuchando."
                else -> "Es de sus favoritas de estos días."
            }
            DjPick(song, DjKind.FAVORITES, reason = why)
        }
        if (heavy.isEmpty()) return@withContext DjSet.EMPTY

        // Recuerdos de verdad: mucho antes, casi nada ahora.
        val throwbackIds = profile.songs.keys.filter { id ->
            val old = profile.oldScore[id] ?: 0.0
            profile.songSkips(id) < 2 && profile.daysSince(id) > 45 &&
                (profile.recentScore[id] ?: 0.0) < 0.5 && (old >= 2.0 || id in profile.liked)
        }
        val throwback = noisy(throwbackIds) { id ->
            (profile.oldScore[id] ?: 0.0) + if (id in profile.liked) 3.0 else 0.0
        }.take(20).mapNotNull { id ->
            val song = profile.songs[id] ?: return@mapNotNull null
            val months = (profile.daysSince(id) / 30).roundToInt().coerceAtLeast(1)
            val why = if (months >= 12) "La ponía mucho y no la escucha desde hace más de un año."
            else "La ponía mucho y lleva $months ${if (months == 1) "mes" else "meses"} sin escucharla."
            DjPick(song, DjKind.THROWBACK, reason = why)
        }

        // Semillas: 5 favoritas de artistas distintos; la primera abre la sesión.
        val seeds = diversify(heavy, minGap = 5, maxPerArtist = 1, limit = 5)
        val inSession = (heavy + throwback).mapTo(HashSet()) { it.id }
        val radios = coroutineScope {
            seeds.map { seed -> async { seed.track to radioOf(seed.id) } }.awaitAll()
        }
        val fresh = scoreRadios(radios, profile, inSession, newOnly = true)
        val artistNew = ArrayDeque(fresh.filter { it.kind == DjKind.ARTIST_NEW })
        // Racimos por semilla: cada favorita va seguida de lo que salió de su radio.
        val clusters = fresh.filter { it.kind == DjKind.DISCOVERY }.groupBy { it.seed }
            .mapValues { ArrayDeque(it.value) }.toMutableMap()

        // Dosis de lo nuevo según cuánto descubres tú (entre 1 de cada 5 y algo más de la mitad).
        val newTarget = (SESSION_SIZE * profile.explorationRate).roundToInt()
        val heavyQ = ArrayDeque(heavy.filter { p -> seeds.none { it.id == p.id } })
        val throwQ = ArrayDeque(throwback)
        val ordered = ArrayList<DjPick>()
        var newCount = 0
        var round = 0
        while (ordered.size < SESSION_SIZE * 2 && (heavyQ.isNotEmpty() || seeds.size > round || clusters.values.any { it.isNotEmpty() })) {
            val seed = seeds.getOrNull(round)
            // Favoritas: la semilla y otras dos.
            if (seed != null) ordered += seed
            repeat(if (seed != null) 2 else 3) { heavyQ.removeFirstOrNull()?.let { ordered += it } }
            // Su racimo de descubrimientos (o el más grande que quede).
            val clusterKey = seed?.track?.title
            val cluster = clusters[clusterKey]?.takeIf { it.isNotEmpty() } ?: clusters.values.maxByOrNull { it.size }
            val wantNew = newCount < newTarget
            if (wantNew && cluster != null) repeat(3) { cluster.removeFirstOrNull()?.let { ordered += it; newCount++ } }
            // Alternan nuevas de tus artistas y recuerdos.
            if (round % 2 == 0 && artistNew.isNotEmpty() && newCount < newTarget) {
                repeat(2) { artistNew.removeFirstOrNull()?.let { ordered += it; newCount++ } }
            } else {
                repeat(3) { throwQ.removeFirstOrNull()?.let { ordered += it } }
            }
            round++
            if (round > 20) break
        }
        DjSet(diversify(ordered))
    }

    /**
     * Más como [seed] (te gustó o la escuchaste entera): su radio puntuada, para meterla en la
     * sesión justo después. Sin perfil, la radio tal cual sin relleno.
     */
    suspend fun moreLike(seed: Track, profile: TasteProfile?, exclude: Set<String>, count: Int = 3): List<DjPick> =
        withContext(Dispatchers.Default) {
            val id = seed.videoId ?: return@withContext emptyList()
            val title = seed.title
            scoreRadios(listOf(seed to radioOf(id)), profile, exclude, newOnly = true)
                .take(count)
                .map { it.copy(kind = DjKind.DISCOVERY, seed = title, reason = "Como le gustó «$title», el DJ trae más en esa línea.") }
        }

    // ── Petición ────────────────────────────────────────────────────────────────────────────────

    /**
     * Sesión a partir de una petición («algo para entrenar», «pon Peso Pluma»). Los resultados se
     * reordenan por tu gusto (primero artistas que escuchas, fuera lo que saltas y el relleno) y
     * los descubrimientos salen de la radio de los dos resultados que más encajan contigo.
     * Si la petición nombra a un artista, ése puede repetirse (alternando con otros).
     */
    suspend fun buildFromRequest(query: String, results: List<Track>, profile: TasteProfile? = null): DjSet =
        withContext(Dispatchers.Default) {
            if (results.isEmpty()) return@withContext DjSet.EMPTY
            val asked = DjText.normalize(query)
            val n = results.size.toDouble()
            val ranked = results.withIndex().mapNotNull { (i, song) ->
                val keys = DjText.artistKeys(song.artists)
                val variant = DjText.variantOf(song.title)
                if (variant != null && profile?.likesVariant(variant) != true && !asked.contains(variant)) return@mapNotNull null
                var score = 2.0 * (n - i) / n
                if (profile != null) {
                    score += 1.2 * soft(profile.artistAffinity(keys)) - 3.0 * profile.artistSkipRatio(keys) -
                        2.0 * profile.songSkips(song.videoId.orEmpty())
                }
                song to score
            }.sortedByDescending { it.second }.map { it.first }.ifEmpty { results }

            val requested = ranked.map { DjPick(it, DjKind.REQUEST, reason = "Es lo que pidió.") }
            val focus = requested.flatMapTo(HashSet()) { artistKeys(it.track) }.filterTo(HashSet()) { it.length > 2 && asked.contains(it) }
            val exclude = results.mapNotNullTo(HashSet()) { it.videoId }
            val seeds = requested.take(2).map { it.track }
            val radios = coroutineScope { seeds.map { s -> async { s to radioOf(s.videoId.orEmpty()) } }.awaitAll() }
            // En una petición sí valen canciones que ya conoces: sólo se filtra el relleno y lo que saltas.
            val discovery = scoreRadios(radios, profile, exclude, newOnly = false)
                .map { it.copy(kind = DjKind.DISCOVERY, reason = "Va con lo que pidió" + (it.seed?.let { s -> " y suena como «$s»" } ?: "") + ".") }
            val ordered = ArrayList<DjPick>()
            val req = ArrayDeque(requested)
            val disc = ArrayDeque(discovery)
            while (req.isNotEmpty() || disc.isNotEmpty()) {
                repeat(4) { req.removeFirstOrNull()?.let { ordered += it } }
                repeat(3) { disc.removeFirstOrNull()?.let { ordered += it } }
            }
            val picked = diversify(ordered, minGap = 4, focus = focus, limit = 30)
            // Si la variedad dejó casi nada (búsqueda de un solo artista sin radio), mejor algo que nada.
            DjSet(picked.ifEmpty { requested.take(1) })
        }

    // ── Vibras ──────────────────────────────────────────────────────────────────────────────────

    /** Palabras con contenido de la petición, sin tildes. */
    fun requestWords(text: String): Set<String> =
        DjText.words(text).filter { it.length >= 4 && it !in EMPTY_WORDS }.toSet()

    /**
     * La canción sólo «encaja» porque su título repite palabras de la petición (pides «tu vibra» y
     * sale una canción que se llama «Vibra»). Si la palabra está en el artista, sí vale: «pon Peso Pluma».
     */
    private fun literalMatch(song: Track, words: Set<String>): Boolean {
        if (words.isEmpty()) return false
        val title = DjText.normalize(song.title)
        val artist = DjText.normalize(song.artists)
        return words.any { title.contains(it) && !artist.contains(it) }
    }

    /** Canciones de las listas que mejor encajan con [query]: traen el ambiente, no el texto. */
    private suspend fun vibeSongs(query: String): List<Track> = coroutineScope {
        val found = djCatching {
            client.search(query, YouTubeEndpoints.FILTER_PLAYLISTS).getOrNull()?.playlists.orEmpty()
        }.getOrDefault(emptyList())
        val lists = found.distinctBy { it.id }.take(3)
        val pages = lists.map { pl ->
            async { djCatching { client.getPlaylist(pl.id).getOrNull()?.tracks.orEmpty() }.getOrDefault(emptyList()).take(25) }
        }.awaitAll()
        // Intercaladas: una de cada lista, para no depender de una sola.
        val out = ArrayList<Track>()
        for (i in 0 until (pages.maxOfOrNull { it.size } ?: 0)) pages.forEach { page -> page.getOrNull(i)?.let { out += it } }
        out.filter { !it.videoId.isNullOrBlank() }.distinctBy { it.videoId }
    }

    /**
     * Sesión para una vibra o petición. El ambiente sale de listas de ese estilo, se quitan las
     * canciones que sólo coinciden por el título, se ordena por tu gusto y se mezclan algunas tuyas
     * de los mismos artistas, para que sean canciones que van contigo.
     */
    suspend fun buildForRequest(request: String, query: String, profile: TasteProfile?): DjSet =
        withContext(Dispatchers.Default) {
            val words = requestWords(request)
            val vibe = vibeSongs(query)
            val songs = djCatching {
                client.search(query, YouTubeEndpoints.FILTER_SONGS).getOrNull()?.songs.orEmpty()
            }.getOrDefault(emptyList()).filter { !it.videoId.isNullOrBlank() }
            val clean = (vibe + songs).distinctBy { it.videoId }.filterNot { literalMatch(it, words) }
            val set = buildFromRequest(query, clean.ifEmpty { songs }, profile)
            if (profile == null || profile.isEmpty || set.picks.isEmpty()) return@withContext set

            // Tuyas que van con la vibra: de los mismos artistas que trae la sesión.
            val vibeArtists = clean.flatMapTo(HashSet()) { s -> DjText.artistKeys(s.artists) }
            val inSet = set.picks.mapTo(HashSet()) { it.id }
            val mine = profile.songScore.entries.sortedByDescending { it.value }.asSequence()
                .mapNotNull { (id, _) -> profile.songs[id]?.takeIf { id !in inSet && profile.songSkips(id) < 2 } }
                .filter { song -> DjText.artistKeys(song.artists).any { it in vibeArtists } }
                .take(6)
                .map { DjPick(it, DjKind.FAVORITES, reason = "Es de las suyas y va con lo que pidió.") }
                .toList()
            if (mine.isEmpty()) return@withContext set
            val mixed = ArrayList<DjPick>(set.picks)
            mine.forEachIndexed { i, pick -> mixed.add(minOf(mixed.size, 2 + i * 4), pick) }
            DjSet(diversify(mixed, limit = 30))
        }

    // ── Lo que dice el DJ ───────────────────────────────────────────────────────────────────────

    companion object {
        private const val MIN_GAP = 4
        private const val MAX_PER_ARTIST = 2
        private const val MAX_FOCUS = 8
        private const val SESSION_SIZE = 40

        /** Palabras que no dicen nada de la música que se quiere («elige tu vibra para esta noche»). */
        private val EMPTY_WORDS = setOf(
            "vibra", "vibras", "vibe", "vibes", "elige", "eligeme", "ponme", "pon", "quiero", "algo", "para", "esta",
            "este", "estos", "estas", "noche", "dia", "tarde", "manana", "musica", "canciones", "cancion", "mezcla",
            "mix", "sesion", "playlist", "lista", "como", "una", "unos", "unas", "tus", "mis", "con", "sin", "que",
            "the", "for", "some", "music", "songs", "tonight", "your",
        )

        fun artistKeys(track: Track): Set<String> = DjText.artistKeys(track.artists)

        /** Lo que el DJ debe contar al empezar [block]. Se pasa a Gemini como contexto. */
        fun describe(block: DjBlock, title: String, artist: String): String {
            val why = when (block.kind) {
                DjKind.FAVORITES -> "Ahora vienen canciones que el oyente tiene en repeat."
                DjKind.THROWBACK -> "Ahora un regreso al pasado: canciones que el oyente escuchaba mucho y no pone desde hace tiempo."
                DjKind.ARTIST_NEW -> "Ahora canciones nuevas para el oyente de artistas que ya le gustan."
                DjKind.DISCOVERY -> "Ahora descubrimientos nuevos para el oyente" + (block.seed?.let { ", parecidos a «$it»" } ?: "") + "."
                DjKind.REQUEST -> "Seguimos con lo que pidió el oyente."
            }
            val fact = block.reason?.let { " Dato real de su historial (úsalo con naturalidad, sin sonar a estadística): $it" }.orEmpty()
            return "$why$fact Cuenta en una frase por qué viene «$title» de $artist, con emoción y cercanía."
        }

        /** Frase de respaldo cuando la IA no responde: también explica el motivo del bloque. */
        fun fallbackLine(block: DjBlock?, title: String, artist: String, username: String, djName: String, first: Boolean): String {
            val hi = if (first) "¡Hola${username.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""}! Soy $djName. " else ""
            val body = when (block?.kind) {
                DjKind.FAVORITES -> listOf(
                    "Empezamos con las que no te sacas de la cabeza: aquí va «$title» de $artist.",
                    "Toca un bloque de tus favoritas. Arrancamos con $artist.",
                )
                DjKind.THROWBACK -> listOf(
                    "Vamos atrás en el tiempo: hace mucho que no ponías «$title».",
                    "Un regreso al pasado con $artist. A ver si te acuerdas de ésta.",
                )
                DjKind.ARTIST_NEW -> listOf(
                    "Tú ya conoces a $artist, pero ésta todavía no: «$title».",
                    "Algo de $artist que aún no habías escuchado. Creo que te va a gustar.",
                )
                DjKind.DISCOVERY -> listOf(
                    "Ahora algo nuevo para ti" + (block.seed?.let { ", parecido a «$it»" } ?: "") + ": «$title» de $artist.",
                    "Te traigo descubrimientos. Empezamos con $artist, creo que te va a gustar.",
                )
                DjKind.REQUEST, null -> listOf(
                    "Va lo que pediste: «$title» de $artist.",
                    "Seguimos con tu petición. Que suene $artist.",
                )
            }
            return hi + body.random()
        }
    }
}
