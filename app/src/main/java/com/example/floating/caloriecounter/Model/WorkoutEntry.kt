package com.example.floating.caloriecounter.Model

import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.annotations.PrimaryKey
import java.util.UUID

class WorkoutEntry : RealmObject {
    @PrimaryKey
    var id: String = UUID.randomUUID().toString()
    var name: String = ""
    var dateMillis: Long = 0L
    var notes: String = ""
    var sets: RealmList<WorkoutSet> = realmListOf()
    var updatedAt: Long = 0L
}
