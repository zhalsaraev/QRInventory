package com.example.qrinventory

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.qrinventory.ui.theme.QRInventoryTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel: InventoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            QRInventoryTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: InventoryViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var torchOn by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showOpenFileDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var newFileNameInput by remember { mutableStateOf("") }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }
    // ДИАЛОГ СОЗДАНИЯ
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text(text = "Создать новую ведомость?", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Текущий файл закроется. Вы сможете открыть его заново через каталог Documents/QRInventory.") },
            confirmButton = {
                TextButton(onClick = { showCreateDialog = false; viewModel.createNew() }) {
                    Text("Создать", color = Color(0xFF33BBEE), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Отмена", color = Color.Gray) }
            }
        )
    }

    // ДИАЛОГ КРИТИЧЕСКОГО ПОДТВЕРЖДЕНИЯ УДАЛЕНИЯ ВСЕХ ФАЙЛОВ
    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(text = "Удалить ВСЕ ведомости?", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Вы собираетесь навсегда стереть все файлы из папки Documents/QRInventory. Это действие полностью уничтожит историю сессий!") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        showOpenFileDialog = false
                        viewModel.deleteAllFiles()
                    }
                ) {
                    Text("Удалить всё", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) { Text("Отмена", color = Color.Gray) }
            }
        )
    }

    // ДИАЛОГ ПЕРЕИМЕНОВАНИЯ ПО КЛИКУ НА ЗАГОЛОВОК
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(text = "Переименовать ведомость", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(text = "Введите новое название (например, Кабинет 304):", fontSize = 14.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFileNameInput,
                        onValueChange = { newFileNameInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRenameDialog = false
                        viewModel.renameCurrentFile(newFileNameInput)
                    }
                ) {
                    Text("Сохранить", color = Color(0xFF33BBEE), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Отмена", color = Color.Gray) }
            }
        )
    }

    // ВНУТРЕННИЙ ПРОВОДНИК ФАЙЛОВ С МАКСИМАЛЬНЫМ РАЗНЕСЕНИЕМ КНОПОК
    if (showOpenFileDialog) {
        val savedFiles = remember(showOpenFileDialog, fileToDelete, showDeleteAllDialog) { viewModel.getSavedFiles() }
        AlertDialog(
            onDismissRequest = { showOpenFileDialog = false },
            title = { Text(text = "Выберите ведомость", fontWeight = FontWeight.Bold) },
            text = {
                if (savedFiles.isEmpty()) {
                    Text("В папке Documents/QRInventory пока нет файлов.")
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(savedFiles) { file ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                TextButton(
                                    onClick = { showOpenFileDialog = false; viewModel.loadFromUri(android.net.Uri.fromFile(file)) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                        Text(text = file.name, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                        Text(
                                            text = "Изменен: " + android.text.format.DateFormat.format("dd.MM.yyyy HH:mm", java.util.Date(file.lastModified())),
                                            color = Color.Gray, fontSize = 11.sp
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { fileToDelete = file },
                                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "Удалить", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (savedFiles.isNotEmpty()) {
                        TextButton(onClick = { showDeleteAllDialog = true }) {
                            Text("Удалить всё", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    TextButton(onClick = { showOpenFileDialog = false }) {
                        Text("Закрыть", color = Color.Gray)
                    }
                }
            },
            dismissButton = null
        )
    }

    // ДИАЛОГ ПОДТВЕРЖДЕНИЯ УДАЛЕНИЯ ОДНОГО ФАЙЛА
    fileToDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { fileToDelete = null },
            title = { Text(text = "Удалить файл?", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Вы собираетесь навсегда удалить файл ${file.name}.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteFile(file); fileToDelete = null }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { fileToDelete = null }) { Text("Отмена", color = Color.Gray) }
            }
        )
    }
    Column(modifier = Modifier.fillMaxSize()) {
        // Верхняя половина: Сканер
        Box(modifier = Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            if (hasCameraPermission) {
                ScannerPreview(
                    modifier = Modifier.fillMaxSize(),
                    torchOn = torchOn,
                    onTorchToggle = { torchOn = it },
                    onScan = { viewModel.onScan(it) }
                )
            } else {
                Text(text = "Требуется разрешение на камеру", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }

        // Нижняя половина: Информация и трехколоночная таблица
        Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            newFileNameInput = state.fileName?.removeSuffix(".tsv") ?: ""
                            showRenameDialog = true
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.fileName ?: "Нет открытого файла",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Переименовать",
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.shareCurrentFile() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Поделиться",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(text = "Всего строк: ${state.lines.size}", fontSize = 13.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(10.dp))

            // ЗАГОЛОВОК ТРЕХКОЛОНОЧНОЙ ТАБЛИЦЫ
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = "№",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.width(30.dp)
                )
                Text(
                    text = "Инвентарный №",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Серийный №",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )
            }

            val listState = rememberLazyListState()
            LaunchedEffect(state.lines.size) {
                if (state.lines.isNotEmpty()) listState.animateScrollToItem(state.lines.size - 1)
            }

            // ИСПРАВЛЕННАЯ ТАБЛИЦА С ГЛУБОКИМ ПАРСИНГОМ СТРОК С ПРИНТЕРА
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(state.lines.size) { index ->
                    val line = state.lines[index]

                    // 1. Сначала бьем всю строку по знакам табуляции
                    val allTokens = line.split("\t").map { it.trim() }.filter { it.isNotEmpty() }

                    var inventoryNum = ""
                    var serialNum = ""

                    // 2. Если у нас 3 или более токенов, значит QR-код внутри содержал собственный разделитель
                    if (allTokens.size >= 3) {
                        inventoryNum = allTokens.getOrNull(0) ?: ""
                        serialNum = allTokens.getOrNull(1) ?: ""
                    } else {
                        // 3. Если токена 2, проверяем, нет ли внутри первого токена скрытых переносов строк (\n или \r)
                        val firstToken = allTokens.getOrNull(0) ?: ""
                        val subTokens = when {
                            firstToken.contains("\r\n") -> firstToken.split("\r\n")
                            firstToken.contains("\n") -> firstToken.split("\n")
                            else -> emptyList()
                        }

                        if (subTokens.size >= 2) {
                            inventoryNum = subTokens.getOrNull(0)?.trim() ?: ""
                            serialNum = subTokens.getOrNull(1)?.trim() ?: ""
                        } else {
                            // Обычный тестовый одиночный код без серийника
                            inventoryNum = firstToken
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 13.sp,
                                color = Color.Gray,
                                modifier = Modifier.width(30.dp)
                            )
                            Text(
                                text = inventoryNum,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = serialNum,
                                fontWeight = FontWeight.Normal,
                                fontSize = 13.sp,
                                color = if (serialNum.isNotEmpty()) MaterialTheme.colorScheme.onSurface else Color.Gray,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (index < state.lines.size - 1) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0x1A000000))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { showCreateDialog = true }, modifier = Modifier.weight(1f)) { Text("Новый") }
                Button(onClick = { showOpenFileDialog = true }, modifier = Modifier.weight(1f)) { Text("Открыть") }
            }
        }
    }
}
