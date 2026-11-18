package com.github.avbox.osc.ui

import android.content.Context
import android.content.Intent
import androidx.compose.ui.platform.ComposeView
import com.github.avbox.osc.R
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.components.SheetHostScaffold
import com.github.avbox.osc.ui.page.HaikuoGalleryScreen
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge

class HaikuoGalleryActivity : BaseActivity() {

    companion object {
        const val EXTRA_URLS = "haikuo_gallery_urls"
        const val EXTRA_TITLE = "haikuo_gallery_title"

        fun start(context: Context, urls: List<String>, title: String) {
            val intent = Intent(context, HaikuoGalleryActivity::class.java)
            intent.putExtra(EXTRA_URLS, urls.toTypedArray())
            intent.putExtra(EXTRA_TITLE, title)
            context.startActivity(intent)
        }
    }

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        val urls = intent.getStringArrayExtra(EXTRA_URLS)?.toList().orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                SheetHostScaffold {
                    HaikuoGalleryScreen(
                        urls = urls,
                        title = title,
                        onNavigateBack = { finish() }
                    )
                }
            }
        }
    }
}
