// #2.1
package com.dzh.qrinventory

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.dzh.qrinventory.ui.theme.QRInventoryTheme
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
// #2.2
@OptIn(ExperimentalFoundationApi::class)
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

    var showCommentDialog by remember { mutableStateOf(false) }
    var showLineDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedLineIndex by remember { mutableStateOf(-1) }
    var commentInput by remember { mutableStateOf("") }

    var fileToDelete by remember { mutableStateOf<File?>(null) }
    var newFileNameInput by remember { mutableStateOf("") }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

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
// #2.3
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

    if (showCommentDialog) {
        AlertDialog(
            onDismissRequest = { showCommentDialog = false },
            title = { Text(text = "Добавить комментарий", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            showCommentDialog = false
                            showLineDeleteConfirmDialog = true
                        },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(imageVector = Icons.Filled.Delete, contentDescription = "Удалить строку")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { showCommentDialog = false }) { Text("Отмена", color = Color.Gray) }
                        TextButton(
                            onClick = {
                                showCommentDialog = false
                                viewModel.addCommentToLine(selectedLineIndex, commentInput)
                            }
                        ) {
                            Text("Сохранить", color = Color(0xFF33BBEE), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            dismissButton = null
        )
    }

    if (showLineDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLineDeleteConfirmDialog = false },
            title = { Text(text = "Удалить эту строку?", fontWeight = FontWeight.Bold) },
            text = { Text(text = "Выбранный код будет навсегда удален из текущей ведомости.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLineDeleteConfirmDialog = false
                        viewModel.deleteLineAt(selectedLineIndex)
                    }
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLineDeleteConfirmDialog = false }) { Text("Отмена", color = Color.Gray) }
            }
        )
    }

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
                    TextButton(onClick = { showOpenFileDialog = false }) { Text("Закрыть", color = Color.Gray) }
                }
            },
            dismissButton = null
        )
    }

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

// #2.4
    Column(modifier = Modifier.fillMaxSize()) {
        // Камера получила компактный вес 0.8f (не загромождает интерфейс)
        Box(
            modifier = Modifier
                .weight(0.8f)
                .fillMaxWidth()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (hasCameraPermission) {
                ScannerPreview(
                    modifier = Modifier.fillMaxSize(),
                    torchOn = torchOn,
                    onTorchToggle = { torchOn = it },
                    onCreateNewClick = { showCreateDialog = true },
                    onOpenFileClick = { showOpenFileDialog = true },
                    onShareClick = { viewModel.shareCurrentFile() },
                    onScan = { viewModel.onScan(it) }
                )
            } else {
                Text(text = "Требуется разрешение на камеру", color = Color.White, modifier = Modifier.align(Alignment.Center))
            }
        }

        // Информационная панель получила вес 1.2f (больше места под таблицу позиций)
        Column(modifier = Modifier.weight(1.2f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium)
                    .padding(4.dp)
            ) {
                // Название ведомости расположено на самом верху бежевой плашки
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .clickable {
                            newFileNameInput = state.fileName?.removeSuffix(".tsv") ?: ""
                            showRenameDialog = true
                        },
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.fileName ?: "Нет открытого файла",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Переименовать",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // Заголовки столбцов ведомости
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Text(text = "№", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.width(30.dp))
                    Text(text = "Инвентарный №", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                    Text(text = "Серийный №", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray, modifier = Modifier.weight(1f))
                }

                val listState = rememberLazyListState()
                LaunchedEffect(state.lines.size) {
                    if (state.lines.isNotEmpty()) listState.animateScrollToItem(state.lines.size - 1)
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.lines.size) { index ->
                        val line = state.lines[index]
                        val tokens = line.split("\t").map { it.trim() }

                        val commentText = tokens.getOrNull(1) ?: ""
                        val hasComment = commentText.isNotEmpty()

                        val inventoryNum = tokens.getOrNull(2) ?: ""
                        val serialNum = tokens.getOrNull(3) ?: ""

                        val rowBackgroundColor = if (hasComment) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        } else {
                            Color.Transparent
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBackgroundColor, shape = RoundedCornerShape(4.dp))
                                .combinedClickable(
                                    onClick = { },
                                    onLongClick = {
                                        selectedLineIndex = index
                                        commentInput = commentText
                                        showCommentDialog = true
                                    }
                                )
                                .padding(horizontal = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "${index + 1}", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.width(30.dp))
                                Text(text = inventoryNum, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(
                                    text = serialNum,
                                    fontWeight = FontWeight.Normal,
                                    fontSize = 13.sp,
                                    color = if (serialNum.isNotEmpty()) MaterialTheme.colorScheme.onSurface else Color.Gray,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            if (index < state.lines.size - 1 && !hasComment) {
                                Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x0F000000)))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(text = "Всего строк: ${state.lines.size}", fontSize = 13.sp, color = Color.Gray)
            }
        }
    }
}
