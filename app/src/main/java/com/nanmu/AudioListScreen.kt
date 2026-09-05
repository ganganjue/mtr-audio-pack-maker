package com.nanmu

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun AudioListScreen(
    modifier: Modifier = Modifier,
    viewModel: AudioViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    var editingItem by remember { mutableStateOf<AudioItem?>(null) }

    // 导出对话框状态
    var showExportDialog by remember { mutableStateOf(false) }
    var exportName by remember { mutableStateOf("我的MTR资源包") }
    var exportVersion by remember { mutableStateOf("1.0") }

    val addAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.addAudio(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { addAudioLauncher.launch(arrayOf("audio/*")) }
            ) {
                Text("添加音频")
            }

            Button(
                onClick = {
                    if (items.any { it.status == AudioStatus.CONVERTED }) {
                        showExportDialog = true
                    }
                },
                enabled = items.any { it.status == AudioStatus.CONVERTED } && !isExporting
            ) {
                Text(if (isExporting) "导出中..." else "导出资源包")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (items.isEmpty()) {
            Text(
                text = "尚未添加音频",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(items, key = { it.uid }) { item ->
                    AudioListItem(
                        item = item,
                        onEdit = { editingItem = item },
                        onConvert = { viewModel.convert(item.uid) }
                    )
                }
            }
        }
    }

    // 编辑 ID 对话框（原功能）
    editingItem?.let { target ->
        EditAudioIdDialog(
            currentId = target.soundId,
            onDismiss = { editingItem = null },
            onConfirm = { newId ->
                viewModel.updateSoundId(target.uid, newId)
                editingItem = null
            }
        )
    }

    // 导出设置对话框（新功能）
    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("导出资源包设置") },
            text = {
                Column {
                    OutlinedTextField(
                        value = exportName,
                        onValueChange = { exportName = it },
                        label = { Text("资源包名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = exportVersion,
                        onValueChange = { exportVersion = it },
                        label = { Text("版本号（如 1.0）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.exportResourcePack(
                            packName = exportName.ifBlank { "我的MTR资源包" },
                            packVersion = exportVersion.ifBlank { "1.0" }
                        )
                        showExportDialog = false
                    }
                ) {
                    Text("导出")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

// AudioListItem 和 EditAudioIdDialog 函数保持不变（省略，与之前相同）
// 请沿用您原有的这两个组件函数