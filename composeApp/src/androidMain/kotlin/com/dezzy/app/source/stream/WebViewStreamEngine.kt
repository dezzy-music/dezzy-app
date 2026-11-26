/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.stream

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.content.Context
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.dezzy.app.core.net.createPlatformHttpEngine
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.headers
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Motor de streaming de Android: una WebView oculta con origen youtube.com que
 * ejecuta youtubei.js (sesión, descifrado) y bgutils-js (BotGuard), ambos MIT y
 * empaquetados en `assets/botguard/`.
 *
 * La red nunca sale de la WebView: el JS pide cada fetch a [NetBridge] y Kotlin lo
 * hace con Ktor, sin cookies y con las cabeceras que el navegador borraría.
 */
class WebViewStreamEngine(context: Context) : StreamEngine {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient(createPlatformHttpEngine()) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = NET_TIMEOUT_MS
            connectTimeoutMillis = NET_TIMEOUT_MS
            socketTimeoutMillis = NET_TIMEOUT_MS
        }
    }

    private val scripts by lazy {
        SCRIPT_ASSETS.map { name -> appContext.assets.open(name).bufferedReader().use { it.readText() } }
    }

    private val webMutex = Mutex()
    @Volatile private var web: WebView? = null
    @Volatile private var ready: CompletableDeferred<WebView>? = null

    private val nextId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<String>>()

    private val minterMutex = Mutex()
    @Volatile private var minterExpiresAt = 0L
    private val tokenCache = ConcurrentHashMap<String, Pair<String, Long>>()

    // StreamEngine -------------------------------------------------------------

    override suspend fun createSession(): String =
        call("crear la sesión") { id -> "window.dezzyYt.session($id)" }

    override suspend fun audioFormats(videoId: String, client: String, poToken: String): List<EngineAudioFormat> {
        val raw = call(client) { id -> "window.dezzyYt.audio($id,${js(videoId)},${js(client)},${js(poToken)})" }
        return json.decodeFromString(ListSerializer(EngineAudioFormat.serializer()), raw)
    }

    override suspend fun mintPoToken(binding: String): String {
        tokenCache[binding]?.let { (token, expiresAt) -> if (System.currentTimeMillis() < expiresAt) return token }
        ensureMinter()
        val token = call("firmar el token") { id -> "window.dezzyBg.mint($id,${js(binding)})" }
        tokenCache[binding] = token to minterExpiresAt
        return token
    }

    override fun reset(hard: Boolean) {
        minterExpiresAt = 0
        tokenCache.clear()
        if (hard) web?.let(::discard)
    }

    // Minter de BotGuard -------------------------------------------------------

    private suspend fun ensureMinter() = minterMutex.withLock {
        if (System.currentTimeMillis() < minterExpiresAt) return@withLock
        createMinter()
    }

    private suspend fun createMinter() {
        val createRaw = postJnn("Create", buildJsonArray { add(JsonPrimitive(REQUEST_KEY)) })
        val challenge = json.parseToJsonElement(call("leer el reto") { id -> "window.dezzyBg.parse($id,${js(createRaw)})" }).jsonObject
        var interpreter = challenge["interpreterJavascript"]?.jsonPrimitive?.contentOrNull
        val interpreterUrl = challenge["interpreterUrl"]?.jsonPrimitive?.contentOrNull
        if (interpreter.isNullOrEmpty() && !interpreterUrl.isNullOrEmpty()) {
            val url = if (interpreterUrl.startsWith("//")) "https:$interpreterUrl" else interpreterUrl
            interpreter = http.request(url).bodyAsText()
        }
        val program = challenge["program"]?.jsonPrimitive?.contentOrNull
        val globalName = challenge["globalName"]?.jsonPrimitive?.contentOrNull
        if (interpreter.isNullOrEmpty() || program.isNullOrEmpty() || globalName.isNullOrEmpty()) {
            throw StreamEngineException("BotGuard: reto incompleto")
        }
        val botguardResponse = call("ejecutar BotGuard") { id ->
            "window.dezzyBg.snapshot($id,${js(interpreter)},${js(program)},${js(globalName)})"
        }
        val itRaw = postJnn("GenerateIT", buildJsonArray {
            add(JsonPrimitive(REQUEST_KEY))
            add(JsonPrimitive(botguardResponse))
        })
        val generated = json.parseToJsonElement(itRaw) as? JsonArray
            ?: throw StreamEngineException("BotGuard: respuesta rara de GenerateIT")
        val integrityToken = (generated.getOrNull(0) as? JsonPrimitive)?.contentOrNull
            ?: throw StreamEngineException("BotGuard: sin integrity token")
        val ttl = (generated.getOrNull(1) as? JsonPrimitive)?.longOrNull ?: 3600L
        val refresh = (generated.getOrNull(2) as? JsonPrimitive)?.longOrNull ?: 300L
        call("crear el minter") { id -> "window.dezzyBg.minter($id,${js(integrityToken)})" }
        minterExpiresAt = System.currentTimeMillis() + maxOf(60L, ttl - refresh) * 1000L
        tokenCache.clear()
    }

    private suspend fun postJnn(method: String, body: JsonArray): String {
        val response = http.request("$JNN_URL/$method") {
            this.method = HttpMethod.Post
            headers {
                append("x-goog-api-key", GOOG_API_KEY)
                append("x-user-agent", "grpc-web-javascript/0.1")
            }
            setBody(TextContent(body.toString(), ContentType.parse("application/json+protobuf")))
        }
        if (!response.status.isSuccess()) throw StreamEngineException("BotGuard: $method respondió ${response.status.value}")
        return response.bodyAsText()
    }

    // WebView ------------------------------------------------------------------

    /** Llama a una función del puente; contesta por [ReplyBridge.reply]. */
    private suspend fun call(label: String, script: (Int) -> String): String {
        val view = webView()
        val id = nextId.getAndIncrement()
        val reply = CompletableDeferred<String>()
        pending[id] = reply
        withContext(Dispatchers.Main) { view.evaluateJavascript(script(id), null) }
        val result = withTimeoutOrNull(CALL_TIMEOUT_MS) { reply.await() }
        pending.remove(id)
        if (result == null) {
            // Una WebView que no contesta en 20 s está colgada: la próxima vez, otra.
            discard(view)
            throw StreamEngineException("BotGuard: tiempo agotado en $label")
        }
        return result
    }

    private suspend fun webView(): WebView {
        val waiting = webMutex.withLock {
            // Una carga que falló no se queda guardada: se crea otra WebView.
            ready?.takeUnless { it.isCancelled } ?: CompletableDeferred<WebView>().also { deferred ->
                ready = deferred
                withContext(Dispatchers.Main) { createWebView(deferred) }
            }
        }
        val view = try {
            withTimeoutOrNull(CALL_TIMEOUT_MS) { waiting.await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            null
        }
        return view ?: run {
            web?.let(::discard)
            throw StreamEngineException("BotGuard: la WebView no cargó")
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView(deferred: CompletableDeferred<WebView>) {
        val sources = runCatching { scripts }.getOrElse {
            deferred.completeExceptionally(StreamEngineException("BotGuard: faltan los scripts", it))
            return
        }
        val view = WebView(appContext)
        web = view
        view.settings.javaScriptEnabled = true
        view.addJavascriptInterface(ReplyBridge(), "DezzyBgBridge")
        view.addJavascriptInterface(NetBridge(), "DezzyNet")
        view.webViewClient = object : WebViewClient() {
            private var injected = false

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            override fun onPageFinished(page: WebView, url: String?) {
                if (injected || web !== page) return
                injected = true
                sources.forEach { page.evaluateJavascript(it, null) }
                page.evaluateJavascript("(typeof window.dezzyBg)+','+(typeof window.dezzyYt)") { result ->
                    if (result?.contains("object,object") == true) deferred.complete(page)
                    else deferred.completeExceptionally(StreamEngineException("BotGuard: el puente no arrancó ($result)"))
                }
            }

            @TargetApi(26)
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail?): Boolean {
                Log.w(TAG, "La WebView de BotGuard murió (crash=${detail?.didCrash()})")
                discard(view)
                return true
            }
        }
        // Documento vacío con origen youtube.com, como hace NewPipe con su WebView.
        view.loadDataWithBaseURL(BASE_URL, BLANK_HTML, "text/html", "utf-8", null)
    }

    /** El minter y la sesión de youtubei.js viven en la WebView: al perderla, se pierden también. */
    private fun discard(view: WebView) {
        if (web === view) {
            web = null
            ready?.let { if (!it.isCompleted) it.completeExceptionally(StreamEngineException("BotGuard cerrado")) }
            ready = null
            minterExpiresAt = 0
            tokenCache.clear()
            pending.values.forEach { it.completeExceptionally(StreamEngineException("BotGuard cerrado")) }
            pending.clear()
        }
        scope.launch(Dispatchers.Main) { runCatching { view.destroy() } }
    }

    private fun evaluate(script: String) {
        scope.launch(Dispatchers.Main) { web?.evaluateJavascript(script, null) }
    }

    private inner class ReplyBridge {
        @JavascriptInterface
        fun reply(id: Int, ok: Boolean, data: String?) {
            val target = pending.remove(id) ?: return
            if (ok) target.complete(data.orEmpty()) else target.completeExceptionally(StreamEngineException(data ?: "error en el puente"))
        }
    }

    /** fetch de youtubei.js. Sólo a dominios de YouTube y Google. */
    private inner class NetBridge {
        @JavascriptInterface
        fun fetch(id: Int, url: String, method: String, headersJson: String?, body: String?) {
            scope.launch {
                try {
                    val target = Url(url)
                    if (target.protocol.name != "https" || ALLOWED_HOSTS.none { target.host == it || target.host.endsWith(".$it") }) {
                        throw StreamEngineException("host no permitido: ${target.host}")
                    }
                    val requested = headersJson?.let { runCatching { JSONObject(it) }.getOrNull() }
                    var contentType: ContentType? = null
                    val response = http.request(target) {
                        this.method = HttpMethod.parse(method.uppercase())
                        headers {
                            requested?.keys()?.forEach { name ->
                                val value = requested.optString(name)
                                when (name.lowercase()) {
                                    "content-type" -> contentType = runCatching { ContentType.parse(value) }.getOrNull()
                                    // OkHttp descomprime solo si no se pide la codificación a mano.
                                    in DROPPED_REQUEST_HEADERS -> Unit
                                    else -> append(name, value)
                                }
                            }
                        }
                        if (!body.isNullOrEmpty() && this.method != HttpMethod.Get) {
                            setBody(ByteArrayContent(body.encodeToByteArray(), contentType))
                        }
                    }
                    val bytes = response.readRawBytes()
                    val outHeaders = JSONObject()
                    response.headers.names().forEach { name ->
                        if (name.lowercase() !in DROPPED_RESPONSE_HEADERS) {
                            outHeaders.put(name.lowercase(), response.headers.getAll(name)?.joinToString(", "))
                        }
                    }
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    evaluate("window.__dezzyNet.resolve($id,${response.status.value},${js(outHeaders.toString())},\"$b64\")")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    evaluate("window.__dezzyNet.reject($id,${js(e.message ?: e::class.java.simpleName)})")
                }
            }
        }
    }

    private companion object {
        const val TAG = "DezzyBotGuard"
        const val BASE_URL = "https://www.youtube.com/"
        const val BLANK_HTML = "<!doctype html><html><head><title>bg</title></head><body></body></html>"
        val SCRIPT_ASSETS = listOf("botguard/dezzy-bg.js", "botguard/dezzy-yt.js")

        const val JNN_URL = "https://www.youtube.com/api/jnn/v1"
        const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        /** Clave pública de la web de YouTube para la API de BotGuard (la misma que usa bgutils-js). */
        const val GOOG_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"

        const val CALL_TIMEOUT_MS = 20_000L
        const val NET_TIMEOUT_MS = 15_000L

        val ALLOWED_HOSTS = listOf("youtube.com", "google.com", "googleapis.com", "googlevideo.com", "ytimg.com", "gstatic.com")
        val DROPPED_REQUEST_HEADERS = setOf("accept-encoding", "content-length", "host", "connection", "transfer-encoding", "upgrade")
        val DROPPED_RESPONSE_HEADERS = setOf("content-encoding", "content-length", "transfer-encoding")

        fun js(value: String): String = JSONObject.quote(value)
    }
}
