package com.github.avbox.osc.miniapp

import android.content.Context

class MiniAppBuiltinFailure(val appId: String, val reason: String)

class MiniAppBuiltinResult(
    val installed: List<String>,
    val skipped: List<String>,
    val failed: List<MiniAppBuiltinFailure>,
    val unavailable: List<String> = emptyList()
) {
    val hasFailures: Boolean
        get() = failed.isNotEmpty()
}

val REMOVED_BUILTIN_APP_IDS = setOf(
    "com.logvar.danmu",
    "com.logvar.danmu.node",
    "com.leospring.demo",
    "com.leospring.launch_target",
    "com.leospring.launcher",
    "com.leospring.web_links",
    "com.leospring.online_site",
    "com.leospring.cctvvideo",
    "com.leospring.tetris",
    "com.leospring.node_demo",
    "com.leospring.migu_video.node",
    "com.example.python.demo",
    "com.leospring.sudugu_reader"
)

fun filterRemovedMarketItems(items: List<MiniAppMarketItem>): List<MiniAppMarketItem> {
    return items.filter { !REMOVED_BUILTIN_APP_IDS.contains(it.appId) }
}

object MiniAppBuiltin {
    const val MARKET_ASSET = "miniapps/builtin/market.json"
    const val SEEDED_KEY = "moyi_miniapp_builtin_seeded_v1"
    const val SEEDED_APP_IDS_KEY = "moyi_miniapp_builtin_seeded_ids_v1"
    const val PREFS_NAME = "miniapp_builtin"

    fun ensureSeeded(store: MiniAppStore, appContext: Context): MiniAppBuiltinResult {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val items: List<MiniAppMarketItem>
        try {
            val raw = appContext.assets.open(MARKET_ASSET).use {
                it.readBytes().toString(Charsets.UTF_8)
            }
            items = parseMiniAppMarketJson(raw)
        } catch (e: Exception) {
            return MiniAppBuiltinResult(emptyList(), emptyList(), emptyList())
        }
        val seeded = prefs.getStringSet(SEEDED_APP_IDS_KEY, emptySet())?.toSet() ?: emptySet()
        val installed = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<MiniAppBuiltinFailure>()
        val unavailable = mutableListOf<String>()
        for (item in items) {
            if (seeded.contains(item.appId)) {
                skipped.add(item.appId)
                continue
            }
            val existing = store.installedDir(item.appId)
            if (existing != null) {
                skipped.add(item.appId)
                continue
            }
            val bytes: ByteArray
            try {
                val assetPath = if (item.url.endsWith(".json")) {
                    "miniapps/builtin/${item.appId}.manifest.json"
                } else {
                    "miniapps/builtin/${item.appId}.zip"
                }
                bytes = appContext.assets.open(assetPath).use { it.readBytes() }
            } catch (e: Exception) {
                unavailable.add(item.appId)
                continue
            }
            try {
                if (item.url.endsWith(".json")) {
                    store.installFromManifestJson(bytes.toString(Charsets.UTF_8))
                } else {
                    store.installFromZipBytes(bytes, expectedMd5 = item.md5)
                }
                installed.add(item.appId)
            } catch (error: Exception) {
                failed.add(MiniAppBuiltinFailure(item.appId, error.toString()))
            }
        }
        if (items.isNotEmpty() && failed.isEmpty()) {
            val nextSeeded = HashSet<String>()
            nextSeeded.addAll(seeded)
            nextSeeded.addAll(installed)
            nextSeeded.addAll(skipped)
            prefs.edit()
                .putBoolean(SEEDED_KEY, true)
                .putStringSet(SEEDED_APP_IDS_KEY, nextSeeded)
                .apply()
        }
        return MiniAppBuiltinResult(installed, skipped, failed, unavailable)
    }
}
