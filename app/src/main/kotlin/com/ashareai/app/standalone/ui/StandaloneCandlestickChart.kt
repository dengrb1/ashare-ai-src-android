package com.ashareai.app.standalone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.ashareai.app.standalone.domain.DailyCandle
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class StandaloneSubChart(val label: String) {
    VOLUME("成交量"),
    MACD("MACD"),
    KDJ("KDJ"),
}

private data class CandleIndicators(
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

private data class CandleViewport(
    val visibleCount: Int = DEFAULT_VISIBLE,
    val offsetFromLatest: Int = 0,
) {
    fun normalized(total: Int): CandleViewport {
        if (total <= 0) return this
        val count = visibleCount.coerceIn(minOf(MIN_VISIBLE, total), total)
        return copy(
            visibleCount = count,
            offsetFromLatest = offsetFromLatest.coerceIn(0, (total - count).coerceAtLeast(0)),
        )
    }

    fun range(total: Int): IntRange {
        val safe = normalized(total)
        val endExclusive = total - safe.offsetFromLatest
        return (endExclusive - safe.visibleCount) until endExclusive
    }

    fun zoom(total: Int, scale: Float): CandleViewport {
        if (!scale.isFinite() || scale <= 0f) return normalized(total)
        val next = (visibleCount / scale).roundToInt().coerceIn(minOf(MIN_VISIBLE, total), total)
        return copy(visibleCount = next).normalized(total)
    }

    fun pan(total: Int, bars: Int): CandleViewport =
        copy(offsetFromLatest = offsetFromLatest + bars).normalized(total)

    companion object {
        const val MIN_VISIBLE = 20
        const val DEFAULT_VISIBLE = 60
    }
}

@Composable
fun StandaloneCandlestickChart(
    candles: List<DailyCandle>,
    subChart: StandaloneSubChart,
    modifier: Modifier = Modifier,
) {
    if (candles.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("暂无 K 线数据", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val indicators = remember(candles) { computeIndicators(candles) }
    var viewport by remember(candles) { mutableStateOf(CandleViewport().normalized(candles.size)) }
    val normalizedViewport = viewport.normalized(candles.size)
    SideEffect { if (viewport != normalizedViewport) viewport = normalizedViewport }
    val visibleRange = normalizedViewport.range(candles.size)
    val visibleCandles = candles.slice(visibleRange)
    val visibleIndicators = indicators.slice(visibleRange)
    val currentViewport by rememberUpdatedState(viewport)
    val currentVisibleCandles by rememberUpdatedState(visibleCandles)
    var crosshair by remember(candles, visibleRange) { mutableStateOf<Int?>(null) }
    var panRemainder by remember { mutableFloatStateOf(0f) }

    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val crosshairColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 9.sp, color = textColor)

    Column(modifier = modifier) {
        val selectedIndex = crosshair
        val selected = if (selectedIndex != null && selectedIndex in visibleCandles.indices) {
            visibleCandles[selectedIndex]
        } else {
            visibleCandles.last()
        }
        Text(
            selected.tradingDate.format(DETAILED_DATE),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CandleLegend("开", selected.open, textColor, Modifier.weight(1f))
            CandleLegend("高", selected.high, STOCK_UP, Modifier.weight(1f))
            CandleLegend("低", selected.low, STOCK_DOWN, Modifier.weight(1f))
            CandleLegend(
                "收",
                selected.close,
                if (selected.close >= selected.open) STOCK_UP else STOCK_DOWN,
                Modifier.weight(1f),
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(candles) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        var nextViewport = currentViewport.zoom(candles.size, zoom)
                        val slotWidth = size.width.toFloat() / nextViewport.visibleCount.coerceAtLeast(1)
                        panRemainder += pan.x / slotWidth
                        val wholeBars = panRemainder.roundToInt()
                        if (wholeBars != 0) {
                            nextViewport = nextViewport.pan(candles.size, wholeBars)
                            panRemainder -= wholeBars
                        }
                        if (nextViewport != currentViewport) viewport = nextViewport
                        crosshair = null
                    }
                }
                .pointerInput(candles) {
                    detectDragGesturesAfterLongPress(
                        onDragEnd = { crosshair = null },
                        onDragCancel = { crosshair = null },
                    ) { change, _ ->
                        val currentCandles = currentVisibleCandles
                        val slotWidth = size.width.toFloat() / currentCandles.size
                        crosshair = (change.position.x / slotWidth)
                            .toInt()
                            .coerceIn(0, currentCandles.lastIndex)
                    }
                },
        ) {
            val mainHeight = size.height * 0.68f
            val subTop = mainHeight + 14.dp.toPx()
            val subHeight = size.height - subTop - 14.dp.toPx()
            val slotWidth = size.width / visibleCandles.size
            val bodyWidth = (slotWidth * 0.68f).coerceAtLeast(1f)
            var minimumPrice = visibleCandles.minOf(DailyCandle::low)
            var maximumPrice = visibleCandles.maxOf(DailyCandle::high)
            listOf(visibleIndicators.ma5, visibleIndicators.ma10, visibleIndicators.ma20).forEach { series ->
                series.filterNotNull().forEach { value ->
                    minimumPrice = min(minimumPrice, value)
                    maximumPrice = max(maximumPrice, value)
                }
            }
            val priceRange = (maximumPrice - minimumPrice).takeIf { it > 0 } ?: 1.0
            fun priceY(price: Double): Float =
                (mainHeight * (1 - (price - minimumPrice) / priceRange)).toFloat()

            for (line in 0..4) {
                val y = mainHeight * line / 4
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                drawText(
                    textMeasurer = textMeasurer,
                    text = (maximumPrice - priceRange * line / 4).formatPrice(),
                    style = labelStyle,
                    topLeft = Offset(3f, (y - 12f).coerceAtLeast(0f)),
                )
            }

            visibleCandles.forEachIndexed { index, candle ->
                val x = slotWidth * index + slotWidth / 2
                val rising = candle.close >= candle.open
                val color = if (rising) STOCK_UP else STOCK_DOWN
                drawLine(color, Offset(x, priceY(candle.high)), Offset(x, priceY(candle.low)), 1.5f)
                val top = priceY(max(candle.open, candle.close))
                val bottom = priceY(min(candle.open, candle.close))
                drawRect(
                    color = color,
                    topLeft = Offset(x - bodyWidth / 2, top),
                    size = Size(bodyWidth, (bottom - top).coerceAtLeast(1f)),
                    style = if (rising) Stroke(1.5f) else androidx.compose.ui.graphics.drawscope.Fill,
                )
            }

            drawMovingAverage(visibleIndicators.ma5, slotWidth, ::priceY, MA5_COLOR)
            drawMovingAverage(visibleIndicators.ma10, slotWidth, ::priceY, MA10_COLOR)
            drawMovingAverage(visibleIndicators.ma20, slotWidth, ::priceY, MA20_COLOR)

            when (subChart) {
                StandaloneSubChart.VOLUME -> drawVolume(visibleCandles, slotWidth, bodyWidth, subTop, subHeight)
                StandaloneSubChart.MACD -> drawMacd(visibleIndicators, slotWidth, bodyWidth, subTop, subHeight)
                StandaloneSubChart.KDJ -> drawKdj(visibleIndicators, slotWidth, subTop, subHeight)
            }

            listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { ratio ->
                val position = (visibleCandles.lastIndex * ratio).roundToInt()
                val label = visibleCandles[position].tradingDate.format(SHORT_DATE)
                val measured = textMeasurer.measure(label, labelStyle)
                val x = (slotWidth * position + slotWidth / 2 - measured.size.width / 2)
                    .coerceIn(0f, (size.width - measured.size.width).coerceAtLeast(0f))
                drawText(textMeasurer, label, style = labelStyle, topLeft = Offset(x, size.height - 12.dp.toPx()))
            }

            crosshair?.let { index ->
                if (index in visibleCandles.indices) {
                    val x = slotWidth * index + slotWidth / 2
                    val y = priceY(visibleCandles[index].close)
                    drawLine(crosshairColor, Offset(x, 0f), Offset(x, size.height), 1f)
                    drawLine(crosshairColor, Offset(0f, y), Offset(size.width, y), 1f)
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("MA5", style = MaterialTheme.typography.labelSmall, color = MA5_COLOR)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("MA10", style = MaterialTheme.typography.labelSmall, color = MA10_COLOR)
            Spacer(Modifier.padding(horizontal = 4.dp))
            Text("MA20", style = MaterialTheme.typography.labelSmall, color = MA20_COLOR)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { viewport = viewport.zoom(candles.size, 1.4f) },
                enabled = normalizedViewport.visibleCount > minOf(CandleViewport.MIN_VISIBLE, candles.size),
            ) { Icon(Icons.Outlined.ZoomIn, contentDescription = "放大 K 线") }
            IconButton(
                onClick = { viewport = viewport.zoom(candles.size, 0.72f) },
                enabled = normalizedViewport.visibleCount < candles.size,
            ) { Icon(Icons.Outlined.ZoomOut, contentDescription = "缩小 K 线") }
            IconButton(
                onClick = { viewport = viewport.copy(offsetFromLatest = 0).normalized(candles.size) },
                enabled = normalizedViewport.offsetFromLatest > 0,
            ) { Icon(Icons.Outlined.History, contentDescription = "回到最新 K 线") }
        }
    }
}

@Composable
private fun CandleLegend(label: String, value: Double, color: Color, modifier: Modifier) {
    Text(
        "$label ${value.formatPrice()}",
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

private fun computeIndicators(candles: List<DailyCandle>): CandleIndicators {
    val closes = candles.map(DailyCandle::close)
    val ema12 = ema(closes, 12)
    val ema26 = ema(closes, 26)
    val dif = closes.indices.map { ema12[it] - ema26[it] }
    val dea = ema(dif, 9)
    val histogram = closes.indices.map { (dif[it] - dea[it]) * 2 }
    val kValues = ArrayList<Double>(candles.size)
    val dValues = ArrayList<Double>(candles.size)
    val jValues = ArrayList<Double>(candles.size)
    var k = 50.0
    var d = 50.0
    candles.forEachIndexed { index, candle ->
        val window = candles.subList(max(0, index - 8), index + 1)
        val high = window.maxOf(DailyCandle::high)
        val low = window.minOf(DailyCandle::low)
        val rsv = if (high == low) 50.0 else (candle.close - low) / (high - low) * 100
        k = k * 2 / 3 + rsv / 3
        d = d * 2 / 3 + k / 3
        kValues.add(k)
        dValues.add(d)
        jValues.add(3 * k - 2 * d)
    }
    return CandleIndicators(
        ma5 = movingAverage(closes, 5),
        ma10 = movingAverage(closes, 10),
        ma20 = movingAverage(closes, 20),
        macdDif = dif,
        macdDea = dea,
        macdHist = histogram,
        k = kValues,
        d = dValues,
        j = jValues,
    )
}

private fun movingAverage(values: List<Double>, period: Int): List<Double?> =
    values.indices.map { index ->
        if (index < period - 1) null else values.subList(index - period + 1, index + 1).average()
    }

private fun ema(values: List<Double>, period: Int): List<Double> {
    val alpha = 2.0 / (period + 1)
    var previous = 0.0
    return values.mapIndexed { index, value ->
        previous = if (index == 0) value else alpha * value + (1 - alpha) * previous
        previous
    }
}

private fun CandleIndicators.slice(range: IntRange) = CandleIndicators(
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

private fun DrawScope.drawMovingAverage(
    values: List<Double?>,
    slotWidth: Float,
    yOf: (Double) -> Float,
    color: Color,
) {
    val path = Path()
    var started = false
    values.forEachIndexed { index, value ->
        if (value != null) {
            val x = slotWidth * index + slotWidth / 2
            if (started) path.lineTo(x, yOf(value)) else {
                path.moveTo(x, yOf(value))
                started = true
            }
        }
    }
    drawPath(path, color, style = Stroke(1.8f))
}

private fun DrawScope.drawVolume(
    candles: List<DailyCandle>,
    slotWidth: Float,
    bodyWidth: Float,
    top: Float,
    height: Float,
) {
    val maximumVolume = candles.maxOf { it.volume ?: 0.0 }.takeIf { it > 0 } ?: 1.0
    candles.forEachIndexed { index, candle ->
        val barHeight = (height * (candle.volume ?: 0.0) / maximumVolume).toFloat()
        val color = if (candle.close >= candle.open) STOCK_UP else STOCK_DOWN
        drawRect(
            color = color.copy(alpha = 0.72f),
            topLeft = Offset(slotWidth * index + (slotWidth - bodyWidth) / 2, top + height - barHeight),
            size = Size(bodyWidth, barHeight.coerceAtLeast(1f)),
        )
    }
}

private fun DrawScope.drawMacd(
    indicators: CandleIndicators,
    slotWidth: Float,
    bodyWidth: Float,
    top: Float,
    height: Float,
) {
    val maximum = (indicators.macdDif + indicators.macdDea + indicators.macdHist)
        .maxOf { abs(it) }
        .takeIf { it > 0 } ?: 1.0
    val middle = top + height / 2
    fun yOf(value: Double): Float = (middle - value / maximum * height / 2).toFloat()
    drawLine(Color.Gray.copy(alpha = 0.35f), Offset(0f, middle), Offset(size.width, middle), 1f)
    indicators.macdHist.forEachIndexed { index, value ->
        val x = slotWidth * index + slotWidth / 2
        drawLine(if (value >= 0) STOCK_UP else STOCK_DOWN, Offset(x, middle), Offset(x, yOf(value)), bodyWidth * 0.5f)
    }
    drawSeries(indicators.macdDif, slotWidth, ::yOf, MA5_COLOR)
    drawSeries(indicators.macdDea, slotWidth, ::yOf, MA10_COLOR)
}

private fun DrawScope.drawKdj(
    indicators: CandleIndicators,
    slotWidth: Float,
    top: Float,
    height: Float,
) {
    val allValues = indicators.k + indicators.d + indicators.j
    val minimum = allValues.min()
    val maximum = allValues.max()
    val range = (maximum - minimum).takeIf { it > 0 } ?: 1.0
    fun yOf(value: Double): Float = (top + height * (1 - (value - minimum) / range)).toFloat()
    drawSeries(indicators.k, slotWidth, ::yOf, MA5_COLOR)
    drawSeries(indicators.d, slotWidth, ::yOf, MA10_COLOR)
    drawSeries(indicators.j, slotWidth, ::yOf, MA20_COLOR)
}

private fun DrawScope.drawSeries(
    values: List<Double>,
    slotWidth: Float,
    yOf: (Double) -> Float,
    color: Color,
) {
    val path = Path()
    values.forEachIndexed { index, value ->
        val x = slotWidth * index + slotWidth / 2
        if (index == 0) path.moveTo(x, yOf(value)) else path.lineTo(x, yOf(value))
    }
    drawPath(path, color, style = Stroke(1.6f))
}

private fun Double.formatPrice(): String = String.format("%.2f", this)

private val SHORT_DATE = DateTimeFormatter.ofPattern("MM-dd")
private val DETAILED_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val STOCK_UP = Color(0xFFE53935)
private val STOCK_DOWN = Color(0xFF00A86B)
private val MA5_COLOR = Color(0xFFF2A93B)
private val MA10_COLOR = Color(0xFF3D5AFE)
private val MA20_COLOR = Color(0xFFAB47BC)
