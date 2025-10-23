package com.example.floating.caloriecounter.Model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class ExpectedPlan : RealmObject {
    @PrimaryKey
    var id: String = "expected_plan_singleton"
    var startDateMillis: Long = 0L
    var baselineWeightKg: Float = 0f
    var dailyDeltaKg: Float = 0f
}
