package com.example.floating.caloriecounter.Model

import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey

class WorkoutName : RealmObject {
    @PrimaryKey
    var name: String = ""
    var lastUsed: Long = 0L
}
