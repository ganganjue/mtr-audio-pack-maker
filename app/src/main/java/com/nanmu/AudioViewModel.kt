package com.nanmu

import android.content.ContentValues
import kotlinx.coroutines.delay
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.Log
import com.arthenica.ffmpegkit.ReturnCode
import com.arthenica.ffmpegkit.Statistics
import com.arthenica.ffmpegkit.StatisticsCallback
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class AudioStatus {
    NOT_CONVERTED,
    CONVERTING,
    CONVERTED,
    FAILED
}

data class AudioItem(
    val uid: String,
    val fileName: String,
    val soundId: String,
    val status: AudioStatus,
    val progress: Float,
    val error: String?
)

private data class AudioRecord(
    val uid: String,
    val fileName: String,
    val soundId: String,
    val inputFile: File,
    val outputFile: File?,
    val status: AudioStatus,
    val progress: Float,
    val error: String?
) {
    fun toItem(): AudioItem = AudioItem(
        uid = uid,
        fileName = fileName,
        soundId = soundId,
        status = status,
        progress = progress,
        error = error
    )
}

internal data class SoundEntry(val sounds: List<SoundName>)
internal data class SoundName(val name: String)

@HiltViewModel
class AudioViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _records = MutableStateFlow<List<AudioRecord>>(emptyList())
    private val _items = MutableStateFlow<List<AudioItem>>(emptyList())
    private val _isExporting = MutableStateFlow(false)
    private val _isBatchConverting = MutableStateFlow(false)
    private val _events = Channel<String>(Channel.BUFFERED)

    val items: StateFlow<List<AudioItem>> = _items.asStateFlow()
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()
    val isBatchConverting: StateFlow<Boolean> = _isBatchConverting.asStateFlow()
    val events = _events.receiveAsFlow()

    private val durationRegex = Regex("""Duration:\s*(\d{2}):(\d{2}):(\d{2}(?:\.\d+)?)""")

    init {
        refreshUi()
    }

    // ============ 音频管理 ============

    fun addAudio(uri: Uri) {
        viewModelScope.launch {
            try {
                val record = withContext(Dispatchers.IO) { importAudio(uri) }
                _records.update { it + record }
                refreshUi()
                _events.send("已添加：${record.fileName}")
            } catch (t: Throwable) {
                _events.send("添加音频失败：${t.message}")
            }
        }
    }

    fun deleteAudio(uid: String) {
        viewModelScope.launch {
            val record = _records.value.find { it.uid == uid }
            if (record != null) {
                withContext(Dispatchers.IO) {
                    record.inputFile.delete()
                    record.outputFile?.delete()
                }
                _records.update { list -> list.filter { it.uid != uid } }
                refreshUi()
                _events.send("已删除：${record.fileName}")
            }
        }
    }

    // ============ 转换 ============

    fun convert(uid: String) {
        viewModelScope.launch {
            val current = _records.value.firstOrNull { it.uid == uid } ?: return@launch
            if (current.status == AudioStatus.CONVERTING || current.status == AudioStatus.CONVERTED) {
                return@launch
            }

            updateRecord(uid) {
                it.copy(status = AudioStatus.CONVERTING, progress = 0f, error = null)
            }

            try {
                val outputFile = transcode(current)
                updateRecord(uid) {
                    it.copy(
                        status = AudioStatus.CONVERTED,
                        progress = 1f,
                        outputFile = outputFile,
                        error = null
                    )
                }
                _events.send("转换完成：${current.fileName}")
            } catch (t: Throwable) {
                updateRecord(uid) {
                    it.copy(
                        status = AudioStatus.FAILED,
                        progress = 0f,
                        error = t.message
                    )
                }
                _events.send("转换失败：${current.fileName} - ${t.message}")
            }
        }
    }

    fun retry(uid: String) {
        val record = _records.value.find { it.uid == uid }
        if (record?.status == AudioStatus.FAILED) {
            updateRecord(uid) {
                it.copy(status = AudioStatus.NOT_CONVERTED, progress = 0f, error = null)
            }
            convert(uid)
        }
    }

    fun convertAll() {
        if (_isBatchConverting.value) return
        viewModelScope.launch {
            _isBatchConverting.value = true
            val pending = _records.value.filter { it.status == AudioStatus.NOT_CONVERTED }
            if (pending.isEmpty()) {
                _events.send("没有待转换的音频")
                _isBatchConverting.value = false
                return@launch
            }
            _events.send("开始批量转换 ${pending.size} 个音频...")
            var successCount = 0
            var failCount = 0
            for (record in pending) {
                convert(record.uid)
                // 等待转换完成（轮询状态变化）
                var attempts = 0
                while (attempts < 60) { // 最多等待30秒
                    val current = _records.value.find { it.uid == record.uid }
                    if (current?.status == AudioStatus.CONVERTED) {
                        successCount++
                        break
                    } else if (current?.status == AudioStatus.FAILED) {
                        failCount++
                        break
                    }
                    delay(500)
                    attempts++
                }
            }
            _isBatchConverting.value = false
            _events.send("批量转换完成：成功 $successCount，失败 $failCount")
        }
    }

    // ============ ID 管理 ============

    fun updateSoundId(uid: String, newId: String) {
        val cleanId = normalizeId(newId)
        if (cleanId.isBlank()) return
        val record = _records.value.find { it.uid == uid }
        if (record?.status == AudioStatus.CONVERTED) {
            record.outputFile?.delete()
        }
        updateRecord(uid) { old ->
            old.copy(
                soundId = cleanId,
                outputFile = null,
                status = if (old.status == AudioStatus.CONVERTED) AudioStatus.NOT_CONVERTED else old.status,
                progress = 0f
            )
        }
    }

    // ============ 导出 ============

    fun exportResourcePack() {
        exportResourcePack("我的MTR资源包", "1.0", 15)
    }

    fun exportResourcePack(packName: String, packVersion: String, packFormat: Int = 15) {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    createAndSavePack(packName, packVersion, packFormat)
                }
                _events.send("资源包已导出到 $result")
            } catch (t: Throwable) {
                _events.send("导出失败：${t.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    // ============ 私有方法 ============

    private fun importAudio(uri: Uri): AudioRecord {
        val fileName = queryDisplayName(uri) ?: "audio_${System.currentTimeMillis()}"
        val uid = UUID.randomUUID().toString()
        val extension = fileName.substringAfterLast('.', missingDelimiterValue = "")
            .take(8)
            .ifBlank { "audio" }
        val inputDir = File(context.filesDir, "inputs").apply { mkdirs() }
        val inputFile = File(inputDir, "${uid}.$extension")

        context.contentResolver.openInputStream(uri)?.use { input ->
            inputFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IOException("无法读取所选音频")

        return AudioRecord(
            uid = uid,
            fileName = fileName,
            soundId = generateDefaultId(fileName),
            inputFile = inputFile,
            outputFile = null,
            status = AudioStatus.NOT_CONVERTED,
            progress = 0f,
            error = null
        )
    }

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = context.contentResolver
        return resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private suspend fun transcode(record: AudioRecord): File {
        val outputDir = File(context.filesDir, "converted").apply { mkdirs() }
        val outputFile = File(outputDir, "${record.soundId}.ogg")
        outputFile.delete()

        val command = "-i ${record.inputFile.absolutePath} " +
                "-ac 1 -c:a libvorbis -q:a 4 -y ${outputFile.absolutePath}"

        return suspendCancellableCoroutine { continuation ->
            var durationMs = 0L

            try {
                val session = FFmpegKit.executeAsync(
                    command,
                    { session: FFmpegSession? ->
                        if (session != null && ReturnCode.isSuccess(session.returnCode)) {
                            if (outputFile.exists()) {
                                continuation.resume(outputFile)
                            } else {
                                continuation.resumeWithException(
                                    IOException("转换命令已结束，但未找到输出文件")
                                )
                            }
                        } else {
                            val error = session?.failStackTrace ?: "FFmpeg 转换失败"
                            continuation.resumeWithException(IOException(error))
                        }
                    },
                    { log: Log? ->
                        val msg = log?.message ?: ""
                        parseDurationMs(msg)?.let { if (it > 0L) durationMs = it }
                    },
                    object : StatisticsCallback {
                        override fun apply(statistics: Statistics?) {
                            val stat = statistics ?: return
                            val totalMs = durationMs.toDouble()
                            val progress = if (totalMs > 0.0) {
                                (statistics.time / totalMs).coerceIn(0.0, 1.0).toFloat()
                            } else {
                                0f
                            }
                            updateRecord(record.uid) { old ->
                                old.copy(
                                    status = AudioStatus.CONVERTING,
                                    progress = progress,
                                    error = null
                                )
                            }
                        }
                    }
                )

                continuation.invokeOnCancellation {
                    FFmpegKit.cancel(session.sessionId)
                }
            } catch (t: Throwable) {
                continuation.resumeWithException(t)
            }
        }
    }

    private fun parseDurationMs(text: String): Long? {
        val match = durationRegex.find(text) ?: return null
        val hours = match.groupValues[1].toLong()
        val minutes = match.groupValues[2].toLong()
        val seconds = match.groupValues[3].toDouble()
        return (hours * 3600 + minutes * 60) * 1000L + (seconds * 1000).toLong()
    }

    private fun createAndSavePack(packName: String, packVersion: String, packFormat: Int): String {
        val converted = _records.value.filter {
            it.status == AudioStatus.CONVERTED && it.outputFile?.exists() == true
        }
        if (converted.isEmpty()) {
            throw IllegalStateException("没有已转换的音频可导出")
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val exportDir = File(context.cacheDir, "export_$timestamp").apply {
            deleteRecursively()
            mkdirs()
        }
        val mtrAssetsDir = File(exportDir, "assets/mtr").apply { mkdirs() }

        // 复制 OGG 文件
        converted.forEach { record ->
            val baseName = record.soundId
            val targetFile = File(mtrAssetsDir, "$baseName.ogg")
            record.outputFile?.copyTo(targetFile, overwrite = true)
        }

        // 生成 sounds.json
        val soundMap = linkedMapOf<String, SoundEntry>()
        converted.forEach { record ->
            val baseName = record.soundId
            soundMap[baseName] = SoundEntry(listOf(SoundName("mtr:$baseName")))
        }
        File(mtrAssetsDir, "sounds.json").writeText(soundMapAdapter.toJson(soundMap))

        // 生成 pack.mcmeta
        val packMcmeta = File(exportDir, "pack.mcmeta")
        val description = packName.replace("\"", "\\\"")
        val mcmetaContent = """{
  "pack": {
    "pack_format": $packFormat,
    "description": "$description"
  }
}"""
        packMcmeta.writeText(mcmetaContent)

        // 打包 ZIP
        val safeName = packName.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]"), "_")
        val zipFileName = "${safeName}_v${packVersion}_$timestamp".replace(Regex("[^a-zA-Z0-9_.\\u4e00-\\u9fa5-]"), "_")
        val zipFile = File(context.cacheDir, "$zipFileName.zip")
        writeZip(exportDir, zipFile)
        return saveToDownloads(zipFile)
    }

    private fun writeZip(sourceDir: File, targetZip: File) {
        ZipOutputStream(targetZip.outputStream().buffered()).use { zos ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(sourceDir).path.replace(File.separatorChar, '/')
                zos.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { input -> input.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun saveToDownloads(source: File): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, source.name)
                put(MediaStore.Downloads.MIME_TYPE, "application/zip")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("无法在 Download 中创建文件")
            resolver.openOutputStream(uri)?.use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IOException("无法写入 Download")
            "Download/${source.name}"
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            downloadsDir.mkdirs()
            val target = File(downloadsDir, source.name)
            source.copyTo(target, overwrite = true)
            target.absolutePath
        }
    }

    private fun updateRecord(uid: String, transform: (AudioRecord) -> AudioRecord) {
        _records.update { list ->
            list.map { if (it.uid == uid) transform(it) else it }
        }
        refreshUi()
    }

    private fun refreshUi() {
        _items.value = _records.value.map { it.toItem() }
    }

    private fun generateDefaultId(fileName: String): String {
        val baseName = fileName.substringBeforeLast('.')
        return normalizeId(baseName)
    }

    private fun normalizeId(input: String): String {
        val cleaned = input.trim()
            .map { c -> if (c.isLetterOrDigit() || c == '_' || c == '-') c else '_' }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')
        return cleaned.ifBlank { "audio" }
    }

    private val soundMapAdapter: JsonAdapter<Map<String, SoundEntry>> = createSoundMapAdapter()

    @Suppress("UNCHECKED_CAST")
    private fun createSoundMapAdapter(): JsonAdapter<Map<String, SoundEntry>> {
        val type = Types.newParameterizedType(
            Map::class.java,
            String::class.java,
            SoundEntry::class.java
        )
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
        return moshi.adapter<Map<String, SoundEntry>>(type)
    }
}