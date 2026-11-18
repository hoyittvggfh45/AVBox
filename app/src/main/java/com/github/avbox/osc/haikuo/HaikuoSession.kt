package com.github.avbox.osc.haikuo

import android.content.Context
import java.io.File
import java.util.concurrent.ConcurrentHashMap

interface HaikuoJsEvaluator {
    fun evaluate(script: String): String?
}

interface HaikuoWebViewFetcher {
    fun fetch(params: Map<String, Any?>): String?
}

interface HaikuoClipboard {
    fun getText(): String
    fun setText(text: String)
}

class HaikuoSession(
    val program: HaikuoProgram,
    val store: HaikuoStore,
    val appContext: Context,
    val appVersionCode: Int,
    dialogHandler: HaikuoDialogHandler? = null
) {

    val cookieJar = HaikuoCookieJar()

    val dialogQueue = HaikuoDialogQueue(dialogHandler)

    var jsEvaluator: HaikuoJsEvaluator? = null

    var webViewFetcher: HaikuoWebViewFetcher? = null

    var clipboard: HaikuoClipboard? = null

    private val savedFiles = ConcurrentHashMap<String, ByteArray>()

    @Volatile
    private var closed = false

    private var presetManifest: Map<String, List<String>>? = null

    val ruleProxy: String?
        get() {
            val raw = program.rawJson
            if (!raw.trim().startsWith("{")) return null
            val decoded = HaikuoJson.parse(raw)
            if (decoded !is Map<*, *>) return null
            val proxy = decoded["proxy"]
            return parseHaikuoProxy(if (proxy is String) proxy else null)
        }

    fun isClosed(): Boolean {
        return closed
    }

    fun storeSavedFile(fileName: String, bytes: ByteArray) {
        val base = haikuoSafeFileName(fileName) ?: return
        savedFiles[base] = bytes
        if (!base.startsWith("imgdec_")) {
            HaikuoSavedFiles.writeSavedFile(store.rootDir(), base, bytes)
        }
    }

    fun deleteSavedFile(fileName: String) {
        val base = haikuoSafeFileName(fileName) ?: return
        savedFiles.remove(base)
        val variants = linkedSetOf(base)
        val dot = base.lastIndexOf('.')
        if (dot > 0) {
            variants.add(base.substring(0, dot))
        }
        for (variant in variants) {
            savedFiles.remove(variant)
            HaikuoSavedFiles.deleteSavedFile(store.rootDir(), variant)
        }
    }

    fun persistedFileBytes(fileName: String): ByteArray? {
        val base = haikuoSafeFileName(fileName) ?: return null
        val memory = savedFiles[base]
        if (memory != null) return memory
        return HaikuoSavedFiles.readSavedFile(store.rootDir(), base)
    }

    fun ruleFileBytes(fileName: String, ruleTitle: String): ByteArray? {
        return HaikuoRuleFiles.readRuleFile(store.rootDir(), ruleTitle, fileName)
    }

    fun presetModuleBytes(fileName: String, ruleTitle: String): ByteArray? {
        val manifest = loadPresetManifest()
        val resolved = resolvePresetModule(fileName, ruleTitle, manifest) ?: return null
        return try {
            appContext.assets.open("haikuo_modules/$resolved").use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    private fun loadPresetManifest(): Map<String, List<String>> {
        val cached = presetManifest
        if (cached != null) return cached
        val parsed = LinkedHashMap<String, List<String>>()
        try {
            val text = appContext.assets.open(PRESET_MANIFEST_ASSET).use {
                HaikuoCodec.decodeText(it.readBytes())
            }
            val decoded = HaikuoJson.parse(text)
            if (decoded is Map<*, *>) {
                val rules = decoded["rules"]
                if (rules is Map<*, *>) {
                    for ((key, value) in rules) {
                        if (value is List<*>) {
                            val files = ArrayList<String>()
                            for (item in value) {
                                if (item is String) files.add(item)
                            }
                            parsed[key.toString()] = files
                        }
                    }
                }
            }
        } catch (e: Exception) {
        }
        presetManifest = parsed
        return parsed
    }

    fun downloadsDir(): File {
        val dir = File(appContext.filesDir, "haikuo_downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun close() {
        if (closed) return
        closed = true
        dialogQueue.close()
    }

    companion object {
        private const val PRESET_MANIFEST_ASSET =
            "haikuo_modules_manifest.json"
    }
}
