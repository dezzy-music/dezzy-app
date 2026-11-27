/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

// Canvas IA: visualizador procedural de «tipografía cinética maximalista». Compone, con la letra
// sincronizada de la canción que suena, palabras gigantes (la «héroe» de cada línea) junto a palabras
// pequeñas y formas como puntuación visual, coloreado con la paleta de la carátula. Es determinista:
// la semilla sale del id de la canción, así cada tema siempre se ve igual.

package com.dezzy.app.ui.player.canvas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dezzy.app.feature.lyrics.LyricsManager
import com.dezzy.app.feature.lyrics.LyricsState
import com.dezzy.app.feature.lyrics.SyncedLine
import com.dezzy.app.feature.lyrics.activeLineIndex
import com.dezzy.app.feature.player.PlayerController
import com.dezzy.app.ui.navigation.PlatformBackHandler
import com.dezzy.app.ui.player.kit.loadArtPalette
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Motor de composición procedural
// ---------------------------------------------------------------------------

/** Personalidades de maquetación. Se elige una por canción, de forma determinista. */
private enum class CanvasTemplate(
    val heroRotation: Float,
    val jitter: Float,
    val bias: Alignment.Horizontal,
    val arrangement: Arrangement.Horizontal,
) {
    CENTER_STACK(0f, 3f, Alignment.CenterHorizontally, Arrangement.Center),
    LEFT_COLUMN(-2f, 5f, Alignment.Start, Arrangement.Start),
    DIAGONAL_SCATTER(-6f, 10f, Alignment.CenterHorizontally, Arrangement.Center),
}

private val CANVAS_TEMPLATES = CanvasTemplate.entries

/**
 * El ánimo de la canción sesga la energía de la composición: cuánto se inclinan las palabras, cuántas
 * formas aparecen y qué plantilla se favorece. Sin la capa de IA el ánimo es NEUTRAL.
 */
private enum class CanvasMood(
    val jitterScale: Float,
    val iconChance: Float,
    val templateBias: CanvasTemplate?,
) {
    HYPE(1.6f, 0.32f, CanvasTemplate.DIAGONAL_SCATTER),
    ENERGETIC(1.4f, 0.28f, CanvasTemplate.DIAGONAL_SCATTER),
    PLAYFUL(1.5f, 0.30f, null),
    ROMANTIC(0.5f, 0.12f, CanvasTemplate.CENTER_STACK),
    DREAMY(0.6f, 0.14f, CanvasTemplate.CENTER_STACK),
    MELANCHOLIC(0.5f, 0.10f, CanvasTemplate.LEFT_COLUMN),
    DARK(0.7f, 0.12f, CanvasTemplate.LEFT_COLUMN),
    NEUTRAL(1f, 0.20f, null),
}

/** Resultado de la capa de IA para una canción: ánimo y palabra héroe por índice de línea. */
private class AiCanvasLayer(val mood: CanvasMood, val heroes: Map<Int, String>)

/** Una consulta por canción y sesión: la misma canción no vuelve a gastar la cuota de la clave. */
private object AiCanvasLayerCache {
    val byTrack = HashMap<String, AiCanvasLayer>()
}

private const val AI_MAX_LINES = 80

/** Pide a Gemini (clave del usuario) el ánimo y la palabra a destacar de cada línea. null si falla. */
private suspend fun requestAiLayer(
    http: io.ktor.client.HttpClient,
    key: String,
    title: String,
    artist: String,
    lines: List<CanvasLine>,
): AiCanvasLayer? {
    val numbered = lines.take(AI_MAX_LINES).mapIndexedNotNull { i, l ->
        l.text.trim().takeIf { it.isNotEmpty() }?.let { "$i: $it" }
    }
    if (numbered.isEmpty()) return null
    val moods = CanvasMood.entries.joinToString("|") { it.name }
    val system = """
        Eres director de arte de un canvas tipográfico para letras de canciones.
        Responde SOLO con un objeto JSON, sin texto alrededor, con esta forma:
        {"mood":"$moods","heroes":{"<número de línea>":"<una palabra exacta de esa línea>"}}
        Para cada línea elige la palabra con más carga emocional o el gancho; cópiala tal cual aparece.
    """.trimIndent()
    val prompt = "Canción: $title — $artist\n" + numbered.joinToString("\n")
    val raw = com.dezzy.app.feature.ai.DezzyIaClient(http)
        .chat(key, system, listOf(com.dezzy.app.feature.ai.IaTurn(true, prompt)))
        .getOrNull() ?: return null
    return runCatching {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(raw.substring(start, end + 1))
            as kotlinx.serialization.json.JsonObject
        val moodName = (obj["mood"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim()?.uppercase()
        val mood = CanvasMood.entries.firstOrNull { it.name == moodName } ?: CanvasMood.NEUTRAL
        val heroes = (obj["heroes"] as? kotlinx.serialization.json.JsonObject).orEmpty().mapNotNull { (k, v) ->
            val idx = k.trim().toIntOrNull() ?: return@mapNotNull null
            val word = (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.trim().orEmpty()
            if (word.isEmpty()) null else idx to word
        }.toMap()
        AiCanvasLayer(mood, heroes)
    }.getOrNull()
}

/** Formas usadas como «puntuación visual» entre palabras y como decorado: una familia geométrica y sólida. */
private enum class Glyph { SPARK, ASTERISK, BARS, BOLT, STAR, NOTE, HEART, ROSE, FLOWER, PETAL, BUTTERFLY, SPARKLE, LEAF, PERSON, FRAME }

private val CANVAS_ICON_POOL = arrayOf(
    Glyph.SPARK, Glyph.ASTERISK, Glyph.BARS, Glyph.BOLT, Glyph.STAR, Glyph.NOTE, Glyph.HEART,
)

/** Objetos decorativos que florecen DETRÁS de la letra; un grupo por ánimo. */
private val DECOR_ROMANTIC = arrayOf(Glyph.ROSE, Glyph.FLOWER, Glyph.PETAL, Glyph.HEART, Glyph.BUTTERFLY)
private val DECOR_DREAMY = arrayOf(Glyph.FLOWER, Glyph.PETAL, Glyph.BUTTERFLY, Glyph.SPARKLE, Glyph.STAR, Glyph.LEAF)
private val DECOR_HYPE = arrayOf(Glyph.STAR, Glyph.SPARKLE, Glyph.BOLT, Glyph.SPARK, Glyph.PERSON)
private val DECOR_DARK = arrayOf(Glyph.FRAME, Glyph.PERSON, Glyph.STAR, Glyph.LEAF)
private val DECOR_NEUTRAL = arrayOf(Glyph.FLOWER, Glyph.STAR, Glyph.SPARKLE, Glyph.FRAME, Glyph.PETAL, Glyph.LEAF, Glyph.PERSON)

private fun decorPoolFor(mood: CanvasMood): Array<Glyph> = when (mood) {
    CanvasMood.ROMANTIC -> DECOR_ROMANTIC
    CanvasMood.DREAMY -> DECOR_DREAMY
    CanvasMood.HYPE, CanvasMood.ENERGETIC, CanvasMood.PLAYFUL -> DECOR_HYPE
    CanvasMood.DARK, CanvasMood.MELANCHOLIC -> DECOR_DARK
    CanvasMood.NEUTRAL -> DECOR_NEUTRAL
}

/** Un objeto de fondo, colocado en el espacio normalizado [0,1] del escenario. */
private data class DecorItem(
    val glyph: Glyph,
    val xFraction: Float,
    val yFraction: Float,
    val sizeDp: Float,
    val rotation: Float,
    val baseAlpha: Float,
    val driftPhase: Float,
    val driftSpeed: Float,
    val spin: Float,
    val appearDelayMs: Int,
)

/** Objetos de fondo de una línea: deterministas por (semilla, línea), cambian al avanzar la canción. */
private fun buildDecor(lineIndex: Int, seed: Int, mood: CanvasMood): List<DecorItem> {
    val pool = decorPoolFor(mood)
    val rnd = Random(seed * 101L + lineIndex * 197L + 7L)
    val count = when (mood) {
        CanvasMood.HYPE, CanvasMood.ENERGETIC, CanvasMood.PLAYFUL -> 7
        CanvasMood.ROMANTIC, CanvasMood.DREAMY -> 6
        else -> 5
    }
    return List(count) {
        DecorItem(
            glyph = pool[rnd.nextInt(pool.size)],
            xFraction = rnd.nextFloat(),
            yFraction = rnd.nextFloat(),
            sizeDp = 40f + rnd.nextFloat() * 90f,
            rotation = rnd.nextFloat() * 360f,
            baseAlpha = 0.06f + rnd.nextFloat() * 0.12f,
            driftPhase = rnd.nextFloat() * TWO_PI_CANVAS,
            driftSpeed = 0.5f + rnd.nextFloat() * 1.2f,
            spin = (rnd.nextFloat() * 2f - 1f) * 18f,
            appearDelayMs = rnd.nextInt(600),
        )
    }
}

private const val TWO_PI_CANVAS = 6.2831855f

private val WORD_SPLIT = Regex("\\s+")
private val PUNCT_STRIP = Regex("[^\\p{L}\\p{N}']")

private val STOPWORDS = setOf(
    "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "at", "is", "it",
    "i", "you", "me", "my", "we", "he", "she", "they", "that", "this", "for", "with",
    "de", "la", "el", "y", "que", "en", "un", "una", "los", "las", "se", "tu", "mi",
    "no", "es", "lo", "por", "con", "su", "al", "del", "me", "te", "yo",
)

private sealed interface CanvasItem
private data class WordItem(
    val text: String,
    val sizeSp: Float,
    val weight: FontWeight,
    val rotation: Float,
    val isHero: Boolean,
    val isAccent: Boolean,
) : CanvasItem

private data class IconItem(val glyph: Glyph, val sizeDp: Float, val rotation: Float) : CanvasItem

/** Línea de letra para el canvas: tiempo de inicio en ms y texto. */
private data class CanvasLine(val time: Long, val text: String)

private fun normalizeWord(word: String): String = word.replace(PUNCT_STRIP, "").lowercase()

/** Las palabras que se repiten en toda la canción puntúan más (son el «gancho»). */
private fun buildRecurringWords(lines: List<CanvasLine>): Map<String, Int> {
    val counts = HashMap<String, Int>()
    for (line in lines) {
        for (raw in line.text.split(WORD_SPLIT)) {
            val w = normalizeWord(raw)
            if (w.length >= 3 && w !in STOPWORDS) {
                counts[w] = (counts[w] ?: 0) + 1
            }
        }
    }
    return counts.filterValues { it >= 2 }
}

private fun scoreWord(word: String, recurring: Map<String, Int>): Int {
    val norm = normalizeWord(word)
    if (norm.isEmpty()) return -1
    var score = norm.length
    if (word.length > 1 && word == word.uppercase() && word.any { it.isLetter() }) score += 5
    if (word.endsWith("!") || word.endsWith("?")) score += 3
    if (norm in STOPWORDS) score -= 4
    score += (recurring[norm] ?: 0) * 2
    return score
}

private fun heroSizeFor(length: Int): Float = when {
    length <= 3 -> 108f
    length <= 5 -> 94f
    length <= 7 -> 80f
    length <= 10 -> 66f
    length <= 14 -> 52f
    else -> 44f
}

/** Convierte una línea de letra en una composición de palabras y formas; determinista por (semilla, línea). */
private fun buildLineComposition(
    line: String,
    lineIndex: Int,
    seed: Int,
    recurring: Map<String, Int>,
    template: CanvasTemplate,
    mood: CanvasMood = CanvasMood.NEUTRAL,
    forcedHero: String? = null,
): List<CanvasItem> {
    val words = line.split(WORD_SPLIT).filter { it.isNotBlank() }
    if (words.isEmpty()) return emptyList()

    val rnd = Random(seed * 31L + lineIndex * 131L)

    // Se prefiere la palabra héroe elegida de fuera; si no, la heurística local.
    var heroIndex = -1
    if (!forcedHero.isNullOrBlank()) {
        val target = normalizeWord(forcedHero)
        if (target.isNotEmpty()) {
            heroIndex = words.indexOfFirst { normalizeWord(it) == target }
        }
    }
    if (heroIndex < 0) {
        var bestScore = Int.MIN_VALUE
        words.forEachIndexed { i, w ->
            val s = scoreWord(w, recurring)
            if (s > bestScore) {
                bestScore = s
                heroIndex = i
            }
        }
    }

    val jitter = template.jitter * mood.jitterScale
    val items = ArrayList<CanvasItem>(words.size + 3)
    var iconsUsed = 0
    val maxIcons = if (words.size <= 3) 1 else 3

    words.forEachIndexed { i, word ->
        if (i == heroIndex) {
            val hero = word.trim().uppercase()
            items.add(
                WordItem(
                    text = hero,
                    sizeSp = heroSizeFor(hero.length),
                    weight = FontWeight.Black,
                    rotation = template.heroRotation,
                    isHero = true,
                    isAccent = false,
                ),
            )
        } else {
            val roll = rnd.nextFloat()
            val accent = roll < 0.24f
            val size = if (accent) 38f + rnd.nextFloat() * 10f else 22f + rnd.nextFloat() * 12f
            items.add(
                WordItem(
                    text = word,
                    sizeSp = size,
                    weight = if (accent) FontWeight.ExtraBold else FontWeight.SemiBold,
                    rotation = (rnd.nextFloat() * 2f - 1f) * jitter,
                    isHero = false,
                    isAccent = accent,
                ),
            )
        }

        // A veces se suelta una forma tras la palabra.
        if (iconsUsed < maxIcons && rnd.nextFloat() < mood.iconChance) {
            items.add(
                IconItem(
                    glyph = CANVAS_ICON_POOL[rnd.nextInt(CANVAS_ICON_POOL.size)],
                    sizeDp = 26f + rnd.nextFloat() * 20f,
                    rotation = (rnd.nextFloat() * 2f - 1f) * (jitter + 4f),
                ),
            )
            iconsUsed++
        }
    }
    return items
}

// ---------- Color ----------

private fun Color.darken(factor: Float): Color =
    copy(red = red * factor, green = green * factor, blue = blue * factor)

/** (matiz 0..360, saturación 0..1, valor 0..1) */
private fun Color.toHsv(): FloatArray {
    val mx = max(red, max(green, blue))
    val mn = min(red, min(green, blue))
    val d = mx - mn
    val h = when {
        d == 0f -> 0f
        mx == red -> 60f * (((green - blue) / d) % 6f)
        mx == green -> 60f * (((blue - red) / d) + 2f)
        else -> 60f * (((red - green) / d) + 4f)
    }
    return floatArrayOf(if (h < 0f) h + 360f else h, if (mx == 0f) 0f else d / mx, mx)
}

/** Sube un color de la paleta hasta que sea lo bastante brillante y saturado como acento sobre fondo oscuro. */
private fun Color.vivid(): Color {
    val hsv = toHsv()
    return Color.hsv(hsv[0], hsv[1].coerceAtLeast(0.65f), hsv[2].coerceIn(0.78f, 1f))
}

private fun accentFrom(colors: List<Color>): Color {
    val best = colors.maxByOrNull {
        val hsv = it.toHsv()
        hsv[1] * hsv[2]
    }
    return best?.vivid() ?: Color(0xFFFF4D6D)
}

private fun backgroundColors(colors: List<Color>): List<Color> {
    val base = if (colors.isEmpty()) listOf(Color(0xFF141414), Color(0xFF1F1F1F)) else colors
    val dark = base.take(3).map { it.darken(0.30f) }
    return if (dark.size >= 2) dark else listOf(Color(0xFF141414), Color(0xFF202020))
}

// ---------------------------------------------------------------------------
// Pantalla
// ---------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiCanvasScreen(onClose: () -> Unit) {
    val player: PlayerController = koinInject()
    val lyricsManager: LyricsManager = koinInject()
    LaunchedEffect(lyricsManager) { lyricsManager.start() }

    val playback by player.state.collectAsState()
    val lyricsState by lyricsManager.state.collectAsState()
    val track = playback.queue.current?.track
    val trackId = track?.videoId ?: "${track?.title}|${track?.artists}"
    val position = playback.positionMs

    // Letra en líneas con tiempo (sincronizada) o, si no hay tiempos, cada línea de la letra suelta.
    val found = (lyricsState as? LyricsState.Found)?.result
    val isSynced = !found?.synced.isNullOrEmpty()
    val lines: List<CanvasLine> = remember(found) {
        when {
            found == null -> emptyList()
            !found.synced.isNullOrEmpty() -> found.synced.map { CanvasLine(it.startMs, it.text) }
            !found.plain.isNullOrBlank() -> found.plain.lines().mapIndexed { i, l -> CanvasLine(i * 100L, l) }
            else -> emptyList()
        }
    }
    val syncedLines: List<SyncedLine> = remember(found) { found?.synced.orEmpty() }

    // Semilla de diseño determinista: la misma canción siempre se ve igual.
    val seed = remember(trackId) { trackId.hashCode() }
    val baseTemplate = remember(seed) { CANVAS_TEMPLATES[seed.mod(CANVAS_TEMPLATES.size)] }
    val recurring = remember(lines) { buildRecurringWords(lines) }

    // Capa de IA (Ajustes › Letras › «Énfasis inteligente (IA)», con la clave de Gemini del usuario):
    // elige el ánimo de la canción y la palabra héroe de cada línea. Sin ella: neutro y heurística local.
    val smartEmphasis by com.dezzy.app.ui.player.kit.rememberPreference(
        com.dezzy.app.ui.screens.settings.prefs.LyricsSmartEmphasisKey, false,
    )
    val djSettings: com.dezzy.app.feature.dj.DjSettings = koinInject()
    val http: io.ktor.client.HttpClient = koinInject()
    var aiLayer by remember(trackId) { mutableStateOf(AiCanvasLayerCache.byTrack[trackId]) }
    LaunchedEffect(trackId, lines, smartEmphasis) {
        if (!smartEmphasis || lines.isEmpty() || aiLayer != null) return@LaunchedEffect
        val key = djSettings.geminiKey()
        if (key.isBlank()) return@LaunchedEffect
        requestAiLayer(http, key, track?.title.orEmpty(), track?.artists.orEmpty(), lines)?.let {
            AiCanvasLayerCache.byTrack[trackId] = it
            aiLayer = it
        }
    }
    val mood = if (smartEmphasis) aiLayer?.mood ?: CanvasMood.NEUTRAL else CanvasMood.NEUTRAL
    val aiHeroes = if (smartEmphasis) aiLayer?.heroes.orEmpty() else emptyMap()
    val template = remember(baseTemplate, mood) { mood.templateBias ?: baseTemplate }

    // Paleta de la carátula.
    var gradientColors by remember(trackId) { mutableStateOf<List<Color>>(emptyList()) }
    LaunchedEffect(trackId, track?.thumbnail) {
        val palette = loadArtPalette(track?.thumbnail, 16) ?: return@LaunchedEffect
        gradientColors = listOfNotNull(
            palette.darkVibrantSwatch,
            palette.dominantSwatch,
            palette.vibrantSwatch,
            palette.darkMutedSwatch,
            palette.mutedSwatch,
        ).map { Color(it.rgb or 0xFF000000.toInt()) }
    }

    val accent = remember(gradientColors) { accentFrom(gradientColors) }
    val bgColors = remember(gradientColors) { backgroundColors(gradientColors) }

    // Posición → línea actual (con 300 ms de adelanto).
    val currentLineIndex = remember(syncedLines, position, isSynced) {
        if (!isSynced || syncedLines.isEmpty()) -1 else activeLineIndex(syncedLines, position + 300L)
    }

    // Progreso por la línea ACTUAL (0..1): revela las palabras una a una al ritmo de la canción.
    val lineProgress = remember(currentLineIndex, position, lines, isSynced) {
        if (currentLineIndex < 0) {
            0f
        } else {
            val start = lines.getOrNull(currentLineIndex)?.time ?: 0L
            val end = lines.getOrNull(currentLineIndex + 1)?.time
                ?: (start + if (isSynced) 4000L else 2600L)
            val span = (end - start).coerceAtLeast(1L)
            ((position - start).toFloat() / span).coerceIn(0f, 1f)
        }
    }

    // Fondo vivo: degradado que se desplaza despacio.
    val infinite = rememberInfiniteTransition(label = "canvasBg")
    val drift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(14000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "drift",
    )

    PlatformBackHandler(enabled = true, onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = bgColors + bgColors.first(),
                    start = Offset(0f, 400f * drift),
                    end = Offset(1200f, 1600f - 400f * drift),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            // Cabecera: lo mínimo, para que mande el contenido maximalista.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                // Botón de cerrar (izquierda)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, radius = 20.dp),
                        ) { onClose() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Cerrar",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp),
                    )
                }

                // Marca (siempre centrada)
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphIcon(Glyph.SPARK, accent, Modifier.size(15.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "CANVAS",
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 3.5.sp,
                        ),
                        color = Color.White.copy(alpha = 0.92f),
                    )
                }
            }

            // Escenario
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Objetos de fondo animados (detrás de la letra): flores, rosas, estrellas, marcos,
                // figuras… florecen y derivan mientras suena la línea y se funden con los de la siguiente.
                if (lines.isNotEmpty()) {
                    AnimatedContent(
                        targetState = currentLineIndex,
                        transitionSpec = {
                            fadeIn(tween(700)) togetherWith fadeOut(tween(500))
                        },
                        label = "canvasDecor",
                        modifier = Modifier.fillMaxSize(),
                    ) { idx ->
                        if (idx >= 0) {
                            val decor = remember(idx, seed, mood) { buildDecor(idx, seed, mood) }
                            CanvasDecorLayer(decor = decor, accent = accent, drift = drift)
                        } else {
                            Box(Modifier.fillMaxSize())
                        }
                    }
                }

                when {
                    lines.isEmpty() -> {
                        CanvasFallback(track?.title.orEmpty(), track?.artists.orEmpty(), accent, template)
                    }

                    else -> {
                        AnimatedContent(
                            targetState = currentLineIndex,
                            transitionSpec = {
                                (
                                    fadeIn(tween(420)) + scaleIn(
                                        initialScale = 0.82f,
                                        animationSpec = tween(520),
                                    ) + slideInVertically(tween(520)) { it / 8 }
                                    ) togetherWith
                                    (
                                        fadeOut(tween(240)) + scaleOut(
                                            targetScale = 1.08f,
                                            animationSpec = tween(300),
                                        )
                                        )
                            },
                            label = "canvasLine",
                        ) { idx ->
                            val text = lines.getOrNull(idx)?.text?.trim().orEmpty()
                            if (idx < 0 || text.isEmpty()) {
                                // Intro / hueco instrumental.
                                CanvasFallback(track?.title.orEmpty(), track?.artists.orEmpty(), accent, template)
                            } else {
                                val items = remember(idx, seed, template, mood, lines, aiHeroes) {
                                    buildLineComposition(
                                        line = text,
                                        lineIndex = idx,
                                        seed = seed,
                                        recurring = recurring,
                                        template = template,
                                        mood = mood,
                                        forcedHero = aiHeroes[idx],
                                    )
                                }
                                // Sólo la línea actual se revela poco a poco; si la línea está
                                // congelada (letra sin tiempos) se enseña entera.
                                val progress = if (idx == currentLineIndex) lineProgress else 1f
                                CanvasComposition(items, accent, template, progress)
                            }
                        }
                    }
                }
            }

            // Pista de contexto: la línea siguiente, apagada.
            val nextLine = lines.getOrNull(currentLineIndex + 1)?.text?.trim()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (!nextLine.isNullOrEmpty()) {
                    Text(
                        text = nextLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.35f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CanvasComposition(
    items: List<CanvasItem>,
    accent: Color,
    template: CanvasTemplate,
    progress: Float = 1f,
) {
    // Los elementos se revelan de uno en uno al sonar la línea. Cada uno recibe una parte igual de su
    // duración y una pequeña ventana final donde anima su fundido, escala y desplazamiento.
    val revealCount = items.size.coerceAtLeast(1)
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = template.arrangement,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, item ->
            // Revelado por elemento, 0..1, escalonado a lo largo de la línea.
            val threshold = index.toFloat() / revealCount
            val window = (1f / revealCount).coerceAtLeast(0.12f)
            val reveal = ((progress - threshold) / window).coerceIn(0f, 1f)
            val eased = reveal * reveal * (3f - 2f * reveal) // smoothstep

            val animAlpha by animateFloatAsState(eased, tween(260), label = "wordAlpha")
            val animScale by animateFloatAsState(0.6f + 0.4f * eased, tween(320), label = "wordScale")

            val revealModifier = Modifier.graphicsLayer {
                alpha = animAlpha
                scaleX = animScale
                scaleY = animScale
                translationY = (1f - animAlpha) * 26f
            }

            when (item) {
                is WordItem -> {
                    val color = when {
                        item.isHero -> Color.White
                        item.isAccent -> accent
                        else -> Color.White.copy(alpha = 0.72f)
                    }
                    Text(
                        text = item.text,
                        style = TextStyle(
                            fontSize = item.sizeSp.sp,
                            fontWeight = item.weight,
                            color = color,
                            lineHeight = (item.sizeSp * 0.98f).sp,
                            shadow = if (item.isHero) {
                                Shadow(color = accent.copy(alpha = 0.55f), blurRadius = 32f)
                            } else {
                                null
                            },
                        ),
                        modifier = revealModifier
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                            .rotate(item.rotation),
                    )
                }

                is IconItem -> {
                    GlyphIcon(
                        glyph = item.glyph,
                        tint = accent,
                        modifier = revealModifier
                            .padding(horizontal = 6.dp)
                            .size(item.sizeDp.dp)
                            .rotate(item.rotation),
                    )
                }
            }
        }
    }
}

/**
 * Dibuja los objetos decorativos animados detrás de la letra. Cada uno florece (escala + fundido) y
 * luego respira y gira despacio mientras la línea está en pantalla.
 */
@Composable
private fun CanvasDecorLayer(
    decor: List<DecorItem>,
    accent: Color,
    drift: Float,
) {
    // Progreso de «floración» compartido: todos aparecen poco después del cambio de línea.
    var bloom by remember(decor) { mutableStateOf(0f) }
    LaunchedEffect(decor) {
        bloom = 0f
        val steps = 30
        repeat(steps) {
            delay(24)
            bloom = (it + 1f) / steps
        }
        bloom = 1f
    }
    val bloomAnim by animateFloatAsState(bloom, tween(500), label = "decorBloom")

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        decor.forEach { d ->
            val breathe = sin(TWO_PI_CANVAS * d.driftSpeed * drift + d.driftPhase)
            val appear = (bloomAnim - d.appearDelayMs / 900f).coerceIn(0f, 1f)
            val eased = appear * appear * (3f - 2f * appear)
            GlyphIcon(
                glyph = d.glyph,
                tint = accent.copy(alpha = d.baseAlpha * eased),
                modifier = Modifier
                    .size(d.sizeDp.dp)
                    .align(Alignment.TopStart)
                    .padding(
                        start = (w - d.sizeDp.dp).coerceAtLeast(0.dp) * d.xFraction,
                        top = (h - d.sizeDp.dp).coerceAtLeast(0.dp) * d.yFraction,
                    )
                    .graphicsLayer {
                        val s = 0.7f + 0.3f * eased + 0.04f * breathe
                        scaleX = s
                        scaleY = s
                        rotationZ = d.rotation + d.spin * breathe
                        translationY = breathe * 10f
                    },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CanvasFallback(
    title: String,
    artists: String,
    accent: Color,
    template: CanvasTemplate,
) {
    Column(horizontalAlignment = template.bias) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(Glyph.BARS, accent, Modifier.size(38.dp))
            Spacer(Modifier.size(12.dp))
            GlyphIcon(Glyph.NOTE, Color.White.copy(alpha = 0.7f), Modifier.size(26.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title.uppercase(),
            style = TextStyle(
                fontSize = heroSizeFor(title.length).sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                lineHeight = heroSizeFor(title.length).sp,
                shadow = Shadow(color = accent.copy(alpha = 0.55f), blurRadius = 32f),
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = artists,
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

// ---------------------------------------------------------------------------
// Formas (dibujadas en Compose; una familia geométrica, sólida y del mismo peso)
// ---------------------------------------------------------------------------

@Composable
private fun GlyphIcon(glyph: Glyph, tint: Color, modifier: Modifier) {
    Canvas(modifier) { drawGlyph(glyph, tint) }
}

/** Estrella de cuatro puntas de lados cóncavos, centrada en ([cx],[cy]) con radio [r]. */
private fun sparkPath(cx: Float, cy: Float, r: Float): Path = Path().apply {
    moveTo(cx, cy - r)
    quadraticTo(cx, cy, cx + r, cy)
    quadraticTo(cx, cy, cx, cy + r)
    quadraticTo(cx, cy, cx - r, cy)
    quadraticTo(cx, cy, cx, cy - r)
    close()
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val s = min(size.width, size.height)
    fun p(v: Float) = v * s
    when (glyph) {
        Glyph.SPARK -> drawPath(sparkPath(p(0.5f), p(0.5f), p(0.5f)), color)

        Glyph.SPARKLE -> {
            drawPath(sparkPath(p(0.4f), p(0.6f), p(0.4f)), color)
            drawPath(sparkPath(p(0.8f), p(0.2f), p(0.2f)), color)
        }

        Glyph.ASTERISK -> {
            val stroke = p(0.14f)
            for (k in 0 until 3) {
                val a = k * PI.toFloat() / 3f
                val dx = cos(a) * p(0.42f)
                val dy = sin(a) * p(0.42f)
                drawLine(color, Offset(p(0.5f) - dx, p(0.5f) - dy), Offset(p(0.5f) + dx, p(0.5f) + dy), stroke, StrokeCap.Round)
            }
        }

        Glyph.BARS -> {
            val w = p(0.2f)
            listOf(0.12f to 0.5f, 0.4f to 0.9f, 0.68f to 0.65f).forEach { (x, hgt) ->
                drawRoundRect(
                    color,
                    topLeft = Offset(p(x), p(0.95f - hgt)),
                    size = Size(w, p(hgt)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2f),
                )
            }
        }

        Glyph.BOLT -> drawPath(
            Path().apply {
                moveTo(p(0.58f), p(0f))
                lineTo(p(0.18f), p(0.56f))
                lineTo(p(0.46f), p(0.56f))
                lineTo(p(0.38f), p(1f))
                lineTo(p(0.82f), p(0.4f))
                lineTo(p(0.54f), p(0.4f))
                close()
            },
            color,
        )

        Glyph.STAR -> drawPath(
            Path().apply {
                for (k in 0 until 10) {
                    val r = if (k % 2 == 0) 0.5f else 0.22f
                    val a = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
                    val x = p(0.5f + r * cos(a))
                    val y = p(0.53f + r * sin(a))
                    if (k == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            },
            color,
        )

        Glyph.NOTE -> {
            drawCircle(color, radius = p(0.16f), center = Offset(p(0.3f), p(0.8f)))
            drawRect(color, topLeft = Offset(p(0.4f), p(0.12f)), size = Size(p(0.09f), p(0.68f)))
            drawPath(
                Path().apply {
                    moveTo(p(0.49f), p(0.12f))
                    cubicTo(p(0.65f), p(0.2f), p(0.88f), p(0.3f), p(0.82f), p(0.56f))
                    cubicTo(p(0.76f), p(0.42f), p(0.62f), p(0.38f), p(0.49f), p(0.36f))
                    close()
                },
                color,
            )
        }

        Glyph.HEART -> drawPath(
            Path().apply {
                moveTo(p(0.5f), p(0.9f))
                cubicTo(p(0.12f), p(0.62f), p(0.05f), p(0.35f), p(0.2f), p(0.2f))
                cubicTo(p(0.32f), p(0.08f), p(0.46f), p(0.14f), p(0.5f), p(0.28f))
                cubicTo(p(0.54f), p(0.14f), p(0.68f), p(0.08f), p(0.8f), p(0.2f))
                cubicTo(p(0.95f), p(0.35f), p(0.88f), p(0.62f), p(0.5f), p(0.9f))
                close()
            },
            color,
        )

        Glyph.ROSE -> {
            for (i in 0 until 4) {
                val r = p(0.44f - i * 0.1f)
                drawCircle(color, radius = r, center = Offset(p(0.5f) + i * p(0.02f), p(0.5f)), style = Stroke(p(0.06f)))
            }
            drawCircle(color, radius = p(0.05f), center = Offset(p(0.56f), p(0.5f)))
        }

        Glyph.FLOWER -> {
            for (k in 0 until 5) {
                val a = -PI.toFloat() / 2f + k * 2f * PI.toFloat() / 5f
                drawCircle(color, radius = p(0.2f), center = Offset(p(0.5f + 0.26f * cos(a)), p(0.5f + 0.26f * sin(a))))
            }
            drawCircle(color, radius = p(0.12f), center = Offset(p(0.5f), p(0.5f)))
        }

        Glyph.PETAL -> drawPath(
            Path().apply {
                moveTo(p(0.5f), p(0.02f))
                cubicTo(p(0.95f), p(0.3f), p(0.9f), p(0.8f), p(0.5f), p(0.98f))
                cubicTo(p(0.1f), p(0.8f), p(0.05f), p(0.3f), p(0.5f), p(0.02f))
                close()
            },
            color,
        )

        Glyph.BUTTERFLY -> {
            drawOval(color, topLeft = Offset(p(0.04f), p(0.1f)), size = Size(p(0.44f), p(0.44f)))
            drawOval(color, topLeft = Offset(p(0.52f), p(0.1f)), size = Size(p(0.44f), p(0.44f)))
            drawOval(color, topLeft = Offset(p(0.14f), p(0.52f)), size = Size(p(0.34f), p(0.36f)))
            drawOval(color, topLeft = Offset(p(0.52f), p(0.52f)), size = Size(p(0.34f), p(0.36f)))
            drawLine(color, Offset(p(0.5f), p(0.12f)), Offset(p(0.5f), p(0.9f)), p(0.07f), StrokeCap.Round)
        }

        Glyph.LEAF -> drawPath(
            Path().apply {
                moveTo(p(0.1f), p(0.9f))
                quadraticTo(p(0.1f), p(0.1f), p(0.9f), p(0.1f))
                quadraticTo(p(0.9f), p(0.9f), p(0.1f), p(0.9f))
                close()
            },
            color,
        )

        Glyph.PERSON -> {
            drawCircle(color, radius = p(0.2f), center = Offset(p(0.5f), p(0.28f)))
            drawPath(
                Path().apply {
                    moveTo(p(0.12f), p(0.98f))
                    cubicTo(p(0.12f), p(0.6f), p(0.88f), p(0.6f), p(0.88f), p(0.98f))
                    close()
                },
                color,
            )
        }

        Glyph.FRAME -> {
            drawRect(color, topLeft = Offset(p(0.12f), p(0.12f)), size = Size(p(0.76f), p(0.76f)), style = Stroke(p(0.09f)))
            drawRect(color, topLeft = Offset(p(0.3f), p(0.3f)), size = Size(p(0.4f), p(0.4f)), style = Stroke(p(0.04f)))
        }
    }
}
