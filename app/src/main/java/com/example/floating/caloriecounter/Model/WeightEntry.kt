package com.example.floating.caloriecounter.Model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.UUID

class WeightEntry : RealmObject {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var timestamp: Long = System.currentTimeMillis() // when the weight is recorded
    var weightKg: Float = 0f
}