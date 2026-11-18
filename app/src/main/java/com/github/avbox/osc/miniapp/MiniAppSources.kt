package com.github.avbox.osc.miniapp

import android.util.Base64
import com.github.avbox.osc.api.ApiConfig
import com.github.avbox.osc.bean.SourceBean
import com.github.avbox.osc.haikuo.HaikuoJson
import com.github.avbox.osc.util.BoundedCall
import com.github.catvod.crawler.Spider
import com.github.catvod.crawler.SpiderReaper
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.net.URLDecoder
import java.util.concurrent.Callable
import java.util.concurrent.Semaphore

class MiniAppSourceFailure(val code: String, message: String) : Exception(message)

interface MiniAppSourceProvider {
    fun listSites(): List<Map<String, Any?>>
    fun home(siteKey: String): Map<String, Any?>
    fun category(siteKey: String, tid: String, page: Int, ext: Map<String, Any?> = emptyMap()): Map<String, Any?>
    fun detail(siteKey: String, id: String): Map<String, Any?>
    fun play(siteKey: String, flag: String, id: String): Map<String, Any?>
    fun search(siteKey: String, wd: String, page: Int): Map<String, Any?>
}

class AvBoxMiniAppSourceProvider(
    private val imageBaseUrl: String? = null
) : MiniAppSourceProvider {

    private val semaphore = Semaphore(MAX_CONCURRENT)

    override fun listSites(): List<Map<String, Any?>> {
        val sites = mutableListOf<Map<String, Any?>>()
        for (bean in ApiConfig.get().getSourceBeanList()) {
            if (bean.isHidden()) continue
            sites.add(
                linkedMapOf<String, Any?>(
                    "key" to (bean.key ?: ""),
                    "name" to (bean.name ?: ""),
                    "type" to if (bean.type == 3) "spider" else "api",
                    "searchable" to (bean.searchable == 1),
                    "available" to (bean.type == 3)
                )
            )
        }
        return sites
    }

    override fun home(siteKey: String): Map<String, Any?> {
        val bean = resolveSite(siteKey)
        return guarded {
            val raw = callSpider(bean, "home") { spider ->
                SpiderReaper.track(spider) { spider.homeContent(true) }
            }
            withProxyImages(withClassesAlias(parseResult(raw)), bean.key ?: "")
        }
    }

    override fun category(siteKey: String, tid: String, page: Int, ext: Map<String, Any?>): Map<String, Any?> {
        val bean = resolveSite(siteKey)
        val safePage = if (page < 1) 1 else page
        return guarded {
            if (tid.isEmpty()) {
                val raw = callSpider(bean, "homeVideo") { spider ->
                    SpiderReaper.track(spider) { spider.homeVideoContent() }
                }
                val parsed = parseResult(raw)
                val list = parsed["list"]
                if (list is List<*> && list.isNotEmpty()) {
                    return@guarded withProxyImages(withClassesAlias(parsed), bean.key ?: "")
                }
                val home = callSpider(bean, "home") { spider ->
                    SpiderReaper.track(spider) { spider.homeContent(true) }
                }
                return@guarded withProxyImages(withClassesAlias(parseResult(home)), bean.key ?: "")
            }
            val extend = HashMap<String, String>()
            for ((key, value) in ext) {
                if (value != null) extend[key] = value.toString()
            }
            val raw = callSpider(bean, "category") { spider ->
                SpiderReaper.track(spider) { spider.categoryContent(tid, safePage.toString(), true, extend) }
            }
            withProxyImages(withClassesAlias(parseResult(raw)), bean.key ?: "")
        }
    }

    override fun detail(siteKey: String, id: String): Map<String, Any?> {
        if (id.isEmpty()) {
            throw MiniAppSourceFailure("INVALID_PARAMS", "source.detail 缺少 id")
        }
        val bean = resolveSite(siteKey)
        return guarded {
            val raw = callSpider(bean, "detail") { spider ->
                SpiderReaper.track(spider) { spider.detailContent(listOf(id)) }
            }
            withProxyImages(normalizeDetailResult(parseResult(raw)), bean.key ?: "")
        }
    }

    override fun play(siteKey: String, flag: String, id: String): Map<String, Any?> {
        if (id.isEmpty()) {
            throw MiniAppSourceFailure("INVALID_PARAMS", "source.play 缺少 id")
        }
        val bean = resolveSite(siteKey)
        return guarded {
            val raw = callSpider(bean, "play") { spider ->
                SpiderReaper.track(spider) { spider.playerContent(flag, id, ApiConfig.get().vipParseFlags) }
            }
            normalizePlayResult(parseResult(raw))
        }
    }

    override fun search(siteKey: String, wd: String, page: Int): Map<String, Any?> {
        if (wd.isEmpty()) {
            throw MiniAppSourceFailure("INVALID_PARAMS", "source.search 缺少 wd")
        }
        val bean = resolveSite(siteKey)
        val safePage = if (page < 1) 1 else page
        return guarded {
            val raw = callSpider(bean, "search") { spider ->
                SpiderReaper.track(spider) { spider.searchContent(wd, false, safePage.toString()) }
            }
            withProxyImages(parseResult(raw), bean.key ?: "")
        }
    }

    fun proxyFetch(source: String, param: Any?): Map<String, Any?> {
        val bean = resolveSite(source)
        val params = HashMap<String, String>()
        params["do"] = "proxy"
        params["siteKey"] = bean.key ?: source
        if (param is Map<*, *>) {
            for ((key, value) in param) {
                if (value != null) params[key.toString()] = value.toString()
            }
        } else if (param is String && param.isNotEmpty()) {
            params["url"] = param
        }
        val result = ApiConfig.get().proxyLocal(params)
            ?: throw MiniAppSourceFailure("SOURCE_FAILED", "图片代理没有返回结果")
        if (result.isEmpty() || result[0] is NanoHTTPD.Response) {
            throw MiniAppSourceFailure("SOURCE_FAILED", "图片代理返回了不能转发的响应")
        }
        if (result.size < 3) {
            throw MiniAppSourceFailure("SOURCE_FAILED", "图片代理返回结果不完整")
        }
        val code = result[0] as? Int ?: 200
        val mime = result[1] as? String ?: "application/octet-stream"
        val stream = result[2] as? InputStream
        val bytes = stream?.readBytes() ?: ByteArray(0)
        val headers = linkedMapOf<String, Any?>()
        if (result.size >= 4 && result[3] is Map<*, *>) {
            val rawHeaders = result[3] as Map<*, *>
            for ((key, value) in rawHeaders) {
                headers[key.toString()] = value.toString()
            }
        }
        return linkedMapOf<String, Any?>(
            "status" to code,
            "mime" to mime,
            "bodyBase64" to Base64.encodeToString(bytes, Base64.DEFAULT),
            "headers" to headers
        )
    }

    private fun resolveSite(siteKey: String): SourceBean {
        val key = siteKey.trim()
        if (key.isEmpty()) {
            throw MiniAppSourceFailure("INVALID_PARAMS", "缺少 siteKey")
        }
        for (bean in ApiConfig.get().getSourceBeanList()) {
            if (bean.key == key) {
                if (bean.type != 3) {
                    throw MiniAppSourceFailure("SITE_UNAVAILABLE", "该站不是蜘蛛源，小程序暂不支持：$key")
                }
                return bean
            }
        }
        throw MiniAppSourceFailure("SITE_NOT_FOUND", "未找到站点：$key")
    }

    private fun <T> guarded(run: () -> T): T {
        if (!semaphore.tryAcquire()) {
            throw MiniAppSourceFailure(
                "TOO_MANY_REQUESTS",
                "采集源并发调用已满（最多 3 个），请稍后重试"
            )
        }
        try {
            return run()
        } catch (failure: MiniAppSourceFailure) {
            throw failure
        } catch (error: Exception) {
            throw MiniAppSourceFailure("SOURCE_FAILED", "采集源调用失败：$error")
        } finally {
            semaphore.release()
        }
    }

    private fun callSpider(bean: SourceBean, tag: String, call: (Spider) -> String?): String {
        val json = BoundedCall.call(
            Callable<String> {
                val spider = ApiConfig.get().getCSP(bean)
                try {
                    call(spider) ?: ""
                } catch (e: Exception) {
                    ""
                }
            },
            CALL_TIMEOUT_MS,
            "miniapp-$tag-" + bean.key
        )
        if (json == null) {
            throw MiniAppSourceFailure("TIMEOUT", "采集源调用超时（60 秒）")
        }
        return json
    }

    private fun parseResult(raw: String): Map<String, Any?> {
        if (raw.isEmpty()) return emptyMap()
        val decoded = HaikuoJson.parse(raw)
            ?: throw MiniAppSourceFailure("SOURCE_FAILED", "采集源返回的不是合法 JSON")
        return HaikuoJson.asMap(decoded)
            ?: throw MiniAppSourceFailure("SOURCE_FAILED", "采集源返回的不是 JSON 对象")
    }

    private fun withProxyImages(raw: Map<String, Any?>, source: String): Map<String, Any?> {
        val base = imageBaseUrl
        if (base.isNullOrEmpty()) return raw
        return rewriteProxyImages(raw, baseUrl = base, source = source)
    }

    companion object {
        const val MAX_CONCURRENT = 3
        const val CALL_TIMEOUT_MS = 60_000L

        fun normalizeDetailResult(raw: Map<String, Any?>): Map<String, Any?> {
            val rows = raw["list"]
            var vod: MutableMap<String, Any?>? = null
            if (rows is List<*> && rows.isNotEmpty() && rows.first() is Map<*, *>) {
                val first = rows.first() as Map<*, *>
                vod = linkedMapOf()
                for ((key, value) in first) vod[key.toString()] = value
            } else if (raw.containsKey("vod_id") || raw.containsKey("vod_name") || raw.containsKey("vod_play_url")) {
                vod = LinkedHashMap(raw)
                vod.remove("list")
            }
            if (vod == null) return emptyMap()
            vod["vod_play_from"] = splitPlayField(vod["vod_play_from"])
            vod["vod_play_url"] = splitPlayField(vod["vod_play_url"])
            return vod
        }

        fun splitPlayField(value: Any?): List<String> {
            if (value is List<*>) {
                return value.map { it.toString() }
            }
            val text = value?.toString() ?: ""
            if (text.isEmpty()) return emptyList()
            return text.split("$$$")
        }

        fun rewriteProxyImages(raw: Map<String, Any?>, baseUrl: String, source: String): Map<String, Any?> {
            val copy = LinkedHashMap(raw)
            rewriteVodPic(copy, baseUrl, source)
            val rows = copy["list"]
            if (rows is List<*>) {
                copy["list"] = rows.map { row ->
                    if (row is Map<*, *>) {
                        val rowCopy = linkedMapOf<String, Any?>()
                        for ((key, value) in row) rowCopy[key.toString()] = value
                        rewriteVodPic(rowCopy, baseUrl, source)
                    } else {
                        row
                    }
                }
            }
            return copy
        }

        fun rewriteVodPic(vod: MutableMap<String, Any?>, baseUrl: String, source: String): MutableMap<String, Any?> {
            val pic = vod["vod_pic"]
            if (pic is String && pic.startsWith("proxy://")) {
                vod["vod_pic"] = proxyImageUrl(baseUrl, source, pic)
            }
            return vod
        }

        fun proxyImageUrl(baseUrl: String, source: String, proxyUrl: String): String {
            val base = if (baseUrl.endsWith("/")) baseUrl.substring(0, baseUrl.length - 1) else baseUrl
            val rest = proxyUrl.substring("proxy://".length)
            val payload = Base64.encodeToString(
                HaikuoJson.stringify(
                    linkedMapOf<String, Any?>("source" to source, "param" to proxyParam(rest))
                ).toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP
            )
            return "$base/pyproxy?d=$payload"
        }

        fun proxyParam(rest: String): Any? {
            if (rest.contains("=")) {
                val map = linkedMapOf<String, String>()
                for (part in rest.split("&")) {
                    val eq = part.indexOf("=")
                    if (eq > 0) {
                        map[URLDecoder.decode(part.substring(0, eq), "UTF-8")] =
                            URLDecoder.decode(part.substring(eq + 1), "UTF-8")
                    }
                }
                if (map.isNotEmpty()) return map
            }
            return mapOf("url" to rest)
        }

        fun withClassesAlias(raw: Map<String, Any?>): Map<String, Any?> {
            val copy = LinkedHashMap(raw)
            if (!copy.containsKey("classes") && copy["class"] is List<*>) {
                copy["classes"] = copy["class"]
            }
            return copy
        }

        fun normalizePlayResult(raw: Map<String, Any?>): Map<String, Any?> {
            var url = HaikuoJson.str(raw["url"])
            if (url.isEmpty()) url = HaikuoJson.str(raw["playUrl"])
            if (url.isEmpty()) {
                throw MiniAppSourceFailure("SOURCE_FAILED", "采集源取流未返回播放地址")
            }
            val result = linkedMapOf<String, Any?>(
                "url" to url,
                "header" to normalizeHeaders(raw["header"])
            )
            if (raw.containsKey("parse")) {
                result["parse"] = parseFlagString(raw["parse"])
            }
            if (raw.containsKey("jx")) {
                result["jx"] = parseFlagString(raw["jx"])
            }
            for (key in listOf("playUrl", "jxFrom", "key", "flag")) {
                val value = raw[key]
                if (value != null && value.toString().isNotEmpty()) {
                    result[key] = value.toString()
                }
            }
            return result
        }

        fun normalizeHeaders(raw: Any?): Map<String, String> {
            val headers = linkedMapOf<String, String>()
            if (raw is Map<*, *>) {
                for ((key, value) in raw) {
                    if (value != null) headers[key.toString()] = value.toString()
                }
            } else if (raw is String) {
                val trimmed = raw.trim()
                if (trimmed.startsWith("{")) {
                    try {
                        val decoded = HaikuoJson.parse(trimmed)
                        val map = if (decoded != null) HaikuoJson.asMap(decoded) else null
                        if (map != null) {
                            for ((key, value) in map) {
                                if (value != null) headers[key.toString()] = value.toString()
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
            } else if (raw is List<*>) {
                for (row in raw) {
                    if (row is List<*> && row.size >= 2) {
                        headers[row[0].toString()] = row[1].toString()
                    } else if (row is Map<*, *>) {
                        val key = row["key"] ?: row["name"]
                        val value = row["value"]
                        if (key != null && value != null) {
                            headers[key.toString()] = value.toString()
                        }
                    }
                }
            }
            return headers
        }

        fun parseFlagString(value: Any?): String {
            if (value == true) return "1"
            if (value is Number) return if (value.toInt() == 1) "1" else "0"
            val text = value?.toString()?.trim()?.lowercase() ?: ""
            return if (text == "1" || text == "true") "1" else "0"
        }
    }
}
