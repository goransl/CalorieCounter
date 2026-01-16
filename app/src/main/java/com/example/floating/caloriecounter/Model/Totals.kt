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

    fun copy(
        id: String = this.id,
        name: String = this.name,
        weight: Float = this.weight,
        totalCalories: Float = this.totalCalories,
        totalProteins: Float = this.totalProteins,
        totalFat: Float = this.totalFat,
        totalCarbs: Float = this.totalCarbs
    ): Totals {
        return Totals().apply {
            this.id = id
            this.name = name
            this.weight = weight
            this.totalCalories = totalCalories
            this.totalProteins = totalProteins
            this.totalFat = totalFat
            this.totalCarbs = totalCarbs
        }
    }
}
