package com.github.avbox.osc.miniapp

import android.content.Context

class MiniAppSettings(
    val basePort: Int = 7810,
    val maxBackground: Int = 3,
    val adFilter: Boolean = true,
    val hybridComposition: Boolean = true,
    val lanShare: Boolean = false,
    val marketUrl: String = DEFAULT_MARKET_URL
) {
    fun copyWith(
        basePort: Int? = null,
        maxBackground: Int? = null,
        adFilter: Boolean? = null,
        hybridComposition: Boolean? = null,
        lanShare: Boolean? = null,
        marketUrl: String? = null
    ): MiniAppSettings {
        return MiniAppSettings(
            basePort = if (basePort == null) this.basePort else clampPort(basePort),
            maxBackground = if (maxBackground == null) this.maxBackground else clampMaxBackground(maxBackground),
            adFilter = adFilter ?: this.adFilter,
            hybridComposition = hybridComposition ?: this.hybridComposition,
            lanShare = lanShare ?: this.lanShare,
            marketUrl = marketUrl ?: this.marketUrl
        )
    }

    fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_BASE_PORT, basePort)
            .putInt(KEY_MAX_BACKGROUND, maxBackground)
            .putBoolean(KEY_AD_FILTER, adFilter)
            .putBoolean(KEY_HYBRID_COMPOSITION, hybridComposition)
            .putBoolean(KEY_LAN_SHARE, lanShare)
            .putString(KEY_MARKET_URL, marketUrl)
            .apply()
    }

    companion object {
        const val DEFAULT_MARKET_URL =
            "https://ghfast.top/https://raw.githubusercontent.com/ant-video/ant-video/refs/heads/main/mini-app/market/market.json"
        const val PREFS_NAME = "miniapp_settings"
        const val KEY_BASE_PORT = "moyi_miniapp_base_port"
        const val KEY_MAX_BACKGROUND = "moyi_miniapp_max_background"
        const val KEY_AD_FILTER = "moyi_miniapp_ad_filter"
        const val KEY_HYBRID_COMPOSITION = "moyi_miniapp_hybrid_composition"
        const val KEY_LAN_SHARE = "moyi_miniapp_lan_share"
        const val KEY_MARKET_URL = "moyi_miniapp_market_url"

        fun clampPort(value: Int): Int = value.coerceIn(1024, 65535)

        fun clampMaxBackground(value: Int): Int = value.coerceIn(1, 10)

        fun load(context: Context): MiniAppSettings {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val rawUrl = prefs.getString(KEY_MARKET_URL, null)
            return MiniAppSettings(
                basePort = clampPort(prefs.getInt(KEY_BASE_PORT, 7810)),
                maxBackground = clampMaxBackground(prefs.getInt(KEY_MAX_BACKGROUND, 3)),
                adFilter = prefs.getBoolean(KEY_AD_FILTER, true),
                hybridComposition = prefs.getBoolean(KEY_HYBRID_COMPOSITION, true),
                lanShare = prefs.getBoolean(KEY_LAN_SHARE, false),
                marketUrl = if (rawUrl.isNullOrEmpty()) DEFAULT_MARKET_URL else rawUrl
            )
        }
    }
}
