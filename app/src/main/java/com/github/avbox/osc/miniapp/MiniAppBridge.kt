package com.github.avbox.osc.miniapp

import android.content.SharedPreferences
import com.github.avbox.osc.haikuo.HaikuoJson
import java.io.File
import java.net.URI
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MiniAppApiException(val code: String, message: String) : Exception(message)

interface MiniAppClipboard {
    fun getText(): String?
    fun setText(text: String)
}

interface MiniAppBridgeCallbacks {
    fun evaluateJavascript(js: String)
    fun showToast(message: String)
    fun showLoading(message: String?)
    fun hideLoading()
    fun confirm(title: String, content: String): Boolean
    fun actionSheet(items: List<String>): Int
    fun navigateTo(path: String)
    fun navigateBack()
    fun exit()
    fun openMiniApp(appId: String)
    fun resolveMiniAppUrl(appId: String): String?
    fun log(message: String)
    fun openPlayer(target: MiniAppPlayTarget): Boolean?
    fun playerState(): Map<String, Any?>?
}

class MiniAppPlayRequest(
    val url: String,
    val title: String = "",
    val headers: Map<String, String> = emptyMap(),
    val parse: String = "",
    val jx: String = "",
    val playUrl: String = "",
    val flag: String = "",
    val jxFrom: String = "",
    val key: String = ""
) {
    val needsParse: Boolean
        get() = parse == "1" || jx == "1"

    companion object {
        fun fromParams(params: Map<String, Any?>): MiniAppPlayRequest {
            val headers = linkedMapOf<String, String>()
            val rawHeaders = params["headers"] ?: params["header"]
            if (rawHeaders is Map<*, *>) {
                for ((key, value) in rawHeaders) {
                    if (value != null) headers[key.toString()] = value.toString()
                }
            }
            fun text(value: Any?): String = value?.toString() ?: ""
            return MiniAppPlayRequest(
                url = text(params["url"]),
                title = text(params["title"]),
                headers = headers,
                parse = text(params["parse"]),
                jx = text(params["jx"]),
                playUrl = text(params["playUrl"]),
                flag = text(params["flag"]),
                jxFrom = text(params["jxFrom"]),
                key = text(params["key"])
            )
        }
    }
}

class MiniAppPlayTarget(
    val kind: String,
    val url: String,
    val title: String = "",
    val headers: Map<String, String> = emptyMap()
) {
    val isDirect: Boolean
        get() = kind == "direct"
    val isSniff: Boolean
        get() = kind == "sniff"
}

val MINI_APP_MEDIA_URL_PATTERN = Regex(
    """https?://[^\s"'<>\\]+?\.(?:m3u8|mpd|mp4|m4v|mov|flv|m4s|webm|m4a|aac|mp3|wav|ogg)(?:\?[^\s"'<>\\]*)?""",
    RegexOption.IGNORE_CASE
)

fun miniAppIsMediaUrl(url: String): Boolean = MINI_APP_MEDIA_URL_PATTERN.containsMatchIn(url)

class MiniAppBridge(
    private val manifest: MiniAppManifest,
    private val appDir: File,
    private val prefs: SharedPreferences,
    private val callbacks: MiniAppBridgeCallbacks,
    private val clipboard: MiniAppClipboard,
    private val launchOptions: Map<String, Any?> = emptyMap(),
    private val httpClient: OkHttpClient? = null,
    private val sourceProvider: MiniAppSourceProvider = AvBoxMiniAppSourceProvider(),
    private val devMode: Boolean = false
) {
    private fun storageKey(key: String): String = "moyi_miniapp_${manifest.appId}_$key"

    fun handleChannelMessage(raw: String) {
        val message = try {
            val decoded = HaikuoJson.parse(raw) ?: return
            HaikuoJson.asMap(decoded) ?: return
        } catch (e: Exception) {
            return
        }
        val callId = message["callId"]
        val api = HaikuoJson.str(message["api"])
        val params = HaikuoJson.asMap(message["params"]) ?: emptyMap()
        try {
            val data = invoke(api, params)
            reply(
                linkedMapOf<String, Any?>(
                    "callId" to callId,
                    "ok" to true,
                    "data" to data
                )
            )
        } catch (error: MiniAppApiException) {
            reply(
                linkedMapOf<String, Any?>(
                    "callId" to callId,
                    "ok" to false,
                    "code" to error.code,
                    "message" to error.message,
                    "api" to api
                )
            )
        } catch (error: Exception) {
            reply(
                linkedMapOf<String, Any?>(
                    "callId" to callId,
                    "ok" to false,
                    "code" to "UNKNOWN",
                    "message" to error.toString(),
                    "api" to api
                )
            )
        }
    }

    private fun reply(payload: Map<String, Any?>) {
        try {
            callbacks.evaluateJavascript(
                "window.__moyiResolve && window.__moyiResolve(${HaikuoJson.stringify(payload)})"
            )
        } catch (e: Exception) {
        }
    }

    fun emit(event: String, data: Any? = null) {
        try {
            callbacks.evaluateJavascript(
                "window.__moyiEmit && window.__moyiEmit(${HaikuoJson.stringify(event)}, ${HaikuoJson.stringify(data)})"
            )
        } catch (e: Exception) {
        }
    }

    fun invoke(api: String, params: Map<String, Any?>): Any? {
        val permission = requiredPermission(api)
        if (permission != null && !manifest.hasPermission(permission)) {
            throw MiniAppApiException(
                "PERMISSION_DENIED",
                "未在 manifest 里声明 $permission 权限"
            )
        }
        when (api) {
            "env.getSystemInfo" -> return linkedMapOf<String, Any?>(
                "platform" to "android",
                "sdkVersion" to 4,
                "isTV" to false,
                "devMode" to devMode
            )
            "log" -> {
                val text = HaikuoJson.str(params["message"])
                callbacks.log("[${manifest.appId}] $text")
                return null
            }
            "request" -> return request(params)
            "resolveService" -> return resolveService(HaikuoJson.str(params["url"]))
            "storage.get" -> return prefs.getString(storageKey(HaikuoJson.str(params["key"])), null)
            "storage.set" -> {
                prefs.edit().putString(storageKey(HaikuoJson.str(params["key"])), HaikuoJson.str(params["value"])).apply()
                return null
            }
            "storage.remove" -> {
                prefs.edit().remove(storageKey(HaikuoJson.str(params["key"]))).apply()
                return null
            }
            "storage.clear" -> {
                clearStorage()
                return null
            }
            "storage.keys" -> return storageKeys()
            "file.write" -> {
                fileWrite(params)
                return null
            }
            "file.read" -> return fileRead(params)
            "file.stat" -> return fileStat(params)
            "file.list" -> return fileList(params)
            "file.mkdir" -> {
                fileMkdir(params)
                return null
            }
            "file.rename" -> {
                fileRename(params)
                return null
            }
            "file.delete" -> {
                fileDelete(params)
                return null
            }
            "clipboard.get" -> return clipboard.getText()
            "clipboard.set" -> {
                clipboard.setText(HaikuoJson.str(params["text"]))
                return null
            }
            "ui.toast" -> {
                callbacks.showToast(HaikuoJson.str(params["message"]))
                return null
            }
            "ui.loading" -> {
                callbacks.showLoading(HaikuoJson.str(params["message"]))
                return null
            }
            "ui.hideLoading" -> {
                callbacks.hideLoading()
                return null
            }
            "ui.confirm" -> return callbacks.confirm(
                title = (params["title"] as? String) ?: "确认",
                content = HaikuoJson.str(params["content"])
            )
            "ui.actionSheet" -> {
                val items = mutableListOf<String>()
                val raw = params["items"]
                if (raw is List<*>) {
                    for (item in raw) items.add(item.toString())
                }
                return callbacks.actionSheet(items)
            }
            "navigateTo", "redirectTo" -> {
                callbacks.navigateTo(HaikuoJson.str(params["path"]))
                return null
            }
            "navigateBack" -> {
                callbacks.navigateBack()
                return null
            }
            "exitMiniApp" -> {
                callbacks.exit()
                return null
            }
            "miniApp.open" -> {
                callbacks.openMiniApp(HaikuoJson.str(params["appId"]))
                return null
            }
            "miniApp.getLaunchOptions" -> return launchOptions
            "source.list" -> return sourceCall { sourceProvider.listSites() }
            "source.home" -> return sourceCall {
                sourceProvider.home(sourceArg(params, listOf("siteKey")))
            }
            "source.category" -> return sourceCall {
                sourceProvider.category(
                    siteKey = sourceArg(params, listOf("siteKey")),
                    tid = sourceArg(params, listOf("tid", "typeId", "category")),
                    page = sourcePage(params),
                    ext = sourceExt(params)
                )
            }
            "source.detail" -> return sourceCall {
                sourceProvider.detail(
                    siteKey = sourceArg(params, listOf("siteKey")),
                    id = sourceArg(params, listOf("id", "vodId"))
                )
            }
            "source.play" -> return sourceCall {
                sourceProvider.play(
                    siteKey = sourceArg(params, listOf("siteKey")),
                    flag = sourceArg(params, listOf("flag", "from")),
                    id = sourceArg(params, listOf("id", "playId", "vodId"))
                )
            }
            "source.search" -> return sourceCall {
                sourceProvider.search(
                    siteKey = sourceArg(params, listOf("siteKey")),
                    wd = sourceArg(params, listOf("wd", "query", "keyword")),
                    page = sourcePage(params)
                )
            }
            "player.open" -> return playerOpen(params)
            "player.getState" -> return callbacks.playerState()
                ?: mapOf<String, Any?>("opened" to false)
            "python.call" -> throw MiniAppApiException(
                "NOT_IMPLEMENTED",
                "Python 服务未接：小程序暂不能调用宿主 Python"
            )
            else -> {
                if (api.startsWith("requestStream") ||
                    api.startsWith("requestSSE") ||
                    api == "requestBytes" ||
                    api == "readLines"
                ) {
                    throw MiniAppApiException(
                        "NOT_IMPLEMENTED",
                        "流式/二进制请求未实现：只支持普通文本请求"
                    )
                }
                throw MiniAppApiException("UNKNOWN_API", "未知接口：$api")
            }
        }
    }

    private fun sourceCall(run: () -> Any?): Any? {
        try {
            return run()
        } catch (failure: MiniAppSourceFailure) {
            throw MiniAppApiException(failure.code, failure.message)
        } catch (error: Exception) {
            throw MiniAppApiException("SOURCE_FAILED", "采集源调用失败：$error")
        }
    }

    private fun playerOpen(params: Map<String, Any?>): Map<String, Any?> {
        val request = MiniAppPlayRequest.fromParams(params)
        if (request.url.isEmpty()) {
            throw MiniAppApiException("INVALID_PARAMS", "播放地址为空")
        }
        val target = resolvePlayTarget(request)
        val opened = callbacks.openPlayer(target)
            ?: throw MiniAppApiException(
                "NOT_IMPLEMENTED",
                "播放器桥未接：宿主未提供播放器回调"
            )
        return linkedMapOf<String, Any?>(
            "opened" to opened,
            "kind" to target.kind,
            "url" to target.url
        )
    }

    private fun sourceArg(params: Map<String, Any?>, keys: List<String>): String {
        for (key in keys) {
            val value = params[key]
            if (value != null && value.toString().isNotEmpty()) {
                return value.toString()
            }
        }
        return ""
    }

    private fun sourcePage(params: Map<String, Any?>): Int {
        val raw = params["page"]
        if (raw is Number) return raw.toInt()
        return raw?.toString()?.toIntOrNull() ?: 1
    }

    private fun sourceExt(params: Map<String, Any?>): Map<String, Any?> {
        val raw = params["ext"]
        if (raw is Map<*, *>) {
            val map = linkedMapOf<String, Any?>()
            for ((key, value) in raw) map[key.toString()] = value
            return map
        }
        if (raw is String && raw.trim().startsWith("{")) {
            try {
                val decoded = HaikuoJson.parse(raw)
                val map = if (decoded != null) HaikuoJson.asMap(decoded) else null
                if (map != null) return map
            } catch (e: Exception) {
            }
        }
        return emptyMap()
    }

    private fun request(params: Map<String, Any?>): Map<String, Any?> {
        var url = HaikuoJson.str(params["url"])
        if (url.isEmpty()) {
            throw MiniAppApiException("INVALID_PARAM", "request 缺少 url")
        }
        if (url.startsWith("miniapp://")) {
            val resolved = resolveService(url)
            val target = HaikuoJson.str(resolved["url"])
            if (target.isEmpty()) {
                throw MiniAppApiException("NOT_FOUND", "miniapp:// 目标未安装或未启动")
            }
            url = target
        }
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            throw MiniAppApiException("INVALID_PARAM", "只支持 http/https 请求")
        }
        if (uri.scheme != "http" && uri.scheme != "https") {
            throw MiniAppApiException("INVALID_PARAM", "只支持 http/https 请求")
        }
        val host = uri.host ?: ""
        if (!hostAllowed(host)) {
            throw MiniAppApiException(
                "PERMISSION_DENIED",
                "域名不在 manifest 的 network.allowlist 里：$host"
            )
        }
        val method = HaikuoJson.str(params["method"]).ifEmpty { "GET" }.uppercase()
        val headers = linkedMapOf<String, String>()
        val rawHeaders = params["headers"]
        if (rawHeaders is Map<*, *>) {
            for ((key, value) in rawHeaders) {
                headers[key.toString()] = value.toString()
            }
        }
        val timeoutMs = HaikuoJson.num(params["timeout"])?.toInt() ?: 10000
        val base = httpClient ?: OkHttpClient()
        val client = base.newBuilder()
            .callTimeout(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .build()
        try {
            val bodyText = params["body"]?.toString()
            val builder = Request.Builder().url(url)
            for ((key, value) in headers) builder.addHeader(key, value)
            when (method) {
                "POST" -> builder.post((bodyText ?: "").toRequestBody(null))
                "PUT" -> builder.put((bodyText ?: "").toRequestBody(null))
                "DELETE" -> builder.delete((bodyText ?: "").toRequestBody(null))
                else -> builder.get()
            }
            val response = client.newCall(builder.build()).execute()
            response.use {
                return linkedMapOf<String, Any?>(
                    "statusCode" to it.code,
                    "data" to (it.body?.string() ?: "")
                )
            }
        } catch (error: MiniAppApiException) {
            throw error
        } catch (error: Exception) {
            throw MiniAppApiException("NETWORK_ERROR", error.toString())
        }
    }

    private fun resolveService(url: String): Map<String, Any?> {
        if (!url.startsWith("miniapp://")) {
            return mapOf("url" to url)
        }
        val rest = url.substring("miniapp://".length)
        val slash = rest.indexOf("/")
        val appId = if (slash < 0) rest else rest.substring(0, slash)
        val path = if (slash < 0) "" else rest.substring(slash)
        val base = callbacks.resolveMiniAppUrl(appId)
        if (base == null) return mapOf("url" to "")
        return mapOf("url" to "$base$path")
    }

    private fun hostAllowed(host: String): Boolean {
        val allowlist = manifest.networkAllowlist
        if (allowlist.contains("*")) return true
        val lower = host.lowercase()
        for (pattern in allowlist) {
            val item = pattern.lowercase().trim()
            if (item.isEmpty()) continue
            if (item.startsWith("*.")) {
                val suffix = item.substring(1)
                if (lower.endsWith(suffix)) return true
            } else if (lower == item || lower.endsWith(".$item")) {
                return true
            }
        }
        return false
    }

    private fun clearStorage() {
        val prefix = "moyi_miniapp_${manifest.appId}_"
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (key.startsWith(prefix)) editor.remove(key)
        }
        editor.apply()
    }

    private fun storageKeys(): List<String> {
        val prefix = "moyi_miniapp_${manifest.appId}_"
        return prefs.all.keys
            .filter { it.startsWith(prefix) }
            .map { it.substring(prefix.length) }
    }

    private fun resolveFilePath(relative: String): String? {
        if (relative.isEmpty()) return appDir.absolutePath
        return miniAppSafeJoinPath(appDir.absolutePath, relative)
    }

    private fun fileWrite(params: Map<String, Any?>) {
        val path = resolveFilePath(HaikuoJson.str(params["path"]))
            ?: throw MiniAppApiException("INVALID_PARAM", "文件路径不合法")
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(HaikuoJson.str(params["content"]), Charsets.UTF_8)
    }

    private fun fileRead(params: Map<String, Any?>): String {
        val path = resolveFilePath(HaikuoJson.str(params["path"]))
            ?: throw MiniAppApiException("INVALID_PARAM", "文件路径不合法")
        val file = File(path)
        if (!file.exists()) {
            throw MiniAppApiException("NOT_FOUND", "文件不存在")
        }
        return file.readText(Charsets.UTF_8)
    }

    private fun fileStat(params: Map<String, Any?>): Map<String, Any?> {
        val path = resolveFilePath(HaikuoJson.str(params["path"]))
            ?: throw MiniAppApiException("INVALID_PARAM", "文件路径不合法")
        val file = File(path)
        if (file.exists() && file.isFile) {
            return linkedMapOf<String, Any?>("isDirectory" to false, "size" to file.length())
        }
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            return linkedMapOf<String, Any?>("isDirectory" to true, "size" to 0)
        }
        throw MiniAppApiException("NOT_FOUND", "文件不存在")
    }

    private fun fileList(params: Map<String, Any?>): List<Map<String, Any?>> {
        val path = resolveFilePath(HaikuoJson.str(params["path"]))
            ?: throw MiniAppApiException("INVALID_PARAM", "文件路径不合法")
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            throw MiniAppApiException("NOT_FOUND", "目录不存在")
        }
        val entries = mutableListOf<Map<String, Any?>>()
        val children = dir.listFiles() ?: emptyArray()
        for (entity in children) {
            entries.add(
                linkedMapOf<String, Any?>(
                    "name" to entity.name,
                    "isDirectory" to entity.isDirectory
                )
            )
        }
        return entries
    }

    private fun fileMkdir(params: Map<String, Any?>) {
        val path = resolveFilePath(HaikuoJson.str(params["path"]))
            ?: throw MiniAppApiException("INVALID_PARAM", "文件路径不合法")
        File(path).mkdirs()
    }

    private fun fileRename(params: Map<String, Any?>) {
        val from = resolveFilePath(HaikuoJson.str(params["from"]))
        val to = resolveFilePath(HaikuoJson.str(params["to"]))
        if (from == null || to == null) {
            throw MiniAppApiException("INVALID_PARAM", "文件路径不合法")
        }
        val file = File(from)
        if (file.exists() && file.isFile) {
            file.renameTo(File(to))
            return
        }
        val dir = File(from)
        if (dir.exists() && dir.isDirectory) {
            dir.renameTo(File(to))
            return
        }
        throw MiniAppApiException("NOT_FOUND", "文件不存在")
    }

    private fun fileDelete(params: Map<String, Any?>) {
        val path = resolveFilePath(HaikuoJson.str(params["path"]))
        if (path == null || path == appDir.absolutePath) {
            throw MiniAppApiException("INVALID_PARAM", "文件路径不合法")
        }
        val file = File(path)
        if (file.exists() && file.isFile) {
            file.delete()
            return
        }
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            dir.deleteRecursively()
            return
        }
        throw MiniAppApiException("NOT_FOUND", "文件不存在")
    }

    companion object {
        fun requiredPermission(api: String): String? {
            if (api.startsWith("request") ||
                api == "resolveService" ||
                api.startsWith("requestStream") ||
                api.startsWith("requestSSE")
            ) {
                return "network"
            }
            if (api.startsWith("storage.") || api.startsWith("file.")) {
                return "storage"
            }
            if (api.startsWith("ui.")) return "ui"
            if (api.startsWith("navigate") ||
                api == "exitMiniApp" ||
                api.startsWith("miniApp.")
            ) {
                return "navigate"
            }
            if (api.startsWith("player.")) return "player"
            if (api.startsWith("source.")) return "source"
            if (api.startsWith("python.")) return "python"
            return null
        }

        fun resolvePlayTarget(request: MiniAppPlayRequest): MiniAppPlayTarget {
            if (request.needsParse) {
                val target = if (request.playUrl.isNotEmpty() && request.playUrl.startsWith("http")) {
                    request.playUrl
                } else {
                    request.url
                }
                return MiniAppPlayTarget(
                    kind = "sniff",
                    url = target,
                    title = request.title,
                    headers = request.headers
                )
            }
            if (miniAppIsMediaUrl(request.url)) {
                return MiniAppPlayTarget(
                    kind = "direct",
                    url = request.url,
                    title = request.title,
                    headers = request.headers
                )
            }
            return MiniAppPlayTarget(
                kind = "sniff",
                url = request.url,
                title = request.title,
                headers = request.headers
            )
        }
    }
}
