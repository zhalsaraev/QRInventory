package com.example.qrinventory

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.PermissionChecker
import com.example.qrinventory.ui.theme.QRInventoryTheme
import java.io.File
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val viewModel: InventoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            QRInventoryTheme {
                MainScreen(viewModel = viewModel, activity = this)
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: InventoryViewModel, activity: ComponentActivity) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var torchOn by remember { mutableStateOf(false) }

    val requestCamera = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) activity.finish()
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.loadFromUri(it) }
    }

    LaunchedEffect(Unit) {
        val granted = PermissionChecker.checkSelfPermission(
            activity, Manifest.permission.CAMERA
        ) == PermissionChecker.PERMISSION_GRANTED
        if (!granted) requestCamera.launch(Manifest.permission.CAMERA)
    }

    // При каждом новом сканировании — автопрокрутка на очередную строку
    LaunchedEffect(state.lines.size) {
        if (state.lines.isNotEmpty()) {
            listState.animateScrollToItem(state.lines.size - 1)
        }
    }
    // Автоскрытие всплывающих сообщений
    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(2500)
            viewModel.clearMessage()
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Верхняя половина: интерфейс камеры + кнопка подсветки
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
            ) {
                ScannerPreview(
                    modifier = Modifier.fillMaxSize(),
                    torchOn = torchOn,
                    onTorchToggle = { torchOn = it },
                    onScan = { viewModel.onScan(it) }
                )
                state.message?.let { msg ->
                    Text(
                        text = msg,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                    )
                }
            }

            HorizontalDivider()

            // Нижняя половина: имя файла, кнопки, список считанных
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.5f)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = state.fileName ?: "Ведомость не создана",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { viewModel.createNew() }) {
                        Text("Создать новую")
                    }
                    TextButton(onClick = {
                        openDocument.launch(
                            arrayOf("text/tab-separated-values", "text/plain", "*/*")
                        )
                    }) {
                        Text("Открыть")
                    }
                    TextButton(onClick = { viewModel.deleteCurrent() }) {
                        Text("Удалить")
                    }
                    TextButton(onClick = { shareCurrent(viewModel.state.value, activity) }) {
                        Text("Отправить")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(state.lines) { line ->
                        Text(
                            text = line.replace("\t", "  |  "),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Системный диалог share: файл через FileProvider, иначе — содержимое текстом. */
fun shareCurrent(state: InventoryViewModel.UiState, activity: ComponentActivity) {
    val ctx = activity
    val intent = when {
        state.filePath != null && File(state.filePath).exists() -> {
            val uri = FileProvider.getUriForFile(
                ctx, "${ctx.packageName}.fileprovider", File(state.filePath)
            )
            Intent(Intent.ACTION_SEND).apply {
                type = "text/tab-separated-values"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, state.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        state.sourceUri != null -> {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/tab-separated-values"
                putExtra(Intent.EXTRA_STREAM, state.sourceUri)
                putExtra(Intent.EXTRA_SUBJECT, state.fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        state.lines.isNotEmpty() -> {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, state.fileName)
                putExtra(Intent.EXTRA_TEXT, state.lines.joinToString("\n"))
            }
        }
        else -> null
    } ?: return
    ctx.startActivity(Intent.createChooser(intent, "Отправить ведомость"))
}
