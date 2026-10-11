package com.ashareai.app.debug

import android.content.Context
import com.ashareai.app.BuildConfig
import com.ashareai.app.HybridApp
import com.ashareai.app.standalone.domain.ResearchCandidate
import com.ashareai.app.standalone.domain.ResearchReport
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Serializable
data class DebugCandidateExport(
    val symbol: String,
    val name: String,
    val score: Double,
    val risk: String,
    val reason: String,
    val trendPhase: String,
    val trendSignal: String,
    val volumePriceSignal: String,
    val capitalActivityProxy: Double?,
    val freshness: String,
)

@Serializable
data class DebugReportExport(
    val schemaVersion: Int = 1,
    val engineVersion: String,
    val report: ReportHeader,
    val candidates: List<DebugCandidateExport>,
)

@Serializable
data class ReportHeader(
    val id: String,
    val runId: String,
    val title: String,
    val deterministicBody: String,
    val aiExplanation: String?,
    val createdAt: Long,
    val signalSummaryJson: String?,
)

class DebugExportService(private val context: Context) {
    private val app = HybridApp.from(context)
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun exportReportJson(reportId: String? = null): File {
        val report = selectReport(reportId)
        val body = app.localContainer.reportExports.json(report.id)
        return writeBytes("report-${report.id}.json", body)
    }

    suspend fun exportReportCsv(reportId: String? = null): File {
        val report = selectReport(reportId)
        return writeBytes("report-${report.id}.csv", app.localContainer.reportExports.csv(report.id))
    }

    suspend fun exportDiagnostics(): File {
        val local = app.localContainer.local
        val reports = local.allReports()
        val runs = local.allResearchRuns()
        val events = local.allMonitoringEvents()
        val diagnostic = buildString {
            appendLine("schemaVersion=1")
            appendLine("appVersion=${BuildConfig.VERSION_NAME}")
            appendLine("debug=true")
            appendLine("reports=${reports.size}")
            appendLine("researchRuns=${runs.size}")
            appendLine("monitoringEvents=${events.size}")
            appendLine("database=room")
            appendLine("secrets=excluded")
            appendLine("tokens=excluded")
            appendLine("privateServerAddress=excluded")
        }
        val output = prepareOutput()
        val file = File(output, "diagnostics-${System.currentTimeMillis()}.zip")
        val temporary = File(output, ".${file.name}.part")
        try {
            ZipOutputStream(FileOutputStream(temporary)).use { zip ->
                zip.putNextEntry(ZipEntry("diagnostics.txt"))
                zip.write(diagnostic.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("reports-index.json"))
                zip.write(json.encodeToString(reports.map { it.toIndex() }).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            check(temporary.renameTo(file)) { "无法完成诊断包导出" }
        } finally {
            temporary.delete()
        }
        return file
    }

    private suspend fun selectReport(id: String?): ResearchReport =
        app.localContainer.local.allReports().firstOrNull { id == null || it.id == id }
            ?: error("没有可导出的研究报告")

    private fun writeBytes(name: String, body: ByteArray): File {
        val output = prepareOutput()
        val target = File(output, name)
        val temporary = File(output, ".${target.name}.part")
        try {
            temporary.writeBytes(body)
            check(temporary.renameTo(target)) { "无法完成报告导出" }
            return target
        } finally {
            temporary.delete()
        }
    }

    private fun prepareOutput(): File = File(context.cacheDir, "debug").apply {
        mkdirs()
        val cutoff = System.currentTimeMillis() - 30 * 60 * 1000L
        listFiles().orEmpty().filter { it.lastModified() < cutoff }.forEach { it.delete() }
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"").replace("\n", " ")}\""

    private fun ResearchCandidate.toDebug() = DebugCandidateExport(
        symbol, name, score, risk, reason, trendPhase.name, trendSignal.name,
        volumePriceSignal.name, capitalActivityProxy, freshness.name,
    )

    private fun ResearchReport.toIndex() = ReportHeader(
        id, runId, title, "", null, createdAt, signalSummaryJson,
    )
}
