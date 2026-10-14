package com.example.qrinventory

import android.app.Application
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.OpenableColumns
import android.text.format.DateFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InventoryViewModel(application: Application) : AndroidViewModel(application) {

    data class UiState(
        val fileName: String? = null,
        val filePath: String? = null,
        val sourceUri: Uri? = null,
        val lines: List<String> = emptyList(),
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var lastCode: String? = null
    private var lastScanAt = 0L
    private var tone: ToneGenerator? = null

    // Мьютекс гарантирует, что записи в файл не наложатся друг на друга
    private val fileWriteMutex = Mutex()

    private val filesDir: File
        get() = getApplication<Application>().getExternalFilesDir(null)!!

    fun createNew() {
        viewModelScope.launch(Dispatchers.IO) {
            val name = "inventory_" +
                    DateFormat.format("yyyy-MM-dd_HH-mm-ss", Date()) + ".tsv"
            val file = File(filesDir, name)
            file.writeText("")
            _state.value = UiState(fileName = name, filePath = file.absolutePath)
            _state.updateMessage("Создана ведомость: $name")
        }
    }

    /** Вызывается при каждом обнаружении кода. Дедупликация: повтор того же кода в пределах 3 с игнорируется. */
    fun onScan(raw: String) {
        val code = raw.trim()
        if (code.isEmpty()) return
        val now = System.currentTimeMillis()
        if (code == lastCode && now - lastScanAt < 3000L) return
        lastCode = code
        lastScanAt = now

        val stamp = DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(now))
        val line = "$code\t$stamp"
        val filePath = _state.value.filePath ?: return

        _state.update { it.copy(lines = it.lines + line, message = null) }
        beep()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                fileWriteMutex.withLock {
                    File(filePath).appendText(line + "\n")
                }
            }
        }
    }

    fun deleteCurrent() {
        val path = _state.value.filePath
        if (path != null) {
            viewModelScope.launch(Dispatchers.IO) {
                runCatching { fileWriteMutex.withLock { File(path).delete() } }
            }
        }
        lastCode = null
        _state.value = UiState()
        _state.updateMessage("Ведомость удалена")
    }

    fun loadFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = try {
                getApplication<Application>().contentResolver
                    .openInputStream(uri)?.use { it.bufferedReader().readText() }
            } catch (e: Exception) { null }
                ?: return@launch
            val lines = text.split('\n').map { it.trimEnd() }.filter { it.isNotEmpty() }
            val name = try {
                var n = "file.tsv"
                getApplication<Application>().contentResolver.query(
                    uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null
                )?.use { c ->
                    val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && c.moveToFirst()) n = c.getString(idx)
                }
                n
            } catch (e: Exception) { "file.tsv" }

            _state.value = UiState(fileName = name, lines = lines, sourceUri = uri)
            _state.updateMessage("Загружено строк: ${lines.size}")
        }
    }

    fun clearMessage() {
        _state.update { if (it.message != null) it.copy(message = null) else it }
    }

    private fun MutableStateFlow<UiState>.updateMessage(msg: String) {
        value = value.copy(message = msg)
    }

    private fun beep() {
        try {
            val tg = tone ?: ToneGenerator(
                AudioManager.STREAM_NOTIFICATION, ToneGenerator.MAX_VOLUME
            ).also { tone = it }
            tg.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) {
        }
    }

    // Освобождаем нативные аудио-ресурсы при уничтожении ViewModel
    override fun onCleared() {
        super.onCleared()
        try {
            tone?.release()
            tone = null
        } catch (_: Exception) {}
    }
}
