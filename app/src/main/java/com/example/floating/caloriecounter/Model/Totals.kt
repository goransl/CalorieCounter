package com.example.floating.caloriecounter.Model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class Totals : RealmObject {
    @PrimaryKey
    var id: String = "" // Unique identifier
    var name: String = ""
    var weight: Float = 0f
    var totalCalories: Float = 0f
    var totalProteins: Float = 0f
    var totalFat: Float = 0f
    var totalCarbs: Float = 0f
    var timestamp: Long = System.currentTimeMillis()
    var included: Boolean = true
    var cost: Float = 0f
    var totalCost: Float = 0f
}
