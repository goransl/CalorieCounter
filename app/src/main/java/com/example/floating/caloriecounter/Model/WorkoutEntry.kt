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
    /** Zero-based position inside a workout day. */
    var position: Int = 0
    /** Only completed exercises are shown in workout history. */
    var completed: Boolean = false
    /** Empty when not in a superset; equal values identify exercises in the same group. */
    var supersetGroupId: String = ""
    var updatedAt: Long = 0L
}

/** Detached, immutable workout data safe to keep in Compose state after Realm changes. */
data class WorkoutEntrySnapshot(
    val id: String,
    val name: String,
    val dateMillis: Long,
    val notes: String,
    val sets: List<WorkoutSetSnapshot>,
    val position: Int,
    val completed: Boolean,
    val supersetGroupId: String,
    val updatedAt: Long
)

/** One incrementally loaded page of completed workout history. */
data class WorkoutHistoryPage(
    val entries: List<WorkoutEntrySnapshot>,
    val hasMore: Boolean
)

/** Detached summary used by the copy-workout picker. */
data class WorkoutDaySummary(
    val dateMillis: Long,
    val exerciseNames: List<String>
)
