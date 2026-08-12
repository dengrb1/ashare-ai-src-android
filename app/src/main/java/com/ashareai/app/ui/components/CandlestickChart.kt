package com.ashareai.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ashareai.app.data.model.KlineBar
import com.ashareai.app.data.KlinePeriod
import com.ashareai.app.data.KlineRepository
import com.ashareai.app.ui.fmt2
import com.ashareai.app.ui.theme.StockDown
import com.ashareai.app.ui.theme.StockUp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.time.format.DateTimeFormatter

enum class SubChart(val label: String) { VOLUME("成交量"), MACD("MACD"), KDJ("KDJ") }

private data class Indicators(
    val ma5: List<Double?>,
    val ma10: List<Double?>,
    val ma20: List<Double?>,
    val macdDif: List<Double>,
    val macdDea: List<Double>,
    val macdHist: List<Double>,
    val k: List<Double>,
    val d: List<Double>,
    val j: List<Double>,
)

private fun ma(closes: List<Double>, n: Int): List<Double?> =
    closes.indices.map { i ->
        if (i < n - 1) null else closes.subList(i - n + 1, i + 1).average()
    }

private fun ema(values: List<Double>, n: Int): List<Double> {
    val alpha = 2.0 / (n + 1)
    val out = ArrayList<Double>(values.size)
    var prev = 0.0
    values.forEachIndexed { i, v ->
        prev = if (i == 0) v else alpha * v + (1 - alpha) * prev
        out.add(prev)
    }
    return out
}

private fun computeIndicators(bars: List<KlineBar>): Indicators {
    val closes = bars.map { it.close }
    val ema12 = ema(closes, 12)
    val ema26 = ema(closes, 26)
    val dif = closes.indices.map { ema12[it] - ema26[it] }
    val dea = ema(dif, 9)
    val hist = closes.indices.map { (dif[it] - dea[it]) * 2 }

    val kList = ArrayList<Double>(bars.size)
    val dList = ArrayList<Double>(bars.size)
    val jList = ArrayList<Double>(bars.size)
    var k = 50.0
    var d = 50.0
    bars.forEachIndexed { i, bar ->
        val from = max(0, i - 8)
        val window = bars.subList(from, i + 1)
        val high = window.maxOf { it.high }
        val low = window.minOf { it.low }
        val rsv = if (high == low) 50.0 else (bar.close - low) / (high - low) * 100
        k = k * 2 / 3 + rsv / 3
        d = d * 2 / 3 + k / 3
        kList.add(k); dList.add(d); jList.add(3 * k - 2 * d)
    }
    return Indicators(ma(closes, 5), ma(closes, 10), ma(closes, 20), dif, dea, hist, kList, dList, jList)
}

/**
 * 蜡烛图 + MA 均线 + 副图（成交量/MACD/KDJ）+ 十字光标。
 */
@Composable
fun CandlestickChart(
    bars: List<KlineBar>,
    period: KlinePeriod,
    subChart: SubChart,
    onLoadEarlier: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (bars.isEmpty()) {
        EmptyPlaceholder("暂无K线数据", modifier)
        return
    }
    val allIndicators = remember(bars) { computeIndicators(bars) }
    var viewport by remember(bars) { mutableStateOf(ChartViewport().normalized(bars.size)) }
    val normalizedViewport = viewport.normalized(bars.size)
    SideEffect { if (viewport != normalizedViewport) viewport = normalizedViewport }
    val visibleRange = normalizedViewport.range(bars.size)
    val visibleBars = bars.slice(visibleRange)
    val indicators = allIndicators.slice(visibleRange)
    val currentViewport by rememberUpdatedState(viewport)
    val currentVisibleBars by rememberUpdatedState(visibleBars)
    var crosshair by remember(bars, visibleRange) { mutableStateOf<Int?>(null) }
    var panRemainder by remember { mutableFloatStateOf(0f) }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val crossColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    val ma5Color = Color(0xFFF2A93B)
    val ma10Color = Color(0xFF3D5AFE)
    val ma20Color = Color(0xFFAB47BC)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = textColor)

    Column(modifier = modifier) {
        // 图例 / 十字光标信息
        val idx = crosshair
        val legendBar = if (idx != null && idx in visibleBars.indices) visibleBars[idx] else visibleBars.last()
        Text(
            formatKlineTime(legendBar.timestamp, period, detailed = true),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("开 ${legendBar.open.fmt2()}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = textColor)
            Text("高 ${legendBar.high.fmt2()}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = StockUp)
            Text("低 ${legendBar.low.fmt2()}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = StockDown)
            Text(
                "收 ${legendBar.close.fmt2()}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = if (legendBar.close >= legendBar.open) StockUp else StockDown,
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(bars) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        var nextViewport = currentViewport.zoom(bars.size, zoom)
                        val slot = size.width.toFloat() / nextViewport.visibleCount.coerceAtLeast(1)
                        panRemainder += pan.x / slot
                        val wholeBars = panRemainder.roundToInt()
                        if (wholeBars != 0) {
                            nextViewport = nextViewport.pan(bars.size, wholeBars)
                            panRemainder -= wholeBars
                        }
                        if (nextViewport != currentViewport) viewport = nextViewport
                        crosshair = null
                    }
                }
                .pointerInput(bars) {
                    detectDragGesturesAfterLongPress(
                        onDragEnd = { crosshair = null },
                        onDragCancel = { crosshair = null },
                    ) { change, _ ->
                        val barsForGesture = currentVisibleBars
                        val w = size.width.toFloat()
                        val slot = w / barsForGesture.size
                        crosshair = (change.position.x / slot).toInt().coerceIn(0, barsForGesture.size - 1)
                    }
                }
        ) {
            val mainRatio = 0.68f
            val gap = 14.dp.toPx()
            val mainH = size.height * mainRatio - gap / 2
            val subTop = size.height * mainRatio + gap / 2
            val subH = size.height - subTop

            val slot = size.width / visibleBars.size
            val bodyW = (slot * 0.7f).coerceAtLeast(1f)

            var minP = visibleBars.minOf { it.low }
            var maxP = visibleBars.maxOf { it.high }
            listOf(indicators.ma5, indicators.ma10, indicators.ma20).forEach { series ->
                series.filterNotNull().forEach { v -> minP = min(minP, v); maxP = max(maxP, v) }
            }
            val range = (maxP - minP).takeIf { it > 0 } ?: 1.0
            fun yOf(price: Double): Float = (mainH * (1 - (price - minP) / range)).toFloat()

            // 网格 + 价格刻度
            for (i in 0..4) {
                val y = mainH * i / 4
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                val price = maxP - range * i / 4
                drawText(
                    textMeasurer, price.fmt2(), style = labelStyle,
                    topLeft = Offset(2f, (y - 12).coerceAtLeast(0f)),
                )
            }

            // 蜡烛
            visibleBars.forEachIndexed { i, bar ->
                val x = slot * i + slot / 2
                val up = bar.close >= bar.open
                val color = if (up) StockUp else StockDown
                drawLine(color, Offset(x, yOf(bar.high)), Offset(x, yOf(bar.low)), 1.5f)
                val top = yOf(max(bar.open, bar.close))
                val bottom = yOf(min(bar.open, bar.close))
                drawRect(
                    color = color,
                    topLeft = Offset(x - bodyW / 2, top),
                    size = androidx.compose.ui.geometry.Size(bodyW, (bottom - top).coerceAtLeast(1f)),
                    style = if (up) Stroke(1.5f) else androidx.compose.ui.graphics.drawscope.Fill,
                )
            }

            // MA 均线
            fun drawMa(series: List<Double?>, color: Color) {
                val path = Path()
                var started = false
                series.forEachIndexed { i, v ->
                    if (v != null) {
                        val x = slot * i + slot / 2
                        val y = yOf(v)
                        if (!started) { path.moveTo(x, y); started = true } else path.lineTo(x, y)
                    }
                }
                drawPath(path, color, style = Stroke(1.8f))
            }
            drawMa(indicators.ma5, ma5Color)
            drawMa(indicators.ma10, ma10Color)
            drawMa(indicators.ma20, ma20Color)

            // 副图
            when (subChart) {
                SubChart.VOLUME -> drawVolume(visibleBars, slot, bodyW, subTop, subH)
                SubChart.MACD -> drawMacd(indicators, visibleBars.size, slot, bodyW, subTop, subH)
                SubChart.KDJ -> drawKdj(indicators, visibleBars.size, slot, subTop, subH)
            }

            listOf(0f, .25f, .5f, .75f, 1f).forEach { ratio ->
                val position = ((visibleBars.lastIndex) * ratio).roundToInt()
                val label = formatKlineTime(visibleBars[position].timestamp, period, detailed = false)
                val measured = textMeasurer.measure(label, labelStyle)
                val x = (slot * position + slot / 2 - measured.size.width / 2)
                    .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                drawText(textMeasurer, label, style = labelStyle, topLeft = Offset(x, size.height - 12.dp.toPx()))
            }

            // 十字光标
            crosshair?.let { ci ->
                if (ci in bars.indices) {
                    val x = slot * ci + slot / 2
                    drawLine(crossColor, Offset(x, 0f), Offset(x, size.height), 1f)
                    val y = yOf(visibleBars[ci].close)
                    drawLine(crossColor, Offset(0f, y), Offset(size.width, y), 1f)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("MA5", style = MaterialTheme.typography.labelSmall, color = ma5Color)
            Text("MA10", style = MaterialTheme.typography.labelSmall, color = ma10Color)
            Text("MA20", style = MaterialTheme.typography.labelSmall, color = ma20Color)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { viewport = viewport.zoom(bars.size, 1.4f) },
                enabled = normalizedViewport.visibleCount > minOf(ChartViewport.MIN_VISIBLE, bars.size),
            ) { Icon(Icons.Outlined.ZoomIn, contentDescription = "放大") }
            IconButton(
                onClick = { viewport = viewport.zoom(bars.size, .72f) },
                enabled = normalizedViewport.visibleCount < bars.size,
            ) { Icon(Icons.Outlined.ZoomOut, contentDescription = "缩小") }
            IconButton(
                onClick = { viewport = viewport.latest(bars.size) },
                enabled = normalizedViewport.offsetFromLatest > 0,
            ) { Icon(Icons.Outlined.History, contentDescription = "回到最新") }
            if (onLoadEarlier != null) {
                IconButton(onClick = onLoadEarlier) {
                    Icon(Icons.Outlined.SkipPrevious, contentDescription = "加载更早")
                }
            }
        }
    }
}

private fun Indicators.slice(range: IntRange): Indicators = Indicators(
    ma5 = ma5.slice(range),
    ma10 = ma10.slice(range),
    ma20 = ma20.slice(range),
    macdDif = macdDif.slice(range),
    macdDea = macdDea.slice(range),
    macdHist = macdHist.slice(range),
    k = k.slice(range),
    d = d.slice(range),
    j = j.slice(range),
)

internal fun formatKlineTime(value: String, period: KlinePeriod, detailed: Boolean): String {
    val instant = KlineRepository.parseBarInstant(value) ?: return value
    val pattern = when {
        detailed && period != KlinePeriod.DAY -> "yyyy-MM-dd HH:mm"
        detailed -> "yyyy-MM-dd"
        period != KlinePeriod.DAY -> "MM-dd HH:mm"
        else -> "MM-dd"
    }
    return DateTimeFormatter.ofPattern(pattern)
        .withZone(java.time.ZoneId.of("Asia/Shanghai"))
        .format(instant)
}

private fun DrawScope.drawVolume(bars: List<KlineBar>, slot: Float, bodyW: Float, top: Float, height: Float) {
    val maxVol = bars.maxOf { it.volume }.takeIf { it > 0 } ?: 1.0
    bars.forEachIndexed { i, bar ->
        val x = slot * i + slot / 2
        val h = (height * bar.volume / maxVol).toFloat()
        val color = if (bar.close >= bar.open) StockUp else StockDown
        drawRect(
            color = color.copy(alpha = 0.75f),
            topLeft = Offset(x - bodyW / 2, top + height - h),
            size = androidx.compose.ui.geometry.Size(bodyW, h.coerceAtLeast(1f)),
        )
    }
}

private fun DrawScope.drawMacd(ind: Indicators, count: Int, slot: Float, bodyW: Float, top: Float, height: Float) {
    val all = ind.macdDif + ind.macdDea + ind.macdHist
    val maxAbs = all.maxOf { kotlin.math.abs(it) }.takeIf { it > 0 } ?: 1.0
    val mid = top + height / 2
    fun yOf(v: Double): Float = (mid - v / maxAbs * height / 2).toFloat()

    drawLine(Color.Gray.copy(alpha = 0.4f), Offset(0f, mid), Offset(size.width, mid), 1f)
    for (i in 0 until count) {
        val x = slot * i + slot / 2
        val v = ind.macdHist[i]
        val color = if (v >= 0) StockUp else StockDown
        drawLine(color, Offset(x, mid), Offset(x, yOf(v)), bodyW * 0.5f)
    }
    fun line(series: List<Double>, color: Color) {
        val path = Path()
        series.forEachIndexed { i, v ->
            val x = slot * i + slot / 2
            if (i == 0) path.moveTo(x, yOf(v)) else path.lineTo(x, yOf(v))
        }
        drawPath(path, color, style = Stroke(1.6f))
    }
    line(ind.macdDif, Color(0xFFF2A93B))
    line(ind.macdDea, Color(0xFF3D5AFE))
}

private fun DrawScope.drawKdj(ind: Indicators, count: Int, slot: Float, top: Float, height: Float) {
    val all = ind.k + ind.d + ind.j
    val minV = all.min()
    val maxV = all.max()
    val range = (maxV - minV).takeIf { it > 0 } ?: 1.0
    fun yOf(v: Double): Float = (top + height * (1 - (v - minV) / range)).toFloat()
    fun line(series: List<Double>, color: Color) {
        val path = Path()
        series.forEachIndexed { i, v ->
            val x = slot * i + slot / 2
            if (i == 0) path.moveTo(x, yOf(v)) else path.lineTo(x, yOf(v))
        }
        drawPath(path, color, style = Stroke(1.6f))
    }
    line(ind.k, Color(0xFFF2A93B))
    line(ind.d, Color(0xFF3D5AFE))
    line(ind.j, Color(0xFFAB47BC))
}
