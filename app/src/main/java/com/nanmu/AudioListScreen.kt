package com.nanmu

import android.net.Uri
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextButtonDefaults
import androidx.compose.material3.TextButtonDefaults
import androidx.compose.material3.TextButtonDefaults
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
import kotlinx.coroutines.launch

// 游戏版本映射表
data class McVersion(val displayName: String, val packFormat: Int)

val versions = listOf(
    McVersion("1.20 - 1.20.1", 15),
    McVersion("1.20.2", 18),
    McVersion("1.20.3 - 1.20.4", 22),
    McVersion("1.20.5 - 1.20.6", 32),
    McVersion("1.21 - 1.21.1", 34),
    McVersion("1.21.2 - 1.21.4", 40),
    McVersion("1.21.5", 44)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioListScreen(
    modifier: Modifier = Modifier,
    viewModel: AudioViewModel = hiltViewModel()
) {
    val items by viewModel.items.collectAsState()
    val isExporting by viewModel.isExporting.collectAsState()
    val isBatchConverting by viewModel.isBatchConverting.collectAsState()
    val scope = rememberCoroutineScope()

    var editingItem by remember { mutableStateOf<AudioItem?>(null) }
    var deletingItem by remember { mutableStateOf<AudioItem?>(null) }

    // 导出对话框状态
    var showExportDialog by remember { mutableStateOf(false) }
    var exportName by remember { mutableStateOf("我的MTR资源包") }
    var exportVersion by remember { mutableStateOf("1.0") }
    var selectedVersion by remember { mutableStateOf(versions.first()) }
    var expanded by remember { mutableStateOf(false) }

    // 文件选择器（保存 ZIP 到用户选择的位置）
    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    viewModel.exportResourcePackToUri(
                        uri = uri,
                        packName = exportName.ifBlank { "我的MTR资源包" },
                        packVersion = exportVersion.ifBlank { "1.0" },
                        packFormat = selectedVersion.packFormat
                    )
                    // 显示成功消息（可选）
                } catch (e: Exception) {
                    // 处理错误
                }
            }
        }
    }

    val addAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.addAudio(it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 顶部按钮行
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

            OutlinedButton(
                onClick = { viewModel.convertAll() },
                enabled = items.any { it.status == AudioStatus.NOT_CONVERTED } && !isBatchConverting
            ) {
                Text(if (isBatchConverting) "批量转换中..." else "全部转换")
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

        // 列表
        if (items.isEmpty()) {
            Text(
                text = "尚未添加音频，点击「添加音频」导入文件",
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
                        onConvert = { viewModel.convert(item.uid) },
                        onRetry = { viewModel.retry(item.uid) },
                        onDelete = { deletingItem = item }
                    )
                }
            }
        }
    }

    // 删除确认对话框
    if (deletingItem != null) {
        AlertDialog(
            onDismissRequest = { deletingItem = null },
            title = { Text("确认删除") },
            text = { Text("确定要删除「${deletingItem?.fileName}」吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        deletingItem?.let { viewModel.deleteAudio(it.uid) }
                        deletingItem = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingItem = null }) {
                    Text("取消")
                }
            }
        )
    }

    // 编辑 ID 对话框
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

    // 导出设置对话框
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
                    Spacer(Modifier.height(8.dp))

                    // 游戏版本选择下拉菜单
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedVersion.displayName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Minecraft 版本") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            versions.forEach { version ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = version.displayName,
                                                fontWeight = if (version == selectedVersion) FontWeight.Bold else FontWeight.Normal
                                            )
                                            Text(
                                                text = "pack_format: ${version.packFormat}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedVersion = version
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "当前 pack_format = ${selectedVersion.packFormat}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // 先关闭对话框，然后启动文件选择器
                        showExportDialog = false
                        // 生成文件名
                        val fileName = "${exportName.ifBlank { "我的MTR资源包" }}_v${exportVersion.ifBlank { "1.0" }}.zip"
                        saveFileLauncher.launch(fileName)
                    }
                ) {
                    Text("选择保存位置")
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

@Composable
private fun AudioListItem(
    item: AudioItem,
    onEdit: () -> Unit,
    onConvert: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "ID：${item.soundId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))

                when (item.status) {
                    AudioStatus.NOT_CONVERTED -> {
                        Text(
                            text = "未转换",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AudioStatus.CONVERTING -> {
                        val percent = (item.progress * 100).toInt()
                        Text(
                            text = "转换中 $percent%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = item.progress,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AudioStatus.CONVERTED -> {
                        Text(
                            text = "✅ 已转换",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    AudioStatus.FAILED -> {
                        val reason = item.error?.let { "：$it" } ?: ""
                        Text(
                            text = "❌ 失败$reason",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 右侧操作按钮
            Spacer(Modifier.width(8.dp))
            when (item.status) {
                AudioStatus.NOT_CONVERTED -> {
                    Button(onClick = onConvert) {
                        Text("转换")
                    }
                }
                AudioStatus.FAILED -> {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("重试")
                    }
                }
                AudioStatus.CONVERTED -> {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                else -> {}
            }
            // 删除按钮
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = onDelete,
                colors = TextButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("✕")
            }
        }
    }
}

@Composable
private fun EditAudioIdDialog(
    currentId: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑音频 ID") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("ID（如 my_door）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}