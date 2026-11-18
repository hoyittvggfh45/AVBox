package com.github.avbox.osc.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.github.avbox.osc.R
import com.github.avbox.osc.haikuo.HAIKUO_CHECK_JS_PASS_SENTINEL
import com.github.avbox.osc.haikuo.HAIKUO_FLOAT_VIDEO_MAX_FAILURES
import com.github.avbox.osc.haikuo.HAIKUO_PAUSE_PAGE_VIDEOS_SCRIPT
import com.github.avbox.osc.haikuo.HAIKUO_RESOURCE_ENTRIES_SCRIPT
import com.github.avbox.osc.haikuo.HaikuoBridge
import com.github.avbox.osc.haikuo.HaikuoBridgeHandler
import com.github.avbox.osc.haikuo.HaikuoClipboard
import com.github.avbox.osc.haikuo.HaikuoDialogHandler
import com.github.avbox.osc.haikuo.HaikuoFormatException
import com.github.avbox.osc.haikuo.HaikuoJsEvaluator
import com.github.avbox.osc.haikuo.HaikuoJson
import com.github.avbox.osc.haikuo.HaikuoPlayLine
import com.github.avbox.osc.haikuo.HaikuoPlayRequest
import com.github.avbox.osc.haikuo.HaikuoPlayRoute
import com.github.avbox.osc.haikuo.HaikuoServer
import com.github.avbox.osc.haikuo.HaikuoSession
import com.github.avbox.osc.haikuo.HaikuoSniffRequest
import com.github.avbox.osc.haikuo.HaikuoStore
import com.github.avbox.osc.haikuo.HaikuoWebViewFetcher
import com.github.avbox.osc.haikuo.HaikuoX5Intercept
import com.github.avbox.osc.haikuo.haikuoBridgePadScript
import com.github.avbox.osc.haikuo.haikuoDialogDefault
import com.github.avbox.osc.haikuo.haikuoFloatVideoGate
import com.github.avbox.osc.haikuo.haikuoFloatVideoPick
import com.github.avbox.osc.haikuo.haikuoFloatVideoShouldPoll
import com.github.avbox.osc.haikuo.haikuoParseX5Intercept
import com.github.avbox.osc.haikuo.haikuoSafeFileName
import com.github.avbox.osc.haikuo.haikuoSplitUserAgent
import com.github.avbox.osc.haikuo.haikuoX5BridgeScript
import com.github.avbox.osc.haikuo.matchRuleTitle
import com.github.avbox.osc.haikuo.pickSniffedVideoUrl
import com.github.avbox.osc.haikuo.wildcardContains
import com.github.avbox.osc.player.PageHost
import com.github.avbox.osc.player.PlaybackService
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.page.HaikuoDialogUi
import com.github.avbox.osc.ui.page.HaikuoRunnerScreen
import com.github.avbox.osc.ui.page.haikuoDialogUiFrom
import com.github.avbox.osc.ui.player.PlayContainer
import com.github.avbox.osc.ui.player.haikuoPlaybackSession
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge
import com.github.avbox.osc.util.AppLog
import com.github.avbox.osc.util.PermissionHelper
import com.github.avbox.osc.util.SpiderStorageNotice
import java.io.File
import java.io.FileOutputStream
import java.util.Timer
import java.util.TimerTask
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

class HaikuoRunnerActivity : BaseActivity(), HaikuoBridgeHandler, HaikuoDialogHandler,
    HaikuoJsEvaluator, HaikuoWebViewFetcher, HaikuoClipboard, PageHost {

    companion object {
        const val EXTRA_PROGRAM_ID = "haikuo_program_id"

        fun start(context: Context, programId: String) {
            val intent = Intent(context, HaikuoRunnerActivity::class.java)
            intent.putExtra(EXTRA_PROGRAM_ID, programId)
            context.startActivity(intent)
        }
    }

    private class PendingDialog(val params: Map<String, Any?>) {
        val latch = CountDownLatch(1)

        @Volatile
        var result: Map<String, Any?> = emptyMap()
    }

    private var webView: WebView? = null
    private var session: HaikuoSession? = null
    private var server: HaikuoServer? = null
    private val pendingDialog = AtomicReference<PendingDialog?>(null)

    private var inlineContainer: PlayContainer? by mutableStateOf<PlayContainer?>(null)
        private set

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

    @Volatile
    private var sniffSession: HaikuoSniffState? = null
    private var sniffWebView: WebView? = null
    private var sniffThread: Thread? = null

    private var x5Session: HaikuoX5State? by mutableStateOf<HaikuoX5State?>(null)
        private set
    private var x5UiTick: Int by mutableStateOf(0)
        private set
    private var x5WebView: WebView? = null
    private var x5CanGoBack: Boolean = false
    private var x5AllowedUrl: String? = null
    private val x5InterceptorChain = Executors.newSingleThreadExecutor()
    private val x5VarChain = Executors.newSingleThreadExecutor()

    private var floatVideoUrl: String by mutableStateOf("")
        private set
    private var floatVideoContainer: PlayContainer? by mutableStateOf<PlayContainer?>(null)
        private set
    private var floatVideoTimer: Timer? = null
    private var floatVideoDismissed: Boolean = false
    private var floatVideoFailures: Int = 0
    private var floatVideoGeneration: Int = 0
    @Volatile
    private var floatVideoTickBusy: Boolean = false

    private var pendingPickCode: String = ""

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) deliverPickedFile(uri, pendingPickCode)
        pendingPickCode = ""
    }

    private fun refreshX5Ui() {
        x5UiTick++
    }

    private val _startUrl = MutableStateFlow<String?>(null)
    private val startUrl: StateFlow<String?> = _startUrl.asStateFlow()
    private val _bootError = MutableStateFlow<String?>(null)
    private val bootError: StateFlow<String?> = _bootError.asStateFlow()
    private val _dialog = MutableStateFlow<HaikuoDialogUi?>(null)
    private val dialog: StateFlow<HaikuoDialogUi?> = _dialog.asStateFlow()
    private val _bridgeReady = MutableStateFlow(false)
    private val bridgeReady: StateFlow<Boolean> = _bridgeReady.asStateFlow()

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (inlineContainer != null) {
                        closeInline()
                        return
                    }
                    if (x5CanGoBack) {
                        x5RowBack()
                        return
                    }
                    val view = webView
                    if (view != null && view.canGoBack()) {
                        view.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )
        val programId = intent.getStringExtra(EXTRA_PROGRAM_ID) ?: ""
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                val url by startUrl.collectAsState()
                val error by bootError.collectAsState()
                val dialogState by dialog.collectAsState()
                val bridgeState by bridgeReady.collectAsState()
                HaikuoRunnerScreen(
                    startUrl = url,
                    bootError = error,
                    dialog = dialogState,
                    bridgeReady = bridgeState,
                    inlineContainer = inlineContainer,
                    x5Session = x5Session,
                    x5UiTick = x5UiTick,
                    x5WebView = x5WebView,
                    floatVideoUrl = floatVideoUrl,
                    floatVideoContainer = floatVideoContainer,
                    onWebViewCreated = { view -> configureWebView(view) },
                    onDialogResult = { result -> answerDialog(result) },
                    onCloseFloatVideo = { closeFloatVideo() },
                    onNavigateBack = { finish() }
                )
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            boot(programId)
        }
    }

    override fun onResume() {
        super.onResume()
        inlineContainer?.hostResume()
        floatVideoContainer?.hostResume()
    }

    override fun onPause() {
        inlineContainer?.hostPause()
        floatVideoContainer?.hostPause()
        super.onPause()
    }

    override fun onDestroy() {
        closeInline()
        closeFloatVideoLayer()
        val sniffing = sniffSession
        if (sniffing != null) finishSniff(sniffing, "")
        x5InterceptorChain.shutdownNow()
        x5VarChain.shutdownNow()
        destroyX5WebView()
        val pending = pendingDialog.get()
        if (pending != null) {
            answerDialog(haikuoDialogDefault(pending.params))
        }
        try {
            server?.stop()
        } catch (e: Exception) {
            AppLog.e("HaikuoRunner", e)
        }
        try {
            session?.close()
        } catch (e: Exception) {
            AppLog.e("HaikuoRunner", e)
        }
        try {
            val view = webView
            webView = null
            view?.destroy()
        } catch (e: Exception) {
            AppLog.e("HaikuoRunner", e)
        }
        super.onDestroy()
    }

    private fun boot(programId: String) {
        val store = HaikuoStore(applicationContext)
        val program = store.list().firstOrNull { it.id == programId }
        if (program == null) {
            _bootError.value = getString(R.string.haikuo_missing)
            return
        }
        val created = HaikuoSession(
            program = program,
            store = store,
            appContext = applicationContext,
            appVersionCode = appVersionCode(),
            dialogHandler = this
        )
        created.jsEvaluator = this
        created.webViewFetcher = this
        created.clipboard = this
        session = created
        runOnUiThread { attachBridge() }
        val started = HaikuoServer.start(created)
        if (started == null) {
            _bootError.value = getString(R.string.haikuo_server_failed)
            return
        }
        server = started
        _startUrl.value = started.baseUrl()
    }

    private fun appVersionCode(): Int {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                info.versionCode
            }
        } catch (e: Exception) {
            0
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
        attachBridge()
        view.webViewClient = HaikuoWebClient()
        view.webChromeClient = object : WebChromeClient() {
            override fun onJsAlert(
                view: WebView,
                url: String,
                message: String,
                result: JsResult
            ): Boolean {
                result.confirm()
                return true
            }

            override fun onJsConfirm(
                view: WebView,
                url: String,
                message: String,
                result: JsResult
            ): Boolean {
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

    private fun attachBridge() {
        val current = session ?: return
        val view = webView ?: return
        if (_bridgeReady.value) return
        view.addJavascriptInterface(HaikuoBridge(current, this), "HaikuoNative")
        _bridgeReady.value = true
    }

    private inner class HaikuoWebClient : WebViewClient() {

        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(
            view: WebView,
            handler: SslErrorHandler,
            error: android.net.http.SslError
        ) {
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
        }

        private fun handleNavigation(url: String): Boolean {
            if (url.startsWith("http://127.0.0.1") || url.startsWith("about:")) {
                return false
            }
            openExternal(url)
            return true
        }
    }

    private fun openExternal(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            AppLog.e("HaikuoRunner", e)
        }
    }

    override fun show(params: Map<String, Any?>): Map<String, Any?> {
        val pending = PendingDialog(params)
        pendingDialog.set(pending)
        _dialog.value = haikuoDialogUiFrom(params)
        pending.latch.await()
        _dialog.value = null
        pendingDialog.set(null)
        if (pending.result.isEmpty()) {
            return haikuoDialogDefault(params)
        }
        return pending.result
    }

    private fun answerDialog(result: Map<String, Any?>) {
        val pending = pendingDialog.get() ?: return
        pending.result = result
        pending.latch.countDown()
    }

    override fun evaluate(script: String): String? {
        val latch = CountDownLatch(1)
        var outcome: String? = null
        runOnUiThread {
            val view = webView
            if (view == null) {
                latch.countDown()
                return@runOnUiThread
            }
            try {
                view.evaluateJavascript(script) { value ->
                    outcome = decodeJsValue(value)
                    latch.countDown()
                }
            } catch (e: Exception) {
                latch.countDown()
            }
        }
        latch.await(30, TimeUnit.SECONDS)
        return outcome
    }

    private fun decodeJsValue(value: String?): String? {
        if (value == null || value == "null" || value == "undefined") return null
        if (value.length >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            val decoded = HaikuoJson.parse(value)
            if (decoded is String) return decoded
        }
        return value
    }

    private fun evaluateAsync(script: String) {
        runOnUiThread {
            try {
                webView?.evaluateJavascript(script, null)
            } catch (e: Exception) {
                AppLog.e("HaikuoRunner", e)
            }
        }
    }

    override fun getText(): String {
        return try {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = manager.primaryClip
            if (clip != null && clip.itemCount > 0) {
                clip.getItemAt(0).coerceToText(this).toString()
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    override fun setText(text: String) {
        try {
            val manager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            manager.setPrimaryClip(ClipData.newPlainText("haikuo", text))
        } catch (e: Exception) {
            AppLog.e("HaikuoRunner", e)
        }
    }

    private fun showToast(text: String) {
        runOnUiThread {
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onToast(text: String) {
        if (text.isNotEmpty()) showToast(text)
    }

    override fun onCopy(text: String) {
        setText(text)
        showToast(getString(R.string.haikuo_copied))
    }

    override fun onOpen(url: String, title: String) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            openExternal(url)
        }
    }

    override fun onOpenSearch(keyword: String, rule: String, group: String, auto: Boolean) {
        if (rule.isNotEmpty()) {
            switchRule(rule) { switched ->
                if (!switched && keyword.isNotEmpty()) {
                    searchInPage(keyword)
                }
            }
        } else if (keyword.isNotEmpty()) {
            searchInPage(keyword)
        }
    }

    override fun onOpenRule(title: String) {
        if (title.isNotEmpty()) {
            switchRule(title) { }
        }
    }

    private fun searchInPage(keyword: String) {
        evaluateAsync("window.__haikuoSearch(" + HaikuoJson.stringify(keyword) + ")")
    }

    private fun switchRule(title: String, done: (Boolean) -> Unit) {
        lifecycleScope.launch(Dispatchers.IO) {
            val store = HaikuoStore(applicationContext)
            val programs = store.list()
            val matched = matchRuleTitle(title, programs.map { it.name })
            val target = programs.firstOrNull { it.name == matched }
            runOnUiThread {
                if (target == null || target.id == session?.program?.id) {
                    done(false)
                    return@runOnUiThread
                }
                start(this@HaikuoRunnerActivity, target.id)
                finish()
                done(true)
            }
        }
    }

    override fun onImportCode(code: String) {
        if (code.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val store = HaikuoStore(applicationContext)
                val result = store.importText(code, "规则内导入")
                showToast(result.summary)
            } catch (e: HaikuoFormatException) {
                showToast(e.message ?: "")
            } catch (e: Exception) {
                showToast(e.message ?: e.toString())
            }
        }
    }

    override fun onPickFile(code: String) {
        pendingPickCode = code
        try {
            SpiderStorageNotice.suppress()
            pickFileLauncher.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            pendingPickCode = ""
            AppLog.e("HaikuoRunner", e)
        }
    }

    private fun deliverPickedFile(uri: Uri, code: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            var deliverPath: String? = null
            try {
                val name = queryDisplayName(uri) ?: "file"
                val dir = File(filesDir, "haikuo/fileSelect")
                dir.mkdirs()
                val copy = File(dir, "_fileSelect_$name")
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(copy).use { output ->
                        input.copyTo(output)
                    }
                }
                deliverPath = copy.absolutePath
            } catch (e: Exception) {
                AppLog.e("HaikuoRunner", e)
            }
            val path = deliverPath ?: return@launch
            evaluateAsync(
                "window.__haikuoFileSelected && window.__haikuoFileSelected(" +
                    HaikuoJson.stringify(path) + ", " + HaikuoJson.stringify(code) + ");"
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    return cursor.getString(index)
                }
            }
        } catch (e: Exception) {
            AppLog.e("HaikuoRunner", e)
        }
        return null
    }

    override fun onDownload(url: String, title: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            downloadToDocs(url, title)
        }
    }

    override fun onDownloadFile(url: String, fileName: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            downloadToDocs(url, fileName)
        }
    }

    private fun downloadToDocs(url: String, name: String) {
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            null
        }
        if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) {
            showToast(getString(R.string.haikuo_download_bad_url))
            return
        }
        var fileName = name.trim()
        if (fileName.isEmpty()) fileName = uri.lastPathSegment ?: ""
        if (fileName.isEmpty()) fileName = "download"
        fileName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (!tryDownloadToDocs(uri.toString(), fileName)) {
            showToast(getString(R.string.haikuo_download_failed))
        }
    }

    private fun tryDownloadToDocs(url: String, fileName: String): Boolean {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(60, TimeUnit.SECONDS)
                .build()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.code < 200 || response.code >= 300) return false
                val bytes = response.body?.bytes() ?: return false
                val dir = File(filesDir, "haikuo_downloads")
                dir.mkdirs()
                val file = File(dir, fileName)
                FileOutputStream(file).use { it.write(bytes) }
                showToast(getString(R.string.haikuo_download_saved, file.absolutePath))
                true
            }
        } catch (e: Exception) {
            AppLog.e("HaikuoRunner", e)
            false
        }
    }

    override fun onSaveImage(urls: List<String>) {
        lifecycleScope.launch(Dispatchers.IO) {
            for (url in urls) {
                val uri = try {
                    Uri.parse(url)
                } catch (e: Exception) {
                    null
                }
                if (uri == null || (uri.scheme != "http" && uri.scheme != "https")) continue
                var fileName = uri.lastPathSegment ?: ""
                if (fileName.isEmpty()) fileName = "image"
                fileName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                if (tryDownloadToDocs(url, fileName)) return@launch
            }
            showToast(getString(R.string.haikuo_image_save_failed))
        }
    }

    override fun onShare(text: String, url: String) {
        val body = listOf(text, url).filter { it.isNotEmpty() }.joinToString("\n")
        if (body.isEmpty()) return
        try {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, body)
            startActivity(Intent.createChooser(intent, null))
        } catch (e: Exception) {
            AppLog.e("HaikuoRunner", e)
        }
    }

    override fun onSetPageImmersive(immersive: Boolean) {
        runOnUiThread {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (immersive) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    override fun onPop() {
        finish()
    }

    override fun onShellFavorite() {
        evaluateAsync("window.__haikuoToggleFavorite()")
    }

    override fun onShellSearch() {
        evaluateAsync("window.__haikuoSearch('')")
    }

    override fun onShellReload() {
        runOnUiThread {
            webView?.reload()
        }
    }

    override fun onSniff(request: HaikuoSniffRequest) {
        val previous = sniffSession
        if (previous != null) finishSniff(previous, "")
        if (request.id.isEmpty()) return
        if (!request.url.startsWith("http://") && !request.url.startsWith("https://")) {
            deliverSniffResult(request.id, "")
            return
        }
        val state = HaikuoSniffState(request, System.currentTimeMillis() + 30000L)
        val created = CountDownLatch(1)
        var createdView: WebView? = null
        runOnUiThread {
            if (isDestroyed || isFinishing) {
                created.countDown()
                return@runOnUiThread
            }
            val view = WebView(this)
            configureAuxWebView(view)
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    v: WebView,
                    r: WebResourceRequest
                ): Boolean {
                    return request.blockRules.any {
                        wildcardContains(r.url.toString(), it)
                    }
                }

                override fun onPageFinished(v: WebView, finishedUrl: String) {
                    if (request.pageJs.isNotEmpty() && !state.pageJsDone) {
                        state.pageJsDone = true
                        try {
                            v.evaluateJavascript(request.pageJs, null)
                        } catch (e: Exception) {
                            AppLog.e("HaikuoRunner", e)
                        }
                    }
                }
            }
            if (request.ua.isNotEmpty()) view.settings.userAgentString = request.ua
            attachHidden(view)
            createdView = view
            created.countDown()
        }
        created.await(10, TimeUnit.SECONDS)
        val view = createdView
        if (view == null) {
            deliverSniffResult(request.id, "")
            return
        }
        sniffSession = state
        sniffWebView = view
        val headers = HashMap<String, String>()
        if (request.referer.isNotEmpty()) headers["Referer"] = request.referer
        runOnUiThread { view.loadUrl(request.url, headers) }
        val thread = Thread { sniffLoop(state, view) }
        sniffThread = thread
        thread.start()
    }

    private fun sniffLoop(state: HaikuoSniffState, view: WebView) {
        while (!state.done && sniffSession === state) {
            if (System.currentTimeMillis() > state.deadlineMs) {
                finishSniff(state, "")
                return
            }
            try {
                if (state.request.mode == "webRule" && state.request.js.isNotEmpty()) {
                    val pad = haikuoBridgePadScript(pageCookiesFor(state.request.url))
                    val script = "(function(){try{$pad}catch(e){}var __r=\"\";try{__r=(0,eval)(" +
                        HaikuoJson.stringify(state.request.js) +
                        ");}catch(e){__r=\"\";}return typeof __r===\"string\"?__r:(__r==null?\"\":String(__r));})()"
                    val raw = evalBlocking(view, script, 5000)
                    drainBridgeWriteFilesSync(view)
                    val text = sniffResultText(raw)
                    if (text.isNotEmpty()) {
                        finishSniff(state, text)
                        return
                    }
                } else {
                    val raw = evalBlocking(view, HAIKUO_RESOURCE_ENTRIES_SCRIPT, 5000)
                    val picked = pickSniffedVideoUrl(
                        sniffUrlList(raw),
                        includeRules = state.request.videoRules,
                        excludeRules = state.request.videoExcludeRules + state.request.blockRules
                    )
                    if (picked.isNotEmpty()) {
                        finishSniff(state, picked)
                        return
                    }
                }
            } catch (e: Exception) {
                AppLog.e("HaikuoRunner", e)
            }
            try {
                Thread.sleep(250)
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun finishSniff(state: HaikuoSniffState, result: String) {
        if (state.done) return
        state.done = true
        if (sniffSession === state) {
            sniffSession = null
            val view = sniffWebView
            sniffWebView = null
            sniffThread = null
            if (view != null) runOnUiThread { detachHidden(view) }
        }
        deliverSniffResult(state.request.id, result)
    }

    private fun deliverSniffResult(id: String, result: String) {
        evaluateAsync(
            "window.__haikuoSniffResult && window.__haikuoSniffResult(" +
                HaikuoJson.stringify(id) + ", " + HaikuoJson.stringify(result) + ");"
        )
    }

    private fun sniffResultText(raw: String?): String {
        if (raw == null) return ""
        val text = raw.trim()
        if (text.isEmpty() || text == "null" || text == "undefined") return ""
        return text
    }

    private fun sniffUrlList(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        val decoded = HaikuoJson.parse(raw.trim())
        if (decoded is List<*>) return decoded.map { it.toString() }
        return emptyList()
    }

    private fun evalBlocking(view: WebView, script: String, timeoutMs: Long): String? {
        val latch = CountDownLatch(1)
        var outcome: String? = null
        runOnUiThread {
            try {
                view.evaluateJavascript(script) { value ->
                    outcome = decodeJsValue(value)
                    latch.countDown()
                }
            } catch (e: Exception) {
                latch.countDown()
            }
        }
        latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        return outcome
    }

    private fun drainBridgeWriteFilesSync(view: WebView) {
        try {
            val raw = evalBlocking(view, "JSON.stringify(window.__hkWriteFiles||[])", 5000)
            runOnUiThread {
                try {
                    view.evaluateJavascript("window.__hkWriteFiles=[];", null)
                } catch (e: Exception) {
                    AppLog.e("HaikuoRunner", e)
                }
            }
            if (raw.isNullOrEmpty()) return
            val decoded = HaikuoJson.parse(raw)
            if (decoded !is List<*>) return
            val active = session ?: return
            for (entry in decoded) {
                if (entry !is Map<*, *>) continue
                val base = haikuoSafeFileName(HaikuoJson.str(entry["path"])) ?: continue
                active.storeSavedFile(
                    base,
                    HaikuoJson.str(entry["content"]).toByteArray(Charsets.UTF_8)
                )
            }
        } catch (e: Exception) {
            AppLog.e("HaikuoRunner", e)
        }
    }

    private fun drainBridgeWriteFilesAsync(view: WebView) {
        Thread { drainBridgeWriteFilesSync(view) }.start()
    }

    private fun pageCookiesFor(url: String): String {
        return try {
            CookieManager.getInstance().getCookie(url) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun configureAuxWebView(view: WebView) {
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
        settings.builtInZoomControls = false
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.defaultTextEncodingName = "utf-8"
    }

    private fun attachHidden(view: WebView) {
        val root = findViewById<ViewGroup>(android.R.id.content)
        view.visibility = View.INVISIBLE
        root.addView(view, ViewGroup.LayoutParams(1, 1))
    }

    private fun detachHidden(view: WebView) {
        try {
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
        } catch (e: Exception) {
            AppLog.e("HaikuoRunner", e)
        }
    }

    private fun stringList(value: Any?): List<String> {
        if (value !is List<*>) return emptyList()
        return value.map { it.toString() }
    }

    private fun stringMap(value: Any?): Map<String, String> {
        if (value !is Map<*, *>) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for ((key, v) in value) {
            out[key.toString()] = v.toString()
        }
        return out
    }

    override fun fetch(params: Map<String, Any?>): String? {
        val url = HaikuoJson.str(params["url"])
        if (!url.startsWith("http://") && !url.startsWith("https://")) return ""
        val timeoutMs = (HaikuoJson.num(params["timeout"])?.toLong() ?: 30000L)
            .coerceIn(1000L, 120000L)
        val checkJs = HaikuoJson.str(params["checkJs"])
        val blockRules = stringList(params["blockRules"])
        val split = haikuoSplitUserAgent(stringMap(params["headers"]))
        val loadedLatch = CountDownLatch(1)
        val created = CountDownLatch(1)
        var createdView: WebView? = null
        runOnUiThread {
            if (isDestroyed || isFinishing) {
                created.countDown()
                return@runOnUiThread
            }
            val view = WebView(this)
            configureAuxWebView(view)
            view.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    v: WebView,
                    r: WebResourceRequest
                ): Boolean {
                    return blockRules.any { wildcardContains(r.url.toString(), it) }
                }

                override fun onPageFinished(v: WebView, finishedUrl: String) {
                    loadedLatch.countDown()
                }
            }
            if (split.userAgent.isNotEmpty()) view.settings.userAgentString = split.userAgent
            attachHidden(view)
            createdView = view
            created.countDown()
        }
        created.await(10, TimeUnit.SECONDS)
        val view = createdView ?: return ""
        try {
            Thread.sleep(60)
            runOnUiThread { view.loadUrl(url, split.headers) }
            val deadline = System.currentTimeMillis() + timeoutMs
            var passed = checkJs.isEmpty()
            if (checkJs.isNotEmpty()) {
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val pad = haikuoBridgePadScript(pageCookiesFor(url))
                        val script = "(function(){try{$pad}catch(e){}try{var __r=(0,eval)(" +
                            HaikuoJson.stringify(checkJs) +
                            ");return (__r===null||__r===undefined||__r===false||__r===\"\")?\"\":\"" +
                            HAIKUO_CHECK_JS_PASS_SENTINEL + "\";}catch(e){return \"\";}})()"
                        val raw = evalBlocking(view, script, 5000)
                        drainBridgeWriteFilesSync(view)
                        if (raw == HAIKUO_CHECK_JS_PASS_SENTINEL) {
                            passed = true
                            break
                        }
                    } catch (e: Exception) {
                        AppLog.e("HaikuoRunner", e)
                    }
                    Thread.sleep(300)
                }
            } else {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining > 0) loadedLatch.await(remaining, TimeUnit.MILLISECONDS)
                Thread.sleep(800)
                passed = true
            }
            if (!passed) return ""
            return evalBlocking(view, "document.documentElement.outerHTML", 10000) ?: ""
        } catch (e: Exception) {
            AppLog.e("HaikuoRunner", e)
            return ""
        } finally {
            runOnUiThread { detachHidden(view) }
        }
    }

    override fun onX5Row(payload: Map<String, Any?>) {
        val url = HaikuoJson.str(payload["url"])
        val desc = HaikuoJson.str(payload["desc"])
        val extra = payload["extra"] as? Map<*, *> ?: emptyMap<Any?, Any?>()
        val blockRules = stringList(extra["blockRules"]).filter { it.trim().isNotEmpty() }
        if (url.isEmpty() ||
            (!url.startsWith("http://") && !url.startsWith("https://") &&
                !url.startsWith("about:"))
        ) {
            x5RowFallback()
            return
        }
        val existing = x5Session
        val existingView = x5WebView
        if (existing != null && existingView != null) {
            val urlChanged = url != existing.url
            val newUa = HaikuoJson.str(extra["ua"])
            val uaChanged = newUa != existing.ua
            existing.url = url
            existing.desc = desc
            existing.ua = newUa
            existing.referer = HaikuoJson.str(extra["referer"])
            existing.js = HaikuoJson.str(extra["js"])
            existing.jsLoadingInject = extra["jsLoadingInject"] == true
            existing.blockRules = blockRules
            existing.urlInterceptor = HaikuoJson.str(extra["urlInterceptor"])
            existing.canBack = extra["canBack"] == true
            existing.floatVideo = haikuoFloatVideoGate(extra["floatVideo"])
            existing.showProgress = extra["showProgress"] != false
            existing.index = HaikuoJson.num(payload["index"])?.toInt() ?: existing.index
            existing.topOffset = HaikuoJson.num(payload["topOffset"])?.toDouble()
                ?: existing.topOffset
            existing.visible = true
            existing.hideLoadingRequested = false
            runOnUiThread {
                if (uaChanged && existing.ua.isNotEmpty()) {
                    existingView.settings.userAgentString = existing.ua
                }
                if (urlChanged) {
                    existing.progress = 0
                    existing.currentUrl = ""
                    existing.expectInitialNavigation = true
                    val headers = HashMap<String, String>()
                    if (existing.referer.isNotEmpty()) headers["Referer"] = existing.referer
                    try {
                        existingView.loadUrl(url, headers)
                    } catch (e: Exception) {
                        AppLog.e("HaikuoRunner", e)
                        x5RowFallback()
                    }
                }
            }
            if (urlChanged) resetFloatVideoState()
            refreshX5Ui()
            updateX5CanGoBack()
            syncFloatVideoPolling()
            return
        }
        val state = HaikuoX5State(
            url = url,
            desc = desc,
            ua = HaikuoJson.str(extra["ua"]),
            referer = HaikuoJson.str(extra["referer"]),
            js = HaikuoJson.str(extra["js"]),
            jsLoadingInject = extra["jsLoadingInject"] == true,
            blockRules = blockRules,
            urlInterceptor = HaikuoJson.str(extra["urlInterceptor"]),
            canBack = extra["canBack"] == true,
            floatVideo = haikuoFloatVideoGate(extra["floatVideo"]),
            showProgress = extra["showProgress"] != false,
            index = HaikuoJson.num(payload["index"])?.toInt() ?: 0,
            topOffset = HaikuoJson.num(payload["topOffset"])?.toDouble() ?: 0.0
        )
        runOnUiThread {
            try {
                val view = WebView(this)
                configureAuxWebView(view)
                view.setBackgroundColor(android.graphics.Color.WHITE)
                view.addJavascriptInterface(HaikuoX5Bridge(), "HaikuoX5Bridge")
                view.webViewClient = x5WebViewClient(state)
                view.webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(v: WebView, progress: Int) {
                        state.progress = progress
                        if (state.showProgress && state.visible) refreshX5Ui()
                    }
                }
                x5Session = state
                x5WebView = view
                resetFloatVideoState()
                if (state.ua.isNotEmpty()) view.settings.userAgentString = state.ua
                val headers = HashMap<String, String>()
                if (state.referer.isNotEmpty()) headers["Referer"] = state.referer
                view.loadUrl(url, headers)
                refreshX5Ui()
                syncFloatVideoPolling()
            } catch (e: Exception) {
                AppLog.e("HaikuoRunner", e)
                x5RowFallback()
            }
        }
    }

    private fun x5WebViewClient(state: HaikuoX5State): WebViewClient {
        return object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val target = request.url.toString()
                if (target == x5AllowedUrl) {
                    x5AllowedUrl = null
                    return false
                }
                if (state.blockRules.any { wildcardContains(target, it) }) return true
                if (!request.isForMainFrame) return false
                if (state.expectInitialNavigation) {
                    state.expectInitialNavigation = false
                    return false
                }
                if (state.urlInterceptor.isEmpty()) return false
                x5InterceptorChain.execute {
                    val decision = decideX5Navigation(state, target)
                    if (decision.action == "exec") {
                        execX5InterceptorCode(state, decision.code)
                    } else if (decision.action == "allow") {
                        runOnUiThread {
                            if (x5Session === state && x5WebView === view) {
                                x5AllowedUrl = target
                                view.loadUrl(target)
                            }
                        }
                    }
                }
                return true
            }

            override fun onPageStarted(view: WebView, startedUrl: String, favicon: Bitmap?) {
                if (x5Session !== state) return
                state.currentUrl = startedUrl
                state.progress = 0
                resetFloatVideoState()
                injectX5Bridge(state, view, startedUrl)
                if (state.jsLoadingInject && state.js.isNotEmpty()) {
                    try {
                        view.evaluateJavascript(state.js, null)
                    } catch (e: Exception) {
                        AppLog.e("HaikuoRunner", e)
                    }
                    drainBridgeWriteFilesAsync(view)
                }
                if (state.showProgress && state.visible) refreshX5Ui()
                syncFloatVideoPolling()
            }

            override fun onPageFinished(view: WebView, finishedUrl: String) {
                if (x5Session !== state) return
                state.currentUrl = finishedUrl
                state.progress = 100
                injectX5Bridge(state, view, finishedUrl)
                if (state.js.isNotEmpty()) {
                    try {
                        view.evaluateJavascript(state.js, null)
                    } catch (e: Exception) {
                        AppLog.e("HaikuoRunner", e)
                    }
                }
                drainBridgeWriteFilesAsync(view)
                refreshX5Ui()
                syncFloatVideoPolling()
                updateX5CanGoBack()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame && x5Session === state) {
                    x5RowFallback()
                }
            }
        }
    }

    private fun injectX5Bridge(state: HaikuoX5State, view: WebView, pageUrl: String) {
        try {
            val cookies = pageCookiesFor(if (pageUrl.isEmpty()) state.url else pageUrl)
            val script = haikuoBridgePadScript(cookies) + haikuoX5BridgeScript()
            view.evaluateJavascript(script, null)
        } catch (e: Exception) {
            AppLog.e("HaikuoRunner", e)
        }
    }

    private fun decideX5Navigation(state: HaikuoX5State, url: String): HaikuoX5Intercept {
        if (x5Session !== state) {
            return HaikuoX5Intercept("allow", "")
        }
        val view = webView
            ?: return HaikuoX5Intercept("allow", "")
        return try {
            val raw = evalBlocking(
                view,
                "window.__haikuoUrlInterceptor && window.__haikuoUrlInterceptor(" +
                    HaikuoJson.stringify(state.urlInterceptor) + ", " +
                    HaikuoJson.stringify(url) + ");",
                5000
            )
            haikuoParseX5Intercept(raw ?: "")
        } catch (e: Exception) {
            HaikuoX5Intercept("allow", "")
        }
    }

    private fun execX5InterceptorCode(state: HaikuoX5State, code: String) {
        val view = x5WebView ?: return
        if (x5Session !== state) return
        runOnUiThread {
            injectX5Bridge(state, view, state.currentUrl)
            try {
                view.evaluateJavascript(code, null)
            } catch (e: Exception) {
                AppLog.e("HaikuoRunner", e)
            }
        }
        drainBridgeWriteFilesAsync(view)
    }

    private inner class HaikuoX5Bridge {
        @JavascriptInterface
        fun postMessage(message: String) {
            onX5BridgeMessage(message)
        }
    }

    private fun onX5BridgeMessage(message: String) {
        val decoded = HaikuoJson.parse(message)
        if (decoded !is Map<*, *>) return
        val method = HaikuoJson.str(decoded["method"])
        val args = if (decoded["args"] is List<*>) decoded["args"] as List<*> else emptyList<Any?>()
        fun argAt(index: Int): Any? = if (index < args.size) args[index] else null
        try {
            when (method) {
                "open" -> {
                    val raw = argAt(0)?.toString() ?: ""
                    var title = ""
                    var url = raw
                    try {
                        val inner = HaikuoJson.parse(raw)
                        if (inner is Map<*, *> && inner["url"] != null) {
                            url = HaikuoJson.str(inner["url"])
                            title = HaikuoJson.str(inner["title"])
                        }
                    } catch (e: Exception) {
                        AppLog.e("HaikuoRunner", e)
                    }
                    if (url.isEmpty()) return
                    if (webView != null) {
                        evaluateAsync(
                            "window.__haikuoX5Open && window.__haikuoX5Open(" +
                                HaikuoJson.stringify(title) + ", " +
                                HaikuoJson.stringify(url) + ");"
                        )
                    } else if (url.startsWith("http://") || url.startsWith("https://")) {
                        openExternal(url)
                    }
                }
                "playVideo" -> {
                    val url = argAt(0)?.toString() ?: ""
                    if (url.isNotEmpty()) {
                        evaluateAsync(
                            "window.__haikuoX5Play && window.__haikuoX5Play(" +
                                HaikuoJson.stringify(url) + ");"
                        )
                    }
                }
                "parseLazyRule", "parseLazyRuleAsync" -> {
                    val lazy = argAt(0)?.toString() ?: ""
                    if (lazy.isNotEmpty()) {
                        evaluateAsync(
                            "window.__haikuoX5ParseLazy && window.__haikuoX5ParseLazy(" +
                                HaikuoJson.stringify(lazy) + ");"
                        )
                    }
                }
                "putVar", "clearVar", "putMyVar", "clearMyVar" -> {
                    val key = argAt(0)
                    val value = argAt(1)
                    x5VarChain.execute {
                        evaluateAsync(
                            "window.__haikuoX5Var && window.__haikuoX5Var(" +
                                HaikuoJson.stringify(method) + ", " +
                                HaikuoJson.stringify(key) + ", " +
                                HaikuoJson.stringify(if (value == null) "" else value.toString()) + ");"
                        )
                    }
                }
                "setWebUa" -> {
                    val ua = argAt(0)?.toString() ?: ""
                    if (ua.isNotEmpty()) {
                        x5Session?.ua = ua
                        val view = x5WebView
                        if (view != null) {
                            runOnUiThread { view.settings.userAgentString = ua }
                        }
                    }
                }
                "hideLoading" -> {
                    val state = x5Session
                    if (state != null && !state.hideLoadingRequested) {
                        state.hideLoadingRequested = true
                        refreshX5Ui()
                    }
                }
                "refreshPage" -> {
                    val view = x5WebView
                    if (view != null) runOnUiThread { view.reload() }
                }
                "back" -> x5RowBack()
                "fetch" -> {
                    val id = HaikuoJson.num(decoded["id"])?.toInt() ?: 0
                    val url = argAt(0)?.toString() ?: ""
                    val opts = argAt(1)
                    if (url.isNotEmpty()) {
                        evaluateAsync(
                            "window.__haikuoX5Fetch && window.__haikuoX5Fetch(" +
                                "$id, " + HaikuoJson.stringify(url) + ", " +
                                HaikuoJson.stringify(opts) + ");"
                        )
                    }
                }
                "toast" -> showToast(argAt(0)?.toString() ?: "")
                else -> AppLog.e("HaikuoRunner", "HaikuoX5Bridge: unknown method: $method")
            }
        } catch (e: Exception) {
            AppLog.e("HaikuoRunner", e)
        }
    }

    override fun onX5RowClear() {
        val state = x5Session ?: return
        if (!state.visible) return
        state.visible = false
        stopFloatVideoPolling()
        val view = x5WebView
        if (view != null) {
            runOnUiThread {
                try {
                    view.evaluateJavascript(HAIKUO_PAUSE_PAGE_VIDEOS_SCRIPT, null)
                } catch (e: Exception) {
                    AppLog.e("HaikuoRunner", e)
                }
            }
            drainBridgeWriteFilesAsync(view)
        }
        refreshX5Ui()
        updateX5CanGoBack()
    }

    override fun onX5RowRefresh(payload: Map<String, Any?>) {
        val state = x5Session ?: return
        val view = x5WebView ?: return
        var changed = false
        if (payload.containsKey("desc")) {
            val desc = HaikuoJson.str(payload["desc"])
            if (desc != state.desc) {
                state.desc = desc
                changed = true
            }
        }
        if (payload.containsKey("url")) {
            val url = HaikuoJson.str(payload["url"])
            if (url.isEmpty()) {
                resetFloatVideoState()
                runOnUiThread { view.reload() }
            } else if (url != state.url &&
                (url.startsWith("http://") || url.startsWith("https://") ||
                    url.startsWith("about:"))
            ) {
                resetFloatVideoState()
                state.url = url
                state.progress = 0
                state.currentUrl = ""
                state.expectInitialNavigation = true
                state.visible = true
                runOnUiThread {
                    val headers = HashMap<String, String>()
                    if (state.referer.isNotEmpty()) headers["Referer"] = state.referer
                    try {
                        view.loadUrl(url, headers)
                    } catch (e: Exception) {
                        AppLog.e("HaikuoRunner", e)
                        x5RowFallback()
                    }
                }
                changed = true
            }
        }
        if (changed) refreshX5Ui()
    }

    override fun onX5RowBack() {
        x5RowBack()
    }

    private fun x5RowBack() {
        val view = x5WebView ?: return
        if (x5Session == null) return
        runOnUiThread {
            try {
                if (view.canGoBack()) view.goBack()
            } catch (e: Exception) {
                AppLog.e("HaikuoRunner", e)
            }
        }
        updateX5CanGoBack()
    }

    private fun updateX5CanGoBack() {
        val view = x5WebView
        val state = x5Session
        Thread {
            var can = false
            if (view != null && state != null && state.canBack && state.visible) {
                val latch = CountDownLatch(1)
                runOnUiThread {
                    try {
                        can = view.canGoBack()
                    } catch (e: Exception) {
                        can = false
                    }
                    latch.countDown()
                }
                latch.await(2, TimeUnit.SECONDS)
            }
            if (can != x5CanGoBack) {
                x5CanGoBack = can
                refreshX5Ui()
                evaluateAsync(
                    "window.__haikuoX5CanGoBack = " + (if (can) "true" else "false") + ";"
                )
            }
        }.start()
    }

    private fun x5RowFallback() {
        resetFloatVideoState()
        x5Session = null
        destroyX5WebView()
        x5CanGoBack = false
        refreshX5Ui()
        evaluateAsync(
            "window.__haikuoX5Fallback && window.__haikuoX5Fallback();" +
                "window.__haikuoX5CanGoBack = false;"
        )
    }

    private fun destroyX5WebView() {
        val view = x5WebView ?: return
        x5WebView = null
        runOnUiThread {
            try {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            } catch (e: Exception) {
                AppLog.e("HaikuoRunner", e)
            }
        }
    }

    override fun onX5FetchResult(payload: Map<String, Any?>) {
        val id = HaikuoJson.num(payload["id"])?.toInt() ?: 0
        val view = x5WebView ?: return
        val result = HaikuoJson.str(payload["result"])
        runOnUiThread {
            try {
                view.evaluateJavascript(
                    "window.__hkX5ResolveFetch && window.__hkX5ResolveFetch(" +
                        "$id, " + HaikuoJson.stringify(result) + ");",
                    null
                )
            } catch (e: Exception) {
                AppLog.e("HaikuoRunner", e)
            }
        }
    }

    private fun stopFloatVideoPolling() {
        floatVideoTimer?.cancel()
        floatVideoTimer = null
    }

    private fun resetFloatVideoState() {
        val hadLayer = floatVideoUrl.isNotEmpty()
        stopFloatVideoPolling()
        floatVideoUrl = ""
        closeFloatVideoLayer()
        floatVideoDismissed = false
        floatVideoTickBusy = false
        floatVideoFailures = 0
        floatVideoGeneration++
        if (hadLayer) refreshX5Ui()
    }

    private fun closeFloatVideoLayer() {
        val container = floatVideoContainer ?: return
        floatVideoContainer = null
        runOnUiThread {
            try {
                container.hostDestroy()
            } catch (e: Exception) {
                AppLog.e("HaikuoRunner", e)
            }
        }
    }

    private fun closeFloatVideo() {
        if (floatVideoUrl.isEmpty()) return
        floatVideoUrl = ""
        closeFloatVideoLayer()
        floatVideoDismissed = true
        stopFloatVideoPolling()
        refreshX5Ui()
    }

    private fun syncFloatVideoPolling() {
        try {
            val state = x5Session
            if (state == null || !haikuoFloatVideoGate(state.floatVideo)) {
                stopFloatVideoPolling()
                if (floatVideoUrl.isNotEmpty()) resetFloatVideoState()
                return
            }
            if (!state.visible) {
                stopFloatVideoPolling()
                return
            }
            if (!haikuoFloatVideoShouldPoll(
                    state.floatVideo,
                    state.visible,
                    floatVideoUrl.isNotEmpty(),
                    floatVideoDismissed,
                    floatVideoFailures
                )
            ) {
                stopFloatVideoPolling()
                return
            }
            if (floatVideoTimer == null) {
                val timer = Timer("haikuo-float-video", true)
                floatVideoTimer = timer
                timer.scheduleAtFixedRate(
                    object : TimerTask() {
                        override fun run() {
                            floatVideoTick(state)
                        }
                    },
                    0L,
                    250L
                )
            }
        } catch (e: Exception) {
            stopFloatVideoPolling()
        }
    }

    private fun floatVideoTick(state: HaikuoX5State) {
        if (x5Session !== state || floatVideoTickBusy) return
        val view = x5WebView ?: return
        if (floatVideoUrl.isNotEmpty() || floatVideoDismissed ||
            !haikuoFloatVideoGate(state.floatVideo)
        ) {
            stopFloatVideoPolling()
            return
        }
        floatVideoTickBusy = true
        val generation = floatVideoGeneration
        try {
            val raw = evalBlocking(view, HAIKUO_RESOURCE_ENTRIES_SCRIPT, 5000)
            if (x5Session !== state || generation != floatVideoGeneration) return
            val picked = haikuoFloatVideoPick(sniffUrlList(raw), state.blockRules)
            if (picked.isEmpty()) {
                floatVideoFailures = 0
                return
            }
            floatVideoFailures = 0
            if (inlineContainer != null || isPlayerOpen()) {
                floatVideoDismissed = true
                stopFloatVideoPolling()
                return
            }
            runOnUiThread {
                try {
                    view.evaluateJavascript(HAIKUO_PAUSE_PAGE_VIDEOS_SCRIPT, null)
                } catch (e: Exception) {
                    AppLog.e("HaikuoRunner", e)
                }
            }
            if (x5Session !== state || generation != floatVideoGeneration) return
            showFloatVideo(picked, state)
            stopFloatVideoPolling()
        } catch (e: Exception) {
            floatVideoFailures++
            if (floatVideoFailures >= HAIKUO_FLOAT_VIDEO_MAX_FAILURES) {
                stopFloatVideoPolling()
            }
        } finally {
            floatVideoTickBusy = false
        }
    }

    private fun showFloatVideo(url: String, state: HaikuoX5State) {
        val headers = HashMap<String, String>()
        if (state.referer.isNotEmpty()) headers["Referer"] = state.referer
        val ruleTitle = session?.program?.name ?: ""
        val request = HaikuoPlayRequest(
            url = url,
            title = ruleTitle,
            lines = listOf(HaikuoPlayLine(url, "线路1", headers))
        )
        runOnUiThread {
            val container = PlayContainer(this).also {
                it.pageHostRef = this
                it.setPreviewMode(true)
            }
            floatVideoContainer = container
            container.cancelCastWait()
            container.endCastPrepare()
            container.clearCastAbort()
            container.setData(haikuoPlaybackSession(request, ruleTitle))
            floatVideoUrl = url
        }
    }

    override fun isPlayerOpen(): Boolean {
        val engine = PlaybackService.peek() ?: return false
        if (engine.isReleased()) return false
        return engine.controller().vod() != null
    }

    override fun onPlay(request: HaikuoPlayRequest, route: HaikuoPlayRoute) {
        val ruleTitle = session?.program?.name ?: ""
        if (route == HaikuoPlayRoute.INLINE) {
            showInline(request, ruleTitle)
            return
        }
        closeInline()
        HaikuoPlayerActivity.start(
            this,
            request,
            ruleTitle,
            route == HaikuoPlayRoute.DELIVER
        )
    }

    override fun onOpenNovel(payload: String, title: String) {
        HaikuoNovelActivity.start(this, payload, title)
    }

    override fun onOpenPictures(urls: List<String>, title: String) {
        HaikuoGalleryActivity.start(this, urls, title)
    }

    private fun showInline(request: HaikuoPlayRequest, ruleTitle: String) {
        runOnUiThread {
            val container = ensureInlineContainer()
            container.cancelCastWait()
            container.endCastPrepare()
            container.clearCastAbort()
            container.setData(haikuoPlaybackSession(request, ruleTitle))
        }
        evaluateAsync("window.__haikuoSetInlinePlaying(true)")
    }

    private fun closeInline() {
        val container = inlineContainer ?: return
        inlineContainer = null
        runOnUiThread {
            try {
                container.hostDestroy()
            } catch (e: Exception) {
                AppLog.e("HaikuoRunner", e)
            }
        }
        evaluateAsync("window.__haikuoSetInlinePlaying(false)")
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
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    override fun requestNotificationPermission() {
        PermissionHelper.requestNotificationIfNeeded(this)
    }

    override fun showEpisodeSheet() {
    }

    override fun onPlaybackLinesExhausted(): Boolean = false
}

private class HaikuoSniffState(
    val request: HaikuoSniffRequest,
    val deadlineMs: Long
) {
    @Volatile
    var done: Boolean = false
    @Volatile
    var pageJsDone: Boolean = false
}

class HaikuoX5State(
    var url: String,
    var desc: String,
    var ua: String,
    var referer: String,
    var js: String,
    var jsLoadingInject: Boolean,
    var blockRules: List<String>,
    var urlInterceptor: String,
    var canBack: Boolean,
    var floatVideo: Boolean,
    var showProgress: Boolean,
    var index: Int,
    var topOffset: Double
) {
    var currentUrl: String = ""
    var visible: Boolean = true
    var progress: Int = 0
    var expectInitialNavigation: Boolean = true
    var hideLoadingRequested: Boolean = false
}
