/*
 * Copyright (c) 2026 Dezzy (https://github.com/dezzy-music)
 * Licensed under the PolyForm Strict License 1.0.0. See LICENSE.md.
 */

package com.dezzy.app.ui.screens.login

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PlatformLoginWebView(
    startUrl: String,
    cookieUrl: String,
    isLoggedIn: (cookieHeader: String) -> Boolean,
    onCaptured: (cookieHeader: String) -> Unit,
    modifier: Modifier,
) {
    val loggedIn by rememberUpdatedState(isLoggedIn)
    val captured by rememberUpdatedState(onCaptured)
    val done = remember { BooleanArray(1) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            val cookies = CookieManager.getInstance()
            cookies.setAcceptCookie(true)
            // Empezar limpio para que entre la cuenta que el usuario elija.
            cookies.removeAllCookies(null)
            cookies.flush()

            fun check() {
                if (done[0]) return
                val header = cookies.getCookie(cookieUrl) ?: return
                if (header.isNotBlank() && loggedIn(header)) {
                    done[0] = true
                    captured(header)
                }
            }

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
                // Google no deja entrar desde una WebView marcada: se quita «; wv».
                settings.userAgentString = WebSettings.getDefaultUserAgent(context).replace("; wv", "")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) = check()
                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) = check()
                }
                loadUrl(startUrl)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
    )
}
