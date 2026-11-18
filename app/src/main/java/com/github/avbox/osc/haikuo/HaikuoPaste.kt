package com.github.avbox.osc.haikuo

import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HaikuoCloud6Upload(val path: String, val password: String)

fun pasteNameIsCloud6(paste: String?): Boolean {
    val name = (paste ?: "").replace(Regex("\\s+"), "")
    if (name.isEmpty()) return true
    if (name == "6" || name == "云剪贴板6") return true
    return name.contains("云6")
}

fun buildCloud6ShareText(path: String, password: String, title: String): String {
    val code = if (password.isEmpty()) path else "$path@$password"
    return "云6oooole/xxxxxx/$code\n小程序：$title"
}

fun pasteFetchUrl(input: String): String {
    val text = input.trim()
    if (text.contains("pasteme.tyrantg.com/")) {
        var tail = text.split("pasteme.tyrantg.com/").last()
        tail = tail.split(Regex("[?#\\s]")).first()
        val code = tail.split("/").lastOrNull { it.isNotEmpty() } ?: ""
        if (code.isEmpty()) {
            throw HaikuoFormatException("无法识别的云剪贴板地址，请确认是云剪贴板 6 的分享文本")
        }
        return "https://pasteme.tyrantg.com/api/getContent/$code"
    }
    val url = HaikuoCodec.cloudApiUrl(text)
    if (url == null) {
        throw HaikuoFormatException("无法识别的云剪贴板地址，请确认是云剪贴板 6 的分享文本")
    }
    return url
}

fun fetchPasteText(url: String, client: OkHttpClient? = null): String {
    val httpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    try {
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
            )
            .header("Accept", "application/json, text/plain, */*")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code != 200) {
                throw HaikuoFormatException("云剪贴板取回失败：HTTP ${response.code}")
            }
            val bytes = response.body?.bytes() ?: ByteArray(0)
            return HaikuoCodec.decodeText(bytes)
        }
    } catch (e: HaikuoFormatException) {
        throw e
    } catch (e: Exception) {
        throw HaikuoFormatException("云剪贴板取回失败：$e")
    }
}

fun uploadCloud6ShareCode(content: String, client: OkHttpClient? = null): HaikuoCloud6Upload {
    val text = content.trim()
    if (text.isEmpty()) {
        throw HaikuoFormatException("分享内容为空，无法上传")
    }
    val httpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    try {
        val bodyJson = HaikuoJson.stringify(mapOf("content" to text, "auto_password" to 1))
        val request = Request.Builder()
            .url("https://pasteme.tyrantg.com/api/create")
            .header("Content-Type", "application/json")
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code != 200) {
                throw HaikuoFormatException("云剪贴板 6 上传失败：HTTP ${response.code}")
            }
            val bytes = response.body?.bytes() ?: ByteArray(0)
            if (bytes.isEmpty()) {
                throw HaikuoFormatException("云剪贴板 6 上传失败：服务端无响应")
            }
            val decoded = HaikuoJson.parse(String(bytes, Charsets.UTF_8))
            if (decoded !is Map<*, *> || HaikuoJson.num(decoded["return_code"]) != 0.0) {
                val message = if (decoded is Map<*, *>) HaikuoJson.str(decoded["message"]) else ""
                throw HaikuoFormatException(
                    "云剪贴板 6 上传失败" + if (message.isNotEmpty()) "：$message" else ""
                )
            }
            val data = decoded["data"]
            if (data !is Map<*, *>) {
                throw HaikuoFormatException("云剪贴板 6 上传失败：服务端返回缺少数据")
            }
            val path = HaikuoJson.str(data["path"])
            if (path.isEmpty()) {
                throw HaikuoFormatException("云剪贴板 6 上传失败：服务端没给短码")
            }
            val password = HaikuoJson.str(data["password"])
            return HaikuoCloud6Upload(path, password)
        }
    } catch (e: HaikuoFormatException) {
        throw e
    } catch (e: Exception) {
        throw HaikuoFormatException("云剪贴板 6 上传失败：$e")
    }
}

fun handlePasteBridgeRequest(payload: Map<String, Any?>): Map<String, Any?> {
    try {
        val op = HaikuoJson.str(payload["op"])
        when (op) {
            "list" -> return mapOf("ok" to true, "result" to listOf("云剪贴板6"))
            "share" -> {
                val paste = HaikuoJson.str(payload["paste"])
                if (!pasteNameIsCloud6(paste)) {
                    return mapOf(
                        "ok" to false,
                        "error" to "“$paste”的服务端地址未公开（官方导入器闭源），暂不能分享到该云；当前仅支持云剪贴板 6"
                    )
                }
                val content = HaikuoJson.str(payload["content"])
                if (content.trim().isEmpty()) {
                    return mapOf("ok" to false, "error" to "分享内容为空，无法上传")
                }
                val uploaded = uploadCloud6ShareCode(content)
                val firstLine = buildCloud6ShareText(
                    uploaded.path,
                    uploaded.password,
                    ""
                ).split("\n").first()
                return mapOf("ok" to true, "result" to firstLine)
            }
            "parse" -> {
                val url = HaikuoJson.str(payload["url"])
                if (url.trim().isEmpty()) {
                    return mapOf("ok" to false, "error" to "云剪贴板地址为空，无法解析")
                }
                val apiUrl = pasteFetchUrl(url)
                val body = fetchPasteText(apiUrl)
                val data = HaikuoCodec.parseCloudApiResponse(body)
                return mapOf("ok" to true, "result" to data)
            }
            else -> return mapOf("ok" to false, "error" to "未知的云剪贴板操作：$op")
        }
    } catch (e: HaikuoFormatException) {
        return mapOf("ok" to false, "error" to e.message)
    } catch (e: Exception) {
        return mapOf("ok" to false, "error" to "云剪贴板操作失败：$e")
    }
}
