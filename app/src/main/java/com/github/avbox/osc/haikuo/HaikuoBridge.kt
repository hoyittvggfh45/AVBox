package com.github.avbox.osc.haikuo

import android.webkit.JavascriptInterface

class HaikuoPlayLine(
    val url: String,
    val name: String,
    val headers: Map<String, String> = emptyMap()
)

class HaikuoPlayRequest(
    val url: String,
    val title: String,
    val series: String = "",
    val subtitle: String = "",
    val lyric: String = "",
    val audioUrls: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val episodes: List<Map<String, Any?>> = emptyList(),
    val lines: List<HaikuoPlayLine> = emptyList(),
    val danmaku: String = "",
    val index: Int = 0,
    val detail: Boolean = false
)

enum class HaikuoPlayRoute {
    DELIVER,
    INLINE,
    PUSH
}

class HaikuoSniffRequest(
    val id: String,
    val url: String,
    val js: String,
    val mode: String,
    val pageJs: String,
    val cacheM3u8: Boolean,
    val videoRules: List<String>,
    val videoExcludeRules: List<String>,
    val blockRules: List<String>,
    val ua: String,
    val referer: String
)

interface HaikuoBridgeHandler {
    fun isPlayerOpen(): Boolean {
        return false
    }

    fun onToast(text: String) {
    }

    fun onCopy(text: String) {
    }

    fun onOpen(url: String, title: String) {
    }

    fun onOpenSearch(keyword: String, rule: String, group: String, auto: Boolean) {
    }

    fun onOpenRule(title: String) {
    }

    fun onImportCode(code: String) {
    }

    fun onPickFile(code: String) {
    }

    fun onDownload(url: String, title: String) {
    }

    fun onDownloadFile(url: String, fileName: String) {
    }

    fun onSaveImage(urls: List<String>) {
    }

    fun onShare(text: String, url: String) {
    }

    fun onSetPageImmersive(immersive: Boolean) {
    }

    fun onPop() {
    }

    fun onShellFavorite() {
    }

    fun onShellSearch() {
    }

    fun onShellReload() {
    }

    fun onSniff(request: HaikuoSniffRequest) {
    }

    fun onX5Row(payload: Map<String, Any?>) {
    }

    fun onX5RowClear() {
    }

    fun onX5RowRefresh(payload: Map<String, Any?>) {
    }

    fun onX5RowBack() {
    }

    fun onX5FetchResult(payload: Map<String, Any?>) {
    }

    fun onPlay(request: HaikuoPlayRequest, route: HaikuoPlayRoute) {
    }

    fun onOpenNovel(payload: String, title: String) {
    }

    fun onOpenPictures(urls: List<String>, title: String) {
    }
}

class HaikuoBridge(
    private val session: HaikuoSession,
    private val handler: HaikuoBridgeHandler
) {

    @JavascriptInterface
    fun postMessage(json: String) {
        val decoded = HaikuoJson.parse(json)
        if (decoded !is Map<*, *>) return
        val payload = LinkedHashMap<String, Any?>()
        for ((key, value) in decoded) {
            payload[key.toString()] = value
        }
        dispatch(payload)
    }

    fun dispatch(payload: Map<String, Any?>) {
        val type = HaikuoJson.str(payload["type"])
        when (type) {
            "toast" -> handler.onToast(HaikuoJson.str(payload["text"]))
            "copy" -> handler.onCopy(HaikuoJson.str(payload["text"]))
            "saveFile" -> {
                val name = HaikuoJson.str(payload["name"])
                    .ifEmpty { HaikuoJson.str(payload["fileName"]) }
                val content = HaikuoJson.str(payload["content"])
                if (name.isNotEmpty()) {
                    session.storeSavedFile(name, content.toByteArray(Charsets.UTF_8))
                }
            }
            "deleteFile" -> {
                val name = HaikuoJson.str(payload["name"])
                    .ifEmpty { HaikuoJson.str(payload["fileName"]) }
                if (name.isNotEmpty()) session.deleteSavedFile(name)
            }
            "download" -> handler.onDownload(
                HaikuoJson.str(payload["url"]),
                HaikuoJson.str(payload["title"])
            )
            "downloadFile" -> handler.onDownloadFile(
                HaikuoJson.str(payload["url"]),
                HaikuoJson.str(payload["fileName"])
                    .ifEmpty { HaikuoJson.str(payload["name"]) }
            )
            "saveImage" -> {
                val urls = ArrayList<String>()
                val raw = payload["urls"]
                if (raw is List<*>) {
                    for (item in raw) {
                        if (item is String && item.isNotEmpty()) urls.add(item)
                    }
                } else {
                    val single = HaikuoJson.str(payload["url"])
                    if (single.isNotEmpty()) urls.add(single)
                }
                handler.onSaveImage(urls)
            }
            "openSearch" -> handler.onOpenSearch(
                HaikuoJson.str(payload["keyword"]),
                HaikuoJson.str(payload["rule"]),
                HaikuoJson.str(payload["group"]),
                payload["auto"] == true
            )
            "openRule" -> handler.onOpenRule(
                HaikuoJson.str(payload["title"])
                    .ifEmpty { HaikuoJson.str(payload["rule"]) }
                    .ifEmpty { HaikuoJson.str(payload["name"]) }
            )
            "importCode" -> handler.onImportCode(
                HaikuoJson.str(payload["code"])
                    .ifEmpty { HaikuoJson.str(payload["text"]) }
            )
            "pickFile" -> handler.onPickFile(HaikuoJson.str(payload["code"]))
            "sniff" -> handler.onSniff(parseSniffRequest(payload))
            "x5row" -> handler.onX5Row(payload)
            "x5rowClear" -> handler.onX5RowClear()
            "x5rowRefresh" -> handler.onX5RowRefresh(payload)
            "x5rowBack" -> handler.onX5RowBack()
            "x5fetchResult" -> handler.onX5FetchResult(payload)
            "setPageImmersive" -> handler.onSetPageImmersive(
                payload["immersive"] == true || payload["value"] == true
            )
            "share" -> handler.onShare(
                HaikuoJson.str(payload["text"]),
                HaikuoJson.str(payload["url"])
            )
            "pop" -> handler.onPop()
            "shellFavorite" -> handler.onShellFavorite()
            "shellSearch" -> handler.onShellSearch()
            "shellReload" -> handler.onShellReload()
            "play" -> handlePlay(payload)
            "open" -> handler.onOpen(
                HaikuoJson.str(payload["url"]),
                HaikuoJson.str(payload["title"])
            )
            else -> {
            }
        }
    }

    private fun handlePlay(payload: Map<String, Any?>) {
        val url = HaikuoJson.str(payload["url"])
        val title = HaikuoJson.str(payload["title"])
        if (url.startsWith("novel://")) {
            handler.onOpenNovel(url.substring("novel://".length), title)
            return
        }
        if (url.startsWith("pics://")) {
            val rest = url.substring("pics://".length)
            val urls = rest.split("&&").map { it.trim() }.filter { it.isNotEmpty() }
            handler.onOpenPictures(urls, title)
            return
        }
        val request = parsePlayRequest(payload)
        val route = resolvePlayRoute(request.detail)
        handler.onPlay(request, route)
    }

    fun resolvePlayRoute(detail: Boolean): HaikuoPlayRoute {
        if (handler.isPlayerOpen()) return HaikuoPlayRoute.DELIVER
        if (detail) return HaikuoPlayRoute.INLINE
        return HaikuoPlayRoute.PUSH
    }

    companion object {

        fun parsePlayRequest(payload: Map<String, Any?>): HaikuoPlayRequest {
            val headers = stringMap(payload["headers"])
            val episodes = ArrayList<Map<String, Any?>>()
            val rawEpisodes = payload["episodes"]
            if (rawEpisodes is List<*>) {
                for (item in rawEpisodes) {
                    if (item is Map<*, *>) {
                        val episode = LinkedHashMap<String, Any?>()
                        for ((key, value) in item) {
                            episode[key.toString()] = value
                        }
                        episodes.add(episode)
                    } else if (item is String && item.isNotEmpty()) {
                        val episode = LinkedHashMap<String, Any?>()
                        episode["name"] = item
                        episodes.add(episode)
                    }
                }
            }
            val audioUrls = ArrayList<String>()
            val rawAudio = payload["audioUrls"]
            if (rawAudio is List<*>) {
                for (item in rawAudio) {
                    if (item is String && item.isNotEmpty()) audioUrls.add(item)
                }
            }
            return HaikuoPlayRequest(
                url = HaikuoJson.str(payload["url"]),
                title = HaikuoJson.str(payload["title"]),
                series = HaikuoJson.str(payload["series"]),
                subtitle = HaikuoJson.str(payload["subtitle"]),
                lyric = HaikuoJson.str(payload["lyric"]),
                audioUrls = audioUrls,
                headers = headers,
                episodes = episodes,
                lines = parseHaikuoPlayLines(payload),
                danmaku = HaikuoJson.str(payload["danmaku"]),
                index = HaikuoJson.num(payload["index"])?.toInt() ?: 0,
                detail = payload["detail"] == true
            )
        }

        fun parseHaikuoPlayLines(payload: Map<String, Any?>): List<HaikuoPlayLine> {
            val lines = ArrayList<HaikuoPlayLine>()
            val raw = payload["lines"]
            if (raw is List<*>) {
                var position = 0
                for (item in raw) {
                    position++
                    if (item is Map<*, *>) {
                        val lineMap = LinkedHashMap<String, Any?>()
                        for ((key, value) in item) {
                            lineMap[key.toString()] = value
                        }
                        val url = HaikuoJson.str(lineMap["url"])
                        if (url.isEmpty()) continue
                        val name = HaikuoJson.str(lineMap["name"])
                            .ifEmpty { "线路$position" }
                        lines.add(
                            HaikuoPlayLine(url, name, stringMap(lineMap["headers"]))
                        )
                    } else if (item is String && item.isNotEmpty()) {
                        lines.add(HaikuoPlayLine(item, "线路$position"))
                    }
                }
            }
            if (lines.isEmpty()) {
                val url = HaikuoJson.str(payload["url"])
                if (url.isNotEmpty()) {
                    lines.add(
                        HaikuoPlayLine(url, "线路1", stringMap(payload["headers"]))
                    )
                }
            }
            return lines
        }

        fun parseSniffRequest(payload: Map<String, Any?>): HaikuoSniffRequest {
            return HaikuoSniffRequest(
                id = HaikuoJson.str(payload["id"]),
                url = HaikuoJson.str(payload["url"]),
                js = HaikuoJson.str(payload["js"]),
                mode = HaikuoJson.str(payload["mode"]).ifEmpty { "video" },
                pageJs = HaikuoJson.str(payload["pageJs"]),
                cacheM3u8 = payload["cacheM3u8"] == true,
                videoRules = stringList(payload["videoRules"]),
                videoExcludeRules = stringList(payload["videoExcludeRules"]),
                blockRules = stringList(payload["blockRules"]),
                ua = HaikuoJson.str(payload["ua"]),
                referer = HaikuoJson.str(payload["referer"])
            )
        }

        private fun stringMap(value: Any?): Map<String, String> {
            val out = LinkedHashMap<String, String>()
            if (value is Map<*, *>) {
                for ((key, value2) in value) {
                    if (value2 != null) out[key.toString()] = value2.toString()
                }
            }
            return out
        }

        private fun stringList(value: Any?): List<String> {
            val out = ArrayList<String>()
            if (value is List<*>) {
                for (item in value) {
                    if (item is String && item.isNotEmpty()) out.add(item)
                }
            }
            return out
        }
    }
}
