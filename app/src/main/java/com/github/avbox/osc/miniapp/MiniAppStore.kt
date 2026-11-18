package com.github.avbox.osc.miniapp

import android.content.Context
import com.github.avbox.osc.haikuo.HaikuoJson
import com.github.avbox.osc.haikuo.haikuoHashHex
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class MiniAppStore(
    private val appContext: Context,
    private val rootOverride: File? = null,
    private val httpClient: OkHttpClient? = null
) {
    fun root(): File {
        val override = rootOverride
        if (override != null) return override
        val dir = File(appContext.filesDir, "miniapps")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listInstalled(): List<MiniAppManifest> {
        val base = root()
        if (!base.exists()) return emptyList()
        val items = mutableListOf<MiniAppManifest>()
        val children = base.listFiles() ?: return emptyList()
        for (entity in children) {
            if (!entity.isDirectory) continue
            val manifest = readManifest(entity)
            if (manifest != null && manifest.isValid) items.add(manifest)
        }
        items.sortBy { it.name }
        return items
    }

    fun readManifest(dir: File): MiniAppManifest? {
        val file = File(dir, "manifest.json")
        if (!file.exists()) return null
        return try {
            val decoded = HaikuoJson.parse(file.readText(Charsets.UTF_8)) ?: return null
            val map = HaikuoJson.asMap(decoded) ?: return null
            MiniAppManifest.fromJson(map)
        } catch (e: Exception) {
            null
        }
    }

    fun installedDir(appId: String): File? {
        val dir = File(root(), appId)
        if (!dir.exists()) return null
        return dir
    }

    fun installFromZipBytes(bytes: ByteArray, expectedMd5: String? = null): MiniAppManifest {
        if (!expectedMd5.isNullOrEmpty()) {
            val actual = haikuoHashHex("md5", bytes)
            if (actual != expectedMd5.lowercase()) {
                throw MiniAppFormatException("安装包校验失败：md5 不一致")
            }
        }
        val entries = readZipEntries(bytes)
        return installArchive(entries)
    }

    fun installFromZipFile(file: File, expectedMd5: String? = null): MiniAppManifest {
        return installFromZipBytes(file.readBytes(), expectedMd5)
    }

    fun installFromUrl(url: String, expectedMd5: String? = null, expectedSize: Int? = null): MiniAppManifest {
        val client = httpClient ?: OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        response.use {
            if (it.code != 200) {
                throw MiniAppFormatException("下载安装包失败：HTTP ${it.code}")
            }
            val bodyBytes = it.body?.bytes() ?: ByteArray(0)
            if (expectedSize != null && bodyBytes.size != expectedSize) {
                throw MiniAppFormatException("安装包校验失败：大小不一致")
            }
            return installFromZipBytes(bodyBytes, expectedMd5)
        }
    }

    fun installFromDirectory(source: File): MiniAppManifest {
        val manifest = readManifest(source)
        if (manifest == null || !manifest.isValid) {
            throw MiniAppFormatException("文件夹里没有合法的 manifest.json")
        }
        val base = root()
        val staging = File(base, ".staging-${manifest.appId}")
        if (staging.exists()) staging.deleteRecursively()
        copyDirectory(source, staging)
        val target = File(base, manifest.appId)
        return promote(staging, target, manifest)
    }

    fun installFromManifestJson(manifestJson: String): MiniAppManifest {
        val decoded = HaikuoJson.parse(manifestJson)
            ?: throw MiniAppFormatException("manifest.json 不合法")
        val map = HaikuoJson.asMap(decoded)
            ?: throw MiniAppFormatException("manifest.json 不合法")
        val manifest = MiniAppManifest.fromJson(map)
        if (!manifest.isValid) {
            throw MiniAppFormatException("manifest.json 不合法")
        }
        val base = root()
        val target = File(base, manifest.appId)
        if (target.exists()) target.deleteRecursively()
        target.mkdirs()
        File(target, "manifest.json").writeText(HaikuoJson.stringify(manifest.toJsonMap()), Charsets.UTF_8)
        return manifest
    }

    fun deleteApp(appId: String) {
        val dir = installedDir(appId)
        if (dir != null) dir.deleteRecursively()
    }

    private fun installArchive(entries: List<ZipEntryData>): MiniAppManifest {
        for (entry in entries) {
            if (!miniAppIsSafeZipEntryName(entry.name)) {
                throw MiniAppFormatException("安装包里有非法路径：${entry.name}")
            }
        }
        val manifestEntry = findManifestEntry(entries)
            ?: throw MiniAppFormatException("安装包里没有 manifest.json")
        val prefix = commonPrefix(manifestEntry.name)
        val manifest = parseManifestBytes(manifestEntry.content)
        if (manifest == null || !manifest.isValid) {
            throw MiniAppFormatException("安装包里的 manifest.json 不合法")
        }
        val base = root()
        val staging = File(base, ".staging-${manifest.appId}")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()
        for (entry in entries) {
            if (entry.isDirectory) continue
            val relative = if (prefix.isEmpty()) entry.name else entry.name.substring(prefix.length)
            if (relative.isEmpty()) continue
            val targetPath = miniAppSafeJoinPath(staging.absolutePath, relative)
                ?: throw MiniAppFormatException("安装包里有非法路径：${entry.name}")
            val target = File(targetPath)
            target.parentFile?.mkdirs()
            target.writeBytes(entry.content)
        }
        val target = File(base, manifest.appId)
        return promote(staging, target, manifest)
    }

    private fun promote(staging: File, target: File, manifest: MiniAppManifest): MiniAppManifest {
        if (target.exists()) target.deleteRecursively()
        staging.renameTo(target)
        return manifest
    }

    private fun findManifestEntry(entries: List<ZipEntryData>): ZipEntryData? {
        for (entry in entries) {
            if (entry.isDirectory) continue
            val name = entry.name.replace("\\", "/")
            if (name == "manifest.json" || name.endsWith("/manifest.json")) {
                return entry
            }
        }
        return null
    }

    private fun commonPrefix(manifestName: String): String {
        val name = manifestName.replace("\\", "/")
        val index = name.lastIndexOf("/")
        if (index < 0) return ""
        return name.substring(0, index + 1)
    }

    private fun parseManifestBytes(bytes: ByteArray): MiniAppManifest? {
        return try {
            val decoded = HaikuoJson.parse(String(bytes, Charsets.UTF_8)) ?: return null
            val map = HaikuoJson.asMap(decoded) ?: return null
            MiniAppManifest.fromJson(map)
        } catch (e: Exception) {
            null
        }
    }

    private fun copyDirectory(source: File, target: File) {
        target.mkdirs()
        val children = source.listFiles() ?: return
        for (entity in children) {
            val name = entity.name
            if (entity.isDirectory) {
                copyDirectory(entity, File(target, name))
            } else if (entity.isFile) {
                entity.copyTo(File(target, name), overwrite = true)
            }
        }
    }

    private class ZipEntryData(val name: String, val isDirectory: Boolean, val content: ByteArray)

    private fun readZipEntries(bytes: ByteArray): List<ZipEntryData> {
        val entries = mutableListOf<ZipEntryData>()
        val stream = ZipInputStream(ByteArrayInputStream(bytes))
        stream.use {
            var entry = it.nextEntry
            while (entry != null) {
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                var read = it.read(chunk)
                while (read >= 0) {
                    buffer.write(chunk, 0, read)
                    read = it.read(chunk)
                }
                entries.add(ZipEntryData(entry.name, entry.isDirectory, buffer.toByteArray()))
                it.closeEntry()
                entry = it.nextEntry
            }
        }
        return entries
    }
}

class MiniAppFormatException(message: String) : Exception(message)
