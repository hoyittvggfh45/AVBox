package com.github.avbox.osc.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import com.github.avbox.osc.R
import com.github.avbox.osc.miniapp.MiniAppSettings
import com.github.avbox.osc.ui.base.BaseActivity
import com.github.avbox.osc.ui.components.SheetHostScaffold
import com.github.avbox.osc.ui.page.MiniAppSettingsScreen
import com.github.avbox.osc.ui.theme.AVBoxTheme
import com.github.avbox.osc.ui.theme.enableTransparentEdgeToEdge

class MiniAppSettingsActivity : BaseActivity() {

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, MiniAppSettingsActivity::class.java))
        }
    }

    override fun shouldRefreshAutoSize(): Boolean = true

    override fun hideSysBar() {
    }

    override fun init() {
        enableTransparentEdgeToEdge()
        val loaded = MiniAppSettings.load(applicationContext)
        findViewById<ComposeView>(R.id.compose_view).setContent {
            AVBoxTheme {
                SheetHostScaffold {
                    MiniAppSettingsScreen(
                        initial = loaded,
                        onNavigateBack = { finish() },
                        onSave = { port, maxBackground, adFilter, hybridComposition, lanShare, marketUrl ->
                            saveSettings(port, maxBackground, adFilter, hybridComposition, lanShare, marketUrl)
                        }
                    )
                }
            }
        }
    }

    private fun saveSettings(
        port: Int?,
        maxBackground: Int,
        adFilter: Boolean,
        hybridComposition: Boolean,
        lanShare: Boolean,
        marketUrl: String
    ) {
        if (port == null || port < 1024 || port > 65535) {
            Toast.makeText(this, getString(R.string.miniapp_settings_port_error), Toast.LENGTH_SHORT).show()
            return
        }
        if (marketUrl.isEmpty()) {
            Toast.makeText(this, getString(R.string.miniapp_settings_url_error), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val settings = MiniAppSettings(
                basePort = port,
                maxBackground = maxBackground,
                adFilter = adFilter,
                hybridComposition = hybridComposition,
                lanShare = lanShare,
                marketUrl = marketUrl
            )
            settings.save(applicationContext)
            Toast.makeText(this, getString(R.string.miniapp_settings_saved), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, getString(R.string.miniapp_settings_save_failed, e.toString()), Toast.LENGTH_SHORT).show()
        }
    }
}
