package com.github.avbox.osc.miniapp

import java.net.URI

val MINI_APP_AD_DOMAINS = setOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "googletagmanager.com",
    "googletagservices.com",
    "adservice.google.com",
    "pagead2.googlesyndication.com",
    "ads.yahoo.com",
    "adnxs.com",
    "adsrvr.org",
    "criteo.com",
    "criteo.net",
    "taboola.com",
    "outbrain.com",
    "pubmatic.com",
    "rubiconproject.com",
    "moatads.com",
    "scorecardresearch.com",
    "advertising.com",
    "amazon-adsystem.com",
    "connect.facebook.net"
)

fun miniAppShouldBlockUrl(url: String): Boolean {
    val uri = try {
        URI(url)
    } catch (e: Exception) {
        return false
    }
    if (uri.scheme != "http" && uri.scheme != "https") return false
    val host = (uri.host ?: "").lowercase()
    if (host.isEmpty()) return false
    for (domain in MINI_APP_AD_DOMAINS) {
        if (host == domain || host.endsWith(".$domain")) return true
    }
    return false
}
