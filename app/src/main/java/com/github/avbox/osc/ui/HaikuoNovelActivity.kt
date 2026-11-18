package com.github.avbox.osc.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.ComposeView
import com.github.avbox.osc.R
import com.github.avbox.osc.haikuo.HaikuoJson
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.components.SheetHostScaffold
import com.github.avbox.osc.ui.page.HaikuoNovelScreen
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge

class HaikuoNovelActivity : BaseActivity() {

    companion object {
        const val EXTRA_PAYLOAD = "haikuo_novel_payload"
        const val EXTRA_TITLE = "haikuo_novel_title"

        fun start(context: Context, payload: String, title: String) {
            val intent = Intent(context, HaikuoNovelActivity::class.java)
            intent.putExtra(EXTRA_PAYLOAD, payload)
            intent.putExtra(EXTRA_TITLE, title)
            context.startActivity(intent)
        }
    }

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        val payload = intent.getStringExtra(EXTRA_PAYLOAD) ?: ""
        val fallbackTitle = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val parsed = parseNovelPayload(payload, fallbackTitle)
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                SheetHostScaffold {
                    HaikuoNovelScreen(
                        title = parsed.first,
                        content = parsed.second,
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }

    private fun parseNovelPayload(payload: String, fallbackTitle: String): Pair<String, String> {
        val body = payload.removePrefix("novel://").trim()
        if (body.startsWith("{")) {
            val decoded = HaikuoJson.parse(body)
            if (decoded is Map<*, *>) {
                val title = HaikuoJson.str(decoded["title"]).ifEmpty { fallbackTitle }
                val content = HaikuoJson.str(decoded["content"])
                    .ifEmpty { HaikuoJson.str(decoded["text"]) }
                    .ifEmpty { HaikuoJson.str(decoded["body"]) }
                if (content.isNotEmpty()) return title to content
            }
        }
        return fallbackTitle to body
    }
}
