/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.login

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformTokenWebView(
    startUrl: String,
    headerUrlPrefix: String,
    onToken: (String) -> Unit,
    modifier: Modifier,
) {
    val tokenCallback by rememberUpdatedState(onToken)
    val done = remember { AtomicBoolean(false) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val cookies = CookieManager.getInstance()
            cookies.setAcceptCookie(true)
            // Empezar limpio para que entre la cuenta que el usuario elija.
            cookies.removeAllCookies(null)
            cookies.flush()

            val main = Handler(Looper.getMainLooper())

            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                cookies.setAcceptThirdPartyCookies(this, true)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString = WebSettings.getDefaultUserAgent(context).replace("; wv", "")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                        if (request != null && !done.get() && request.url.toString().startsWith(headerUrlPrefix)) {
                            val value = request.requestHeaders.entries
                                .firstOrNull { it.key.equals("Authorization", ignoreCase = true) }
                                ?.value
                            if (!value.isNullOrBlank() && !value.startsWith("Bearer ") && done.compareAndSet(false, true)) {
                                main.post { tokenCallback(value) }
                            }
                        }
                        return null
                    }
                }
                loadUrl(startUrl)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}
