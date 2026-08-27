package com.example.floating.caloriecounter.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val CURRENT_BACKUP_FORMAT_VERSION = 2
const val MIN_SUPPORTED_BACKUP_FORMAT_VERSION = 1

@Serializable
data class BackupManifest(
    val formatVersion: Int = CURRENT_BACKUP_FORMAT_VERSION,
    val createdAtEpochMillis: Long,
    val appVersion: String
)

@Serializable
data class FoodBackup(
    val id: String = "",
    val name: String = "",
    val weight: Float = 0f,
    @SerialName("calories_per_100g") val caloriesPer100g: Float = 0f,
    @SerialName("proteins_per_100g") val proteinsPer100g: Float = 0f,
    @SerialName("fat_per_100g") val fatPer100g: Float = 0f,
    @SerialName("carbs_per_100g") val carbsPer100g: Float = 0f,
    val lastUsed: Long = 0L,
    val price: Float = 0f,
    val priceGrams: Float = 0f
)

@Serializable
data class TotalsBackup(
    val id: String = "",
    val name: String = "",
    val weight: Float = 0f,
    val totalCalories: Float = 0f,
    val totalProteins: Float = 0f,
    val totalFat: Float = 0f,
    val totalCarbs: Float = 0f,
    val timestamp: Long = 0L,
    // Old backups do not have this property. Their rows were included by default.
    val included: Boolean = true,
    val cost: Float = 0f
)

@Serializable
data class ExpectedPlanBackup(
    val id: String = "expected_plan_singleton",
    val startDateMillis: Long = 0L,
    val baselineWeightKg: Float = 0f,
    val dailyDeltaKg: Float = 0f
)

@Serializable
data class WeightEntryBackup(
    val id: String = "",
    val timestamp: Long = 0L,
    val weightKg: Float = 0f
)

@Serializable
data class WorkoutSetBackup(
    val weightKg: Float = 0f,
    val reps: Int = 0,
    val rest: String = "",
    val restSeconds: Int = 0,
    val notes: String = "",
    // Workout data in older archives represents already performed history.
    val completed: Boolean = true
)

@Serializable
data class WorkoutEntryBackup(
    val id: String = "",
    val name: String = "",
    val dateMillis: Long = 0L,
    val notes: String = "",
    val position: Int = 0,
    // Workout data in older archives represents already performed history.
    val completed: Boolean = true,
    val supersetGroupId: String = "",
    val updatedAt: Long = 0L,
    val sets: List<WorkoutSetBackup> = emptyList()
)

@Serializable
data class WorkoutNameBackup(
    val name: String = "",
    val lastUsed: Long = 0L
)

data class BackupData(
    val foods: List<FoodBackup> = emptyList(),
    val totals: List<TotalsBackup> = emptyList(),
    val expectedPlan: ExpectedPlanBackup? = null,
    val weights: List<WeightEntryBackup> = emptyList(),
    val workoutEntries: List<WorkoutEntryBackup> = emptyList(),
    val workoutNames: List<WorkoutNameBackup> = emptyList()
) {
    val recordCount: Int
        get() = foods.size +
            totals.size +
            weights.size +
            workoutEntries.size +
            workoutEntries.sumOf { it.sets.size } +
            workoutNames.size +
            if (expectedPlan == null) 0 else 1
}

data class BackupArchive(
    val manifest: BackupManifest?,
    val data: BackupData
) {
    val isLegacy: Boolean
        get() = manifest == null
}

class BackupFormatException(
    message: String,
    cause: Throwable? = null
) : IllegalArgumentException(message, cause)
