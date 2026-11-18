package com.github.avbox.osc.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import com.github.avbox.osc.BuildConfig
import com.github.avbox.osc.R
import com.github.avbox.osc.haikuo.HaikuoJson
import com.github.avbox.osc.haikuo.HaikuoPlayLine
import com.github.avbox.osc.haikuo.HaikuoPlayRequest
import com.github.avbox.osc.miniapp.AvBoxMiniAppSourceProvider
import com.github.avbox.osc.miniapp.MiniAppBridge
import com.github.avbox.osc.miniapp.MiniAppBridgeCallbacks
import com.github.avbox.osc.miniapp.MiniAppClipboard
import com.github.avbox.osc.miniapp.MiniAppInstance
import com.github.avbox.osc.miniapp.MiniAppManifest
import com.github.avbox.osc.miniapp.MiniAppPlayTarget
import com.github.avbox.osc.miniapp.MiniAppRuntime
import com.github.avbox.osc.miniapp.MiniAppSdkScript
import com.github.avbox.osc.miniapp.MiniAppSettings
import com.github.avbox.osc.miniapp.MiniAppStore
import com.github.avbox.osc.miniapp.miniAppShouldBlockUrl
import com.github.avbox.osc.player.PageHost
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.page.MiniAppHostScreen
import com.github.avbox.osc.ui.player.PlayContainer
import com.github.avbox.osc.ui.player.haikuoPlaybackSession
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.PermissionHelper
import com.github.avbox.osc.util.SpiderStorageNotice
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MiniAppHostActivity : BaseActivity(), MiniAppBridgeCallbacks, MiniAppClipboard, PageHost {

    companion object {
        const val EXTRA_APP_ID = "miniapp_app_id"
        const val EXTRA_LAUNCH_OPTIONS = "miniapp_launch_options"

        fun start(context: Context, appId: String, launchOptions: String = "") {
            val intent = Intent(context, MiniAppHostActivity::class.java)
            intent.putExtra(EXTRA_APP_ID, appId)
            intent.putExtra(EXTRA_LAUNCH_OPTIONS, launchOptions)
            context.startActivity(intent)
        }
    }

    private class PendingConfirm(val title: String, val content: String) {
        val latch = CountDownLatch(1)

        @Volatile
        var result: Boolean = false
    }

    private class PendingSheet(val items: List<String>) {
        val latch = CountDownLatch(1)

        @Volatile
        var result: Int = -1
    }

    private inner class MiniAppJsChannel {
        @JavascriptInterface
        fun postMessage(raw: String) {
            bridgeExecutor.execute {
                try {
                    bridge?.handleChannelMessage(raw)
                } catch (e: Exception) {
                    AppLog.e("MiniAppHost", e)
                }
            }
        }
    }

    private var webView: WebView? = null
    private var runtime: MiniAppRuntime? = null
    private var instance: MiniAppInstance? = null
    private var manifest: MiniAppManifest? = null
    private var hostSettings: MiniAppSettings? = null
    private var bridge: MiniAppBridge? = null
    private val bridgeExecutor = Executors.newSingleThreadExecutor()
    private val pendingConfirm = AtomicReference<PendingConfirm?>(null)
    private val pendingSheet = AtomicReference<PendingSheet?>(null)

    private var inlineContainer: PlayContainer? by mutableStateOf<PlayContainer?>(null)
        private set
    private var lastInlineUrl: String = ""

    @Volatile
    private var playerStateSnapshot: Map<String, Any?> = mapOf("opened" to false)

    private val sniffLatch = AtomicReference<CountDownLatch?>(null)

    @Volatile
    private var sniffResult: String? = null

    private val sniffLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val url = result.data?.getStringExtra(MiniAppSniffActivity.EXTRA_FOUND_URL)
        sniffResult = url
        sniffLatch.get()?.countDown()
    }

    private val localSubtitlePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) inlineContainer?.onLocalSubtitlePicked(uri)
    }

    override fun launchLocalSubtitlePicker() {
        try {
            SpiderStorageNotice.suppress()
            localSubtitlePicker.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_file_picker_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    private val _startUrl = MutableStateFlow<String?>(null)
    private val startUrl: StateFlow<String?> = _startUrl.asStateFlow()
    private val _bootError = MutableStateFlow<String?>(null)
    private val bootError: StateFlow<String?> = _bootError.asStateFlow()
    private val _pageLoading = MutableStateFlow(true)
    private val pageLoading: StateFlow<Boolean> = _pageLoading.asStateFlow()
    private val _loadingOverlay = MutableStateFlow<String?>(null)
    private val loadingOverlay: StateFlow<String?> = _loadingOverlay.asStateFlow()
    private val _confirmDialog = MutableStateFlow<PendingConfirm?>(null)
    private val confirmDialog: StateFlow<PendingConfirm?> = _confirmDialog.asStateFlow()
    private val _actionSheet = MutableStateFlow<PendingSheet?>(null)
    private val actionSheet: StateFlow<PendingSheet?> = _actionSheet.asStateFlow()
    private val _appName = MutableStateFlow("")
    private val appName: StateFlow<String> = _appName.asStateFlow()

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val container = inlineContainer
                    if (container != null && container.onBackPressed()) return
                    if (container != null) {
                        closeInline()
                        return
                    }
                    finish()
                }
            }
        )
        val appId = intent.getStringExtra(EXTRA_APP_ID) ?: ""
        val launchOptions = intent.getStringExtra(EXTRA_LAUNCH_OPTIONS) ?: ""
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                val url by startUrl.collectAsState()
                val error by bootError.collectAsState()
                val loading by pageLoading.collectAsState()
                val overlay by loadingOverlay.collectAsState()
                val confirm by confirmDialog.collectAsState()
                val sheet by actionSheet.collectAsState()
                val name by appName.collectAsState()
                MiniAppHostScreen(
                    appName = name,
                    startUrl = url,
                    bootError = error,
                    pageLoading = loading,
                    loadingOverlay = overlay,
                    confirmTitle = confirm?.title,
                    confirmContent = confirm?.content,
                    sheetItems = sheet?.items,
                    inlineContainer = inlineContainer,
                    onWebViewCreated = { view -> configureWebView(view) },
                    onConfirmResult = { result -> answerConfirm(result) },
                    onSheetResult = { index -> answerSheet(index) },
                    onRetry = { retryBoot(appId, launchOptions) },
                    onRefresh = { webView?.reload() },
                    onNavigateBack = { finish() }
                )
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            boot(appId, launchOptions)
        }
    }

    override fun onResume() {
        super.onResume()
        val id = manifest?.appId
        if (id != null) runtime?.markForeground(id)
        inlineContainer?.hostResume()
    }

    override fun onPause() {
        inlineContainer?.hostPause()
        val id = manifest?.appId
        if (id != null) runtime?.markBackground(id)
        super.onPause()
    }

    override fun onDestroy() {
        val confirm = pendingConfirm.get()
        if (confirm != null) answerConfirm(false)
        val sheet = pendingSheet.get()
        if (sheet != null) answerSheet(-1)
        sniffLatch.get()?.countDown()
        bridgeExecutor.shutdownNow()
        try {
            runtime?.stopAll()
        } catch (e: Exception) {
            AppLog.e("MiniAppHost", e)
        }
        try {
            val container = inlineContainer
            inlineContainer = null
            runOnUiThread { container?.hostDestroy() }
        } catch (e: Exception) {
            AppLog.e("MiniAppHost", e)
        }
        try {
            val view = webView
            webView = null
            view?.destroy()
        } catch (e: Exception) {
            AppLog.e("MiniAppHost", e)
        }
        super.onDestroy()
    }

    private fun retryBoot(appId: String, launchOptions: String) {
        _bootError.value = null
        _startUrl.value = null
        _pageLoading.value = true
        lifecycleScope.launch(Dispatchers.IO) {
            boot(appId, launchOptions)
        }
    }

    private fun boot(appId: String, launchOptionsJson: String) {
        try {
            val store = MiniAppStore(applicationContext)
            val settings = MiniAppSettings.load(applicationContext)
            hostSettings = settings
            val createdRuntime = MiniAppRuntime(store, settings)
            runtime = createdRuntime
            val found = store.listInstalled().firstOrNull { it.appId == appId }
            if (found == null) {
                _bootError.value = getString(R.string.miniapp_target_missing)
                return
            }
            manifest = found
            _appName.value = found.name
            val created = createdRuntime.ensureRunning(found)
            instance = created
            val provider = AvBoxMiniAppSourceProvider(imageBaseUrl = created.baseUrl)
            created.server.proxyFetch = { source, param -> provider.proxyFetch(source, param) }
            val prefs = getSharedPreferences("miniapp_storage", Context.MODE_PRIVATE)
            val options = try {
                val decoded = HaikuoJson.parse(launchOptionsJson)
                if (decoded != null) HaikuoJson.asMap(decoded) ?: emptyMap() else emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
            bridge = MiniAppBridge(
                manifest = found,
                appDir = created.dir,
                prefs = prefs,
                callbacks = this,
                clipboard = this,
                launchOptions = options,
                sourceProvider = provider,
                devMode = BuildConfig.DEBUG
            )
            val entry = found.entry
            if (entry.startsWith("http://") || entry.startsWith("https://")) {
                _startUrl.value = entry
            } else {
                _startUrl.value = created.server.entryUrl()
            }
        } catch (e: Exception) {
            _bootError.value = getString(R.string.miniapp_start_failed) + ": " + e.message
        }
    }

    private fun configureWebView(view: WebView) {
        webView = view
        val settings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.allowUniversalAccessFromFileURLs = true
        settings.allowFileAccessFromFileURLs = true
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
        view.addJavascriptInterface(MiniAppJsChannel(), MiniAppSdkScript.CHANNEL_NAME)
        view.webViewClient = MiniAppWebClient()
        view.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                result.confirm()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                result.confirm()
                return true
            }

            override fun onJsPrompt(
                view: WebView,
                url: String,
                message: String,
                defaultValue: String,
                result: JsPromptResult
            ): Boolean {
                result.confirm(defaultValue)
                return true
            }
        }
    }

    private inner class MiniAppWebClient : WebViewClient() {

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
            handler.proceed()
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            return handleNavigation(request.url.toString())
        }

        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            return handleNavigation(url)
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            _pageLoading.value = true
            injectSdk(view)
        }

        override fun onPageFinished(view: WebView, url: String) {
            super.onPageFinished(view, url)
            _pageLoading.value = false
            injectSdk(view)
            bridgeExecutor.execute {
                try {
                    bridge?.emit("app.show")
                } catch (e: Exception) {
                    AppLog.e("MiniAppHost", e)
                }
            }
        }
    }

    private fun injectSdk(view: WebView) {
        try {
            view.evaluateJavascript(MiniAppSdkScript.SCRIPT, null)
        } catch (e: Exception) {
        }
    }

    private fun handleNavigation(url: String): Boolean {
        if (url.startsWith("miniapp://")) {
            val rest = url.substring("miniapp://".length)
            val appId = rest.split("/").firstOrNull() ?: ""
            if (appId.isNotEmpty()) {
                openMiniAppById(appId)
            }
            return true
        }
        val settings = hostSettings ?: MiniAppSettings()
        if (settings.adFilter && miniAppShouldBlockUrl(url)) {
            return true
        }
        return false
    }

    private fun openMiniAppById(appId: String) {
        val store = MiniAppStore(applicationContext)
        val dir = store.installedDir(appId)
        if (dir == null) {
            Toast.makeText(this, getString(R.string.miniapp_target_missing), Toast.LENGTH_SHORT).show()
            return
        }
        start(this, appId)
    }

    override fun evaluateJavascript(js: String) {
        runOnUiThread {
            try {
                webView?.evaluateJavascript(js, null)
            } catch (e: Exception) {
            }
        }
    }

    override fun showToast(message: String) {
        if (message.isEmpty()) return
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun showLoading(message: String?) {
        _loadingOverlay.value = message ?: ""
    }

    override fun hideLoading() {
        _loadingOverlay.value = null
    }

    override fun confirm(title: String, content: String): Boolean {
        val pending = PendingConfirm(title, content)
        pendingConfirm.set(pending)
        _confirmDialog.value = pending
        pending.latch.await()
        return pending.result
    }

    override fun actionSheet(items: List<String>): Int {
        val pending = PendingSheet(items)
        pendingSheet.set(pending)
        _actionSheet.value = pending
        pending.latch.await()
        return pending.result
    }

    private fun answerConfirm(result: Boolean) {
        val pending = pendingConfirm.getAndSet(null) ?: return
        _confirmDialog.value = null
        pending.result = result
        pending.latch.countDown()
    }

    private fun answerSheet(index: Int) {
        val pending = pendingSheet.getAndSet(null) ?: return
        _actionSheet.value = null
        pending.result = index
        pending.latch.countDown()
    }

    override fun navigateTo(path: String) {
        val base = instance?.baseUrl ?: return
        val clean = if (path.startsWith("/")) path.substring(1) else path
        val latch = CountDownLatch(1)
        runOnUiThread {
            try {
                webView?.loadUrl("$base/$clean")
            } finally {
                latch.countDown()
            }
        }
        latch.await(5, TimeUnit.SECONDS)
    }

    override fun navigateBack() {
        val latch = CountDownLatch(1)
        runOnUiThread {
            try {
                val view = webView
                if (view != null && view.canGoBack()) {
                    view.goBack()
                } else {
                    finish()
                }
            } finally {
                latch.countDown()
            }
        }
        latch.await(5, TimeUnit.SECONDS)
    }

    override fun exit() {
        runOnUiThread { finish() }
    }

    override fun openMiniApp(appId: String) {
        val store = MiniAppStore(applicationContext)
        val found = store.listInstalled().firstOrNull { it.appId == appId }
        if (found == null) {
            runOnUiThread {
                Toast.makeText(this, getString(R.string.miniapp_target_missing), Toast.LENGTH_SHORT).show()
            }
            return
        }
        runOnUiThread { start(this, appId) }
    }

    override fun resolveMiniAppUrl(appId: String): String? {
        return runtime?.resolveUrl(appId)
    }

    override fun log(message: String) {
        AppLog.i(message)
    }

    override fun getText(): String? {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        return manager.primaryClip?.getItemAt(0)?.text?.toString()
    }

    override fun setText(text: String) {
        val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        manager.setPrimaryClip(ClipData.newPlainText("miniapp", text))
    }

    override fun openPlayer(target: MiniAppPlayTarget): Boolean {
        var url = target.url
        if (target.isSniff) {
            val found = sniffForUrl(target)
            if (found.isNullOrEmpty()) return false
            url = found
        }
        showInline(url, target.title, target.headers)
        return true
    }

    override fun playerState(): Map<String, Any?> {
        return LinkedHashMap(playerStateSnapshot)
    }

    private fun sniffForUrl(target: MiniAppPlayTarget): String? {
        sniffResult = null
        val latch = CountDownLatch(1)
        sniffLatch.set(latch)
        runOnUiThread {
            val intent = Intent(this, MiniAppSniffActivity::class.java)
            intent.putExtra(MiniAppSniffActivity.EXTRA_URL, target.url)
            intent.putExtra(
                MiniAppSniffActivity.EXTRA_HEADERS,
                HaikuoJson.stringify(target.headers)
            )
            sniffLauncher.launch(intent)
        }
        latch.await(65, TimeUnit.SECONDS)
        sniffLatch.set(null)
        return sniffResult
    }

    private fun showInline(url: String, title: String, headers: Map<String, String>) {
        val latch = CountDownLatch(1)
        runOnUiThread {
            try {
                val container = ensureInlineContainer()
                container.cancelCastWait()
                container.endCastPrepare()
                container.clearCastAbort()
                val request = HaikuoPlayRequest(
                    url = url,
                    title = title,
                    headers = headers,
                    lines = listOf(HaikuoPlayLine(url, "线路1", headers))
                )
                container.setData(haikuoPlaybackSession(request, manifest?.name ?: ""))
                lastInlineUrl = url
                playerStateSnapshot = linkedMapOf<String, Any?>(
                    "opened" to true,
                    "url" to url,
                    "title" to title,
                    "playing" to false,
                    "positionMs" to 0,
                    "durationMs" to 0
                )
            } finally {
                latch.countDown()
            }
        }
        latch.await(5, TimeUnit.SECONDS)
        bridge?.emit("player.stateChange", LinkedHashMap(playerStateSnapshot))
    }

    private fun closeInline() {
        val container = inlineContainer ?: return
        inlineContainer = null
        runOnUiThread { container.hostDestroy() }
        val closedUrl = lastInlineUrl
        lastInlineUrl = ""
        playerStateSnapshot = mapOf("opened" to false)
        bridge?.emit("player.close", mapOf("url" to closedUrl))
    }

    private fun ensureInlineContainer(): PlayContainer {
        if (inlineContainer == null) {
            inlineContainer = PlayContainer(this).also {
                it.pageHostRef = this
                it.setPreviewMode(true)
            }
        }
        return requireNotNull(inlineContainer) { "inline container not created" }
    }

    override fun context(): Context = this

    override fun isPageAlive(): Boolean = !isFinishing && !isDestroyed

    override fun runOnUi(action: Runnable) {
        if (isPageAlive()) runOnUiThread(action)
    }

    override fun toast(text: CharSequence) {
        runOnUiThread {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    override fun requestNotificationPermission() {
        PermissionHelper.requestNotificationIfNeeded(this)
    }

    override fun showEpisodeSheet() {
    }

    override fun onPlaybackLinesExhausted(): Boolean = false
}
