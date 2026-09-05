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
                onClick = { viewModel.exportResourcePack() },
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
}

@Composable
private fun AudioListItem(
    item: AudioItem,
    onEdit: () -> Unit,
    onConvert: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onEdit)
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
                            style = MaterialTheme.typography.bodyMedium
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
                            text = "已转换",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    AudioStatus.FAILED -> {
                        val reason = item.error?.let { "：$it" } ?: ""
                        Text(
                            text = "失败$reason",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            if (item.status == AudioStatus.NOT_CONVERTED) {
                Spacer(Modifier.width(8.dp))
                Button(onClick = onConvert) {
                    Text("转换")
                }
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
                label = { Text("ID") },
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
