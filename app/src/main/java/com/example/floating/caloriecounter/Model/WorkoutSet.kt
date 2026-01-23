package com.example.floating.caloriecounter.Model

import io.realm.kotlin.types.EmbeddedRealmObject

class WorkoutSet : EmbeddedRealmObject {
    var weightKg: Float = 0f
    var reps: Int = 0
    var rest: String = ""
}
