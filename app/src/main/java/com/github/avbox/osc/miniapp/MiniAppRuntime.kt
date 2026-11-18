package com.github.avbox.osc.miniapp

import java.io.File

class MiniAppInstance(
    val manifest: MiniAppManifest,
    val server: MiniAppServer,
    val dir: File
) {
    var lastActive: Long = System.currentTimeMillis()
    var foreground: Boolean = false

    val baseUrl: String
        get() = server.baseUrl

    val appId: String
        get() = manifest.appId
}

class MiniAppRuntime(
    val store: MiniAppStore,
    var settings: MiniAppSettings
) {
    private val running = LinkedHashMap<String, MiniAppInstance>()

    val runningInstances: List<MiniAppInstance>
        get() = running.values.toList()

    fun runningInstance(appId: String): MiniAppInstance? = running[appId]

    @Synchronized
    fun ensureRunning(manifest: MiniAppManifest): MiniAppInstance {
        val existing = running[manifest.appId]
        if (existing != null) {
            existing.lastActive = System.currentTimeMillis()
            existing.foreground = true
            return existing
        }
        val dir = store.installedDir(manifest.appId)
            ?: throw IllegalStateException("小程序未安装：${manifest.appId}")
        val usedPorts = running.values.map { it.server.listeningPort }.toSet()
        val server = MiniAppServer.start(
            root = dir,
            entry = manifest.entry,
            sdkScript = MiniAppSdkScript.SCRIPT,
            basePort = settings.basePort,
            usedPorts = usedPorts,
            lanShare = settings.lanShare
        )
        val instance = MiniAppInstance(manifest, server, dir)
        instance.foreground = true
        running[manifest.appId] = instance
        evictBackground()
        return instance
    }

    @Synchronized
    fun markForeground(appId: String) {
        val instance = running[appId] ?: return
        instance.foreground = true
        instance.lastActive = System.currentTimeMillis()
        evictBackground()
    }

    @Synchronized
    fun markBackground(appId: String) {
        val instance = running[appId] ?: return
        instance.foreground = false
        instance.lastActive = System.currentTimeMillis()
        evictBackground()
    }

    fun resolveUrl(appId: String): String? {
        val live = synchronized(this) { running[appId] }
        if (live != null) return live.baseUrl
        val dir = store.installedDir(appId) ?: return null
        val manifest = store.readManifest(dir)
        if (manifest == null || !manifest.isValid) return null
        val instance = ensureRunning(manifest)
        return instance.baseUrl
    }

    @Synchronized
    fun stop(appId: String) {
        val instance = running.remove(appId)
        if (instance != null) instance.server.stop()
    }

    @Synchronized
    fun stopAll() {
        val instances = running.values.toList()
        running.clear()
        for (instance in instances) {
            instance.server.stop()
        }
    }

    private fun evictBackground() {
        val background = running.values
            .filter { !it.foreground }
            .sortedBy { it.lastActive }
            .toMutableList()
        while (background.size > settings.maxBackground) {
            val victim = background.removeAt(0)
            stop(victim.appId)
        }
    }
}
