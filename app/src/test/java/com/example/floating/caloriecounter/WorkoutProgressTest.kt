package com.example.floating.caloriecounter

import com.example.floating.caloriecounter.Model.WorkoutEntrySnapshot
import com.example.floating.caloriecounter.Model.WorkoutSetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutProgressTest {

    @Test
    fun progress_usesOnlyCompletedSessionsAndCalculatesMetrics() {
        val older = workoutEntry(
            id = "older",
            dateMillis = 1_000L,
            completed = true,
            sets = listOf(workoutSet(weightKg = 50f, reps = 10))
        )
        val latest = workoutEntry(
            id = "latest",
            dateMillis = 2_000L,
            completed = true,
            sets = listOf(
                workoutSet(weightKg = 60f, reps = 5),
                workoutSet(weightKg = 40f, reps = 10)
            )
        )
        val incomplete = workoutEntry(
            id = "incomplete",
            dateMillis = 3_000L,
            completed = false,
            sets = listOf(workoutSet(weightKg = 200f, reps = 1))
        )

        val stats = calculateWorkoutProgress(listOf(latest, incomplete, older))

        assertEquals(2, stats.sessionCount)
        assertEquals("latest", stats.latestEntry?.id)
        assertEquals(700f, stats.latestVolumeKg, 0.001f)
        assertEquals(60f, stats.maxWeightKg, 0.001f)
        assertEquals(50f, stats.bestSet?.weightKg ?: 0f, 0.001f)
        assertEquals(10, stats.bestSet?.reps)
        assertEquals(listOf(1_000L, 2_000L), stats.chartPoints.map { it.dateMillis })
        assertEquals(listOf(500f, 700f), stats.chartPoints.map { it.volumeKg })
    }

    @Test
    fun progressChart_keepsNewestPointsInChronologicalOrder() {
        val entries = (1L..15L).map { date ->
            workoutEntry(
                id = date.toString(),
                dateMillis = date,
                completed = true,
                sets = listOf(workoutSet(weightKg = date.toFloat(), reps = 1))
            )
        }

        val stats = calculateWorkoutProgress(entries, chartLimit = 12)

        assertEquals((4L..15L).toList(), stats.chartPoints.map { it.dateMillis })
    }

    @Test
    fun bodyweightExercise_usesRepetitionsForBestSet() {
        val entry = workoutEntry(
            id = "bodyweight",
            dateMillis = 1_000L,
            completed = true,
            sets = listOf(
                workoutSet(weightKg = 0f, reps = 8),
                workoutSet(weightKg = 0f, reps = 12)
            )
        )

        val stats = calculateWorkoutProgress(listOf(entry))

        assertEquals(12, stats.bestSet?.reps)
        assertEquals(0f, stats.latestVolumeKg, 0.001f)
    }

    private fun workoutEntry(
        id: String,
        dateMillis: Long,
        completed: Boolean,
        sets: List<WorkoutSetSnapshot>
    ) = WorkoutEntrySnapshot(
        id = id,
        name = "Bench press",
        dateMillis = dateMillis,
        notes = "",
        sets = sets,
        position = 0,
        completed = completed,
        supersetGroupId = "",
        updatedAt = dateMillis
    )

    private fun workoutSet(weightKg: Float, reps: Int) = WorkoutSetSnapshot(
        weightKg = weightKg,
        reps = reps,
        rest = "",
        restSeconds = 0,
        notes = "",
        completed = false
    )
}
