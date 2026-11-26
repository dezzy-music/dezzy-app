/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.source.spotify

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Captura el token cargando el reproductor web en una WebView oculta (sin ventana) con la
 * cookie `sp_dc` y mirando la cabecera `Authorization: Bearer …` que la propia web pide.
 */
class AndroidSpotifyTokenSource(private val context: Context) : SpotifyTokenSource {

    @SuppressLint("SetJavaScriptEnabled")
    override suspend fun capture(spDc: String): String {
        var webView: WebView? = null
        try {
            return withTimeout(TIMEOUT_MS) {
                withContext(Dispatchers.Main) {
                    val found = CompletableDeferred<String>()
                    val wv = WebView(context.applicationContext)
                    webView = wv
                    wv.layout(0, 0, 1, 1)
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.settings.mediaPlaybackRequiresUserGesture = true
                    wv.settings.userAgentString = SPOTIFY_USER_AGENT

                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(wv, true)
                    cookies.setCookie(
                        WEB_URL,
                        "sp_dc=$spDc; Domain=.spotify.com; Path=/; Secure; HttpOnly",
                    )
                    cookies.flush()

                    wv.webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            val auth = request.requestHeaders.entries
                                .firstOrNull { it.key.equals("authorization", ignoreCase = true) }
                                ?.value
                            if (auth != null && auth.startsWith("Bearer ")) {
                                found.complete(auth.removePrefix("Bearer ").trim())
                            }
                            return null // la petición sigue su curso
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError,
                        ) {
                            if (request.isForMainFrame) {
                                found.completeExceptionally(IllegalStateException("Tu biblioteca no responde"))
                            }
                        }
                    }
                    wv.loadUrl("$WEB_URL/")
                    found.await()
                }
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("Tu biblioteca no responde")
        } finally {
            withContext(NonCancellable + Dispatchers.Main) {
                // La cookie no se queda guardada en la WebView compartida.
                runCatching {
                    val cookies = CookieManager.getInstance()
                    cookies.setCookie(WEB_URL, "sp_dc=; Domain=.spotify.com; Path=/; Max-Age=0")
                    cookies.flush()
                }
                runCatching { webView?.stopLoading() }
                runCatching { webView?.destroy() }
            }
        }
    }

    private companion object {
        const val WEB_URL = "https://open.spotify.com"
        const val TIMEOUT_MS = 25_000L
    }
}
