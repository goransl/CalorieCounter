package com.example.floating.caloriecounter.Model

import com.example.floating.caloriecounter.backup.ExpectedPlanBackup
import com.example.floating.caloriecounter.backup.FoodBackup
import com.example.floating.caloriecounter.backup.TotalsBackup
import com.example.floating.caloriecounter.backup.WeightEntryBackup
import com.example.floating.caloriecounter.backup.WorkoutEntryBackup
import com.example.floating.caloriecounter.backup.WorkoutNameBackup
import com.example.floating.caloriecounter.backup.WorkoutSetBackup

internal fun Food.toBackupModel() = FoodBackup(
    id = id,
    name = name,
    weight = weight,
    caloriesPer100g = calories,
    proteinsPer100g = proteins,
    fatPer100g = fat,
    carbsPer100g = carbs,
    lastUsed = lastUsed,
    price = price,
    priceGrams = priceGrams
)

internal fun FoodBackup.toRealmModel() = Food().also { food ->
    food.id = id
    food.name = name
    food.weight = weight
    food.calories = caloriesPer100g
    food.proteins = proteinsPer100g
    food.fat = fatPer100g
    food.carbs = carbsPer100g
    food.lastUsed = lastUsed
    food.price = price
    food.priceGrams = priceGrams
}

internal fun Totals.toBackupModel() = TotalsBackup(
    id = id,
    name = name,
    weight = weight,
    totalCalories = totalCalories,
    totalProteins = totalProteins,
    totalFat = totalFat,
    totalCarbs = totalCarbs,
    timestamp = timestamp,
    included = included,
    cost = cost
)

internal fun TotalsBackup.toRealmModel() = Totals().also { total ->
    total.id = id
    total.name = name
    total.weight = weight
    total.totalCalories = totalCalories
    total.totalProteins = totalProteins
    total.totalFat = totalFat
    total.totalCarbs = totalCarbs
    total.timestamp = timestamp
    total.included = included
    total.cost = cost
    total.totalCost = 0f // Aggregated UI-only value; recalculated from row costs.
}

internal fun ExpectedPlan.toBackupModel() = ExpectedPlanBackup(
    id = id,
    startDateMillis = startDateMillis,
    baselineWeightKg = baselineWeightKg,
    dailyDeltaKg = dailyDeltaKg
)

internal fun ExpectedPlanBackup.toRealmModel() = ExpectedPlan().also { plan ->
    plan.id = id
    plan.startDateMillis = startDateMillis
    plan.baselineWeightKg = baselineWeightKg
    plan.dailyDeltaKg = dailyDeltaKg
}

internal fun WeightEntry.toBackupModel() = WeightEntryBackup(
    id = id,
    timestamp = timestamp,
    weightKg = weightKg
)

internal fun WeightEntryBackup.toRealmModel() = WeightEntry().also { weight ->
    weight.id = id
    weight.timestamp = timestamp
    weight.weightKg = weightKg
}

internal fun WorkoutEntry.toBackupModel() = WorkoutEntryBackup(
    id = id,
    name = name,
    dateMillis = dateMillis,
    notes = notes,
    position = position,
    completed = completed,
    supersetGroupId = supersetGroupId,
    updatedAt = updatedAt,
    sets = sets.map { set ->
        WorkoutSetBackup(
            weightKg = set.weightKg,
            reps = set.reps,
            rest = set.rest,
            restSeconds = set.restSeconds,
            notes = set.notes,
            completed = set.completed
        )
    }
)

internal fun WorkoutEntryBackup.toRealmModel() = WorkoutEntry().also { workout ->
    workout.id = id
    workout.name = name
    workout.dateMillis = dateMillis
    workout.notes = notes
    workout.position = position
    workout.completed = completed
    workout.supersetGroupId = supersetGroupId
    workout.updatedAt = updatedAt
    sets.forEach { set ->
        workout.sets.add(WorkoutSet().also { workoutSet ->
            workoutSet.weightKg = set.weightKg
            workoutSet.reps = set.reps
            workoutSet.rest = set.rest
            workoutSet.restSeconds = if (set.restSeconds > 0) {
                set.restSeconds
            } else {
                parseRestSeconds(set.rest)
            }
            workoutSet.notes = set.notes
            workoutSet.completed = set.completed
        })
    }
}

internal fun WorkoutName.toBackupModel() = WorkoutNameBackup(
    name = name,
    lastUsed = lastUsed
)

internal fun WorkoutNameBackup.toRealmModel() = WorkoutName().also { workoutName ->
    workoutName.name = name
    workoutName.lastUsed = lastUsed
}
