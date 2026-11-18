package com.github.avbox.osc.miniapp

import android.util.Base64
import com.github.avbox.osc.haikuo.HaikuoJson
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.net.URLDecoder

typealias MiniAppProxyFetch = (String, Any?) -> Map<String, Any?>

val MINI_APP_MIME_TYPES = mapOf(
    ".html" to "text/html; charset=utf-8",
    ".htm" to "text/html; charset=utf-8",
    ".css" to "text/css; charset=utf-8",
    ".js" to "text/javascript; charset=utf-8",
    ".mjs" to "text/javascript; charset=utf-8",
    ".json" to "application/json; charset=utf-8",
    ".png" to "image/png",
    ".jpg" to "image/jpeg",
    ".jpeg" to "image/jpeg",
    ".gif" to "image/gif",
    ".svg" to "image/svg+xml",
    ".webp" to "image/webp",
    ".ico" to "image/x-icon",
    ".mp4" to "video/mp4",
    ".m3u8" to "application/vnd.apple.mpegurl",
    ".wasm" to "application/wasm",
    ".txt" to "text/plain; charset=utf-8",
    ".xml" to "application/xml; charset=utf-8",
    ".woff" to "font/woff",
    ".woff2" to "font/woff2",
    ".ttf" to "font/ttf"
)

class MiniAppServer(
    hostname: String,
    port: Int,
    private val root: File,
    private val entry: String,
    private val sdkScript: String
) : NanoHTTPD(hostname, port) {

    var proxyFetch: MiniAppProxyFetch? = null

    val baseUrl: String
        get() = "http://127.0.0.1:$listeningPort"

    fun entryUrl(): String = "$baseUrl/$entry"

    override fun serve(session: IHTTPSession): Response {
        try {
            if (session.method != Method.GET && session.method != Method.HEAD) {
                return newFixedLengthResponse(Response.Status.METHOD_NOT_ALLOWED, MIME_PLAINTEXT, "Method Not Allowed")
            }
            var path = session.uri
            if (path == "/pyproxy") {
                return handlePyProxy(session)
            }
            if (path.startsWith("/")) path = path.substring(1)
            if (path.isEmpty()) path = entry
            val decoded = try {
                URLDecoder.decode(path, "UTF-8")
            } catch (e: Exception) {
                path
            }
            val filePath = miniAppSafeJoinPath(root.absolutePath, decoded)
            var file: File? = null
            if (filePath != null) {
                val candidate = File(filePath)
                if (candidate.exists() && candidate.isFile) {
                    file = candidate
                } else {
                    val dir = File(filePath)
                    if (dir.exists() && dir.isDirectory) {
                        val index = File(filePath + File.separator + entry)
                        if (index.exists() && index.isFile) file = index
                    }
                }
            }
            if (file == null) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
            }
            val extension = extensionOf(file.absolutePath)
            val mime = MINI_APP_MIME_TYPES[extension] ?: "application/octet-stream"
            if (session.method == Method.HEAD) {
                val response = newFixedLengthResponse(Response.Status.OK, mime, "")
                response.addHeader("Content-Length", file.length().toString())
                return response
            }
            if (extension == ".html" || extension == ".htm") {
                val html = file.readText(Charsets.UTF_8)
                return newFixedLengthResponse(Response.Status.OK, mime, injectSdk(html))
            }
            return newChunkedResponse(Response.Status.OK, mime, FileInputStream(file))
        } catch (e: Exception) {
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Internal Server Error")
        }
    }

    private fun handlePyProxy(session: IHTTPSession): Response {
        val fetch = proxyFetch
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
        val remote = session.remoteIpAddress
        if (remote != null && remote != "127.0.0.1" && remote != "::1" && remote != "0:0:0:0:0:0:0:1") {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "Forbidden")
        }
        val payload = session.parms["d"] ?: ""
        val source: String
        val param: Any?
        try {
            if (payload.isEmpty()) throw MiniAppFormatException("缺少 d 参数")
            val decoded = HaikuoJson.parse(String(Base64.decode(payload, Base64.URL_SAFE), Charsets.UTF_8))
                ?: throw MiniAppFormatException("负载不是对象")
            val map = HaikuoJson.asMap(decoded) ?: throw MiniAppFormatException("负载不是对象")
            val rawSource = map["source"]
            if (rawSource !is String || rawSource.isEmpty()) {
                throw MiniAppFormatException("负载缺少 source")
            }
            source = rawSource
            param = map["param"]
        } catch (e: Exception) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "Bad Request")
        }
        return try {
            val result = fetch(source, param)
            val body = Base64.decode(HaikuoJson.str(result["bodyBase64"]), Base64.DEFAULT)
            val status = HaikuoJson.num(result["status"])?.toInt() ?: 200
            val mime = HaikuoJson.str(result["mime"]).ifEmpty { "application/octet-stream" }
            val response = newFixedLengthResponse(nanoStatus(status), mime, ByteArrayInputStream(body), body.size.toLong())
            val headers = result["headers"]
            if (headers is Map<*, *>) {
                for ((key, value) in headers) {
                    response.addHeader(key.toString(), value.toString())
                }
            }
            response
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "py proxy failed: $e")
        }
    }

    private fun injectSdk(html: String): String {
        val tag = "<script>$sdkScript</script>"
        val headIndex = html.indexOf("<head>")
        if (headIndex >= 0) {
            val insertAt = headIndex + "<head>".length
            return html.substring(0, insertAt) + tag + html.substring(insertAt)
        }
        val headOpen = Regex("<head[^>]*>").find(html)
        if (headOpen != null) {
            return html.substring(0, headOpen.range.last + 1) + tag + html.substring(headOpen.range.last + 1)
        }
        return tag + html
    }

    private fun extensionOf(path: String): String {
        val dot = path.lastIndexOf(".")
        if (dot < 0) return ""
        return path.substring(dot).lowercase()
    }

    private fun nanoStatus(code: Int): Response.Status {
        return Response.Status.lookup(code) ?: Response.Status.OK
    }

    companion object {
        fun start(
            root: File,
            entry: String,
            sdkScript: String,
            basePort: Int,
            usedPorts: Set<Int>,
            lanShare: Boolean
        ): MiniAppServer {
            val hostname = if (lanShare) "0.0.0.0" else "127.0.0.1"
            var lastError: Exception? = null
            if (basePort <= 0) {
                val server = MiniAppServer(hostname, 0, root, entry, sdkScript)
                server.start()
                return server
            }
            for (attempt in 0 until 64) {
                val candidate = basePort + attempt
                if (usedPorts.contains(candidate)) continue
                try {
                    val server = MiniAppServer(hostname, candidate, root, entry, sdkScript)
                    server.start()
                    return server
                } catch (e: Exception) {
                    lastError = e
                }
            }
            throw IllegalStateException("本地服务启动失败：$lastError")
        }
    }
}
