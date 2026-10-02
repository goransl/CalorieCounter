package com.example.floating.caloriecounter

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.floating.caloriecounter.Model.FoodRepository
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

private enum class WeightChartRange(val label: String, val days: Long?) {
    WEEK("1W", 7),
    MONTH("1M", 30),
    THREE_MONTHS("3M", 90),
    YEAR("1Y", 365),
    MAX("MAX", null)
}

private data class WeightChartPoint(
    val date: LocalDate,
    val weightKg: Float
)

private const val MIN_CHART_SPAN_DAYS = 2.0
private const val MAX_CHART_SPAN_DAYS = 365250.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightChartScreen(
    repository: FoodRepository,
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    dataRevision: Int = 0
) {
    val today = remember { LocalDate.now() }
    var selectedRange by remember { mutableStateOf<WeightChartRange?>(WeightChartRange.THREE_MONTHS) }
    var lastPreset by remember { mutableStateOf(WeightChartRange.THREE_MONTHS) }
    var viewportStart by remember { mutableStateOf(today.minusDays(89).toEpochDay().toDouble()) }
    var viewportSpan by remember { mutableStateOf(89.0) }
    var oldestTrackedDate by remember { mutableStateOf<LocalDate?>(null) }
    var newestTrackedDate by remember { mutableStateOf<LocalDate?>(null) }
    var points by remember { mutableStateOf(emptyList<WeightChartPoint>()) }
    var selectedPoint by remember { mutableStateOf<WeightChartPoint?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    fun applyPreset(range: WeightChartRange) {
        selectedRange = range
        lastPreset = range
        selectedPoint = null

        if (range == WeightChartRange.MAX) {
            val oldest = oldestTrackedDate
            val newest = newestTrackedDate
            if (oldest != null && newest != null) {
                val start = oldest.toEpochDay().toDouble()
                val span = (newest.toEpochDay() - oldest.toEpochDay()).toDouble()
                viewportStart = if (span < MIN_CHART_SPAN_DAYS) start - 1.0 else start
                viewportSpan = span.coerceAtLeast(MIN_CHART_SPAN_DAYS)
            } else {
                viewportStart = today.minusDays(89).toEpochDay().toDouble()
                viewportSpan = 89.0
            }
        } else {
            val visibleDays = range.days ?: 90L
            viewportSpan = (visibleDays - 1L).toDouble().coerceAtLeast(MIN_CHART_SPAN_DAYS)
            viewportStart = today.toEpochDay().toDouble() - viewportSpan
        }
    }

    LaunchedEffect(dataRevision) {
        val oldest = repository.getOldestWeight()
        val newest = repository.getNewestWeight()
        oldestTrackedDate = oldest?.let { weightMillisToDate(it.timestamp) }
        newestTrackedDate = newest?.let { weightMillisToDate(it.timestamp) }
        if (selectedRange == WeightChartRange.MAX) applyPreset(WeightChartRange.MAX)
    }

    val queryStart = remember(viewportStart, viewportSpan) {
        LocalDate.ofEpochDay(floor(viewportStart).toLong())
    }
    val queryEnd = remember(viewportStart, viewportSpan) {
        LocalDate.ofEpochDay(ceil(viewportStart + viewportSpan).toLong())
    }

    LaunchedEffect(queryStart, queryEnd, dataRevision) {
        // Avoid querying Realm for every individual frame while a gesture is still moving.
        delay(90)
        isLoading = true
        points = repository.getWeightsForDateRange(queryStart, queryEnd).map { entry ->
            WeightChartPoint(
                date = weightMillisToDate(entry.timestamp),
                weightKg = entry.weightKg
            )
        }
        isLoading = false
    }

    val visiblePoints = remember(points, viewportStart, viewportSpan) {
        val end = viewportStart + viewportSpan
        points.filter { point -> point.date.toEpochDay().toDouble() in viewportStart..end }
            .sortedBy { it.date }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                title = { Text("Weight chart", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF121212),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(bottom = contentPadding.calculateBottomPadding())
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WeightChartRange.entries.forEach { range ->
                    FilterChip(
                        selected = selectedRange == range,
                        onClick = { applyPreset(range) },
                        label = { Text(range.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (selectedRange == null) {
                        "Custom | ${formatChartRange(queryStart, queryEnd)}"
                    } else {
                        formatChartRange(queryStart, queryEnd)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { applyPreset(lastPreset) }) {
                    Text("Reset")
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
                )
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    WeightLineChart(
                        points = points,
                        viewportStart = viewportStart,
                        viewportSpan = viewportSpan,
                        selectedPoint = selectedPoint,
                        onViewportChange = { newStart, newSpan ->
                            selectedRange = null
                            selectedPoint = null
                            viewportStart = newStart
                            viewportSpan = newSpan
                        },
                        onPointSelected = { selectedPoint = it }
                    )

                    if (isLoading && points.isEmpty()) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(32.dp)
                        )
                    } else if (!isLoading && visiblePoints.isEmpty()) {
                        Text(
                            text = "No weight entries in this period",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    selectedPoint?.let { point ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 8.dp),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            Text(
                                text = "${formatChartDate(point.date)} | ${formatDecimal(point.weightKg)} kg",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            WeightChartSummary(visiblePoints)
            Text(
                text = "Pinch to zoom | Drag to move | Tap near a point for details",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeightChartSummary(points: List<WeightChartPoint>) {
    val first = points.firstOrNull()
    val last = points.lastOrNull()
    val change = if (first != null && last != null) last.weightKg - first.weightKg else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        ChartSummaryValue("Entries", points.size.toString())
        ChartSummaryValue("Latest", last?.let { "${formatDecimal(it.weightKg)} kg" } ?: "-")
        ChartSummaryValue(
            "Change",
            change?.let { "${if (it > 0f) "+" else ""}${formatDecimal(it)} kg" } ?: "-"
        )
    }
}

@Composable
private fun ChartSummaryValue(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WeightLineChart(
    points: List<WeightChartPoint>,
    viewportStart: Double,
    viewportSpan: Double,
    selectedPoint: WeightChartPoint?,
    onViewportChange: (Double, Double) -> Unit,
    onPointSelected: (WeightChartPoint?) -> Unit
) {
    val density = LocalDensity.current
    val leftPaddingPx = with(density) { 54.dp.toPx() }
    val rightPaddingPx = with(density) { 14.dp.toPx() }
    val tapRadiusPx = with(density) { 32.dp.toPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val currentStart by rememberUpdatedState(viewportStart)
    val currentSpan by rememberUpdatedState(viewportSpan)
    val currentPoints by rememberUpdatedState(points)
    val currentViewportCallback by rememberUpdatedState(onViewportChange)
    val currentSelectionCallback by rememberUpdatedState(onPointSelected)

    val lineColor = MaterialTheme.colorScheme.primary
    val missingDataLineColor = Color.LightGray.copy(alpha = 0.68f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectionColor = MaterialTheme.colorScheme.tertiary
    val labelPaint = remember(labelColor, density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = with(density) { 11.sp.toPx() }
        }
    }

    val gestureModifier = Modifier
        .fillMaxSize()
        .onSizeChanged { canvasSize = it }
        .pointerInput(canvasSize, leftPaddingPx, rightPaddingPx) {
            detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                val plotWidth = canvasSize.width - leftPaddingPx - rightPaddingPx
                if (plotWidth <= 0f) return@detectTransformGestures

                val oldSpan = currentSpan
                val newSpan = (oldSpan / zoom.toDouble())
                    .coerceIn(MIN_CHART_SPAN_DAYS, MAX_CHART_SPAN_DAYS)
                val anchorFraction = ((centroid.x - leftPaddingPx) / plotWidth).coerceIn(0f, 1f)
                val anchorDay = currentStart + oldSpan * anchorFraction
                var newStart = anchorDay - newSpan * anchorFraction
                newStart -= pan.x.toDouble() / plotWidth.toDouble() * newSpan

                if (abs(pan.x) > 0.25f || abs(zoom - 1f) > 0.001f) {
                    currentViewportCallback(newStart, newSpan)
                }
            }
        }
        .pointerInput(canvasSize, leftPaddingPx, rightPaddingPx, tapRadiusPx) {
            detectTapGestures { offset ->
                val plotWidth = canvasSize.width - leftPaddingPx - rightPaddingPx
                if (plotWidth <= 0f || currentPoints.isEmpty()) {
                    currentSelectionCallback(null)
                    return@detectTapGestures
                }
                val end = currentStart + currentSpan
                val nearest = currentPoints
                    .asSequence()
                    .filter { it.date.toEpochDay().toDouble() in currentStart..end }
                    .minByOrNull { point ->
                        val fraction = (point.date.toEpochDay().toDouble() - currentStart) / currentSpan
                        val x = leftPaddingPx + fraction.toFloat() * plotWidth
                        abs(x - offset.x)
                    }
                val nearestX = nearest?.let { point ->
                    val fraction = (point.date.toEpochDay().toDouble() - currentStart) / currentSpan
                    leftPaddingPx + fraction.toFloat() * plotWidth
                }
                currentSelectionCallback(
                    if (nearest != null && nearestX != null && abs(nearestX - offset.x) <= tapRadiusPx) {
                        nearest
                    } else {
                        null
                    }
                )
            }
        }

    Canvas(modifier = gestureModifier) {
        val plotLeft = leftPaddingPx
        val plotRight = size.width - rightPaddingPx
        val plotTop = 28.dp.toPx()
        val plotBottom = size.height - 36.dp.toPx()
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        val visibleEnd = viewportStart + viewportSpan
        val visible = points
            .filter { it.date.toEpochDay().toDouble() in viewportStart..visibleEnd }
            .sortedBy { it.date }

        val rawMin = visible.minOfOrNull { it.weightKg } ?: 0f
        val rawMax = visible.maxOfOrNull { it.weightKg } ?: 1f
        val yPadding = max(0.5f, (rawMax - rawMin) * 0.15f)
        val yMin = rawMin - yPadding
        val yMax = rawMax + yPadding
        val ySpan = (yMax - yMin).coerceAtLeast(1f)

        fun xFor(point: WeightChartPoint): Float {
            val fraction = (point.date.toEpochDay().toDouble() - viewportStart) / viewportSpan
            return plotLeft + fraction.toFloat() * plotWidth
        }

        fun yFor(weight: Float): Float =
            plotBottom - ((weight - yMin) / ySpan) * plotHeight

        repeat(5) { index ->
            val fraction = index / 4f
            val y = plotBottom - fraction * plotHeight
            drawLine(
                color = gridColor,
                start = Offset(plotLeft, y),
                end = Offset(plotRight, y),
                strokeWidth = 1.dp.toPx()
            )
            labelPaint.textAlign = Paint.Align.RIGHT
            drawContext.canvas.nativeCanvas.drawText(
                formatDecimal(yMin + ySpan * fraction),
                plotLeft - 7.dp.toPx(),
                y + 4.dp.toPx(),
                labelPaint
            )
        }

        repeat(5) { index ->
            val fraction = index / 4.0
            val x = plotLeft + fraction.toFloat() * plotWidth
            val date = LocalDate.ofEpochDay((viewportStart + viewportSpan * fraction).toLong())
            drawLine(
                color = gridColor.copy(alpha = 0.55f),
                start = Offset(x, plotTop),
                end = Offset(x, plotBottom),
                strokeWidth = 1.dp.toPx()
            )
            labelPaint.textAlign = when (index) {
                0 -> Paint.Align.LEFT
                4 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                formatChartAxisDate(date, viewportSpan),
                x,
                plotBottom + 23.dp.toPx(),
                labelPaint
            )
        }

        if (visible.size >= 2) {
            val continuousRuns = mutableListOf<MutableList<WeightChartPoint>>()
            visible.forEach { point ->
                val currentRun = continuousRuns.lastOrNull()
                if (currentRun == null || ChronoUnit.DAYS.between(currentRun.last().date, point.date) > 1L) {
                    continuousRuns.add(mutableListOf(point))
                } else {
                    currentRun.add(point)
                }
            }

            continuousRuns.filter { it.size >= 2 }.forEach { run ->
                val areaPath = Path().apply {
                    val first = run.first()
                    moveTo(xFor(first), plotBottom)
                    lineTo(xFor(first), yFor(first.weightKg))
                    run.drop(1).forEach { point -> lineTo(xFor(point), yFor(point.weightKg)) }
                    lineTo(xFor(run.last()), plotBottom)
                    close()
                }
                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                        startY = plotTop,
                        endY = plotBottom
                    )
                )
            }

            visible.zipWithNext().forEach { (start, end) ->
                val hasMissingDays = ChronoUnit.DAYS.between(start.date, end.date) > 1L
                drawLine(
                    color = if (hasMissingDays) missingDataLineColor else lineColor,
                    start = Offset(xFor(start), yFor(start.weightKg)),
                    end = Offset(xFor(end), yFor(end.weightKg)),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        if (visible.size <= 90) {
            visible.forEach { point ->
                drawCircle(
                    color = lineColor,
                    radius = 3.2.dp.toPx(),
                    center = Offset(xFor(point), yFor(point.weightKg))
                )
            }
        }

        selectedPoint?.takeIf { it.date.toEpochDay().toDouble() in viewportStart..visibleEnd }?.let { point ->
            val x = xFor(point)
            val y = yFor(point.weightKg)
            drawLine(
                color = selectionColor.copy(alpha = 0.65f),
                start = Offset(x, plotTop),
                end = Offset(x, plotBottom),
                strokeWidth = 1.dp.toPx()
            )
            drawCircle(color = selectionColor.copy(alpha = 0.24f), radius = 9.dp.toPx(), center = Offset(x, y))
            drawCircle(color = selectionColor, radius = 4.5.dp.toPx(), center = Offset(x, y))
        }
    }
}

private val chartDateFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.getDefault())
private val chartShortDateFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
private val chartMonthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

private fun weightMillisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

private fun formatChartDate(date: LocalDate): String = date.format(chartDateFormatter)

private fun formatChartRange(start: LocalDate, end: LocalDate): String =
    "${formatChartDate(start)} - ${formatChartDate(end)}"

private fun formatChartAxisDate(date: LocalDate, spanDays: Double): String = when {
    spanDays > 180.0 -> date.format(chartMonthFormatter)
    else -> date.format(chartShortDateFormatter)
}
