package com.ashareai.app.standalone.data.ai

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest

enum class QwenModelStatus { MISSING, READY, INVALID, UNSUPPORTED, ERROR }

data class QwenModelInfo(
    val id: String,
    val format: String,
    val sha256: String,
    val sizeBytes: Long,
    val status: QwenModelStatus,
    val runtimeVersion: String,
)

/**
 * Owns optional on-device Qwen model files. The scoring engine never calls this
 * class; it is limited to explanation and monitoring summaries.
 */
class LocalQwenRuntime(private val context: Context) {
    private val modelDir = File(context.filesDir, "qwen-models")
    private val modelFile get() = File(modelDir, "qwen2-0.5b.gguf")

    fun currentModel(): QwenModelInfo? {
        if (!modelFile.exists()) return null
        val hash = runCatching { sha256(modelFile) }.getOrNull() ?: return QwenModelInfo(
            id = modelFile.name,
            format = "GGUF",
            sha256 = "",
            sizeBytes = modelFile.length(),
            status = QwenModelStatus.ERROR,
            runtimeVersion = RUNTIME_VERSION,
        )
        return QwenModelInfo(
            id = modelFile.name,
            format = "GGUF",
            sha256 = hash,
            sizeBytes = modelFile.length(),
            status = if (modelFile.length() >= MIN_MODEL_BYTES) QwenModelStatus.READY else QwenModelStatus.INVALID,
            runtimeVersion = RUNTIME_VERSION,
        )
    }

    fun isEnabled(): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getBoolean(KEY_ENABLED, false)

    fun setEnabled(enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun importModel(uri: Uri, expectedSha256: String? = null): QwenModelInfo {
        modelDir.mkdirs()
        val temporary = File(modelDir, "qwen2-0.5b.gguf.part")
        var committed = false
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporary.outputStream().use { output -> input.copyTo(output, bufferSize = 64 * 1024) }
            } ?: error("无法读取模型文件")
            require(temporary.length() in MIN_MODEL_BYTES..MAX_MODEL_BYTES) { "模型文件大小不符合 Qwen 2 0.5B 要求" }
            require(hasGgufMagic(temporary)) { "模型格式不是 GGUF" }
            val hash = sha256(temporary)
            require(expectedSha256.isNullOrBlank() || hash.equals(expectedSha256, ignoreCase = true)) { "模型 SHA-256 校验失败" }
            check(temporary.renameTo(modelFile)) { "无法保存模型文件" }
            committed = true
            return currentModel() ?: error("模型状态读取失败")
        } finally {
            if (!committed) temporary.delete()
        }
    }

    fun removeModel() {
        modelFile.delete()
        File(modelDir, "qwen2-0.5b.gguf.part").delete()
        setEnabled(false)
    }

    /** Runtime hook kept explicit so a future GGUF JNI backend cannot alter scoring. */
    suspend fun explain(prompt: String): Result<String> {
        val model = currentModel()
        if (!isEnabled() || model?.status != QwenModelStatus.READY) {
            return Result.failure(IllegalStateException("Qwen 模型未启用或不可用"))
        }
        return Result.failure(UnsupportedOperationException("当前构建未包含 GGUF 推理运行时"))
    }

    companion object {
        private const val PREFS = "local_qwen_runtime"
        private const val KEY_ENABLED = "enabled"
        private const val RUNTIME_VERSION = "qwen-runtime-contract-v1"
        private const val MIN_MODEL_BYTES = 32L * 1024L * 1024L
        private const val MAX_MODEL_BYTES = 1_024L * 1024L * 1024L
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hasGgufMagic(file: File): Boolean = file.inputStream().use { input ->
        val magic = ByteArray(4)
        input.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
    }
}
