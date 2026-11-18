package com.github.avbox.osc.ui.page

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.avbox.osc.R
import com.github.avbox.osc.miniapp.MiniAppBuiltin
import com.github.avbox.osc.miniapp.MiniAppManifest
import com.github.avbox.osc.miniapp.MiniAppMarketItem
import com.github.avbox.osc.miniapp.MiniAppSettings
import com.github.avbox.osc.miniapp.MiniAppStore
import com.github.avbox.osc.miniapp.filterRemovedMarketItems
import com.github.avbox.osc.miniapp.parseMiniAppMarketJson
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MiniAppHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = MiniAppStore(application.applicationContext)

    private val _installed = MutableStateFlow<List<MiniAppManifest>>(emptyList())
    val installed: StateFlow<List<MiniAppManifest>> = _installed.asStateFlow()

    private val _market = MutableStateFlow<List<MiniAppMarketItem>>(emptyList())
    val market: StateFlow<List<MiniAppMarketItem>> = _market.asStateFlow()

    private val _marketState = MutableStateFlow("idle")
    val marketState: StateFlow<String> = _marketState.asStateFlow()

    private val _marketError = MutableStateFlow("")
    val marketError: StateFlow<String> = _marketError.asStateFlow()

    private val _installing = MutableStateFlow<Set<String>>(emptySet())
    val installing: StateFlow<Set<String>> = _installing.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _gridMode = MutableStateFlow(true)
    val gridMode: StateFlow<Boolean> = _gridMode.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var settings: MiniAppSettings? = null

    init {
        refresh()
    }

    fun setGridMode(grid: Boolean) {
        _gridMode.value = grid
    }

    fun refresh() {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                MiniAppSettings.load(getApplication())
            }
            settings = loaded
            withContext(Dispatchers.IO) {
                MiniAppBuiltin.ensureSeeded(store, getApplication())
            }
            refreshInstalled()
            _loading.value = false
            loadMarket()
        }
    }

    fun refreshInstalled() {
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) { store.listInstalled() }
            _installed.value = items
        }
    }

    fun loadMarket() {
        val current = settings ?: return
        viewModelScope.launch {
            val silent = _market.value.isNotEmpty()
            if (!silent) {
                _marketState.value = "loading"
                _marketError.value = ""
            }
            try {
                val items = withContext(Dispatchers.IO) { fetchMarket(current.marketUrl) }
                _market.value = items
                _marketState.value = if (items.isEmpty()) "empty" else "ready"
            } catch (e: Exception) {
                if (silent) return@launch
                _market.value = emptyList()
                _marketState.value = "error"
                _marketError.value = e.toString()
            }
        }
    }

    fun installFromZipBytes(bytes: ByteArray) {
        runInstall { store.installFromZipBytes(bytes) }
    }

    fun installFromDirectory(dir: File) {
        runInstall { store.installFromDirectory(dir) }
    }

    fun installFromUrl(url: String) {
        runInstall { store.installFromUrl(url) }
    }

    fun installMarketItem(item: MiniAppMarketItem) {
        if (_installing.value.contains(item.appId)) return
        viewModelScope.launch {
            _installing.value = _installing.value + item.appId
            try {
                withContext(Dispatchers.IO) {
                    store.installFromUrl(item.url, expectedMd5 = item.md5, expectedSize = item.size)
                }
                refreshInstalled()
                _events.emit(getApplication<Application>().getString(R.string.miniapp_installed, item.name))
            } catch (e: Exception) {
                _events.emit(getApplication<Application>().getString(R.string.miniapp_install_failed, e.toString()))
            } finally {
                _installing.value = _installing.value - item.appId
            }
        }
    }

    fun deleteApp(manifest: MiniAppManifest) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { store.deleteApp(manifest.appId) }
            refreshInstalled()
            _events.emit(getApplication<Application>().getString(R.string.miniapp_deleted))
        }
    }

    private fun runInstall(install: () -> MiniAppManifest) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            try {
                val manifest = withContext(Dispatchers.IO) { install() }
                refreshInstalled()
                _events.emit(getApplication<Application>().getString(R.string.miniapp_installed, manifest.name))
            } catch (e: Exception) {
                _events.emit(getApplication<Application>().getString(R.string.miniapp_install_failed, e.toString()))
            } finally {
                _busy.value = false
            }
        }
    }

    private fun fetchMarket(url: String): List<MiniAppMarketItem> {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        response.use {
            if (it.code != 200) {
                throw IllegalStateException("HTTP ${it.code}")
            }
            val text = it.body?.string() ?: ""
            return filterRemovedMarketItems(parseMiniAppMarketJson(text))
        }
    }
}
