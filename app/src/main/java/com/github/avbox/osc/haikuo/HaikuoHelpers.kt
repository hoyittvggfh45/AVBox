package com.github.avbox.osc.haikuo

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

fun isSqliteBytes(bytes: ByteArray): Boolean {
    val magic = byteArrayOf(
        0x53, 0x51, 0x4C, 0x69, 0x74, 0x65, 0x20, 0x66, 0x6F, 0x72, 0x6D, 0x61,
        0x74, 0x20, 0x33, 0x00
    )
    if (bytes.size < magic.size) return false
    for (i in magic.indices) {
        if (bytes[i] != magic[i]) return false
    }
    return true
}

fun haikuoDbRowToRule(row: Map<String, Any?>): Map<String, Any?>? {
    val title = dbText(row["title"])
    if (title.isEmpty()) return null
    val rule = LinkedHashMap<String, Any?>()
    rule["title"] = title
    rule["url"] = dbText(row["url"])
    rule["find_rule"] = dbText(row["find_rule"])
    rule["detail_find_rule"] = dbText(row["detail_find_rule"])
    rule["sdetail_find_rule"] = dbText(row["sdetail_find_rule"])
    rule["searchFind"] = dbText(row["searchfind"])
    rule["search_url"] = dbText(row["search_url"])
    rule["prerule"] = dbText(row["prerule"])
    rule["storage"] = dbText(row["storage"])
    rule["last_chapter_rule"] = dbText(row["last_chapter_rule"])
    rule["chapter_find"] = dbText(row["chapter_find"])
    rule["movie_find"] = dbText(row["movie_find"])
    rule["col_type"] = dbText(row["col_type"])
    rule["detail_col_type"] = dbText(row["detail_col_type"])
    rule["sdetail_col_type"] = dbText(row["sdetail_col_type"])
    rule["class_name"] = dbText(row["class_name"])
    rule["class_url"] = dbText(row["class_url"])
    rule["area_name"] = dbText(row["area_name"])
    rule["area_url"] = dbText(row["area_url"])
    rule["sort_name"] = dbText(row["sort_name"])
    rule["sort_url"] = dbText(row["sort_url"])
    rule["year_name"] = dbText(row["year_name"])
    rule["year_url"] = dbText(row["year_url"])
    rule["firstHeader"] = dbText(row["firstheader"])
    rule["group"] = dbText(row["group_lpcolumn"])
    rule["type"] = dbText(row["type"])
    rule["ua"] = dbText(row["ua"])
    rule["proxy"] = dbText(row["proxy"])
    rule["author"] = dbText(row["author"])
    rule["icon"] = dbText(row["icon"])
    rule["version"] = dbVersion(row["version"])
    val pages = row["pages"]
    if (pages is String) {
        rule["pages"] = pages
    } else if (pages is List<*>) {
        rule["pages"] = pages
    } else {
        rule["pages"] = "[]"
    }
    return rule
}

private fun dbText(value: Any?): String {
    if (value == null) return ""
    val text = value.toString().trim()
    if (text == "None" || text == "null") return ""
    return text
}

private fun dbVersion(value: Any?): String {
    if (value == null) return ""
    val text = value.toString().trim()
    if (text == "None" || text == "null") return ""
    return text
}

fun parseHaikuoProxy(raw: String?): String? {
    val value = (raw ?: "").trim()
    if (value.isEmpty()) return null
    if (value.contains("=>")) return null
    val lower = value.lowercase()
    if (lower == "false" || lower == "0" || lower == "none" || lower == "no" || lower == "off") {
        return null
    }
    if (lower.contains("://")) return value
    return "http://$value"
}

fun haikuoPbkdf2(
    password: ByteArray,
    salt: ByteArray,
    iterations: Int,
    keyBytes: Int,
    hash: String = "sha1"
): ByteArray {
    val op = when (hash.lowercase()) {
        "sha256" -> "sha256"
        "sha512" -> "sha512"
        else -> "sha1"
    }
    val hashLen = when (op) {
        "sha256" -> 32
        "sha512" -> 64
        else -> 20
    }
    val blocks = (keyBytes + hashLen - 1) / hashLen
    val out = ArrayList<Byte>()
    for (block in 1..blocks) {
        val blockInput = ByteArrayOutputStream()
        blockInput.write(salt)
        blockInput.write((block shr 24) and 0xFF)
        blockInput.write((block shr 16) and 0xFF)
        blockInput.write((block shr 8) and 0xFF)
        blockInput.write(block and 0xFF)
        var u = hmacBytes(op, password, blockInput.toByteArray())
        val t = u.copyOf()
        for (i in 1 until iterations) {
            u = hmacBytes(op, password, u)
            for (j in t.indices) {
                t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
        }
        for (b in t) out.add(b)
    }
    val result = ByteArray(out.size) { out[it] }
    val take = keyBytes.coerceIn(0, result.size)
    return result.copyOf(take)
}

fun wildcardContains(url: String, pattern: String): Boolean {
    if (pattern.isEmpty()) return false
    return Regex(wildcardRegex(pattern)).containsMatchIn(url)
}

fun parseHaikuoProxyRewrites(raw: String?): List<Pair<String, String>> {
    val out = ArrayList<Pair<String, String>>()
    val value = raw ?: ""
    if (!value.contains("=>")) return out
    for (line in value.split(Regex("\r?\n"))) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue
        val idx = trimmed.indexOf("=>")
        if (idx <= 0) continue
        val from = trimmed.substring(0, idx).trim()
        val to = trimmed.substring(idx + 2).trim()
        if (from.isEmpty() || to.isEmpty()) continue
        out.add(Pair(from, to))
    }
    return out
}

fun applyHaikuoProxyRewrites(url: String, rewrites: List<Pair<String, String>>): String {
    for ((from, to) in rewrites) {
        val idx = url.indexOf(from)
        if (idx >= 0) {
            return url.substring(0, idx) + to + url.substring(idx + from.length)
        }
    }
    return url
}

class HaikuoCookieJar {

    private val byHost = LinkedHashMap<String, LinkedHashMap<String, String>>()

    @Synchronized
    fun store(host: String, setCookieHeaders: Iterable<String>) {
        if (host.isEmpty()) return
        val jar = byHost.getOrPut(host) { LinkedHashMap() }
        for (header in setCookieHeaders) {
            val first = header.split(";").first().trim()
            val eq = first.indexOf('=')
            if (eq <= 0) continue
            val name = first.substring(0, eq).trim()
            val value = first.substring(eq + 1).trim()
            if (name.isEmpty()) continue
            jar[name] = value
        }
    }

    @Synchronized
    fun setCookie(host: String, cookie: String) {
        if (host.isEmpty() || cookie.trim().isEmpty()) return
        store(host, cookie.split(";").map { it.trim() })
    }

    @Synchronized
    fun headerFor(host: String): String {
        val jar = byHost[host]
        if (jar == null || jar.isEmpty()) return ""
        return jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    @Synchronized
    fun isEmpty(): Boolean {
        return byHost.values.all { it.isEmpty() }
    }
}

fun haikuoHexToBytes(hex: String): ByteArray {
    val clean = hex.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    val out = ByteArrayOutputStream()
    var i = 0
    while (i + 1 < clean.length) {
        out.write(clean.substring(i, i + 2).toInt(16))
        i += 2
    }
    return out.toByteArray()
}

fun haikuoHashHex(op: String, bytes: ByteArray): String {
    val algorithm = when (op) {
        "md5" -> "MD5"
        "sha1" -> "SHA-1"
        "sha256" -> "SHA-256"
        "sha512" -> "SHA-512"
        else -> return ""
    }
    val digest = MessageDigest.getInstance(algorithm).digest(bytes)
    return digest.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

fun haikuoHmacHex(op: String, key: ByteArray, message: ByteArray): String {
    if (op != "md5" && op != "sha1" && op != "sha256" && op != "sha512") return ""
    val result = hmacBytes(op, key, message)
    return result.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

private fun hmacBytes(op: String, key: ByteArray, message: ByteArray): ByteArray {
    val algorithm = when (op) {
        "md5" -> "MD5"
        "sha1" -> "SHA-1"
        "sha256" -> "SHA-256"
        "sha512" -> "SHA-512"
        else -> return ByteArray(0)
    }
    val blockSize = if (op == "sha512") 128 else 64
    var keyBytes = key
    if (keyBytes.size > blockSize) {
        keyBytes = MessageDigest.getInstance(algorithm).digest(keyBytes)
    }
    if (keyBytes.size < blockSize) {
        keyBytes = keyBytes.copyOf(blockSize)
    }
    val ipad = ByteArray(blockSize) { (keyBytes[it].toInt() xor 0x36).toByte() }
    val opad = ByteArray(blockSize) { (keyBytes[it].toInt() xor 0x5c).toByte() }
    val inner = MessageDigest.getInstance(algorithm)
    inner.update(ipad)
    inner.update(message)
    val innerHash = inner.digest()
    val outer = MessageDigest.getInstance(algorithm)
    outer.update(opad)
    outer.update(innerHash)
    return outer.digest()
}

fun normalizeRuleTitle(title: String): String {
    val builder = StringBuilder()
    var i = 0
    while (i < title.length) {
        val rune = title.codePointAt(i)
        i += Character.charCount(rune)
        if (rune == 0x1F51E) continue
        val folded = foldMathAlphanumeric(rune)
        if (folded != null) {
            builder.append(folded)
        } else {
            builder.appendCodePoint(rune)
        }
    }
    return builder.toString().trim().lowercase()
}

private fun foldMathAlphanumeric(rune: Int): String? {
    if (rune >= 0xFF21 && rune <= 0xFF3A) {
        return (0x41 + rune - 0xFF21).toChar().toString()
    }
    if (rune >= 0xFF41 && rune <= 0xFF5A) {
        return (0x61 + rune - 0xFF41).toChar().toString()
    }
    if (rune >= 0xFF10 && rune <= 0xFF19) {
        return (0x30 + rune - 0xFF10).toChar().toString()
    }
    val digitBases = listOf(0x1D7CE, 0x1D7D8, 0x1D7E2, 0x1D7EC, 0x1D7F6)
    for (base in digitBases) {
        if (rune >= base && rune < base + 10) {
            return (0x30 + rune - base).toChar().toString()
        }
    }
    val letterBases = listOf(
        Pair(0x1D400, 0x1D41A),
        Pair(0x1D434, 0x1D44E),
        Pair(0x1D468, 0x1D482),
        Pair(0x1D504, 0x1D51E),
        Pair(0x1D538, 0x1D552),
        Pair(0x1D5A0, 0x1D5BA),
        Pair(0x1D5D4, 0x1D5EE),
        Pair(0x1D608, 0x1D622),
        Pair(0x1D63C, 0x1D656),
        Pair(0x1D670, 0x1D68A),
        Pair(0x1D4D0, 0x1D4EA)
    )
    for ((upperBase, lowerBase) in letterBases) {
        if (rune >= upperBase && rune < upperBase + 26) {
            return (0x41 + rune - upperBase).toChar().toString()
        }
        if (rune >= lowerBase && rune < lowerBase + 26) {
            return (0x61 + rune - lowerBase).toChar().toString()
        }
    }
    if (rune == 0x1D49C) return "A"
    return null
}

fun matchRuleTitle(query: String, titles: Iterable<String>): String? {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return null
    for (title in titles) {
        if (title == trimmed) return title
    }
    val stripped = trimmed.replace("🔞", "").trim()
    for (title in titles) {
        if (title.replace("🔞", "").trim() == stripped) return title
    }
    val wanted = normalizeRuleTitle(trimmed)
    for (title in titles) {
        if (normalizeRuleTitle(title) == wanted) return title
    }
    return null
}

fun resolvePresetModule(
    requestBasename: String,
    ruleTitle: String,
    manifest: Map<String, List<String>>
): String? {
    val base = requestBasename.trim()
    if (base.isEmpty()) return null
    fun manifestKeyFor(title: String): String? {
        for (key in manifest.keys) {
            if (key == title) return key
        }
        return matchRuleTitle(title, manifest.keys)
    }
    val ruleKey = manifestKeyFor(ruleTitle)
    if (ruleKey != null) {
        val wanted = "${normalizeRuleTitle(ruleTitle)}.js"
        if (normalizeRuleTitle(base) == wanted) {
            val files = manifest[ruleKey] ?: emptyList()
            for (file in files) {
                if (file.contains("Apollo")) return file
            }
        }
    }
    for (files in manifest.values) {
        for (file in files) {
            val sep = file.indexOf("__")
            val suffix = if (sep >= 0) file.substring(sep + 2) else file
            if (suffix == base) return file
        }
    }
    for (files in manifest.values) {
        for (file in files) {
            if (file == base) return file
        }
    }
    val wantedNorm = normalizeRuleTitle(base)
    for (files in manifest.values) {
        for (file in files) {
            if (normalizeRuleTitle(file) == wantedNorm) return file
        }
    }
    return null
}

fun parseProxyServerId(path: String): String? {
    val prefix = "/__proxyserver/"
    if (!path.startsWith(prefix)) return null
    val rest = path.substring(prefix.length)
    if (rest.isEmpty()) return null
    val id = rest.split("/").first()
    return if (id.isEmpty()) null else id
}

fun pickSniffedVideoUrl(
    entries: List<String>,
    includeRules: List<String> = emptyList(),
    excludeRules: List<String> = emptyList()
): String {
    fun matches(url: String, pattern: String): Boolean {
        return Regex(wildcardRegex(pattern)).containsMatchIn(url)
    }
    fun isMedia(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".m3u8") ||
            lower.contains(".mp4") ||
            lower.contains(".flv") ||
            lower.contains(".mkv") ||
            lower.contains(".ts") ||
            lower.contains(".m4a") ||
            lower.contains(".mp3") ||
            lower.contains(".webm") ||
            lower.contains(".mov")
    }
    for (entry in entries) {
        val url = entry.trim()
        if (url.isEmpty() || !url.startsWith("http")) continue
        if (!isMedia(url)) continue
        if (excludeRules.any { matches(url, it) }) continue
        if (includeRules.isNotEmpty() && !includeRules.any { matches(url, it) }) continue
        return url
    }
    return ""
}

private fun wildcardRegex(pattern: String): String {
    val builder = StringBuilder()
    for (ch in pattern) {
        if (ch == '*') {
            builder.append(".*")
        } else {
            builder.append(Regex.escape(ch.toString()))
        }
    }
    return builder.toString()
}

fun haikuoSafeFileName(name: String): String? {
    val base = name.replace('\\', '/').split("/").last().trim()
    if (base.isEmpty() || base == "." || base == "..") return null
    return base
}

fun haikuoIsHikerFilesTarget(target: String): Boolean {
    return target.startsWith("hiker://files/")
}

fun haikuoShareFileKey(target: String): String {
    if (!haikuoIsHikerFilesTarget(target)) return ""
    var rest = target.substring("hiker://files/".length)
    val cut = rest.indexOfFirst { it == '?' || it == '#' }
    if (cut >= 0) rest = rest.substring(0, cut)
    return try {
        java.net.URLDecoder.decode(rest, "UTF-8")
    } catch (e: Exception) {
        rest
    }
}

fun sortHaikuoDownloadEntries(entries: List<Map<String, Any?>>): List<Map<String, Any?>> {
    val sorted = entries.toMutableList()
    sorted.sortWith { a, b ->
        val aTime = HaikuoJson.num(a["mtime"])?.toLong() ?: 0L
        val bTime = HaikuoJson.num(b["mtime"])?.toLong() ?: 0L
        if (aTime != bTime) {
            bTime.compareTo(aTime)
        } else {
            HaikuoJson.str(a["name"]).compareTo(HaikuoJson.str(b["name"]))
        }
    }
    return sorted
}

object HaikuoSessionVars {

    private val vars = LinkedHashMap<String, String>()

    @Synchronized
    fun snapshot(): Map<String, String> {
        return LinkedHashMap(vars)
    }

    @Synchronized
    fun apply(payload: Map<String, Any?>) {
        val op = HaikuoJson.str(payload["op"])
        if (op == "set") {
            vars[HaikuoJson.str(payload["key"])] = HaikuoJson.str(payload["value"])
        } else if (op == "clear") {
            vars.remove(HaikuoJson.str(payload["key"]))
        } else if (op == "clearAll") {
            vars.clear()
        }
    }
}

fun haikuoGzipBytes(bytes: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(bytes) }
    return out.toByteArray()
}

fun haikuoGunzipBytes(bytes: ByteArray): ByteArray {
    return GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
}

fun haikuoInflateBytes(bytes: ByteArray): ByteArray {
    return InflaterInputStream(ByteArrayInputStream(bytes), Inflater(true)).use { it.readBytes() }
}

class HaikuoSplitUa(
    val userAgent: String,
    val headers: Map<String, String>
)

fun haikuoSplitUserAgent(headers: Map<String, String>): HaikuoSplitUa {
    var ua = ""
    val rest = LinkedHashMap<String, String>()
    for ((key, value) in headers) {
        if (key.lowercase() == "user-agent") {
            if (ua.isEmpty()) ua = value
        } else {
            rest[key] = value
        }
    }
    return HaikuoSplitUa(ua, rest)
}

const val HAIKUO_CHECK_JS_PASS_SENTINEL = "__HK_PASS__"

fun haikuoBridgePadScript(pageCookies: String): String {
    val cookiesJson = HaikuoJson.stringify(pageCookies)
    return "window.__hkPageCookies=$cookiesJson;" +
        "window.__hkWriteFiles=window.__hkWriteFiles||[];" +
        "window._getUrls=function(){try{return performance.getEntriesByType(" +
        "\"resource\").map(function(e){return e.name;});}catch(e){return [];}};" +
        "window.fy_bridge_app={getUrls:window._getUrls," +
        "log:function(){}," +
        "getCookie:function(){return window.__hkPageCookies||\"\";}," +
        "writeFile:function(p,c){window.__hkWriteFiles.push({path:String(p||\"\")," +
        "content:String(c==null?\"\":c)});}," +
        "getUa:function(){return navigator.userAgent;}," +
        "setWebTitle:function(){}};" +
        "window.fba=window.fy_bridge_app;"
}

fun haikuoJsResultText(raw: String?): String {
    if (raw == null) return ""
    var text = raw.trim()
    if (text.isEmpty() || text == "null" || text == "undefined") return ""
    if (text.length >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
        try {
            val decoded = HaikuoJson.parse(text)
            if (decoded is String) text = decoded
        } catch (e: Exception) {
        }
    }
    return if (text == "null" || text == "undefined") "" else text
}

fun haikuoSlotHeight(aspectRatio: Double, maxWidth: Double, maxHeight: Double): Double {
    val ratio = if (aspectRatio.isFinite() && aspectRatio > 0) aspectRatio else 16.0 / 9.0
    return (maxWidth / ratio).coerceIn(0.0, maxHeight * 0.56)
}

class HaikuoX5RowLayout(
    val height: Double,
    val floatMode: Boolean,
    val topMode: Boolean,
    val videoMode: Boolean
)

fun haikuoX5RowLayout(
    desc: String,
    viewportHeight: Double,
    viewportWidth: Double,
    topOffset: Double
): HaikuoX5RowLayout {
    val vh = if (viewportHeight < 0) 0.0 else viewportHeight
    val vw = if (viewportWidth < 0) 0.0 else viewportWidth
    val top = if (topOffset < 0) 0.0 else topOffset
    var floatMode = false
    var topMode = false
    var videoMode = false
    var heightPx: Double? = null
    for (rawPart in desc.split("&&")) {
        val p = rawPart.trim()
        if (p.isEmpty() || p == "list") continue
        if (p == "float") {
            floatMode = true
            continue
        }
        if (p == "top") {
            topMode = true
            floatMode = true
            continue
        }
        if (p == "auto") {
            heightPx = 240.0
            continue
        }
        if (p == "video") {
            videoMode = true
            heightPx = null
            continue
        }
        if (p == "screen" || p == "100%") {
            heightPx = vh
            continue
        }
        val screenMinus = Regex("^screen-(\\d+)$").find(p)
        if (screenMinus != null) {
            heightPx = vh - (screenMinus.groupValues[1].toDoubleOrNull() ?: 0.0)
            continue
        }
        if (Regex("^\\d+$").matches(p)) {
            heightPx = p.toDouble()
            continue
        }
        if (Regex("^\\d+%$").matches(p)) {
            heightPx = vh * (p.substring(0, p.length - 1).toDouble() / 100)
            continue
        }
    }
    var height = when {
        videoMode -> vw * 9 / 16
        heightPx != null -> heightPx
        topMode -> vh - top
        else -> 240.0
    }
    if (height < 0) height = 0.0
    if (height > vh) height = vh
    return HaikuoX5RowLayout(height, floatMode, topMode, videoMode)
}

class HaikuoX5Intercept(
    val action: String,
    val code: String
)

fun haikuoParseX5Intercept(text: String): HaikuoX5Intercept {
    return try {
        val decoded = HaikuoJson.parse(text)
        if (decoded !is Map<*, *>) {
            return HaikuoX5Intercept("allow", "")
        }
        val action = HaikuoJson.str(decoded["d"])
        if (action == "block") return HaikuoX5Intercept("block", "")
        if (action == "exec") {
            val code = HaikuoJson.str(decoded["code"])
            if (code.isEmpty()) return HaikuoX5Intercept("allow", "")
            return HaikuoX5Intercept("exec", code)
        }
        HaikuoX5Intercept("allow", "")
    } catch (e: Exception) {
        HaikuoX5Intercept("allow", "")
    }
}

fun haikuoX5BridgeScript(): String {
    return """window.__hkX5Seq=window.__hkX5Seq||0;""" +
        """window.__hkX5Fetches=window.__hkX5Fetches||{};""" +
        """window.__hkX5Post=function(m,a,id){try{window.HaikuoX5Bridge.postMessage(JSON.stringify({method:m,args:a||[],id:(id===undefined?0:id)}));}catch(e){}};""" +
        """window.__hkX5ResolveFetch=function(id,res){try{var r=window.__hkX5Fetches[id];delete window.__hkX5Fetches[id];if(r)r(res);}catch(e){}};""" +
        """window.__hkX5Base64Encode=function(t){try{return btoa(unescape(encodeURIComponent(String(t==null?"":t))));}catch(e){return "";}};""" +
        """if(typeof window.base64Encode!=="function"){window.base64Encode=window.__hkX5Base64Encode;}""" +
        """window.${'$'}${'$'}${'$'}=function(sel,headers,titleArg){""" +
        """var url=(typeof sel==="string")?sel:"";""" +
        """var selectOptions=null;var selectCol;var selectTitle;""" +
        """if(Array.isArray(sel)){selectOptions=sel.map(function(v){return String(v);});if(typeof headers==="number")selectCol=headers;if(typeof titleArg==="string")selectTitle=titleArg;}""" +
        """function fnSrc(fn){return (typeof fn==="function")?String(fn):String(fn==null?"":fn);}""" +
        """function serArg(a){if(typeof a==="function")return fnSrc(a);if(a===undefined)return "undefined";try{var s=JSON.stringify(a);return (s===undefined)?JSON.stringify(String(a)):s;}catch(e){return JSON.stringify(String(a));}}""" +
        """function toStr(fn){var a=[];for(var i=1;i<arguments.length;i++)a.push(serArg(arguments[i]));return "("+fnSrc(fn)+")("+a.join(",")+")";}""" +
        """var chained={};""" +
        """chained.rule=function(fn){return url+"@rule=.js:("+fnSrc(fn)+")()";};""" +
        """chained.lazyRule=function(fn){var a=[];for(var i=1;i<arguments.length;i++)a.push(arguments[i]);if(!a.length)a.push(url);else if(a[0]===undefined)a[0]=url;return url+"@lazyRule="+toStr.apply(null,[fn].concat(a));};""" +
        """chained.image=function(fn,s1,s2){var a=[];if(s1!==undefined)a.push(serArg(s1));if(s2!==undefined)a.push(serArg(s2));if(!a.length)a.push("input");return url+"@headers="+JSON.stringify(headers||{})+"@js=("+fnSrc(fn)+")("+a.join(",")+")";};""" +
        """chained.x5Rule=function(fn){return "javascript:("+fnSrc(fn)+")()";};""" +
        """chained.input=function(fn){return "input://"+JSON.stringify({value:url,js:"("+fnSrc(fn)+")(input)"});};""" +
        """chained.confirm=function(fn){return "confirm://"+url+".js:("+fnSrc(fn)+")()";};""" +
        """chained.select=function(fn){var a=[];for(var i=1;i<arguments.length;i++)a.push(arguments[i]);var cfg={options:(selectOptions?selectOptions.slice():[url]),js:toStr.apply(null,[fn].concat(a))};if(selectTitle!==undefined)cfg.title=selectTitle;if(typeof selectCol==="number")cfg.col=selectCol;return "select://"+JSON.stringify(cfg);};""" +
        """chained.b64=function(){return chained;};""" +
        """return chained;};""" +
        """(function(){var b=window.fy_bridge_app||{};window.fy_bridge_app=b;window.fba=b;""" +
        """b.base64Encode=window.__hkX5Base64Encode;""" +
        """b.open=function(arg){var s=(typeof arg==="string")?arg:JSON.stringify(arg||{});window.__hkX5Post("open",[s]);};""" +
        """b.playVideo=function(u){window.__hkX5Post("playVideo",[String(u||"")]);};""" +
        """b.parseLazyRule=function(s){window.__hkX5Post("parseLazyRule",[String(s==null?"":s)]);};""" +
        """b.parseLazyRuleAsync=function(s){window.__hkX5Post("parseLazyRuleAsync",[String(s==null?"":s)]);};""" +
        """b.putVar=function(k,v){window.__hkX5Post("putVar",[String(k==null?"":k),String(v==null?"":v)]);};""" +
        """b.clearVar=function(k){window.__hkX5Post("clearVar",[(k===undefined||k===null)?null:String(k)]);};""" +
        """b.putMyVar=function(k,v){window.__hkX5Post("putMyVar",[String(k==null?"":k),String(v==null?"":v)]);};""" +
        """b.clearMyVar=function(k){window.__hkX5Post("clearMyVar",[(k===undefined||k===null)?null:String(k)]);};""" +
        """b.setWebUa=function(ua){window.__hkX5Post("setWebUa",[String(ua||"")]);};""" +
        """b.hideLoading=function(){window.__hkX5Post("hideLoading",[]);};""" +
        """b.refreshPage=function(){window.__hkX5Post("refreshPage",[]);};""" +
        """b.back=function(){window.__hkX5Post("back",[]);};""" +
        """b.toast=function(m){window.__hkX5Post("toast",[String(m==null?"":m)]);};""" +
        """b.fetch=function(u,o){return new Promise(function(resolve){var id=++window.__hkX5Seq;window.__hkX5Fetches[id]=resolve;window.__hkX5Post("fetch",[String(u||""),o||null],id);setTimeout(function(){if(window.__hkX5Fetches[id]){delete window.__hkX5Fetches[id];resolve("");}},60000);});};""" +
        """})();"""
}

const val HAIKUO_RESOURCE_ENTRIES_SCRIPT =
    "(function(){try{return performance.getEntriesByType(\"resource\")" +
        ".map(function(e){return e.name;});}catch(e){return [];}})()"

const val HAIKUO_PAUSE_PAGE_VIDEOS_SCRIPT =
    "try{document.querySelectorAll(\"video\").forEach(function(v){v.pause();});}catch(e){}"

fun haikuoFloatVideoGate(value: Any?): Boolean = value == true

const val HAIKUO_FLOAT_VIDEO_MAX_FAILURES = 10

fun haikuoFloatVideoShouldPoll(
    floatVideo: Boolean,
    visible: Boolean,
    hasVideo: Boolean,
    dismissed: Boolean,
    consecutiveFailures: Int
): Boolean {
    return floatVideo &&
        visible &&
        !hasVideo &&
        !dismissed &&
        consecutiveFailures < HAIKUO_FLOAT_VIDEO_MAX_FAILURES
}

fun haikuoFloatVideoPick(entries: List<String>, blockRules: List<String>): String {
    return pickSniffedVideoUrl(entries, excludeRules = blockRules)
}
