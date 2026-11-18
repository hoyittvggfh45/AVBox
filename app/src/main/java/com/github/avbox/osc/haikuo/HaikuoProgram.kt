package com.github.avbox.osc.haikuo

import android.util.Base64
import java.net.URI
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class HaikuoKind {
    script,
    rule,
    link
}

fun haikuoKindLabel(kind: HaikuoKind): String {
    return when (kind) {
        HaikuoKind.script -> "脚本"
        HaikuoKind.rule -> "规则"
        HaikuoKind.link -> "链接"
    }
}

fun haikuoDataIconBytes(iconValue: String): ByteArray? {
    val text = iconValue.trim()
    if (!text.startsWith("data:image/")) return null
    val comma = text.indexOf(',')
    if (comma < 0) return null
    if (!text.substring(0, comma).contains(";base64")) return null
    return try {
        val bytes = Base64.decode(text.substring(comma + 1).trim(), Base64.DEFAULT)
        if (bytes.isEmpty()) null else bytes
    } catch (e: Exception) {
        null
    }
}

fun haikuoFaviconUrl(program: HaikuoProgram): String? {
    for (candidate in listOf(program.host, program.url)) {
        val origin = haikuoOriginOf(candidate)
        if (origin != null) return "$origin/favicon.ico"
    }
    return null
}

fun haikuoSiteUrl(program: HaikuoProgram): String? {
    return haikuoSiteCandidate(program.host) ?: haikuoSiteCandidate(program.url)
}

private fun haikuoSiteCandidate(value: String): String? {
    val uri = haikuoHttpUri(value) ?: return null
    return uri.toString()
}

private fun haikuoOriginOf(value: String): String? {
    val uri = haikuoHttpUri(value) ?: return null
    val scheme = uri.scheme
    val host = uri.host
    val port = uri.port
    val defaultPort = if (scheme == "https") 443 else 80
    return if (port != -1 && port != defaultPort) {
        "$scheme://$host:$port"
    } else {
        "$scheme://$host"
    }
}

private fun haikuoHttpUri(value: String): URI? {
    val text = value.trim()
    if (text.isEmpty()) return null
    val embedded = Regex("https?://[^\\s#]+").find(text)?.value
    val raw = embedded ?: text
    var uri = try {
        URI(raw)
    } catch (e: Exception) {
        null
    }
    if (uri == null) return null
    if (uri.scheme.isNullOrEmpty() && !raw.contains("://")) {
        uri = try {
            URI("https://$raw")
        } catch (e: Exception) {
            null
        }
    }
    if (uri == null) return null
    if (uri.scheme != "http" && uri.scheme != "https") return null
    if (uri.host.isNullOrEmpty()) return null
    return uri
}

class HaikuoProgram(
    val id: String,
    val name: String,
    val author: String = "",
    val version: String = "",
    val icon: String = "",
    val description: String = "",
    val script: String = "",
    val url: String = "",
    val host: String = "",
    val kind: HaikuoKind = HaikuoKind.script,
    val origin: String = "",
    val rawJson: String = "",
    val entries: Int = 0,
    val importedAtMillis: Long = 0L,
    val group: String = "",
    val ruleType: String = ""
) {

    val runnable: Boolean
        get() = kind == HaikuoKind.script && script.trim().isNotEmpty()

    val subtitle: String
        get() {
            val parts = ArrayList<String>()
            if (group.isNotEmpty()) parts.add(group)
            if (author.isNotEmpty()) parts.add(author)
            if (version.isNotEmpty()) parts.add("v$version")
            if (!runnable && entries > 0) parts.add("$entries 条")
            parts.add(haikuoKindLabel(kind))
            return parts.joinToString(" · ")
        }

    fun toJsonMap(): Map<String, Any?> {
        val map = LinkedHashMap<String, Any?>()
        map["id"] = id
        map["name"] = name
        map["author"] = author
        map["version"] = version
        map["icon"] = icon
        map["description"] = description
        map["script"] = script
        map["url"] = url
        map["host"] = host
        map["kind"] = kind.name
        map["origin"] = origin
        map["raw"] = rawJson
        map["entries"] = entries
        map["importedAt"] = formatIso(importedAtMillis)
        map["group"] = group
        map["ruleType"] = ruleType
        return map
    }

    fun copyWith(
        id: String? = null,
        name: String? = null,
        origin: String? = null,
        kind: HaikuoKind? = null,
        entries: Int? = null
    ): HaikuoProgram {
        return HaikuoProgram(
            id = id ?: this.id,
            name = name ?: this.name,
            author = author,
            version = version,
            icon = icon,
            description = description,
            script = script,
            url = url,
            host = host,
            kind = kind ?: this.kind,
            origin = origin ?: this.origin,
            rawJson = rawJson,
            entries = entries ?: this.entries,
            importedAtMillis = if (importedAtMillis == 0L) System.currentTimeMillis() else importedAtMillis,
            group = group,
            ruleType = ruleType
        )
    }

    companion object {

        fun fromJson(json: Map<String, Any?>): HaikuoProgram {
            val kindName = HaikuoJson.str(json["kind"])
            val kind = HaikuoKind.entries.firstOrNull { it.name == kindName } ?: HaikuoKind.script
            val rawName = json["name"]
            return HaikuoProgram(
                id = HaikuoJson.str(json["id"]),
                name = if (rawName == null) "未命名小程序" else HaikuoJson.str(rawName),
                author = HaikuoJson.str(json["author"]),
                version = HaikuoJson.str(json["version"]),
                icon = HaikuoJson.str(json["icon"]),
                description = HaikuoJson.str(json["description"]),
                script = HaikuoJson.str(json["script"]),
                url = HaikuoJson.str(json["url"]),
                host = HaikuoJson.str(json["host"]),
                kind = kind,
                origin = HaikuoJson.str(json["origin"]),
                rawJson = HaikuoJson.str(json["raw"]),
                entries = HaikuoJson.num(json["entries"])?.toInt() ?: 0,
                importedAtMillis = parseIso(HaikuoJson.str(json["importedAt"])),
                group = HaikuoJson.str(json["group"]),
                ruleType = HaikuoJson.str(json["ruleType"])
            )
        }

        fun makeId(name: String, body: String): String {
            val digest = MessageDigest.getInstance("SHA-1")
            val bytes = digest.digest("$name\u0000$body".toByteArray(Charsets.UTF_8))
            val hex = bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
            return hex.substring(0, 16)
        }

        fun formatIso(millis: Long): String {
            val value = if (millis == 0L) System.currentTimeMillis() else millis
            return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date(value))
        }

        fun parseIso(text: String): Long {
            if (text.isEmpty()) return 0L
            val cleaned = text.trim().removeSuffix("Z")
            val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss")
            for (pattern in patterns) {
                try {
                    val parsed = SimpleDateFormat(pattern, Locale.US).parse(cleaned)
                    if (parsed != null) return parsed.time
                } catch (e: Exception) {
                }
            }
            return 0L
        }
    }
}

class HaikuoImportResult(
    val added: List<HaikuoProgram>,
    val updated: List<HaikuoProgram>,
    val notes: List<String> = emptyList(),
    val cancelled: Boolean = false
) {

    val total: Int
        get() = added.size + updated.size

    val summary: String
        get() {
            if (cancelled) return "已取消导入"
            if (total == 0) return "没有新增条目"
            val parts = ArrayList<String>()
            if (added.isNotEmpty()) parts.add("新增 ${added.size} 个")
            if (updated.isNotEmpty()) parts.add("更新 ${updated.size} 个")
            return parts.joinToString("，")
        }
}

class HaikuoDuplicate(
    val incoming: HaikuoProgram,
    val existing: HaikuoProgram
)

enum class HaikuoDuplicateDecision {
    replace,
    keepBoth,
    cancel
}

class HaikuoFormatException(message: String) : Exception(message)
