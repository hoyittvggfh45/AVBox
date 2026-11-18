package com.github.avbox.osc.haikuo

import android.util.Base64
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Proxy
import java.net.URI
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class HaikuoServer(
    hostname: String,
    port: Int,
    private val session: HaikuoSession
) : NanoHTTPD(hostname, port) {

    private var runtimeHtml: String? = null

    fun baseUrl(): String {
        return "http://127.0.0.1:$listeningPort/"
    }

    override fun serve(httpSession: IHTTPSession): Response {
        return try {
            route(httpSession)
        } catch (e: Exception) {
            jsonResponse(
                Response.Status.INTERNAL_ERROR,
                mapOf("error" to (e.message ?: e.toString()))
            )
        }
    }

    private fun route(httpSession: IHTTPSession): Response {
        val method = httpSession.method
        var bodyText = ""
        if (method == Method.POST || method == Method.PUT) {
            try {
                val files = HashMap<String, String>()
                httpSession.parseBody(files)
                bodyText = files["postData"] ?: ""
            } catch (e: Exception) {
                bodyText = ""
            }
        }
        val uri = httpSession.uri
        val params = httpSession.parms
        return when {
            uri == "/" -> serveRuntime()
            uri == "/__fetch" -> handleFetch(params)
            uri == "/__crypto" -> handleCrypto(params)
            uri == "/__vars" -> handleVars(method, bodyText)
            uri == "/__cookie" -> handleCookie(params)
            uri == "/__dialog" -> handleDialog(params, bodyText)
            uri == "/__img" -> handleImg(params)
            uri == "/__savefile" -> handleSaveFile(params, bodyText)
            uri == "/__rulepage" || uri.startsWith("/__rulepage/") -> handleRulePage(params)
            uri == "/__findrule" -> handleFindRule(params)
            uri == "/__ip" -> textResponse(Response.Status.OK, "text/plain", localIp())
            uri == "/__appinfo" -> handleAppInfo()
            uri == "/__lastrules" -> handleLastRules(params)
            uri == "/__clipboard" -> handleClipboard()
            uri == "/__paste" -> handlePaste(bodyText)
            uri.startsWith("/__proxyserver/") -> handleProxyServer(uri, httpSession)
            uri == "/__webview" -> handleWebView(bodyText)
            uri == "/__history" ||
                uri == "/__collection" ||
                uri == "/__bookmark" ||
                uri == "/__favorite" -> handleCollections(uri, method, params, bodyText)
            uri == "/__downloads" -> handleDownloads(method, bodyText)
            uri.startsWith("/__files/") -> handleFiles(uri, params)
            else -> textResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
    }

    private fun serveRuntime(): Response {
        val cached = runtimeHtml
        val html = cached ?: try {
            val raw = session.appContext.assets.open(RUNTIME_ASSET).use {
                HaikuoCodec.decodeText(it.readBytes())
            }
            runtimeHtml = raw
            raw
        } catch (e: Exception) {
            return textResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain",
                "runtime missing"
            )
        }
        val payload = programPayload()
        val filled = html
            .replace(
                "__PROGRAM_B64__",
                Base64.encodeToString(
                    HaikuoJson.stringify(payload).toByteArray(Charsets.UTF_8),
                    Base64.NO_WRAP
                )
            )
            .replace("__TITLE__", escapeHtml(session.program.name))
        return textResponse(Response.Status.OK, "text/html; charset=utf-8", filled)
    }

    private fun programPayload(): Map<String, Any?> {
        val rule: Any? = if (session.program.rawJson.trim().startsWith("{")) {
            HaikuoJson.parse(session.program.rawJson) ?: emptyMap<String, Any?>()
        } else {
            emptyMap<String, Any?>()
        }
        val payload = LinkedHashMap<String, Any?>()
        payload["name"] = session.program.name
        payload["script"] = session.program.script
        payload["url"] = session.program.url
        payload["kind"] = session.program.kind.name
        payload["rule"] = rule
        return payload
    }

    private fun handleFetch(params: Map<String, String>): Response {
        val target = params["u"] ?: ""
        if (!target.lowercase().startsWith("http://") &&
            !target.lowercase().startsWith("https://")
        ) {
            return jsonResponse(
                Response.Status.OK,
                fetchEnvelope(0, "", emptyMap(), target)
            )
        }
        val method = (params["m"] ?: "GET").uppercase()
        val bodyParam = params["b"] ?: ""
        val headersParam = params["h"] ?: ""
        val timeoutMs = (params["t"]?.toLongOrNull() ?: 30000L).coerceIn(5000L, 120000L)
        val followRedirects = params["r"] != "0"
        val headersOnly = params["o"] == "1"
        val hexBody = params["hex"] == "1"
        val charsetName = params["cs"] ?: ""
        val scriptHeaders = LinkedHashMap<String, String>()
        if (headersParam.isNotEmpty()) {
            val decoded = HaikuoJson.parse(headersParam)
            if (decoded is Map<*, *>) {
                for ((key, value) in decoded) {
                    if (value is List<*>) {
                        val first = value.firstOrNull()
                        if (first != null) scriptHeaders[key.toString()] = first.toString()
                    } else if (value != null) {
                        scriptHeaders[key.toString()] = value.toString()
                    }
                }
            }
        }
        val host = try {
            URI(target).host ?: ""
        } catch (e: Exception) {
            ""
        }
        val rewritten = applyHaikuoProxyRewrites(target, sessionProxyRewrites())
        val effectiveTarget = rewritten
        val client = try {
            buildFetchClient(followRedirects, timeoutMs)
        } catch (e: Exception) {
            return jsonResponse(
                Response.Status.OK,
                fetchEnvelope(0, "", emptyMap(), target, e.message ?: e.toString())
            )
        }
        val requestBuilder = Request.Builder().url(effectiveTarget)
        requestBuilder.header("User-Agent", DEFAULT_USER_AGENT)
        var hasCookie = false
        for ((name, value) in scriptHeaders) {
            if (value.isEmpty()) continue
            if (name.equals("content-length", ignoreCase = true)) continue
            if (name.equals("cookie", ignoreCase = true)) hasCookie = true
            requestBuilder.header(name, value)
        }
        if (!hasCookie && host.isNotEmpty()) {
            val jarCookie = session.cookieJar.headerFor(host)
            if (jarCookie.isNotEmpty()) {
                requestBuilder.header("Cookie", jarCookie)
            }
        }
        val effectiveMethod = if (headersOnly && method == "GET") "HEAD" else method
        if (effectiveMethod == "GET" || effectiveMethod == "HEAD" || effectiveMethod == "DELETE") {
            if (effectiveMethod == "DELETE" && bodyParam.isNotEmpty()) {
                requestBuilder.method(
                    effectiveMethod,
                    bodyParam.toByteArray(Charsets.UTF_8)
                        .toRequestBody(contentTypeOf(scriptHeaders))
                )
            } else {
                requestBuilder.method(effectiveMethod, null)
            }
        } else {
            requestBuilder.method(
                effectiveMethod,
                bodyParam.toByteArray(Charsets.UTF_8)
                    .toRequestBody(contentTypeOf(scriptHeaders))
            )
        }
        return try {
            val response = client.newCall(requestBuilder.build()).execute()
            response.use { resp ->
                val setCookies = resp.headers("Set-Cookie")
                if (host.isNotEmpty() && setCookies.isNotEmpty()) {
                    session.cookieJar.store(host, setCookies)
                }
                val headerMap = LinkedHashMap<String, List<String>>()
                for ((name, values) in resp.headers.toMultimap()) {
                    headerMap[name] = values
                }
                val finalUrl = resp.request.url.toString()
                val rawBytes = if (headersOnly) ByteArray(0) else (resp.body?.bytes() ?: ByteArray(0))
                val body = if (hexBody) {
                    Base64.encodeToString(rawBytes, Base64.NO_WRAP)
                } else {
                    decodeFetchBody(rawBytes, charsetName)
                }
                jsonResponse(
                    Response.Status.OK,
                    fetchEnvelope(resp.code, body, headerMap, finalUrl)
                )
            }
        } catch (e: Exception) {
            jsonResponse(
                Response.Status.OK,
                fetchEnvelope(0, "", emptyMap(), target, e.message ?: e.toString())
            )
        }
    }

    private fun sessionProxyRewrites(): List<Pair<String, String>> {
        val raw = session.program.rawJson
        if (!raw.trim().startsWith("{")) return emptyList()
        val decoded = HaikuoJson.parse(raw)
        if (decoded !is Map<*, *>) return emptyList()
        val proxy = decoded["proxy"]
        if (proxy !is String) return emptyList()
        return parseHaikuoProxyRewrites(proxy)
    }

    private fun buildFetchClient(followRedirects: Boolean, timeoutMs: Long): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .followRedirects(followRedirects)
            .followSslRedirects(followRedirects)
        val proxy = session.ruleProxy
        if (!proxy.isNullOrEmpty()) {
            builder.proxy(parseOkHttpProxy(proxy))
        }
        return builder.build()
    }

    private fun parseOkHttpProxy(raw: String): Proxy {
        var value = raw.trim()
        var type = Proxy.Type.HTTP
        if (value.lowercase().startsWith("socks5://")) {
            type = Proxy.Type.SOCKS
            value = value.substring("socks5://".length)
        } else if (value.lowercase().startsWith("socks://")) {
            type = Proxy.Type.SOCKS
            value = value.substring("socks://".length)
        } else if (value.lowercase().startsWith("http://")) {
            value = value.substring("http://".length)
        } else if (value.lowercase().startsWith("https://")) {
            value = value.substring("https://".length)
        }
        value = value.substringBefore("/")
        val host = value.substringBefore(":")
        val port = value.substringAfter(":", "").toIntOrNull() ?: 8080
        if (host.isEmpty()) throw IllegalArgumentException("代理地址不对")
        return Proxy(type, InetSocketAddress(host, port))
    }

    private fun contentTypeOf(headers: Map<String, String>): okhttp3.MediaType? {
        for ((name, value) in headers) {
            if (name.equals("content-type", ignoreCase = true)) {
                return value.toMediaTypeOrNull()
            }
        }
        return null
    }

    private fun decodeFetchBody(bytes: ByteArray, charsetName: String): String {
        val cs = charsetName.trim().lowercase()
        if (cs.isEmpty()) return HaikuoCodec.decodeText(bytes)
        return when (cs) {
            "gbk", "gb2312", "gb18030", "gb_2312" -> try {
                String(bytes, Charset.forName("GBK"))
            } catch (e: Exception) {
                HaikuoCodec.decodeText(bytes)
            }
            "utf-8", "utf8" -> String(bytes, Charsets.UTF_8)
            "iso-8859-1", "latin1" -> String(bytes, Charsets.ISO_8859_1)
            else -> HaikuoCodec.decodeText(bytes)
        }
    }

    private fun fetchEnvelope(
        code: Int,
        body: String,
        headers: Map<String, List<String>>,
        url: String,
        error: String? = null
    ): Map<String, Any?> {
        val envelope = LinkedHashMap<String, Any?>()
        envelope["code"] = code
        envelope["body"] = body
        envelope["headers"] = headers
        envelope["url"] = url
        if (error != null) envelope["error"] = error
        return envelope
    }

    private fun handleCrypto(params: Map<String, String>): Response {
        val query = params["q"] ?: ""
        val decoded = HaikuoJson.parse(query)
        if (decoded !is Map<*, *>) {
            return jsonResponse(Response.Status.OK, mapOf("result" to ""))
        }
        val op = HaikuoJson.str(decoded["op"]).lowercase()
        val result: Any? = try {
            when (op) {
                "md5", "sha1", "sha256", "sha512" -> {
                    haikuoHashHex(op, cryptoInput(decoded))
                }
                "hmac-md5", "hmac-sha1", "hmac-sha256", "hmac-sha512",
                "hmacmd5", "hmacsha1", "hmacsha256", "hmacsha512" -> {
                    val keyHex = HaikuoJson.str(decoded["keyHex"])
                    val key = if (keyHex.isNotEmpty()) {
                        haikuoHexToBytes(keyHex)
                    } else {
                        HaikuoJson.str(decoded["key"]).toByteArray(Charsets.UTF_8)
                    }
                    val normalized = op.removePrefix("hmac-").removePrefix("hmac")
                    haikuoHmacHex(normalized, key, cryptoInput(decoded))
                }
                "pbkdf2" -> {
                    val passwordHex = HaikuoJson.str(decoded["passwordHex"])
                    val saltHex = HaikuoJson.str(decoded["saltHex"])
                    val password = if (passwordHex.isNotEmpty()) {
                        haikuoHexToBytes(passwordHex)
                    } else {
                        HaikuoJson.str(decoded["password"]).toByteArray(Charsets.UTF_8)
                    }
                    val salt = if (saltHex.isNotEmpty()) {
                        haikuoHexToBytes(saltHex)
                    } else {
                        HaikuoJson.str(decoded["salt"]).toByteArray(Charsets.UTF_8)
                    }
                    val iterations = (HaikuoJson.num(decoded["iterations"])?.toInt() ?: 1000)
                        .coerceIn(1, 10000)
                    val keyBytes = (HaikuoJson.num(decoded["keyBytes"])?.toInt() ?: 32)
                        .coerceIn(1, 1024)
                    val hash = HaikuoJson.str(decoded["hash"]).ifEmpty { "sha1" }
                    val derived = haikuoPbkdf2(password, salt, iterations, keyBytes, hash)
                    derived.joinToString("") { "%02x".format(it) }
                }
                "md5file" -> {
                    val name = HaikuoJson.str(decoded["text"])
                        .ifEmpty { HaikuoJson.str(decoded["path"]) }
                        .ifEmpty { HaikuoJson.str(decoded["file"]) }
                    val bytes = session.persistedFileBytes(name) ?: ByteArray(0)
                    haikuoHashHex("md5", bytes)
                }
                "gunzip" -> {
                    val bytes = haikuoGunzipBytes(cryptoInput(decoded))
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
                "gzip" -> {
                    val bytes = haikuoGzipBytes(cryptoInput(decoded))
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
                "inflate" -> {
                    val bytes = haikuoInflateBytes(cryptoInput(decoded))
                    Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
        return jsonResponse(Response.Status.OK, mapOf("result" to result))
    }

    private fun cryptoInput(payload: Map<*, *>): ByteArray {
        val hex = HaikuoJson.str(payload["hex"])
        if (hex.isNotEmpty()) return haikuoHexToBytes(hex)
        val msgHex = HaikuoJson.str(payload["msgHex"])
        if (msgHex.isNotEmpty()) return haikuoHexToBytes(msgHex)
        return HaikuoJson.str(payload["text"]).toByteArray(Charsets.UTF_8)
    }

    private fun handleVars(method: Method, bodyText: String): Response {
        if (method == Method.POST) {
            val decoded = HaikuoJson.parse(bodyText)
            if (decoded is Map<*, *>) {
                val payload = LinkedHashMap<String, Any?>()
                for ((key, value) in decoded) {
                    payload[key.toString()] = value
                }
                HaikuoSessionVars.apply(payload)
            }
            return jsonResponse(Response.Status.OK, mapOf("ok" to true))
        }
        return jsonResponse(
            Response.Status.OK,
            mapOf("vars" to HaikuoSessionVars.snapshot())
        )
    }

    private fun handleCookie(params: Map<String, String>): Response {
        val op = params["op"] ?: "get"
        val target = params["u"] ?: ""
        val host = try {
            URI(target).host ?: ""
        } catch (e: Exception) {
            ""
        }
        if (op == "set") {
            val cookie = params["c"] ?: ""
            if (host.isNotEmpty() && cookie.isNotEmpty()) {
                session.cookieJar.setCookie(host, cookie)
            }
            return jsonResponse(Response.Status.OK, mapOf("ok" to true))
        }
        val cookie = if (host.isEmpty()) "" else session.cookieJar.headerFor(host)
        return jsonResponse(Response.Status.OK, mapOf("cookie" to cookie))
    }

    private fun handleDialog(params: Map<String, String>, bodyText: String): Response {
        val payload = LinkedHashMap<String, Any?>()
        if (bodyText.isNotEmpty()) {
            val decoded = HaikuoJson.parse(bodyText)
            if (decoded is Map<*, *>) {
                for ((key, value) in decoded) {
                    payload[key.toString()] = value
                }
            }
        } else {
            val query = params["q"]
            if (!query.isNullOrEmpty()) {
                val decoded = HaikuoJson.parse(query)
                if (decoded is Map<*, *>) {
                    for ((key, value) in decoded) {
                        payload[key.toString()] = value
                    }
                }
            }
            if (payload.isEmpty()) {
                for ((key, value) in params) {
                    payload[key] = value
                }
            }
        }
        if (payload.isEmpty()) {
            return jsonResponse(Response.Status.OK, mapOf("result" to true))
        }
        val result = session.dialogQueue.show(payload)
        return jsonResponse(Response.Status.OK, result)
    }

    private fun handleImg(params: Map<String, String>): Response {
        val target = params["u"] ?: ""
        if (!target.lowercase().startsWith("http://") &&
            !target.lowercase().startsWith("https://")
        ) {
            return textResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
        val headersParam = params["h"] ?: ""
        val scriptHeaders = LinkedHashMap<String, String>()
        if (headersParam.isNotEmpty()) {
            val decoded = HaikuoJson.parse(headersParam)
            if (decoded is Map<*, *>) {
                for ((key, value) in decoded) {
                    if (value != null) scriptHeaders[key.toString()] = value.toString()
                }
            }
        }
        val host = try {
            URI(target).host ?: ""
        } catch (e: Exception) {
            ""
        }
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .callTimeout(30, TimeUnit.SECONDS)
                .build()
            val builder = Request.Builder().url(target)
            builder.header("User-Agent", DEFAULT_USER_AGENT)
            var hasCookie = false
            for ((name, value) in scriptHeaders) {
                if (value.isEmpty()) continue
                if (name.equals("cookie", ignoreCase = true)) hasCookie = true
                builder.header(name, value)
            }
            if (!hasCookie && host.isNotEmpty()) {
                val jarCookie = session.cookieJar.headerFor(host)
                if (jarCookie.isNotEmpty()) builder.header("Cookie", jarCookie)
            }
            val response = client.newCall(builder.build()).execute()
            response.use { resp ->
                if (resp.code < 200 || resp.code >= 300) {
                    return textResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
                }
                val setCookies = resp.headers("Set-Cookie")
                if (host.isNotEmpty() && setCookies.isNotEmpty()) {
                    session.cookieJar.store(host, setCookies)
                }
                val bytes = resp.body?.bytes() ?: ByteArray(0)
                val contentType = resp.header("Content-Type") ?: "application/octet-stream"
                val stream = ByteArrayInputStream(bytes)
                newFixedLengthResponse(
                    Response.Status.OK,
                    contentType,
                    stream,
                    bytes.size.toLong()
                )
            }
        } catch (e: Exception) {
            textResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
    }

    private fun handleSaveFile(params: Map<String, String>, bodyText: String): Response {
        val name = params["n"] ?: ""
        if (name.isEmpty() || bodyText.isEmpty()) {
            return jsonResponse(Response.Status.OK, mapOf("ok" to false))
        }
        return try {
            val bytes = Base64.decode(bodyText.trim(), Base64.DEFAULT)
            session.storeSavedFile(name, bytes)
            jsonResponse(Response.Status.OK, mapOf("ok" to true))
        } catch (e: Exception) {
            jsonResponse(Response.Status.OK, mapOf("ok" to false))
        }
    }

    private fun handleRulePage(params: Map<String, String>): Response {
        val ruleTitle = params["rule"] ?: params["title"] ?: ""
        val path = params["path"] ?: params["name"] ?: ""
        val programs = session.store.list()
        val titles = programs.map { it.name }
        val matched = matchRuleTitle(ruleTitle, titles)
            ?: return textResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        val program = programs.first { it.name == matched }
        val decoded = HaikuoJson.parse(program.rawJson)
        if (decoded !is Map<*, *>) {
            return textResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
        val pagesValue = decoded["pages"]
        val pages = ArrayList<Map<String, Any?>>()
        if (pagesValue is List<*>) {
            for (item in pagesValue) {
                if (item is Map<*, *>) {
                    val page = LinkedHashMap<String, Any?>()
                    for ((key, value) in item) {
                        page[key.toString()] = value
                    }
                    pages.add(page)
                }
            }
        } else if (pagesValue is String && pagesValue.trim().startsWith("[")) {
            val parsed = HaikuoJson.parse(pagesValue)
            if (parsed is List<*>) {
                for (item in parsed) {
                    if (item is Map<*, *>) {
                        val page = LinkedHashMap<String, Any?>()
                        for ((key, value) in item) {
                            page[key.toString()] = value
                        }
                        pages.add(page)
                    }
                }
            }
        }
        val decodedPath = try {
            java.net.URLDecoder.decode(path, "UTF-8")
        } catch (e: Exception) {
            path
        }
        val page = pages.firstOrNull {
            HaikuoJson.str(it["name"]) == path ||
                HaikuoJson.str(it["path"]) == path ||
                HaikuoJson.str(it["name"]) == decodedPath ||
                HaikuoJson.str(it["path"]) == decodedPath
        } ?: return textResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        val ruleValue = page["rule"]
        val pagePayload = LinkedHashMap<String, Any?>()
        pagePayload["name"] = HaikuoJson.str(page["name"])
        pagePayload["path"] = HaikuoJson.str(page["path"])
        pagePayload["rule"] = if (ruleValue is String) {
            ruleValue
        } else {
            HaikuoJson.stringify(page)
        }
        val payload = LinkedHashMap<String, Any?>()
        payload["rule"] = decoded
        payload["page"] = pagePayload
        return jsonResponse(Response.Status.OK, payload)
    }

    private fun handleFindRule(params: Map<String, String>): Response {
        val query = params["title"] ?: params["rule"] ?: params["name"] ?: params["q"] ?: ""
        val titles = session.store.list().map { it.name }
        val matched = matchRuleTitle(query, titles)
            ?: return textResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        return jsonResponse(Response.Status.OK, mapOf("name" to matched))
    }

    private fun localIp(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val addresses = interfaces.nextElement().inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: ""
                    }
                }
            }
        } catch (e: Exception) {
        }
        return ""
    }

    private fun handleAppInfo(): Response {
        val payload = LinkedHashMap<String, Any?>()
        payload["version"] = session.appVersionCode
        payload["ruleCount"] = session.store.list().size
        return jsonResponse(Response.Status.OK, payload)
    }

    private fun handleLastRules(params: Map<String, String>): Response {
        val count = (params["n"]?.toIntOrNull() ?: 10).coerceIn(1, 100)
        val names = session.store.list().take(count).map { it.name }
        return jsonResponse(Response.Status.OK, names)
    }

    private fun handleClipboard(): Response {
        val text = try {
            session.clipboard?.getText() ?: ""
        } catch (e: Exception) {
            ""
        }
        return textResponse(Response.Status.OK, "text/plain; charset=utf-8", text)
    }

    private fun handleProxyServer(uri: String, httpSession: IHTTPSession): Response {
        val id = parseProxyServerId(uri)
            ?: return textResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        val evaluator = session.jsEvaluator
            ?: return textResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        val queryJson = HaikuoJson.stringify(httpSession.parms)
        val script = "window.__haikuoProxyServe(" +
            HaikuoJson.stringify(id) + ", " + queryJson + ")"
        val raw = try {
            evaluator.evaluate(script)
        } catch (e: Exception) {
            null
        } ?: return textResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        val decoded = HaikuoJson.parse(raw)
        if (decoded == null) {
            return textResponse(
                Response.Status.OK,
                "application/vnd.apple.mpegurl; charset=utf-8",
                raw
            )
        }
        if (decoded is String) {
            return textResponse(
                Response.Status.OK,
                "application/vnd.apple.mpegurl; charset=utf-8",
                decoded
            )
        }
        if (decoded is Map<*, *>) {
            val statusCode = HaikuoJson.num(decoded["statusCode"])?.toInt()
            if (statusCode != null) {
                val body = HaikuoJson.str(decoded["body"])
                val headersValue = decoded["headers"]
                val status = Response.Status.lookup(statusCode) ?: Response.Status.OK
                val response = textResponse(status, "text/plain; charset=utf-8", body)
                if (headersValue is Map<*, *>) {
                    for ((key, value) in headersValue) {
                        if (value != null) {
                            response.addHeader(key.toString(), value.toString())
                        }
                    }
                }
                return response
            }
        }
        return textResponse(
            Response.Status.OK,
            "application/vnd.apple.mpegurl; charset=utf-8",
            raw
        )
    }

    private fun handlePaste(bodyText: String): Response {
        val decoded = HaikuoJson.parse(bodyText)
        val payload = LinkedHashMap<String, Any?>()
        if (decoded is Map<*, *>) {
            for ((key, value) in decoded) {
                payload[key.toString()] = value
            }
        }
        return jsonResponse(Response.Status.OK, handlePasteBridgeRequest(payload))
    }

    private fun handleWebView(bodyText: String): Response {
        val fetcher = session.webViewFetcher
        if (fetcher == null) {
            return jsonResponse(Response.Status.OK, mapOf("html" to ""))
        }
        val decoded = HaikuoJson.parse(bodyText)
        val payload = LinkedHashMap<String, Any?>()
        if (decoded is Map<*, *>) {
            for ((key, value) in decoded) {
                payload[key.toString()] = value
            }
        }
        val html = try {
            fetcher.fetch(payload) ?: ""
        } catch (e: Exception) {
            ""
        }
        return jsonResponse(Response.Status.OK, mapOf("html" to html))
    }

    private fun handleCollections(
        uri: String,
        method: Method,
        params: Map<String, String>,
        bodyText: String
    ): Response {
        val rule = params["rule"] ?: ""
        val group = params["group"]
        if (method == Method.POST) {
            val decoded = HaikuoJson.parse(bodyText)
            val entry = LinkedHashMap<String, Any?>()
            if (decoded is Map<*, *>) {
                for ((key, value) in decoded) {
                    entry[key.toString()] = value
                }
            }
            if (entry["rule"] == null && rule.isNotEmpty()) entry["rule"] = rule
            return when (uri) {
                "/__history" -> {
                    session.store.recordHistory(
                        HaikuoJson.str(entry["rule"]),
                        HaikuoJson.str(entry["url"]),
                        HaikuoJson.str(entry["title"]),
                        HaikuoJson.str(entry["pic"])
                    )
                    jsonResponse(Response.Status.OK, mapOf("ok" to true))
                }
                "/__bookmark" -> {
                    session.store.addBookmark(entry)
                    jsonResponse(Response.Status.OK, mapOf("ok" to true))
                }
                else -> {
                    val favorite = session.store.toggleFavorite(entry)
                    jsonResponse(Response.Status.OK, mapOf("favorite" to favorite))
                }
            }
        }
        val entries = when (uri) {
            "/__history" -> session.store.listHistory(rule.takeIf { it.isNotEmpty() })
            "/__bookmark" -> session.store.listBookmarks(rule.takeIf { it.isNotEmpty() })
            else -> session.store.listFavorites(rule.takeIf { it.isNotEmpty() }, group)
        }
        return jsonResponse(Response.Status.OK, mapOf("entries" to entries))
    }

    private fun handleDownloads(method: Method, bodyText: String): Response {
        val dir = session.downloadsDir()
        if (method == Method.POST) {
            val decoded = HaikuoJson.parse(bodyText)
            if (decoded is Map<*, *>) {
                val action = HaikuoJson.str(decoded["action"])
                if (action == "delete") {
                    val name = HaikuoJson.str(decoded["name"])
                        .replace('\\', '/').substringAfterLast('/')
                    if (name.isNotEmpty() && name != "." && name != "..") {
                        val file = File(dir, name)
                        try {
                            if (file.exists()) file.delete()
                        } catch (e: Exception) {
                        }
                    }
                }
            }
            return jsonResponse(Response.Status.OK, mapOf("ok" to true))
        }
        val files = dir.listFiles() ?: emptyArray()
        val entries = ArrayList<Map<String, Any?>>()
        for (file in files) {
            if (!file.isFile) continue
            val entry = LinkedHashMap<String, Any?>()
            entry["name"] = file.name
            entry["size"] = file.length()
            entry["mtime"] = file.lastModified()
            entry["path"] = file.absolutePath
            entries.add(entry)
        }
        val sorted = sortHaikuoDownloadEntries(entries)
        return jsonResponse(Response.Status.OK, mapOf("entries" to sorted))
    }

    private fun handleFiles(uri: String, params: Map<String, String>): Response {
        val name = uri.substring("/__files/".length)
        if (name.isEmpty()) {
            return textResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
        val ruleTitle = params["rule"] ?: session.program.name
        var bytes = session.persistedFileBytes(name)
        if (bytes == null && ruleTitle.isNotEmpty()) {
            bytes = session.ruleFileBytes(name, ruleTitle)
        }
        if (bytes == null) {
            bytes = session.presetModuleBytes(name, ruleTitle)
        }
        if (bytes == null) {
            return textResponse(Response.Status.NOT_FOUND, "text/plain", "not found")
        }
        val mime = mimeForFile(name)
        val stream = ByteArrayInputStream(bytes)
        return newFixedLengthResponse(
            Response.Status.OK,
            mime,
            stream,
            bytes.size.toLong()
        )
    }

    private fun mimeForFile(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".m3u8") -> "application/vnd.apple.mpegurl; charset=utf-8"
            lower.endsWith(".json") -> "application/json; charset=utf-8"
            lower.endsWith(".srt") || lower.endsWith(".vtt") ||
                lower.endsWith(".txt") || lower.endsWith(".lrc") -> "text/plain; charset=utf-8"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".gif") -> "image/gif"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".mp4") -> "video/mp4"
            lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html; charset=utf-8"
            lower.endsWith(".js") -> "application/javascript; charset=utf-8"
            else -> "application/octet-stream"
        }
    }

    private fun jsonResponse(status: Response.IStatus, value: Any?): Response {
        return textResponse(status, "application/json; charset=utf-8", HaikuoJson.stringify(value))
    }

    private fun textResponse(status: Response.IStatus, mime: String, text: String): Response {
        return newFixedLengthResponse(status, mime, text)
    }

    private fun escapeHtml(text: String): String {
        val out = StringBuilder()
        for (ch in text) {
            when (ch) {
                '&' -> out.append("&amp;")
                '<' -> out.append("&lt;")
                '>' -> out.append("&gt;")
                '"' -> out.append("&quot;")
                '\'' -> out.append("&#39;")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

        private const val RUNTIME_ASSET = "haikuo/runtime.html"

        private val PORT_CANDIDATES = (18081..18090).toList()

        fun start(session: HaikuoSession): HaikuoServer? {
            for (port in PORT_CANDIDATES) {
                val server = HaikuoServer("127.0.0.1", port, session)
                try {
                    server.start()
                    return server
                } catch (e: Exception) {
                    try {
                        server.stop()
                    } catch (ignored: Exception) {
                    }
                }
            }
            val fallback = HaikuoServer("127.0.0.1", 0, session)
            return try {
                fallback.start()
                fallback
            } catch (e: Exception) {
                try {
                    fallback.stop()
                } catch (ignored: Exception) {
                }
                null
            }
        }
    }
}
