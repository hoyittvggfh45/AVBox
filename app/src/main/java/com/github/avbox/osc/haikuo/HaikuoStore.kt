package com.github.avbox.osc.haikuo

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.google.gson.GsonBuilder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

class HaikuoStore(
    private val appContext: Context,
    private val rootOverride: File? = null,
    private val httpClient: OkHttpClient? = null,
    private val dbReader: ((ByteArray) -> List<Map<String, Any?>>)? = null
) {

    fun rootDir(): File {
        val override = rootOverride
        if (override != null) {
            if (!override.exists()) override.mkdirs()
            return override
        }
        val dir = File(appContext.filesDir, "haikuo")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun programsFile(): File {
        return File(rootDir(), FILE_NAME)
    }

    private fun collectionsFile(): File {
        return File(rootDir(), COLLECTIONS_FILE_NAME)
    }

    fun list(): List<HaikuoProgram> {
        val file = programsFile()
        if (!file.exists()) return emptyList()
        try {
            val decoded = HaikuoJson.parse(file.readText(Charsets.UTF_8))
            val rawList = if (decoded is Map<*, *>) decoded["programs"] else decoded
            if (rawList !is List<*>) return emptyList()
            val programs = ArrayList<HaikuoProgram>()
            for (item in rawList) {
                if (item is Map<*, *>) {
                    val map = LinkedHashMap<String, Any?>()
                    for ((key, value) in item) {
                        map[key.toString()] = value
                    }
                    programs.add(HaikuoProgram.fromJson(map))
                }
            }
            programs.sortByDescending { it.importedAtMillis }
            return programs
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun writeAll(programs: List<HaikuoProgram>) {
        val file = programsFile()
        val payload = LinkedHashMap<String, Any?>()
        payload["version"] = 1
        payload["updatedAt"] = isoNow()
        payload["programs"] = programs.map { it.toJsonMap() }
        val temp = File("${file.path}.tmp")
        temp.writeText(HaikuoJson.stringify(payload), Charsets.UTF_8)
        if (file.exists()) file.delete()
        temp.renameTo(file)
    }

    fun addAll(
        incoming: List<HaikuoProgram>,
        origin: String = "",
        notes: List<String> = emptyList(),
        onDuplicates: ((List<HaikuoDuplicate>) -> HaikuoDuplicateDecision)? = null
    ): HaikuoImportResult {
        val existing = list()
        val byId = LinkedHashMap<String, HaikuoProgram>()
        for (program in existing) {
            byId[program.id] = program
        }
        val byName = LinkedHashMap<String, HaikuoProgram>()
        for (program in existing) {
            if (!byName.containsKey(program.name)) {
                byName[program.name] = program
            }
        }
        val added = ArrayList<HaikuoProgram>()
        val updated = ArrayList<HaikuoProgram>()
        val duplicates = ArrayList<HaikuoDuplicate>()
        for (program in incoming) {
            val tagged = program.copyWith(
                origin = if (origin.isEmpty()) program.origin else origin
            )
            val previous = byId[tagged.id]
            if (previous != null) {
                updated.add(tagged)
                byId[tagged.id] = tagged
                byName[tagged.name] = tagged
                continue
            }
            val sameName = byName[tagged.name]
            if (sameName != null) {
                duplicates.add(HaikuoDuplicate(tagged, sameName))
                continue
            }
            added.add(tagged)
            byId[tagged.id] = tagged
            byName[tagged.name] = tagged
        }
        if (duplicates.isNotEmpty()) {
            val byDuplicateName = LinkedHashMap<String, HaikuoDuplicate>()
            for (duplicate in duplicates) {
                byDuplicateName[duplicate.incoming.name] = duplicate
            }
            val effective = byDuplicateName.values.toList()
            val decision = onDuplicates?.invoke(effective) ?: HaikuoDuplicateDecision.keepBoth
            when (decision) {
                HaikuoDuplicateDecision.cancel -> {
                    val cancelNotes = ArrayList<String>()
                    cancelNotes.add("已取消导入")
                    cancelNotes.addAll(notes)
                    return HaikuoImportResult(emptyList(), emptyList(), cancelNotes, true)
                }
                HaikuoDuplicateDecision.replace -> {
                    for (duplicate in effective) {
                        val tagged = duplicate.incoming
                        val iterator = byId.entries.iterator()
                        while (iterator.hasNext()) {
                            val entry = iterator.next()
                            if (entry.value.name == tagged.name && entry.key != tagged.id) {
                                iterator.remove()
                            }
                        }
                        byId[tagged.id] = tagged
                        byName[tagged.name] = tagged
                        updated.add(tagged)
                    }
                }
                HaikuoDuplicateDecision.keepBoth -> {
                    val taken = byName.keys.toMutableSet()
                    for (duplicate in effective) {
                        val renamed = renameForKeepBoth(duplicate.incoming, taken)
                        taken.add(renamed.name)
                        byId[renamed.id] = renamed
                        byName[renamed.name] = renamed
                        added.add(renamed)
                    }
                }
            }
        }
        if (added.isEmpty() && updated.isEmpty()) {
            return HaikuoImportResult(emptyList(), emptyList(), notes)
        }
        val merged = byId.values.toMutableList()
        merged.sortByDescending { it.importedAtMillis }
        writeAll(merged)
        return HaikuoImportResult(added, updated, notes)
    }

    fun addAllSkipExisting(
        incoming: List<HaikuoProgram>,
        origin: String = ""
    ): Pair<List<HaikuoProgram>, List<HaikuoProgram>> {
        val existing = list()
        val takenIds = existing.map { it.id }.toMutableSet()
        val takenNames = existing.map { it.name }.toMutableSet()
        val added = ArrayList<HaikuoProgram>()
        val skipped = ArrayList<HaikuoProgram>()
        for (program in incoming) {
            val tagged = program.copyWith(
                origin = if (origin.isEmpty()) program.origin else origin
            )
            if (takenIds.contains(tagged.id) || takenNames.contains(tagged.name)) {
                skipped.add(tagged)
                continue
            }
            takenIds.add(tagged.id)
            takenNames.add(tagged.name)
            added.add(tagged)
        }
        if (added.isEmpty()) {
            return Pair(added, skipped)
        }
        val merged = (existing + added).toMutableList()
        merged.sortByDescending { it.importedAtMillis }
        writeAll(merged)
        return Pair(added, skipped)
    }

    private fun renameForKeepBoth(program: HaikuoProgram, taken: Set<String>): HaikuoProgram {
        var suffix = 2
        var name = "${program.name}_$suffix"
        while (taken.contains(name)) {
            suffix++
            name = "${program.name}_$suffix"
        }
        val body = when {
            program.rawJson.isNotEmpty() -> program.rawJson
            program.script.isNotEmpty() -> program.script
            else -> program.url
        }
        return program.copyWith(name = name, id = HaikuoProgram.makeId(name, body))
    }

    fun remove(id: String) {
        val programs = list()
        val remaining = programs.filter { it.id != id }
        writeAll(remaining)
    }

    fun clear() {
        writeAll(emptyList())
    }

    private fun readCollections(): Map<String, MutableList<Map<String, Any?>>> {
        val empty = LinkedHashMap<String, MutableList<Map<String, Any?>>>()
        empty["favorites"] = ArrayList()
        empty["history"] = ArrayList()
        empty["bookmarks"] = ArrayList()
        try {
            val file = collectionsFile()
            if (!file.exists()) return empty
            val decoded = HaikuoJson.parse(file.readText(Charsets.UTF_8))
            if (decoded !is Map<*, *>) return empty
            for (key in empty.keys) {
                val rawList = decoded[key]
                if (rawList is List<*>) {
                    val items = ArrayList<Map<String, Any?>>()
                    for (item in rawList) {
                        if (item is Map<*, *>) {
                            val map = LinkedHashMap<String, Any?>()
                            for ((entryKey, entryValue) in item) {
                                map[entryKey.toString()] = entryValue
                            }
                            items.add(map)
                        }
                    }
                    empty[key] = items
                }
            }
            return empty
        } catch (e: Exception) {
            return empty
        }
    }

    private fun writeCollections(data: Map<String, List<Map<String, Any?>>>) {
        val file = collectionsFile()
        val temp = File("${file.path}.tmp")
        temp.writeText(HaikuoJson.stringify(data), Charsets.UTF_8)
        if (file.exists()) file.delete()
        temp.renameTo(file)
    }

    private fun sameEntry(a: Map<String, Any?>, b: Map<String, Any?>): Boolean {
        return normalizeRuleTitle(HaikuoJson.str(a["rule"])) ==
            normalizeRuleTitle(HaikuoJson.str(b["rule"])) &&
            HaikuoJson.str(a["url"]) == HaikuoJson.str(b["url"])
    }

    fun recordHistory(rule: String, url: String, title: String = "", pic: String = "") {
        if (url.isEmpty()) return
        val data = readCollections()
        val entry = LinkedHashMap<String, Any?>()
        entry["rule"] = rule
        entry["url"] = url
        entry["title"] = title
        entry["pic"] = pic
        entry["at"] = isoNow()
        val history = data["history"] ?: ArrayList()
        val filtered = history.filter { !sameEntry(it, entry) }.toMutableList()
        filtered.add(0, entry)
        while (filtered.size > 200) {
            filtered.removeAt(filtered.size - 1)
        }
        val next = LinkedHashMap<String, List<Map<String, Any?>>>()
        next["favorites"] = data["favorites"] ?: emptyList()
        next["history"] = filtered
        next["bookmarks"] = data["bookmarks"] ?: emptyList()
        writeCollections(next)
    }

    fun listHistory(rule: String? = null): List<Map<String, Any?>> {
        val data = readCollections()
        val wanted = if (rule == null) null else normalizeRuleTitle(rule)
        val history = data["history"] ?: emptyList()
        return history.filter { wanted == null || normalizeRuleTitle(HaikuoJson.str(it["rule"])) == wanted }
    }

    fun toggleFavorite(entry: Map<String, Any?>): Boolean {
        val data = readCollections()
        val favorites = (data["favorites"] ?: emptyList()).toMutableList()
        val existing = favorites.indexOfFirst { sameEntry(it, entry) }
        if (existing >= 0) {
            favorites.removeAt(existing)
            val next = LinkedHashMap<String, List<Map<String, Any?>>>()
            next["favorites"] = favorites
            next["history"] = data["history"] ?: emptyList()
            next["bookmarks"] = data["bookmarks"] ?: emptyList()
            writeCollections(next)
            return false
        }
        val withAt = LinkedHashMap<String, Any?>()
        withAt.putAll(entry)
        withAt["at"] = isoNow()
        favorites.add(0, withAt)
        val next = LinkedHashMap<String, List<Map<String, Any?>>>()
        next["favorites"] = favorites
        next["history"] = data["history"] ?: emptyList()
        next["bookmarks"] = data["bookmarks"] ?: emptyList()
        writeCollections(next)
        return true
    }

    fun isFavorite(rule: String, url: String): Boolean {
        val data = readCollections()
        val favorites = data["favorites"] ?: emptyList()
        return favorites.any {
            normalizeRuleTitle(HaikuoJson.str(it["rule"])) == normalizeRuleTitle(rule) &&
                HaikuoJson.str(it["url"]) == url
        }
    }

    fun listFavorites(rule: String? = null, group: String? = null): List<Map<String, Any?>> {
        val data = readCollections()
        val wanted = if (rule == null) null else normalizeRuleTitle(rule)
        val favorites = data["favorites"] ?: emptyList()
        return favorites.filter {
            (wanted == null || normalizeRuleTitle(HaikuoJson.str(it["rule"])) == wanted) &&
                (group == null || HaikuoJson.str(it["group"]) == group)
        }
    }

    fun listFavoriteGroups(): List<String> {
        val data = readCollections()
        val groups = sortedSetOf<String>()
        for (item in data["favorites"] ?: emptyList()) {
            val group = HaikuoJson.str(item["group"])
            if (group.isNotEmpty()) groups.add(group)
        }
        return groups.toList()
    }

    fun addBookmark(entry: Map<String, Any?>) {
        val data = readCollections()
        val bookmarks = (data["bookmarks"] ?: emptyList()).filter { !sameEntry(it, entry) }.toMutableList()
        val withAt = LinkedHashMap<String, Any?>()
        withAt.putAll(entry)
        withAt["at"] = isoNow()
        bookmarks.add(0, withAt)
        val next = LinkedHashMap<String, List<Map<String, Any?>>>()
        next["favorites"] = data["favorites"] ?: emptyList()
        next["history"] = data["history"] ?: emptyList()
        next["bookmarks"] = bookmarks
        writeCollections(next)
    }

    fun removeBookmark(rule: String, url: String) {
        val data = readCollections()
        val bookmarks = (data["bookmarks"] ?: emptyList()).filter {
            !(normalizeRuleTitle(HaikuoJson.str(it["rule"])) == normalizeRuleTitle(rule) &&
                HaikuoJson.str(it["url"]) == url)
        }
        val next = LinkedHashMap<String, List<Map<String, Any?>>>()
        next["favorites"] = data["favorites"] ?: emptyList()
        next["history"] = data["history"] ?: emptyList()
        next["bookmarks"] = bookmarks
        writeCollections(next)
    }

    fun listBookmarks(rule: String? = null): List<Map<String, Any?>> {
        val data = readCollections()
        val wanted = if (rule == null) null else normalizeRuleTitle(rule)
        val bookmarks = data["bookmarks"] ?: emptyList()
        return bookmarks.filter { wanted == null || normalizeRuleTitle(HaikuoJson.str(it["rule"])) == wanted }
    }

    fun importFile(
        file: File,
        onDuplicates: ((List<HaikuoDuplicate>) -> HaikuoDuplicateDecision)? = null
    ): HaikuoImportResult {
        val bytes = file.readBytes()
        return importBytes(bytes, "文件：${file.name}", onDuplicates)
    }

    fun importBytes(
        bytes: ByteArray,
        origin: String = "文件",
        onDuplicates: ((List<HaikuoDuplicate>) -> HaikuoDuplicateDecision)? = null
    ): HaikuoImportResult {
        val outcome = programsFromBytes(bytes, origin)
        return addAll(outcome.first, origin, outcome.second, onDuplicates)
    }

    private fun programsFromBytes(
        bytes: ByteArray,
        origin: String
    ): Pair<List<HaikuoProgram>, List<String>> {
        val notes = ArrayList<String>()
        if (isSqliteBytes(bytes)) {
            val programs = programsFromDbBytes(bytes, origin)
            return Pair(programs, notes)
        }
        val dbEntries = HaikuoCodec.zipDbEntries(bytes)
        if (dbEntries.isNotEmpty()) {
            val programs = ArrayList<HaikuoProgram>()
            for ((entryName, entryBytes) in dbEntries) {
                try {
                    programs.addAll(programsFromDbBytes(entryBytes, origin))
                } catch (error: HaikuoFormatException) {
                    notes.add("数据库条目 $entryName 导入失败：${error.message}")
                }
            }
            val dropped = ArrayList<String>()
            try {
                programs.addAll(HaikuoCodec.parseBytes(bytes, origin, 0, dropped))
            } catch (e: HaikuoFormatException) {
            }
            for (note in dropped) {
                if (!note.contains("数据库条目")) notes.add(note)
            }
            if (programs.isEmpty()) {
                throw HaikuoFormatException("压缩包里没有找到小程序 JSON 或规则数据库")
            }
            return Pair(programs, notes)
        }
        val programs = HaikuoCodec.parseBytes(bytes, origin, 0, notes)
        return Pair(programs, notes)
    }

    private fun programsFromDbBytes(bytes: ByteArray, origin: String): List<HaikuoProgram> {
        val rows = readDbRows(bytes)
        val rules = ArrayList<Map<String, Any?>>()
        for (row in rows) {
            val rule = haikuoDbRowToRule(row)
            if (rule == null) continue
            rules.add(rule)
        }
        if (rules.isEmpty()) {
            throw HaikuoFormatException("数据库里没有找到可导入的规则")
        }
        val programs = HaikuoCodec.parseDecoded(rules, origin)
        if (programs.isEmpty()) {
            throw HaikuoFormatException("数据库里没有识别到海阔小程序条目")
        }
        return programs
    }

    private fun readDbRows(bytes: ByteArray): List<Map<String, Any?>> {
        val reader = dbReader
        if (reader != null) return reader(bytes)
        val dir = rootDir()
        val tmp = File(dir, "haikuo_import_${System.nanoTime()}.db")
        try {
            tmp.writeBytes(bytes)
            val db = SQLiteDatabase.openDatabase(tmp.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val tables = db.rawQuery(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name='articlelistrule'",
                    null
                )
                val hasTable = try {
                    tables.count > 0
                } finally {
                    tables.close()
                }
                if (!hasTable) {
                    throw HaikuoFormatException("数据库里没有找到规则表 articlelistrule")
                }
                val cursor = db.query("articlelistrule", null, null, null, null, null, null)
                try {
                    val rows = ArrayList<Map<String, Any?>>()
                    val columnNames = cursor.columnNames
                    while (cursor.moveToNext()) {
                        val row = LinkedHashMap<String, Any?>()
                        for (i in columnNames.indices) {
                            val value: Any? = when (cursor.getType(i)) {
                                Cursor.FIELD_TYPE_NULL -> null
                                Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(i)
                                Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(i)
                                Cursor.FIELD_TYPE_STRING -> cursor.getString(i)
                                Cursor.FIELD_TYPE_BLOB -> {
                                    val blob = cursor.getBlob(i)
                                    if (blob == null) null else String(blob, Charsets.UTF_8)
                                }
                                else -> cursor.getString(i)
                            }
                            row[columnNames[i]] = value
                        }
                        rows.add(row)
                    }
                    return rows
                } finally {
                    cursor.close()
                }
            } finally {
                db.close()
            }
        } finally {
            try {
                tmp.delete()
            } catch (e: Exception) {
            }
        }
    }

    fun importText(
        text: String,
        origin: String = "文本",
        onDuplicates: ((List<HaikuoDuplicate>) -> HaikuoDuplicateDecision)? = null
    ): HaikuoImportResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            throw HaikuoFormatException("内容为空")
        }
        val cloudUrl = HaikuoCodec.cloudApiUrl(trimmed)
        if (cloudUrl != null) {
            val bytes = fetch(cloudUrl)
            val resolved = HaikuoCodec.parseCloudApiResponse(HaikuoCodec.decodeText(bytes))
            return importText(resolved, origin, onDuplicates)
        }
        if (HaikuoCodec.looksLikeJson(trimmed)) {
            val programs = HaikuoCodec.parseText(trimmed, origin)
            return addAll(programs, origin, emptyList(), onDuplicates)
        }
        val candidates = HaikuoCodec.extractCandidates(trimmed)
        val errors = ArrayList<String>()
        for (candidate in candidates) {
            try {
                val programs = importCandidate(candidate, origin)
                if (programs.isNotEmpty()) {
                    return addAll(programs, origin, emptyList(), onDuplicates)
                }
            } catch (error: HaikuoFormatException) {
                errors.add(error.message ?: "")
            } catch (error: Exception) {
                errors.add(error.toString())
            }
        }
        throw HaikuoFormatException(
            if (errors.isEmpty()) "口令里没有可用的海阔小程序" else "口令解析失败：${errors.first()}"
        )
    }

    private fun importCandidate(candidate: String, origin: String): List<HaikuoProgram> {
        val value = candidate.trim()
        if (value.isEmpty()) return emptyList()
        val lower = value.lowercase()
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            val bytes = fetch(value)
            return HaikuoCodec.parseBytes(bytes, origin)
        }
        if (HaikuoCodec.looksLikeJson(value)) {
            return HaikuoCodec.parseText(value, origin)
        }
        val decoded = HaikuoCodec.tryBase64(value)
        if (decoded != null && decoded.isNotEmpty()) {
            return HaikuoCodec.parseBytes(decoded, origin)
        }
        return HaikuoCodec.parseText(value, origin)
    }

    fun importUrl(
        url: String,
        onDuplicates: ((List<HaikuoDuplicate>) -> HaikuoDuplicateDecision)? = null
    ): HaikuoImportResult {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) {
            throw HaikuoFormatException("链接为空")
        }
        val lower = trimmed.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            throw HaikuoFormatException("链接需要以 http:// 或 https:// 开头")
        }
        val bytes = fetch(trimmed)
        var programs: List<HaikuoProgram>
        var notes: List<String> = emptyList()
        try {
            val outcome = programsFromBytes(bytes, "链接")
            programs = outcome.first
            notes = outcome.second
        } catch (e: HaikuoFormatException) {
            val text = HaikuoCodec.decodeText(bytes)
            val candidates = HaikuoCodec.extractCandidates(text)
            programs = emptyList()
            for (candidate in candidates) {
                if (candidate == trimmed) continue
                try {
                    val parsed = importCandidate(candidate, "链接")
                    if (parsed.isNotEmpty()) {
                        programs = parsed
                        break
                    }
                } catch (ignored: Exception) {
                    continue
                }
            }
            if (programs.isEmpty()) {
                throw HaikuoFormatException("这个链接里没有找到小程序 JSON")
            }
        }
        return addAll(programs, "链接", notes, onDuplicates)
    }

    private fun fetch(url: String): ByteArray {
        val client = httpClient ?: OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
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
            val response = client.newCall(request).execute()
            response.use { resp ->
                if (resp.code != 200) {
                    throw HaikuoFormatException("下载失败：HTTP ${resp.code}")
                }
                return resp.body?.bytes() ?: ByteArray(0)
            }
        } catch (error: HaikuoFormatException) {
            throw error
        } catch (error: Exception) {
            throw HaikuoFormatException("下载失败：$error")
        }
    }

    fun exportJson(programs: List<HaikuoProgram>): String {
        val payload = LinkedHashMap<String, Any?>()
        payload["name"] = "海阔小程序导出"
        payload["version"] = 1
        payload["exportedAt"] = isoNow()
        payload["data"] = programs.map { it.toJsonMap() }
        return GsonBuilder().setPrettyPrinting().create().toJson(payload)
    }

    fun ensureBuiltinRules(bundleText: String? = null, force: Boolean = false): Int {
        try {
            val dir = rootDir()
            val marker = File(dir, BUILTIN_MARKER_FILE)
            var previousNames = emptySet<String>()
            var markerCurrent = false
            if (marker.exists()) {
                try {
                    val saved = HaikuoJson.parse(marker.readText(Charsets.UTF_8))
                    if (saved is Map<*, *>) {
                        val names = saved["names"]
                        if (names is List<*>) {
                            previousNames = names.map { it.toString() }.toSet()
                        }
                        markerCurrent = HaikuoJson.num(saved["version"])?.toInt() == BUILTIN_BUNDLE_VERSION
                    }
                } catch (e: Exception) {
                    previousNames = emptySet()
                }
            }
            if (!force && markerCurrent) return 0
            val text = bundleText ?: appContext.assets.open(BUILTIN_ASSET_PATH).use {
                HaikuoCodec.decodeText(it.readBytes())
            }
            val programs = HaikuoCodec.parseText(text, "内置规则")
            if (programs.isEmpty()) return 0
            val existing = list()
            val byId = HashSet<String>()
            for (program in existing) {
                byId.add(program.id)
            }
            val incoming = ArrayList<HaikuoProgram>()
            for (program in programs) {
                if (byId.contains(program.id)) continue
                incoming.add(program)
            }
            val removedNames = previousNames - programs.map { it.name }.toSet()
            val merged = existing.filter { old ->
                !incoming.any { next -> next.name == old.name } &&
                    !(old.origin == "内置规则" && removedNames.contains(old.name))
            }.toMutableList()
            merged.addAll(incoming)
            merged.sortByDescending { it.importedAtMillis }
            writeAll(merged)
            val markerPayload = LinkedHashMap<String, Any?>()
            markerPayload["version"] = BUILTIN_BUNDLE_VERSION
            markerPayload["seededAt"] = isoNow()
            markerPayload["names"] = programs.map { it.name }
            marker.writeText(HaikuoJson.stringify(markerPayload), Charsets.UTF_8)
            return incoming.size
        } catch (e: Exception) {
            return 0
        }
    }

    private fun isoNow(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date())
    }

    companion object {
        const val BUILTIN_BUNDLE_VERSION = 13
        const val BUILTIN_ASSET_PATH = "haikuo_builtin_rules.json"
        private const val FILE_NAME = "programs.json"
        private const val COLLECTIONS_FILE_NAME = "collections.json"
        private const val BUILTIN_MARKER_FILE = "builtin_seeded.json"
    }
}

object HaikuoSavedFiles {

    private const val DIR_NAME = "saved_files"

    private fun safeBaseName(fileName: String): String? {
        val base = fileName.replace('\\', '/').substringAfterLast('/')
        if (base.isEmpty() || base == "." || base == "..") return null
        return base
    }

    fun writeSavedFile(root: File, fileName: String, bytes: ByteArray) {
        val base = safeBaseName(fileName) ?: return
        val dir = File(root, DIR_NAME)
        dir.mkdirs()
        File(dir, base).writeBytes(bytes)
    }

    fun readSavedFile(root: File, fileName: String): ByteArray? {
        val base = safeBaseName(fileName) ?: return null
        val file = File(File(root, DIR_NAME), base)
        try {
            if (!file.exists()) return null
            return file.readBytes()
        } catch (e: Exception) {
            return null
        }
    }

    fun deleteSavedFile(root: File, fileName: String) {
        val base = safeBaseName(fileName) ?: return
        val file = File(File(root, DIR_NAME), base)
        try {
            if (file.exists()) file.delete()
        } catch (e: Exception) {
        }
    }
}

object HaikuoRuleFiles {

    private const val DIR_NAME = "rule_files"

    const val SHARED_FOLDER = "__shared__"

    private fun safeBaseName(fileName: String): String? {
        val base = fileName.replace('\\', '/').substringAfterLast('/')
        if (base.isEmpty() || base == "." || base == "..") return null
        return base
    }

    fun writeRuleFile(root: File, ruleTitle: String, fileName: String, bytes: ByteArray) {
        val base = safeBaseName(fileName)
            ?: throw HaikuoFormatException("模块文件名不对")
        val dir = File(
            File(root, DIR_NAME),
            java.net.URLEncoder.encode(ruleTitle, "UTF-8")
        )
        dir.mkdirs()
        File(dir, base).writeBytes(bytes)
    }

    fun readRuleFile(root: File, ruleTitle: String, fileName: String): ByteArray? {
        val base = safeBaseName(fileName)
        if (base == null || ruleTitle.trim().isEmpty()) return null
        val dir = File(root, DIR_NAME)
        if (!dir.exists()) return null
        val exact = File(
            File(dir, java.net.URLEncoder.encode(ruleTitle, "UTF-8")),
            base
        )
        if (exact.exists()) return exact.readBytes()
        val children = dir.listFiles() ?: emptyArray()
        for (entity in children) {
            if (!entity.isDirectory) continue
            val folderName = entity.name
            if (folderName == SHARED_FOLDER) continue
            val decoded = try {
                java.net.URLDecoder.decode(folderName, "UTF-8")
            } catch (e: Exception) {
                continue
            }
            if (matchRuleTitle(ruleTitle, listOf(decoded)) == null) continue
            val candidate = File(entity, base)
            if (candidate.exists()) return candidate.readBytes()
        }
        val shared = File(File(dir, SHARED_FOLDER), base)
        if (shared.exists()) return shared.readBytes()
        return null
    }
}
