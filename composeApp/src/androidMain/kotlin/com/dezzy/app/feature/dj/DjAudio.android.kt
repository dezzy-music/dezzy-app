/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.feature.dj

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.koin.core.scope.Scope
import java.io.File
import java.util.Locale
import kotlin.coroutines.resume

actual fun createDjAudioPlayer(scope: Scope): DjAudioPlayer = AndroidDjAudioPlayer(scope.get<Context>().applicationContext)

private class AndroidDjAudioPlayer(private val context: Context) : DjAudioPlayer {

    @Volatile private var player: MediaPlayer? = null
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var ttsReady: CompletableDeferred<Boolean>? = null

    override suspend fun play(audio: ByteArray, onSession: (Int) -> Unit): Boolean {
        val file = withContext(Dispatchers.IO) {
            File(context.cacheDir, "dj_voice_${System.nanoTime()}.mp3").also { it.writeBytes(audio) }
        }
        return try {
            withContext(Dispatchers.Main) {
                suspendCancellableCoroutine { cont ->
                    val mp = MediaPlayer()
                    player = mp
                    fun finish(ok: Boolean) {
                        runCatching { mp.release() }
                        if (player === mp) player = null
                        if (cont.isActive) cont.resume(ok)
                    }
                    try {
                        mp.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build(),
                        )
                        mp.setDataSource(file.absolutePath)
                        mp.setOnCompletionListener { finish(true) }
                        mp.setOnErrorListener { _, _, _ -> finish(false); true }
                        mp.setOnPreparedListener {
                            onSession(it.audioSessionId)
                            it.start()
                        }
                        mp.prepareAsync()
                    } catch (e: Exception) {
                        finish(false)
                    }
                    cont.invokeOnCancellation { runCatching { mp.release() }; if (player === mp) player = null }
                }
            }
        } finally {
            file.delete()
        }
    }

    override suspend fun speak(text: String, languageTag: String): Boolean = withContext(Dispatchers.Main) {
        val engine = ensureTts() ?: return@withContext false
        val locale = Locale.forLanguageTag(languageTag)
        if (engine.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) return@withContext false
        suspendCancellableCoroutine { cont ->
            val id = "dj_${System.nanoTime()}"
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(true) }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { if (utteranceId == id && cont.isActive) cont.resume(false) }
                override fun onStop(utteranceId: String?, interrupted: Boolean) { if (utteranceId == id && cont.isActive) cont.resume(false) }
            })
            val queued = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
            if (queued != TextToSpeech.SUCCESS && cont.isActive) cont.resume(false)
            cont.invokeOnCancellation { runCatching { engine.stop() } }
        }
    }

    private suspend fun ensureTts(): TextToSpeech? {
        tts?.let { return it }
        val ready = CompletableDeferred<Boolean>()
        ttsReady = ready
        val engine = TextToSpeech(context) { status -> ready.complete(status == TextToSpeech.SUCCESS) }
        val ok = try { ready.await() } catch (e: CancellationException) { runCatching { engine.shutdown() }; throw e }
        if (!ok) { runCatching { engine.shutdown() }; return null }
        tts = engine
        return engine
    }

    override fun stop() {
        runCatching { player?.let { it.stop(); it.release() } }
        player = null
        runCatching { tts?.stop() }
    }
}

@Composable
actual fun rememberDjDictation(djName: String, onResult: (String) -> Unit): () -> Unit {
    val latest by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) latest(text)
        }
    }
    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Dile a $djName qué quieres escuchar...")
        }
        runCatching { launcher.launch(intent) }
    }
}
