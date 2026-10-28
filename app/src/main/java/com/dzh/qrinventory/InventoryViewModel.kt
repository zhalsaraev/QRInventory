package com.dzh.qrinventory

import android.app.Application
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.text.format.DateFormat
import androidx.core.content.FileProvider
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

    private var tone: ToneGenerator? = null
    private val fileWriteMutex = Mutex()

    private val filesDir: File
        get() = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            .let { File(it, "QRInventory") }
            .apply { if (!exists()) mkdirs() }

    init {
        restoreOrCreateTodaySession()
    }

    private fun restoreOrCreateTodaySession() {
        viewModelScope.launch(Dispatchers.IO) {
            val allFiles = filesDir.listFiles()

            val mostRecentFile = allFiles?.filter {
                it.name.endsWith(".tsv")
            }?.maxByOrNull { it.lastModified() }

            if (mostRecentFile != null) {
                val text = runCatching { mostRecentFile.readText() }.getOrDefault("")
                val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

                _state.value = UiState(
                    fileName = mostRecentFile.name,
                    filePath = mostRecentFile.absolutePath,
                    lines = lines
                )
                _state.updateMessage("Восстановлена сессия: ${mostRecentFile.name}")
            } else {
                createNew()
            }
        }
    }


    fun createNew() {
        viewModelScope.launch(Dispatchers.IO) {
            val name = "inventory_" + DateFormat.format("yyyy-MM-dd_HH-mm-ss", Date()) + ".tsv"
            val file = File(filesDir, name)
            file.writeText("")

            _state.value = UiState(fileName = name, filePath = file.absolutePath)
            _state.updateMessage("Создана ведомость: $name")
        }
    }

    private val scanHistoryMap = mutableMapOf<String, Long>()

    fun onScan(raw: String) {
        val code = raw.trim()
        if (code.isEmpty()) return

        val now = System.currentTimeMillis()
        scanHistoryMap.entries.removeIf { now - it.value > 3000L }

        if (scanHistoryMap.containsKey(code)) return
        scanHistoryMap[code] = now

        val incomingSubParts = code.split("\t", "\n", "\r").map { it.trim() }.filter { it.isNotEmpty() }
        val incomingInventoryNum = incomingSubParts.getOrNull(0) ?: ""

        val codeExists = _state.value.lines.any { line ->
            val tokens = line.split("\t").map { it.trim() }
            if (tokens.size >= 3) {
                val existingInventoryNum = tokens[2]
                existingInventoryNum == incomingInventoryNum
            } else false
        }

        if (codeExists) {
            beep(isError = true)
            _state.updateMessage("⚠️ Код $incomingInventoryNum уже есть в ведомости!")
            return
        }

        val stamp = DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(now)).toString()
        val line = "$stamp\t\t$code"
        val filePath = _state.value.filePath ?: return

        _state.update { it.copy(lines = it.lines + line, message = null) }
        beep(isError = false)

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                fileWriteMutex.withLock { File(filePath).appendText(line + "\n") }
            }
        }
    }

    fun addCommentToLine(index: Int, comment: String) {
        val filePath = _state.value.filePath ?: return
        val currentLines = _state.value.lines.toMutableList()
        val targetLine = currentLines.getOrNull(index) ?: return

        val tokens = targetLine.split("\t").map { it.trim() }
        if (tokens.size < 3) return

        val stamp = tokens[0]
        val cleanedComment = comment.trim().replace("\n", " ").replace("\t", " ")
        val qrDataAndRemainder = tokens.subList(2, tokens.size).joinToString("\t")

        val newLine = "$stamp\t$cleanedComment\t$qrDataAndRemainder"
        currentLines[index] = newLine
        _state.update { it.copy(lines = currentLines) }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                fileWriteMutex.withLock {
                    val file = File(filePath)
                    file.writeText(currentLines.joinToString("\n") + "\n")
                }
            }
            _state.updateMessage("Комментарий сохранен")
        }
    }

    fun deleteLineAt(index: Int) {
        val filePath = _state.value.filePath ?: return
        val currentLines = _state.value.lines.toMutableList()
        if (index in currentLines.indices) {
            currentLines.removeAt(index)
            _state.update { it.copy(lines = currentLines) }

            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    fileWriteMutex.withLock {
                        val file = File(filePath)
                        file.writeText(currentLines.joinToString("\n") + if (currentLines.isEmpty()) "" else "\n")
                    }
                }
                _state.updateMessage("Строка удалена")
            }
        }
    }

    fun renameCurrentFile(newCustomName: String) {
        val currentPath = _state.value.filePath ?: return
        var cleanedName = newCustomName.trim()
        if (cleanedName.isEmpty()) return
        if (!cleanedName.endsWith(".tsv", ignoreCase = true)) cleanedName += ".tsv"

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                fileWriteMutex.withLock {
                    val oldFile = File(currentPath)
                    val newFile = File(filesDir, cleanedName)
                    if (oldFile.exists() && !newFile.exists()) {
                        if (oldFile.renameTo(newFile)) {
                            _state.update { it.copy(fileName = cleanedName, filePath = newFile.absolutePath) }
                            _state.updateMessage("Переименовано в: $cleanedName")
                        }
                    } else if (newFile.exists()) {
                        _state.updateMessage("Файл с таким именем уже существует!")
                    }
                }
            }
        }
    }

    fun shareCurrentFile() {
        val currentPath = _state.value.filePath ?: return
        val file = File(currentPath)
        if (!file.exists()) return

        val context = getApplication<Application>()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/tab-separated-values"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooserIntent = Intent.createChooser(intent, "Отправить ведомость...").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooserIntent)
    }

    fun getSavedFiles(): List<File> {
        return filesDir.listFiles()?.filter { it.name.endsWith(".tsv") }?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun deleteFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { fileWriteMutex.withLock { if (file.exists()) file.delete() } }
            if (_state.value.filePath == file.absolutePath) {
                _state.value = UiState()
                _state.updateMessage("Текущая ведомость удалена")
                createNew()
            } else {
                _state.updateMessage("Файл ${file.name} удален")
            }
        }
    }

    fun deleteAllFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                fileWriteMutex.withLock {
                    filesDir.listFiles()?.forEach { if (it.name.endsWith(".tsv")) it.delete() }
                }
            }
            _state.value = UiState()
            _state.updateMessage("Все сохраненные ведомости удалены")
            createNew()
        }
    }

    fun loadFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = try {
                getApplication<Application>().contentResolver.openInputStream(uri)
                    ?.use { it.bufferedReader().readText() }
            } catch (e: Exception) {
                null
            } ?: return@launch
            val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            var name = if (uri.scheme == "file") File(uri.path ?: "").name else "file.tsv"

            if (uri.scheme == "content") {
                runCatching {
                    getApplication<Application>().contentResolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null
                    )?.use { c ->
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0 && c.moveToFirst()) name = c.getString(idx)
                    }
                }
            }

            val matchedFile = File(filesDir, name)
            _state.value = UiState(fileName = name, lines = lines, filePath = matchedFile.absolutePath, sourceUri = uri)
            _state.updateMessage("Загружена ведомость: $name")
        }
    }

    fun clearMessage() {
        _state.update { if (it.message != null) it.copy(message = null) else it }
    }

    private fun MutableStateFlow<UiState>.updateMessage(msg: String) {
        value = value.copy(message = msg)
    }

    private fun beep(isError: Boolean = false) {
        try {
            val tg = tone ?: ToneGenerator(AudioManager.STREAM_MUSIC, 85).also { tone = it }
            if (isError) tg.startTone(
                ToneGenerator.TONE_CDMA_DIAL_TONE_LITE,
                350
            ) else tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (_: Exception) {
        }
    }

    override fun onCleared() {
        runCatching { tone?.release(); tone = null }
    }
}
