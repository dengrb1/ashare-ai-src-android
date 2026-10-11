package com.ashareai.app.debug

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class DebugBridgeActivity : androidx.activity.ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val command = intent.getStringExtra(EXTRA_COMMAND).orEmpty().ifBlank { COMMAND_DIAGNOSTICS }
        val status = TextView(this).apply { text = "Debug ADB 请求：$command\n确认后生成脱敏导出文件。" }
        val confirm = Button(this).apply { text = "确认导出" }
        val close = Button(this).apply { text = "取消"; setOnClickListener { finish() } }
        confirm.setOnClickListener {
            confirm.isEnabled = false
            lifecycleScope.launch {
                runCatching {
                    val service = DebugExportService(this@DebugBridgeActivity)
                    when (command) {
                        COMMAND_JSON -> service.exportReportJson(intent.getStringExtra(EXTRA_REPORT_ID))
                        COMMAND_CSV -> service.exportReportCsv(intent.getStringExtra(EXTRA_REPORT_ID))
                        else -> service.exportDiagnostics()
                    }
                }.onSuccess { status.text = "已生成：${it.absolutePath}\n可使用 adb exec-out run-as 读取。" }
                    .onFailure { status.text = "导出失败：${it.message ?: "未知错误"}" }
                confirm.isEnabled = true
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
            addView(status)
            addView(confirm)
            addView(close)
        })
    }

    companion object {
        const val ACTION = "com.ashareai.app.DEBUG_EXPORT"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_REPORT_ID = "report_id"
        const val COMMAND_DIAGNOSTICS = "diagnostics"
        const val COMMAND_JSON = "report_json"
        const val COMMAND_CSV = "report_csv"
    }
}
