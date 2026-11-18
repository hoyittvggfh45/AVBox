package com.github.avbox.osc.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import com.github.avbox.osc.R
import com.github.avbox.osc.haikuo.HaikuoPlayRequest
import com.github.avbox.osc.player.PageHost
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.page.HaikuoPlayerScreen
import com.github.avbox.osc.ui.player.PlayContainer
import com.github.avbox.osc.ui.player.haikuoPlaybackSession
import com.github.avbox.osc.ui.player.haikuoPlayRequestFromJson
import com.github.avbox.osc.ui.player.haikuoPlayRequestToJson
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge
import com.github.avbox.osc.util.PermissionHelper
import com.github.avbox.osc.util.SpiderStorageNotice

class HaikuoPlayerActivity : BaseActivity(), PageHost {

    companion object {
        const val EXTRA_REQUEST_JSON = "haikuo_request_json"
        const val EXTRA_RULE_TITLE = "haikuo_rule_title"

        fun start(
            context: Context,
            request: HaikuoPlayRequest,
            ruleTitle: String,
            reorder: Boolean
        ) {
            val intent = Intent(context, HaikuoPlayerActivity::class.java)
            intent.putExtra(EXTRA_REQUEST_JSON, haikuoPlayRequestToJson(request))
            intent.putExtra(EXTRA_RULE_TITLE, ruleTitle)
            if (reorder) {
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            }
            context.startActivity(intent)
        }
    }

    private var playContainer: PlayContainer? by mutableStateOf<PlayContainer?>(null)
        private set

    private val localSubtitlePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) playContainer?.onLocalSubtitlePicked(uri)
    }

    override fun launchLocalSubtitlePicker() {
        try {
            SpiderStorageNotice.suppress()
            localSubtitlePicker.launch(arrayOf("*/*"))
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.toast_file_picker_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val container = playContainer
                if (container != null && container.onBackPressed()) return
                finish()
            }
        })
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                val container = playContainer
                if (container != null) {
                    HaikuoPlayerScreen(container)
                }
            }
        }
        playFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        playFromIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        playContainer?.hostResume()
    }

    override fun onPause() {
        playContainer?.hostPause()
        super.onPause()
    }

    override fun onDestroy() {
        playContainer?.hostDestroy()
        playContainer = null
        super.onDestroy()
    }

    private fun playFromIntent(intent: Intent?) {
        val json = intent?.getStringExtra(EXTRA_REQUEST_JSON) ?: return
        val ruleTitle = intent.getStringExtra(EXTRA_RULE_TITLE) ?: ""
        val request = haikuoPlayRequestFromJson(json) ?: return
        play(request, ruleTitle)
    }

    private fun play(request: HaikuoPlayRequest, ruleTitle: String) {
        val container = ensurePlayContainer()
        container.cancelCastWait()
        container.endCastPrepare()
        container.clearCastAbort()
        container.setData(haikuoPlaybackSession(request, ruleTitle))
    }

    private fun ensurePlayContainer(): PlayContainer {
        if (playContainer == null) {
            playContainer = PlayContainer(this).also {
                it.pageHostRef = this
                it.setPreviewMode(true)
            }
        }
        return requireNotNull(playContainer) { "play container not created" }
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
