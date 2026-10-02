package com.example.floating.caloriecounter

import android.graphics.Paint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.example.floating.caloriecounter.Model.WorkoutEntrySnapshot
import com.example.floating.caloriecounter.Model.WorkoutSetSnapshot
import com.example.floating.caloriecounter.Model.calculateOneRepMax
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal data class WorkoutProgressPoint(
    val dateMillis: Long,
    val volumeKg: Float,
    val bestSetVolumeKg: Float,
    val bestOneRepMaxKg: Float
)

internal data class WorkoutProgressStats(
    val sessionCount: Int,
    val latestEntry: WorkoutEntrySnapshot?,
    val latestVolumeKg: Float,
    val maxWeightKg: Float,
    val maxWeightSet: WorkoutSetSnapshot?,
    val maxWeightDateMillis: Long?,
    val bestSet: WorkoutSetSnapshot?,
    val bestSetDateMillis: Long?,
    val bestOneRepMaxKg: Float,
    val bestOneRepMaxDateMillis: Long?,
    val chartPoints: List<WorkoutProgressPoint>
)

private data class WorkoutSetRecord(
    val dateMillis: Long,
    val set: WorkoutSetSnapshot
)

internal fun calculateWorkoutProgress(
    entries: List<WorkoutEntrySnapshot>,
    chartLimit: Int = 12
): WorkoutProgressStats {
    val completedEntries = entries
        .asSequence()
        .filter { it.completed }
        .sortedWith(
            compareByDescending<WorkoutEntrySnapshot> { it.dateMillis }
                .thenByDescending { it.updatedAt }
                .thenBy { it.position }
        )
        .toList()
    val setRecords = completedEntries.flatMap { entry ->
        entry.sets.map { set -> WorkoutSetRecord(entry.dateMillis, set) }
    }
    val maxWeightRecord = setRecords.filter { it.set.weightKg > 0f }.maxWithOrNull(
        compareBy<WorkoutSetRecord> { it.set.weightKg }
            .thenBy { it.set.reps }
            .thenBy { it.dateMillis }
    )
    val bestSetRecord = setRecords.filter { workoutSetScore(it.set) > 0f }.maxWithOrNull(
        compareBy<WorkoutSetRecord> { workoutSetScore(it.set) }
            .thenBy { it.set.weightKg }
            .thenBy { it.set.reps }
            .thenBy { it.dateMillis }
    )
    val bestOneRepMaxRecord = setRecords.filter { workoutSetOneRepMax(it.set) > 0f }.maxWithOrNull(
        compareBy<WorkoutSetRecord> { workoutSetOneRepMax(it.set) }
            .thenBy { it.dateMillis }
    )
    val latestEntry = completedEntries.firstOrNull()
    val points = completedEntries
        .take(chartLimit.coerceAtLeast(0))
        .asReversed()
        .map { entry ->
            WorkoutProgressPoint(
                dateMillis = entry.dateMillis,
                volumeKg = workoutVolumeKg(entry),
                bestSetVolumeKg = entry.sets.maxOfOrNull(::workoutSetVolumeKg) ?: 0f,
                bestOneRepMaxKg = entry.sets.maxOfOrNull(::workoutSetOneRepMax) ?: 0f
            )
        }

    return WorkoutProgressStats(
        sessionCount = completedEntries.size,
        latestEntry = latestEntry,
        latestVolumeKg = latestEntry?.let(::workoutVolumeKg) ?: 0f,
        maxWeightKg = maxWeightRecord?.set?.weightKg?.coerceAtLeast(0f) ?: 0f,
        maxWeightSet = maxWeightRecord?.set,
        maxWeightDateMillis = maxWeightRecord?.dateMillis,
        bestSet = bestSetRecord?.set,
        bestSetDateMillis = bestSetRecord?.dateMillis,
        bestOneRepMaxKg = bestOneRepMaxRecord?.set?.let(::workoutSetOneRepMax) ?: 0f,
        bestOneRepMaxDateMillis = bestOneRepMaxRecord?.dateMillis,
        chartPoints = points
    )
}

internal fun workoutVolumeKg(entry: WorkoutEntrySnapshot): Float =
    entry.sets.sumOf { set ->
        set.weightKg.coerceAtLeast(0f).toDouble() * set.reps.coerceAtLeast(0)
    }.toFloat()

private fun workoutSetScore(set: WorkoutSetSnapshot): Float =
    if (set.weightKg > 0f && set.reps > 0) {
        set.weightKg * set.reps
    } else {
        set.reps.coerceAtLeast(0).toFloat()
    }

private fun workoutSetVolumeKg(set: WorkoutSetSnapshot): Float =
    set.weightKg.coerceAtLeast(0f) * set.reps.coerceAtLeast(0)

private fun workoutSetOneRepMax(set: WorkoutSetSnapshot): Float =
    set.oneRepMaxKg.takeIf { it > 0f }
        ?: calculateOneRepMax(set.weightKg, set.reps)

@Composable
internal fun WorkoutProgressView(
    exerciseName: String,
    entries: List<WorkoutEntrySnapshot>,
    onEdit: (WorkoutEntrySnapshot) -> Unit
) {
    val stats = remember(entries) {
        calculateWorkoutProgress(entries, chartLimit = Int.MAX_VALUE)
    }
    val latestEntry = stats.latestEntry
    val listState = rememberLazyListState()
    var visibleHistoryCount by remember(exerciseName) { mutableIntStateOf(25) }
    val visibleHistoryEntries = remember(entries, visibleHistoryCount) {
        entries.take(visibleHistoryCount)
    }
    val shouldLoadMoreHistory by remember(listState, visibleHistoryCount, entries.size) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            layoutInfo.totalItemsCount > 0 &&
                visibleHistoryCount < entries.size &&
                lastVisibleIndex >= layoutInfo.totalItemsCount - 4
        }
    }

    LaunchedEffect(shouldLoadMoreHistory) {
        if (shouldLoadMoreHistory) {
            visibleHistoryCount = (visibleHistoryCount + 25).coerceAtMost(entries.size)
        }
    }

    if (latestEntry == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "No completed sessions for $exerciseName.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column {
                Text(
                    text = exerciseName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Completed exercise progress",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProgressMetricCard(
                        label = "Sessions",
                        value = stats.sessionCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    ProgressMetricCard(
                        label = "One rep max",
                        value = formatProgressWeight(stats.bestOneRepMaxKg),
                        supportingText = stats.bestOneRepMaxDateMillis?.let(::formatProgressDate),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProgressMetricCard(
                        label = "Max weight",
                        value = formatBestSet(stats.maxWeightSet),
                        supportingText = stats.maxWeightDateMillis?.let(::formatProgressDate),
                        modifier = Modifier.weight(1f)
                    )
                    ProgressMetricCard(
                        label = "Best set",
                        value = formatBestSet(stats.bestSet),
                        supportingText = stats.bestSetDateMillis?.let(::formatProgressDate),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Latest workout", fontWeight = FontWeight.Bold)
                        Text(formatProgressDate(latestEntry.dateMillis))
                    }
                    WorkoutSetsSummary(latestEntry.sets)
                    if (latestEntry.notes.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(latestEntry.notes, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Volume: ${formatProgressVolume(stats.latestVolumeKg)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        item {
            WorkoutProgressChart(stats.chartPoints)
        }

        item {
            Text("Workout history", style = MaterialTheme.typography.titleMedium)
        }

        items(visibleHistoryEntries, key = { it.id }) { entry ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEdit(entry) }
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            formatProgressDate(entry.dateMillis),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            formatProgressVolume(workoutVolumeKg(entry)),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    WorkoutSetsSummary(entry.sets)
                    WorkoutSessionHighlights(entry.sets)
                    if (entry.notes.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(entry.notes, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressMetricCard(
    label: String,
    value: String,
    supportingText: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.heightIn(min = 78.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            if (supportingText != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = supportingText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun WorkoutSessionHighlights(sets: List<WorkoutSetSnapshot>) {
    val bestOneRepMax = sets.maxOfOrNull(::workoutSetOneRepMax) ?: 0f
    val bestVolumeSet = sets.maxOfOrNull(::workoutSetVolumeKg) ?: 0f

    Spacer(Modifier.height(8.dp))
    HorizontalDivider(
        thickness = 1.dp,
        color = Color.White.copy(alpha = 0.38f)
    )
    Spacer(Modifier.height(7.dp))
    Text(
        text = "One rep max: ${formatProgressWeight(bestOneRepMax)}",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium
    )
    Spacer(Modifier.height(2.dp))
    Text(
        text = "Best volume set: ${formatProgressVolume(bestVolumeSet)}",
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium
    )
}

private enum class WorkoutProgressMetric(val label: String) {
    TOTAL_VOLUME("Volume / workout"),
    BEST_SET_VOLUME("Best set volume"),
    BEST_ONE_REP_MAX("Best 1RM")
}

private fun WorkoutProgressPoint.valueFor(metric: WorkoutProgressMetric): Float = when (metric) {
    WorkoutProgressMetric.TOTAL_VOLUME -> volumeKg
    WorkoutProgressMetric.BEST_SET_VOLUME -> bestSetVolumeKg
    WorkoutProgressMetric.BEST_ONE_REP_MAX -> bestOneRepMaxKg
}

@Composable
private fun WorkoutProgressChart(points: List<WorkoutProgressPoint>) {
    val maxViewportSpan = points.lastIndex.coerceAtLeast(1).toFloat()
    val defaultViewportSpan = minOf(11f, maxViewportSpan)
    var selectedMetric by remember { mutableStateOf(WorkoutProgressMetric.TOTAL_VOLUME) }
    var viewportSpan by remember(points.size) { mutableFloatStateOf(defaultViewportSpan) }
    var windowStart by remember(points.size) {
        mutableFloatStateOf((points.lastIndex - defaultViewportSpan).coerceAtLeast(0f))
    }
    var selectedPoint by remember { mutableStateOf<WorkoutProgressPoint?>(null) }

    LaunchedEffect(points.size) {
        viewportSpan = defaultViewportSpan
        windowStart = (points.lastIndex - defaultViewportSpan).coerceAtLeast(0f)
        selectedPoint = null
    }

    val windowEnd = windowStart + viewportSpan
    val visiblePoints = remember(points, windowStart, viewportSpan) {
        points.filterIndexed { index, _ -> index.toFloat() in windowStart..windowEnd }
    }
    val peakValue = visiblePoints.maxOfOrNull { it.valueFor(selectedMetric) } ?: 0f

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedMetric.label, fontWeight = FontWeight.Bold)
                Text(
                    text = "Peak ${formatProgressMetricValue(peakValue, selectedMetric)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WorkoutProgressMetric.entries.forEach { metric ->
                    FilterChip(
                        selected = selectedMetric == metric,
                        onClick = {
                            selectedMetric = metric
                            selectedPoint = null
                        },
                        label = { Text(metric.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
            ) {
                WorkoutProgressLineChart(
                    points = points,
                    metric = selectedMetric,
                    windowStart = windowStart,
                    viewportSpan = viewportSpan,
                    selectedPoint = selectedPoint,
                    onViewportChange = { newStart, newSpan ->
                        windowStart = newStart
                        viewportSpan = newSpan
                        selectedPoint = null
                    },
                    onPointSelected = { selectedPoint = it }
                )

                selectedPoint?.let { point ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 6.dp
                    ) {
                        Text(
                            text = "${formatProgressDate(point.dateMillis)} | " +
                                formatProgressMetricValue(point.valueFor(selectedMetric), selectedMetric),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Text(
                text = if (points.size > 1) {
                    "Pinch to zoom | Drag left or right to browse sessions"
                } else {
                    "Tap near a point for details"
                },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WorkoutProgressLineChart(
    points: List<WorkoutProgressPoint>,
    metric: WorkoutProgressMetric,
    windowStart: Float,
    viewportSpan: Float,
    selectedPoint: WorkoutProgressPoint?,
    onViewportChange: (Float, Float) -> Unit,
    onPointSelected: (WorkoutProgressPoint?) -> Unit
) {
    val density = LocalDensity.current
    val leftPaddingPx = with(density) { 54.dp.toPx() }
    val rightPaddingPx = with(density) { 14.dp.toPx() }
    val tapRadiusPx = with(density) { 32.dp.toPx() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val currentWindowStart by rememberUpdatedState(windowStart)
    val currentViewportSpan by rememberUpdatedState(viewportSpan)
    val currentPoints by rememberUpdatedState(points)
    val currentViewportCallback by rememberUpdatedState(onViewportChange)
    val currentSelectionCallback by rememberUpdatedState(onPointSelected)

    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.48f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selectionColor = MaterialTheme.colorScheme.tertiary
    val labelPaint = remember(labelColor, density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = labelColor.toArgb()
            textSize = with(density) { 11.sp.toPx() }
        }
    }

    val chartModifier = Modifier
        .fillMaxSize()
        .onSizeChanged { canvasSize = it }
        .pointerInput(canvasSize, points.size) {
            detectTransformGestures(panZoomLock = true) { centroid, pan, zoom, _ ->
                val plotWidth = canvasSize.width - leftPaddingPx - rightPaddingPx
                if (plotWidth <= 0f || currentPoints.size <= 1) return@detectTransformGestures

                val maximumSpan = currentPoints.lastIndex.toFloat().coerceAtLeast(1f)
                val minimumSpan = minOf(2f, maximumSpan)
                val oldSpan = currentViewportSpan
                val newSpan = (oldSpan / zoom).coerceIn(minimumSpan, maximumSpan)
                val anchorFraction = ((centroid.x - leftPaddingPx) / plotWidth).coerceIn(0f, 1f)
                val anchorSession = currentWindowStart + oldSpan * anchorFraction
                var newStart = anchorSession - newSpan * anchorFraction
                newStart -= pan.x / plotWidth * newSpan
                newStart = newStart.coerceIn(0f, (maximumSpan - newSpan).coerceAtLeast(0f))

                if (abs(pan.x) > 0.25f || abs(zoom - 1f) > 0.001f) {
                    currentViewportCallback(newStart, newSpan)
                }
            }
        }
        .pointerInput(canvasSize, points.size, tapRadiusPx) {
            detectTapGestures { offset ->
                val plotWidth = canvasSize.width - leftPaddingPx - rightPaddingPx
                if (plotWidth <= 0f || currentPoints.isEmpty()) {
                    currentSelectionCallback(null)
                    return@detectTapGestures
                }
                val end = currentWindowStart + currentViewportSpan
                val nearest = currentPoints.mapIndexedNotNull { index, point ->
                    if (index.toFloat() !in currentWindowStart..end) return@mapIndexedNotNull null
                    val x = leftPaddingPx + (index - currentWindowStart) / currentViewportSpan * plotWidth
                    point to abs(x - offset.x)
                }.minByOrNull { it.second }
                currentSelectionCallback(
                    nearest?.takeIf { it.second <= tapRadiusPx }?.first
                )
            }
        }

    Canvas(modifier = chartModifier) {
        val plotLeft = leftPaddingPx
        val plotRight = size.width - rightPaddingPx
        val plotTop = 26.dp.toPx()
        val plotBottom = size.height - 34.dp.toPx()
        val plotWidth = plotRight - plotLeft
        val plotHeight = plotBottom - plotTop
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        val end = windowStart + viewportSpan
        val visibleIndexed = points.mapIndexedNotNull { index, point ->
            if (index.toFloat() in windowStart..end) index to point else null
        }
        val visibleValues = visibleIndexed.map { it.second.valueFor(metric) }
        val rawMin = visibleValues.minOrNull() ?: 0f
        val rawMax = visibleValues.maxOrNull() ?: 1f
        val yPadding = max(1f, (rawMax - rawMin) * 0.15f)
        val yMin = (rawMin - yPadding).coerceAtLeast(0f)
        val yMax = rawMax + yPadding
        val ySpan = (yMax - yMin).coerceAtLeast(1f)

        fun xFor(index: Int): Float =
            plotLeft + (index - windowStart) / viewportSpan * plotWidth

        fun yFor(value: Float): Float =
            plotBottom - ((value - yMin) / ySpan) * plotHeight

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
                formatProgressAxisValue(yMin + ySpan * fraction, metric),
                plotLeft - 7.dp.toPx(),
                y + 4.dp.toPx(),
                labelPaint
            )
        }

        repeat(3) { tick ->
            if (points.isEmpty()) return@repeat
            val fraction = tick / 2f
            val pointIndex = (windowStart + viewportSpan * fraction)
                .roundToInt()
                .coerceIn(0, points.lastIndex)
            val x = plotLeft + fraction * plotWidth
            drawLine(
                color = gridColor.copy(alpha = 0.55f),
                start = Offset(x, plotTop),
                end = Offset(x, plotBottom),
                strokeWidth = 1.dp.toPx()
            )
            labelPaint.textAlign = when (tick) {
                0 -> Paint.Align.LEFT
                2 -> Paint.Align.RIGHT
                else -> Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText(
                formatProgressShortDate(points[pointIndex].dateMillis),
                x,
                plotBottom + 22.dp.toPx(),
                labelPaint
            )
        }

        if (visibleIndexed.size >= 2) {
            val areaPath = Path().apply {
                val first = visibleIndexed.first()
                moveTo(xFor(first.first), plotBottom)
                lineTo(xFor(first.first), yFor(first.second.valueFor(metric)))
                visibleIndexed.drop(1).forEach { (index, point) ->
                    lineTo(xFor(index), yFor(point.valueFor(metric)))
                }
                lineTo(xFor(visibleIndexed.last().first), plotBottom)
                close()
            }
            val linePath = Path().apply {
                val first = visibleIndexed.first()
                moveTo(xFor(first.first), yFor(first.second.valueFor(metric)))
                visibleIndexed.drop(1).forEach { (index, point) ->
                    lineTo(xFor(index), yFor(point.valueFor(metric)))
                }
            }
            drawPath(
                path = areaPath,
                brush = Brush.verticalGradient(
                    colors = listOf(lineColor.copy(alpha = 0.28f), Color.Transparent),
                    startY = plotTop,
                    endY = plotBottom
                )
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        visibleIndexed.forEach { (index, point) ->
            drawCircle(
                color = lineColor,
                radius = 3.2.dp.toPx(),
                center = Offset(xFor(index), yFor(point.valueFor(metric)))
            )
        }

        selectedPoint?.let { selected ->
            val selectedIndex = points.indexOf(selected)
            if (selectedIndex >= 0 && selectedIndex.toFloat() in windowStart..end) {
                val x = xFor(selectedIndex)
                val y = yFor(selected.valueFor(metric))
                drawLine(
                    color = selectionColor.copy(alpha = 0.65f),
                    start = Offset(x, plotTop),
                    end = Offset(x, plotBottom),
                    strokeWidth = 1.dp.toPx()
                )
                drawCircle(
                    color = selectionColor.copy(alpha = 0.24f),
                    radius = 9.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(color = selectionColor, radius = 4.5.dp.toPx(), center = Offset(x, y))
            }
        }
    }
}

private fun formatProgressMetricValue(value: Float, metric: WorkoutProgressMetric): String =
    when (metric) {
        WorkoutProgressMetric.TOTAL_VOLUME,
        WorkoutProgressMetric.BEST_SET_VOLUME -> formatProgressVolume(value)
        WorkoutProgressMetric.BEST_ONE_REP_MAX -> formatProgressWeight(value)
    }

private fun formatProgressAxisValue(value: Float, metric: WorkoutProgressMetric): String =
    when (metric) {
        WorkoutProgressMetric.TOTAL_VOLUME,
        WorkoutProgressMetric.BEST_SET_VOLUME -> String.format(Locale.US, "%,.0f", value)
        WorkoutProgressMetric.BEST_ONE_REP_MAX -> formatWeightDisplay(value)
    }

private fun formatProgressWeight(value: Float): String =
    if (value > 0f) "${formatWeightDisplay(value)} kg" else "—"

private fun formatBestSet(set: WorkoutSetSnapshot?): String = when {
    set == null -> "—"
    set.weightKg > 0f && set.reps > 0 ->
        "${formatWeightDisplay(set.weightKg)} kg × ${set.reps}"
    set.reps > 0 -> "${set.reps} reps"
    set.weightKg > 0f -> "${formatWeightDisplay(set.weightKg)} kg"
    else -> "—"
}

private fun formatProgressVolume(value: Float): String =
    if (value > 0f) String.format(Locale.US, "%,.0f kg", value) else "—"

private val progressDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val progressShortDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yy")

private fun formatProgressDate(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(progressDateFormatter)

private fun formatProgressShortDate(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(progressShortDateFormatter)
