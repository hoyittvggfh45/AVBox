package com.github.avbox.osc.miniapp

import com.github.avbox.osc.haikuo.HaikuoJson
import java.io.File

val MINI_APP_ID_PATTERN = Regex("^[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+$")

class MiniAppManifest(
    val appId: String,
    val name: String,
    val version: String = "",
    val versionCode: Int = 0,
    val entry: String = "index.html",
    val renderer: String = "webview",
    val description: String = "",
    val permissions: List<String> = emptyList(),
    val networkAllowlist: List<String> = emptyList(),
    val pythonEntry: String? = null
) {
    val isValid: Boolean
        get() = MINI_APP_ID_PATTERN.matches(appId) && name.isNotEmpty()

    fun hasPermission(permission: String): Boolean = permissions.contains(permission)

    fun toJsonMap(): Map<String, Any?> {
        val map = linkedMapOf<String, Any?>(
            "appId" to appId,
            "name" to name,
            "version" to version,
            "versionCode" to versionCode,
            "entry" to entry,
            "renderer" to renderer,
            "description" to description,
            "permissions" to permissions,
            "network" to mapOf("allowlist" to networkAllowlist)
        )
        if (pythonEntry != null) map["python"] = mapOf("entry" to pythonEntry)
        return map
    }

    companion object {
        fun fromJson(json: Map<String, Any?>): MiniAppManifest {
            val permissions = mutableListOf<String>()
            val rawPermissions = json["permissions"]
            if (rawPermissions is List<*>) {
                for (item in rawPermissions) permissions.add(item.toString())
            }
            val allowlist = mutableListOf<String>()
            val network = json["network"]
            if (network is Map<*, *>) {
                val rawAllowlist = network["allowlist"]
                if (rawAllowlist is List<*>) {
                    for (item in rawAllowlist) allowlist.add(item.toString())
                }
            }
            var pythonEntry: String? = null
            val python = json["python"]
            if (python is Map<*, *> && python["entry"] is String) {
                pythonEntry = python["entry"] as String
            }
            return MiniAppManifest(
                appId = HaikuoJson.str(json["appId"]),
                name = HaikuoJson.str(json["name"]),
                version = HaikuoJson.str(json["version"]),
                versionCode = HaikuoJson.num(json["versionCode"])?.toInt() ?: 0,
                entry = (json["entry"] as? String) ?: "index.html",
                renderer = (json["renderer"] as? String) ?: "webview",
                description = HaikuoJson.str(json["description"]),
                permissions = permissions,
                networkAllowlist = allowlist,
                pythonEntry = pythonEntry
            )
        }
    }
}

class MiniAppMarketItem(
    val appId: String,
    val name: String,
    val version: String = "",
    val versionCode: Int = 0,
    val url: String,
    val md5: String? = null,
    val size: Int? = null,
    val author: String = "",
    val description: String = ""
) {
    val isValid: Boolean
        get() = appId.isNotEmpty() && name.isNotEmpty() && url.isNotEmpty()

    companion object {
        fun fromJson(json: Map<String, Any?>): MiniAppMarketItem {
            return MiniAppMarketItem(
                appId = HaikuoJson.str(json["appId"]),
                name = HaikuoJson.str(json["name"]),
                version = HaikuoJson.str(json["version"]),
                versionCode = HaikuoJson.num(json["versionCode"])?.toInt() ?: 0,
                url = HaikuoJson.str(json["url"]),
                md5 = (json["md5"] as? String)?.lowercase(),
                size = HaikuoJson.num(json["size"])?.toInt(),
                author = HaikuoJson.str(json["author"]),
                description = HaikuoJson.str(json["description"])
            )
        }
    }
}

fun parseMiniAppMarketJson(raw: String): List<MiniAppMarketItem> {
    val decoded = HaikuoJson.parse(raw) ?: return emptyList()
    val map = HaikuoJson.asMap(decoded) ?: return emptyList()
    val apps = map["apps"] as? List<*> ?: return emptyList()
    val items = mutableListOf<MiniAppMarketItem>()
    for (entry in apps) {
        val entryMap = HaikuoJson.asMap(entry) ?: continue
        val item = MiniAppMarketItem.fromJson(entryMap)
        if (item.isValid) items.add(item)
    }
    return items
}

fun miniAppSafeJoinPath(base: String, relative: String): String? {
    val normalized = File(base, relative).normalize().absolutePath
    val baseNormalized = File(base).normalize().absolutePath
    if (normalized == baseNormalized) return normalized
    if (!normalized.startsWith(baseNormalized + File.separator)) return null
    return normalized
}

fun miniAppIsSafeZipEntryName(name: String): Boolean {
    if (name.isEmpty()) return false
    if (name.startsWith("/") || name.startsWith("\\")) return false
    if (name.contains(":")) return false
    val parts = name.replace("\\", "/").split("/")
    for (part in parts) {
        if (part == "..") return false
    }
    return true
}
