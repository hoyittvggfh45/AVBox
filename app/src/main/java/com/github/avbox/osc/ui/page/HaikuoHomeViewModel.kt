package com.github.avbox.osc.ui.page

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.github.avbox.osc.haikuo.HaikuoDuplicate
import com.github.avbox.osc.haikuo.HaikuoDuplicateDecision
import com.github.avbox.osc.haikuo.HaikuoFormatException
import com.github.avbox.osc.haikuo.HaikuoImportResult
import com.github.avbox.osc.haikuo.HaikuoProgram
import com.github.avbox.osc.haikuo.HaikuoStore
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HaikuoDuplicatePrompt(
    val duplicates: List<HaikuoDuplicate>,
    val latch: CountDownLatch = CountDownLatch(1)
) {
    @Volatile
    var decision: HaikuoDuplicateDecision = HaikuoDuplicateDecision.keepBoth
}

class HaikuoHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val store = HaikuoStore(application.applicationContext)

    private val _programs = MutableStateFlow<List<HaikuoProgram>>(emptyList())
    val programs: StateFlow<List<HaikuoProgram>> = _programs.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _duplicatePrompt = MutableStateFlow<HaikuoDuplicatePrompt?>(null)
    val duplicatePrompt: StateFlow<HaikuoDuplicatePrompt?> = _duplicatePrompt.asStateFlow()

    init {
        refresh()
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _loading.value = true
            store.ensureBuiltinRules()
            _programs.value = store.list()
            _loading.value = false
        }
    }

    fun resolveDuplicates(decision: HaikuoDuplicateDecision) {
        val prompt = _duplicatePrompt.value ?: return
        prompt.decision = decision
        prompt.latch.countDown()
    }

    fun importText(text: String) {
        runImport { store.importText(text, "文本", ::askDuplicates) }
    }

    fun importUrl(url: String) {
        runImport { store.importUrl(url, ::askDuplicates) }
    }

    fun importBytes(bytes: ByteArray, name: String) {
        runImport { store.importBytes(bytes, "文件：$name", ::askDuplicates) }
    }

    fun remove(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            store.remove(id)
            _programs.value = store.list()
        }
    }

    private fun askDuplicates(duplicates: List<HaikuoDuplicate>): HaikuoDuplicateDecision {
        val prompt = HaikuoDuplicatePrompt(duplicates)
        _duplicatePrompt.value = prompt
        prompt.latch.await()
        _duplicatePrompt.value = null
        return prompt.decision
    }

    private fun runImport(block: () -> HaikuoImportResult) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = block()
                _programs.value = store.list()
                if (result.summary.isNotEmpty()) {
                    _events.emit(result.summary)
                }
            } catch (e: HaikuoFormatException) {
                _events.emit(e.message ?: "")
            } catch (e: Exception) {
                _events.emit(e.message ?: e.toString())
            }
        }
    }
}
