package com.github.avbox.osc.haikuo

import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

object HaikuoCodec {

    private val wrapperKeys = listOf(
    "data",
    "list",
    "items",
    "apps",
    "programs",
    "programmes",
    "miniapps",
    "miniApps",
    "miniprograms",
    "sites",
    "rules",
    "result",
    "results",
    "payload",
    "body",
    "小程序",
    "海阔小程序",
    "海阔",
    "appsList",
    "appList",
    "sourceList",

    )

    private val urlPattern = Regex(
    """https?://[^\s，。、"',<>（）()\[\]]+""",
    RegexOption.IGNORE_CASE
    )

    private val cloudCodePattern = Regex(
    """云\s*(\d+)\s*oooole\s*/\s*([A-Za-z0-9_.\-/@]+)""",
    RegexOption.IGNORE_CASE
    )

    fun parseText(
    text: String,
    origin: String = "",
    dropped: MutableList<String>? = null
    ): List<HaikuoProgram> {
        val trimmed = stripBom(text.trim())
        if (trimmed.isEmpty()) {
            throw HaikuoFormatException("内容为空，没有可导入的小程序")
        }
        val bytes = trimmed.toByteArray(Charsets.UTF_8)
        return parseBytes(bytes, origin, dropped = dropped)
    }

    fun parseBytes(
    bytes: ByteArray,
    origin: String = "",
    depth: Int = 0,
    dropped: MutableList<String>? = null
    ): List<HaikuoProgram> {
        if (bytes.isEmpty()) {
            throw HaikuoFormatException("内容为空，没有可导入的小程序")
        }
        if (depth > 4) {
            throw HaikuoFormatException("小程序包嵌套层级过深，已停止解析")
        }
        if (bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
            val programs = ArrayList<HaikuoProgram>()
            val entries = readZipEntries(bytes)
            for ((entryName, content) in entries) {
                val name = entryName.lowercase()
                if (content.isEmpty()) continue
                if (name.endsWith(".db")) {
                    dropped?.add("跳过数据库条目 $entryName：请单独导入.db 文件")
                    continue
                }
                if (!(name.endsWith(".json") || name.endsWith(".txt") || name.endsWith(".js") || name.endsWith(".zip"))) {
                    continue
                }
                try {
                    val parsed = parseBytes(content, origin, depth + 1, dropped)
                    for (item in parsed) {
                        programs.add(item.copyWith(origin = origin))
                    }
                } catch (error: HaikuoFormatException) {
                    dropped?.add("跳过损坏条目 $entryName：${error.message}")
                    continue
                }
            }
            if (programs.isEmpty()) {
                throw HaikuoFormatException("压缩包里没有找到小程序 JSON")
            }
            return programs
        }
        if (bytes.size > 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()) {
            val raw = GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes()}
            return parseBytes(raw, origin, depth + 1, dropped)
        }
        val text = decodeText(bytes).trim()
        val head = if (text.isEmpty()) 0 else text[0].code
        if (head == 0x7B || head == 0x5B) {
            return parseJsonText(text, origin)
        }
        val decoded = tryBase64(text)
        if (decoded!= null && decoded.isNotEmpty()) {
            try {
                return parseBytes(decoded, origin, depth + 1, dropped)
            } catch (e: HaikuoFormatException) {
            }
        }
        throw HaikuoFormatException("没认出这个格式，海阔小程序需要 JSON、压缩包或有效口令")
    }

    fun zipDbEntries(bytes: ByteArray, depth: Int = 0): List<Pair<String, ByteArray>> {
        val out = ArrayList<Pair<String, ByteArray>>()
        if (depth > 4) return out
        if (bytes.size <= 4 || bytes[0]!= 0x50.toByte() || bytes[1]!= 0x4B.toByte()) {
            return out
        }
        try {
            val entries = readZipEntries(bytes)
            for ((entryName, content) in entries) {
                if (content.isEmpty()) continue
                val name = entryName.lowercase()
                if (name.endsWith(".db")) {
                    out.add(Pair(entryName, content))
                } else if (name.endsWith(".zip")) {
                    out.addAll(zipDbEntries(content, depth + 1))
                }
            }
        } catch (e: Exception) {
        }
        return out
    }

    fun decodeText(bytes: ByteArray): String {
        val decoded = try {
            Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            try {
                String(bytes, Charset.forName("GBK"))
            } catch (e2: Exception) {
                String(bytes, Charsets.ISO_8859_1)
            }
        }
        return stripBom(decoded)
    }

    fun tryBase64(text: String): ByteArray? {
        var value = text.trim()
        if (value.isEmpty()) return null
        if (value.startsWith("data:") && value.contains(",")) {
            value = value.substring(value.indexOf(',') + 1)
        }
        value = value.replace(Regex("\\s"), "")
        if (value.length < 8) return null
        if (!Regex("^[A-Za-z0-9+/\\-_]+=*$").matches(value)) return null
        var normalized = value.replace('-', '+').replace('_', '/')
        val remainder = normalized.length % 4
        if (remainder == 2) {
            normalized += "=="
        } else if (remainder == 3) {
            normalized += "="
        } else if (remainder!= 0) {
            return null
        }
        return try {
            Base64.decode(normalized, Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    fun parseJsonText(text: String, origin: String = ""): List<HaikuoProgram> {
        val cleaned = stripBom(text.trim())
        val decoded: Any?
        try {
            decoded = HaikuoJson.parseOrThrow(cleaned)
        } catch (e: Exception) {
            try {
                decoded = HaikuoJson.parseOrThrow(lenientJson(cleaned))
            } catch (e2: Exception) {
                throw HaikuoFormatException("JSON 格式有误，检查一下括号和引号")
            }
        }
        val programs = parseDecoded(decoded, origin, cleaned)
        if (programs.isEmpty()) {
            throw HaikuoFormatException("JSON 里没有识别到海阔小程序条目")
        }
        return programs
    }

    fun parseDecoded(
    decoded: Any?,
    origin: String = "",
    rawText: String = "",
    depth: Int = 0
    ): List<HaikuoProgram> {
        val out = ArrayList<HaikuoProgram>()
        collect(decoded, out, origin, rawText, depth)
        return out
    }

    fun extractCandidates(raw: String): List<String> {
        val text = raw.trim()
        val candidates = ArrayList<String>()
        fun add(value: String) {
            var v = value.trim()
            while (v.endsWith("￥") || v.endsWith("|")) {
                v = v.substring(0, v.length - 1).trim()
            }
            if (v.isEmpty()) return
            if (!candidates.contains(v)) candidates.add(v)
        }
        add(text)
        if (text.lowercase().startsWith("rule://")) {
            val decoded = tryBase64(text.substring("rule://".length))
            if (decoded!= null && decoded.isNotEmpty()) {
                for (nested in extractCandidates(decodeText(decoded))) {
                    add(nested)
                }
            }
        }
        if (text.contains("base64://@")) {
            val parts = text.split("@")
            for (part in parts) {
                val trimmedPart = part.trim().replace("base64://", "")
                if (trimmedPart.length > 24) {
                    val decoded = tryBase64(trimmedPart)
                    if (decoded!= null && decoded.isNotEmpty()) {
                        val inner = decodeText(decoded).trim()
                        if (looksLikeJson(inner)) add(inner)
                    }
                }
            }
        }
        if (text.contains("￥")) {
            val segments = text.split("￥")
            if (segments.size >= 3) {
                add(segments.subList(2, segments.size).joinToString("￥"))
                for (segment in segments.subList(2, segments.size)) {
                    add(segment)
                }
            }
            for (segment in segments) {
                add(segment)
            }
        }
        for (match in urlPattern.findAll(text)) {
            add(match.value)
        }
        val deepLink = Regex(
        """(?:hiker|haikuo|haik|海阔)://[^\s，。、"',<>（）]+""",
        RegexOption.IGNORE_CASE
        )
        for (match in deepLink.findAll(text)) {
            val link = match.value
            add(link)
            try {
                val uri = java.net.URI(link)
                val query = uri.query?: ""
                val queryParams = LinkedHashMap<String, String>()
                for (pair in query.split("&")) {
                    val eq = pair.indexOf('=')
                    if (eq > 0) {
                        val key = java.net.URLDecoder.decode(pair.substring(0, eq), "UTF-8")
                        val value = java.net.URLDecoder.decode(pair.substring(eq + 1), "UTF-8")
                        queryParams[key] = value
                    }
                }
                for (key in listOf("url", "u", "code", "data", "json", "src")) {
                    val value = queryParams[key]
                    if (value!= null && value.isNotEmpty()) add(value)
                }
                val segments = (uri.path?: "").split("/").filter { it.isNotEmpty()}
                for (segment in segments) {
                    if (segment.length > 12) add(segment)
                }
            } catch (e: Exception) {
            }
        }
        for (separator in listOf("：", ":", "】", "]", ">")) {
            val index = text.indexOf(separator)
            if (index > 0 && index < text.length - 8) {
                add(text.substring(index + 1))
            }
        }
        return candidates
    }

    fun cloudCodeMatches(text: String): List<MatchResult> {
        return cloudCodePattern.findAll(text).toList()
    }

    fun cloudApiUrl(text: String): String? {
        val match = cloudCodePattern.find(text)?: return null
        val cloud = match.groupValues[1].toIntOrNull()?: 0
        val tail = match.groupValues[2]
        val code = tail.split("/").lastOrNull { it.isNotEmpty()}?: ""
        if (code.isEmpty()) return null
        if (cloud == 6) {
            return "https://pasteme.tyrantg.com/api/getContent/$code"
        }
        throw HaikuoFormatException(
        "云剪贴板$cloud 的取回地址未公开（官方导入器闭源），暂不能直接解析；" +

        )
    }

    fun parseCloudApiResponse(body: String): String {
        val decoded: Any?
        try {
            decoded = HaikuoJson.parseOrThrow(body)
        } catch (e: Exception) {
            throw HaikuoFormatException("云剪贴板返回的内容无法解析")
        }
        if (decoded!is Map<*, *>) {
            throw HaikuoFormatException("云剪贴板返回的内容无法解析")
        }
        if (HaikuoJson.num(decoded["return_code"])?.toInt()!= 0) {
            if (HaikuoJson.num(decoded["return_code"])?.toInt() == 4001) {
                throw HaikuoFormatException(
                "云剪贴板 6 密码不对：这条分享带密码，取回时须带上 @密码，" +

                )
            }
            throw HaikuoFormatException("云剪贴板没找到这条内容，可能已过期或被删除")
        }
        val data = decoded["data"]
        if (data!is String || data.trim().isEmpty()) {
            throw HaikuoFormatException("云剪贴板没找到这条内容，可能已过期或被删除")
        }
        return data
    }

    fun buildShareCode(program: HaikuoProgram): String {
        val payload = Base64.encodeToString(
        shareJson(program).toByteArray(Charsets.UTF_8),
        Base64.NO_WRAP
        )
        return "海阔视界规则分享，当前分享的是：小程序￥home_rule￥$payload￥"
    }

    fun shareJson(program: HaikuoProgram): String {
        val raw = program.rawJson.trim()
        if (raw.isNotEmpty()) {
            try {
                val decoded = HaikuoJson.parseOrThrow(raw)
                if (decoded is Map<*, *>) return raw
            } catch (e: Exception) {
            }
        }
        val map = LinkedHashMap<String, Any?>()
        map["title"] = program.name
        if (program.author.isNotEmpty()) map["author"] = program.author
        if (program.version.isNotEmpty()) map["version"] = program.version
        if (program.url.isNotEmpty()) map["url"] = program.url
        if (program.script.isNotEmpty()) map["script"] = program.script
        return HaikuoJson.stringify(map)
    }

    fun looksLikeJson(text: String): Boolean {
        val trimmed = text.trim()
        return trimmed.startsWith("{") || trimmed.startsWith("[")
    }

    private fun stripBom(text: String): String {
        var result = text
        while (result.startsWith("\uFEFF")) {
            result = result.substring(1)
        }
        return result
    }

    private fun readZipEntries(bytes: ByteArray): List<Pair<String, ByteArray>> {
        val out = ArrayList<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry!= null) {
                if (!entry.isDirectory) {
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    var read = zip.read(chunk)
                    while (read >= 0) {
                        buffer.write(chunk, 0, read)
                        read = zip.read(chunk)
                    }
                    out.add(Pair(entry.name, buffer.toByteArray()))
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return out
    }

    private fun lenientJson(text: String): String {
        val out = StringBuilder()
        var i = 0
        var inString = false
        var escaped = false
        while (i < text.length) {
            val ch = text[i]
            if (inString) {
                out.append(ch)
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                i++
                continue
            }
            if (ch == '"') {
                inString = true
                out.append(ch)
                i++
                continue
            }
            if (ch == '/' && i + 1 < text.length && text[i + 1] == '/') {
                while (i < text.length && text[i]!= '\n') {
                    i++
                }
                continue
            }
            if (ch == '/' && i + 1 < text.length && text[i + 1] == '*') {
                val end = text.indexOf("*/", i + 2)
                if (end < 0) break
                i = end + 2
                continue
            }
            if (ch == ',') {
                var j = i + 1
                while (j < text.length && text[j].toString().trim().isEmpty()) {
                    j++
                }
                if (j < text.length && (text[j] == '}' || text[j] == ']')) {
                    i++
                    continue
                }
                out.append(ch)
                i++
                continue
            }
            out.append(ch)
            i++
        }
        return out.toString()
    }

    private fun collect(
    node: Any?,
    out: MutableList<HaikuoProgram>,
    origin: String,
    rawText: String,
    depth: Int
    ) {
        if (depth > 6) return
        if (node is String) {
            val trimmed = stripBom(node.trim())
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    collect(HaikuoJson.parseOrThrow(trimmed), out, origin, trimmed, depth + 1)
                } catch (e: Exception) {
                }
            }
            return
        }
        if (node is List<*>) {
            for (item in node) {
                collect(item, out, origin, rawText, depth + 1)
            }
            return
        }
        if (node!is Map<*, *>) return
        val map = LinkedHashMap<String, Any?>()
        for ((key, value) in node) {
            map[key.toString()] = value
        }
        val name = firstText(
        map,
        listOf("name", "title", "名称", "appName", "nickName", "nickname", "appname")
        )
        val script = firstText(
        map,
        listOf(
        "script", "find_rule", "findRule", "rule", "code", "js", "javascript",
        "sourceCode", "scriptCode", "searchFind", "chapter_find", "movie_find",
        "main", "content", "body", "src"
        ),
        minLength = 6
        )
        val url = firstText(
        map,
        listOf("url", "link", "address", "entry", "entryUrl", "homepage", "网址", "api")
        )
        val host = firstText(
        map,
        listOf("host", "homeUrl", "home_url", "baseUrl", "base_url", "siteUrl", "domain")
        )
        val entries = countEntries(map)
        if (name!= null && script!= null) {
            val icon = firstText(map, listOf("icon", "img", "logo", "cover", "pic"))
            val author = firstText(map, listOf("author", "user", "作者", "nick"))?: ""
            val version = firstText(map, listOf("version", "ver", "版本"))?: ""
            val description = firstText(
            map,
            listOf("desc", "description", "intro", "简介", "remark")
            )?: ""
            val group = firstText(map, listOf("group", "group_lpcolumn", "分组"))?: ""
            val ruleType = firstText(map, listOf("type", "类型"))?: ""
            val isRule = map.containsKey("find_rule") ||
            map.containsKey("findRule") ||
            map.containsKey("col_type") ||
            map.containsKey("pages") ||
            map.containsKey("searchFind")
            val raw = HaikuoJson.stringify(map)
            out.add(
            HaikuoProgram(
            id = HaikuoProgram.makeId(name, raw),
            name = name,
            author = author,
            version = version,
            icon = icon?: "",
            description = description,
            script = script,
            url = url?: "",
            host = host?: "",
            kind = if (isRule) HaikuoKind.rule else HaikuoKind.script,
            origin = origin,
            rawJson = raw,
            entries = entries,
            importedAtMillis = System.currentTimeMillis(),
            group = group,
            ruleType = ruleType
            )
            )
            return
        }
        val looksLikeRule = url!= null ||
        map.containsKey("find_rule") ||
        map.containsKey("findRule") ||
        map.containsKey("col_type") ||
        map.containsKey("pages") ||
        map.containsKey("searchFind")
        if (name!= null && looksLikeRule) {
            val icon = firstText(map, listOf("icon", "img", "logo", "cover", "pic"))
            val author = firstText(map, listOf("author", "user", "作者", "nick"))?: ""
            val description = firstText(
            map,
            listOf("desc", "description", "intro", "简介", "remark")
            )?: ""
            val group = firstText(map, listOf("group", "group_lpcolumn", "分组"))?: ""
            val ruleType = firstText(map, listOf("type", "类型"))?: ""
            val body = url?: HaikuoJson.stringify(map)
            out.add(
            HaikuoProgram(
            id = HaikuoProgram.makeId(name, body),
            name = name,
            author = author,
            icon = icon?: "",
            description = description,
            url = url?: "",
            host = host?: "",
            kind = if (url!= null && entries == 0) HaikuoKind.link else HaikuoKind.rule,
            origin = origin,
            rawJson = HaikuoJson.stringify(map),
            entries = entries,
            importedAtMillis = System.currentTimeMillis(),
            group = group,
            ruleType = ruleType
            )
            )
            return
        }
        for (key in wrapperKeys) {
            val child = map[key]
            if (child == null) continue
            collect(child, out, origin, rawText, depth + 1)
        }
    }

    private fun countEntries(map: Map<String, Any?>): Int {
        for (key in listOf("data", "list", "items", "sites", "rules", "sources")) {
            val value = map[key]
            if (value is List<*> && value.isNotEmpty()) return value.size
        }
        val pages = map["pages"]
        if (pages is List<*> && pages.isNotEmpty()) return pages.size
        if (pages is String && pages.trim().startsWith("[")) {
            try {
                val decoded = HaikuoJson.parseOrThrow(pages)
                if (decoded is List<*>) return decoded.size
            } catch (e: Exception) {
            }
        }
        return 0
    }

    private fun firstText(
    map: Map<String, Any?>,
    keys: List<String>,
    minLength: Int = 1
    ): String? {
        for (key in keys) {
            val value = map[key]
            if (value is String) {
                val trimmed = value.trim()
                if (trimmed.length >= minLength) return trimmed
            } else if (value is Number) {
                val text = value.toString()
                if (text.length >= minLength) return text
            }
        }
        return null
    }
}
