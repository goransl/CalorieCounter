package com.example.floating.caloriecounter.Model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.UUID

class Food : RealmObject {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var name: String = ""
    var weight: Float = 0f
    var calories: Float = 0f
    var proteins: Float = 0f
    var fat: Float = 0f
    var carbs: Float = 0f
    var lastUsed: Long = 0L
    var price: Float = 0f
    var priceGrams: Float = 0f
}
