package com.example.floating.caloriecounter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.floating.caloriecounter.Model.WorkoutEntrySnapshot
import com.example.floating.caloriecounter.Model.WorkoutSetSnapshot
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class WorkoutProgressPoint(
    val dateMillis: Long,
    val volumeKg: Float,
    val maxWeightKg: Float
)

internal data class WorkoutProgressStats(
    val sessionCount: Int,
    val latestEntry: WorkoutEntrySnapshot?,
    val latestVolumeKg: Float,
    val maxWeightKg: Float,
    val bestSet: WorkoutSetSnapshot?,
    val chartPoints: List<WorkoutProgressPoint>
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
    val allSets = completedEntries.flatMap { it.sets }
    val bestSet = allSets.maxWithOrNull(
        compareBy<WorkoutSetSnapshot> { workoutSetScore(it) }
            .thenBy { it.weightKg }
            .thenBy { it.reps }
    )
    val latestEntry = completedEntries.firstOrNull()
    val points = completedEntries
        .take(chartLimit.coerceAtLeast(0))
        .asReversed()
        .map { entry ->
            WorkoutProgressPoint(
                dateMillis = entry.dateMillis,
                volumeKg = workoutVolumeKg(entry),
                maxWeightKg = entry.sets.maxOfOrNull { it.weightKg.coerceAtLeast(0f) } ?: 0f
            )
        }

    return WorkoutProgressStats(
        sessionCount = completedEntries.size,
        latestEntry = latestEntry,
        latestVolumeKg = latestEntry?.let(::workoutVolumeKg) ?: 0f,
        maxWeightKg = allSets.maxOfOrNull { it.weightKg.coerceAtLeast(0f) } ?: 0f,
        bestSet = bestSet,
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

@Composable
internal fun WorkoutProgressView(
    exerciseName: String,
    entries: List<WorkoutEntrySnapshot>,
    onEdit: (WorkoutEntrySnapshot) -> Unit
) {
    val stats = remember(entries) { calculateWorkoutProgress(entries) }
    val latestEntry = stats.latestEntry

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
                        label = "Latest",
                        value = formatProgressDate(latestEntry.dateMillis),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProgressMetricCard(
                        label = "Max weight",
                        value = formatProgressWeight(stats.maxWeightKg),
                        modifier = Modifier.weight(1f)
                    )
                    ProgressMetricCard(
                        label = "Best set",
                        value = formatBestSet(stats.bestSet),
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
            WorkoutVolumeChart(stats.chartPoints)
        }

        item {
            Text("Workout history", style = MaterialTheme.typography.titleMedium)
        }

        items(entries, key = { it.id }) { entry ->
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
private fun ProgressMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
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
        }
    }
}

@Composable
private fun WorkoutVolumeChart(points: List<WorkoutProgressPoint>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val peakVolume = points.maxOfOrNull { it.volumeKg } ?: 0f

    Card {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Volume per workout", fontWeight = FontWeight.Bold)
                Text(
                    text = "Peak ${formatProgressVolume(peakVolume)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val verticalPadding = 8.dp.toPx()
                val chartHeight = (size.height - verticalPadding * 2f).coerceAtLeast(1f)
                val maxValue = peakVolume.coerceAtLeast(1f)

                repeat(4) { index ->
                    val y = verticalPadding + chartHeight * index / 3f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val offsets = points.mapIndexed { index, point ->
                    val x = if (points.size == 1) {
                        size.width / 2f
                    } else {
                        size.width * index / (points.lastIndex.toFloat())
                    }
                    val y = verticalPadding + chartHeight * (1f - point.volumeKg / maxValue)
                    Offset(x, y)
                }

                if (offsets.size > 1) {
                    val path = Path().apply {
                        moveTo(offsets.first().x, offsets.first().y)
                        offsets.drop(1).forEach { point -> lineTo(point.x, point.y) }
                    }
                    drawPath(
                        path = path,
                        color = lineColor,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                offsets.forEach { point ->
                    drawCircle(color = lineColor, radius = 4.dp.toPx(), center = point)
                }
            }
            if (points.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (points.size == 1) {
                        Arrangement.Center
                    } else {
                        Arrangement.SpaceBetween
                    }
                ) {
                    Text(
                        formatProgressDate(points.first().dateMillis),
                        style = MaterialTheme.typography.labelSmall
                    )
                    if (points.size > 1) {
                        Text(
                            formatProgressDate(points.last().dateMillis),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
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

private fun formatProgressDate(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(progressDateFormatter)
