package com.github.avbox.osc.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.SslErrorHandler
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.platform.ComposeView
import com.github.avbox.osc.R
import com.github.avbox.osc.haikuo.HAIKUO_RESOURCE_ENTRIES_SCRIPT
import com.github.avbox.osc.haikuo.HaikuoJson
import com.github.avbox.osc.miniapp.MINI_APP_MEDIA_URL_PATTERN
import com.github.avbox.osc.miniapp.miniAppIsMediaUrl
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.page.MiniAppSniffScreen
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge

class MiniAppSniffActivity : BaseActivity() {

    companion object {
        const val EXTRA_URL = "miniapp_sniff_url"
        const val EXTRA_HEADERS = "miniapp_sniff_headers"
        const val EXTRA_FOUND_URL = "miniapp_sniff_found_url"
        const val SNIFF_TIMEOUT_MS = 30_000L
        const val POLL_INTERVAL_MS = 250L
    }

    private var webView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false
    private var deadlineMs: Long = 0L

    private val pollTask: Runnable = object : Runnable {
        override fun run() {
            if (finished) return
            if (System.currentTimeMillis() >= deadlineMs) {
                finishCanceled()
                return
            }
            val view = webView
            if (view == null) {
                handler.postDelayed(this, POLL_INTERVAL_MS)
                return
            }
            val current = view.url ?: ""
            if (miniAppIsMediaUrl(current)) {
                finishWith(current)
                return
            }
            try {
                view.evaluateJavascript(HAIKUO_RESOURCE_ENTRIES_SCRIPT) { value ->
                    if (finished) return@evaluateJavascript
                    val found = firstMediaUrl(value)
                    if (found != null) {
                        finishWith(found)
                    } else {
                        handler.postDelayed(pollTask, POLL_INTERVAL_MS)
                    }
                }
            } catch (e: Exception) {
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        val headers = parseHeaders(intent.getStringExtra(EXTRA_HEADERS) ?: "")
        deadlineMs = System.currentTimeMillis() + SNIFF_TIMEOUT_MS
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                MiniAppSniffScreen(
                    title = getString(R.string.miniapp_sniff_title),
                    startUrl = url.ifEmpty { null },
                    startHeaders = headers,
                    onWebViewCreated = { view -> configureWebView(view) },
                    onNavigateBack = { finishCanceled() }
                )
            }
        }
        handler.postDelayed(pollTask, POLL_INTERVAL_MS)
    }

    override fun onDestroy() {
        handler.removeCallbacks(pollTask)
        try {
            val view = webView
            webView = null
            view?.destroy()
        } catch (e: Exception) {
        }
        super.onDestroy()
    }

    private fun configureWebView(view: WebView) {
        webView = view
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.setSupportZoom(false)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.defaultTextEncodingName = "utf-8"
        view.webViewClient = object : WebViewClient() {
            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: android.net.http.SslError
            ) {
                handler.proceed()
            }
        }
    }

    private fun firstMediaUrl(raw: String?): String? {
        if (raw.isNullOrEmpty()) return null
        val decoded = HaikuoJson.parse(raw.trim()) ?: return null
        val entries: List<*> = when (decoded) {
            is List<*> -> decoded
            is String -> {
                val inner = HaikuoJson.parse(decoded)
                if (inner is List<*>) inner else return null
            }
            else -> return null
        }
        for (entry in entries) {
            val text = entry.toString()
            val match = MINI_APP_MEDIA_URL_PATTERN.find(text)
            if (match != null) return match.value
        }
        return null
    }

    private fun parseHeaders(raw: String): Map<String, String> {
        val headers = linkedMapOf<String, String>()
        try {
            val decoded = HaikuoJson.parse(raw) ?: return headers
            val map = HaikuoJson.asMap(decoded) ?: return headers
            for ((key, value) in map) {
                if (value != null) headers[key] = value.toString()
            }
        } catch (e: Exception) {
        }
        return headers
    }

    private fun finishWith(url: String) {
        if (finished) return
        finished = true
        handler.removeCallbacks(pollTask)
        val data = Intent()
        data.putExtra(EXTRA_FOUND_URL, url)
        setResult(RESULT_OK, data)
        finish()
    }

    private fun finishCanceled() {
        if (finished) return
        finished = true
        handler.removeCallbacks(pollTask)
        setResult(RESULT_CANCELED)
        finish()
    }
}
