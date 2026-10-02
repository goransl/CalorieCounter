package com.example.floating.caloriecounter.Model

import io.realm.kotlin.types.EmbeddedRealmObject

class WorkoutSet : EmbeddedRealmObject {
    var weightKg: Float = 0f
    var reps: Int = 0
    /** Original free-form value is retained for compatibility with existing workouts. */
    var rest: String = ""
    /** Parsed rest duration for timers and future statistics; 0 means unspecified. */
    var restSeconds: Int = 0
    var notes: String = ""
    var completed: Boolean = false
}

/** Detached, immutable workout-set data safe to keep in Compose state. */
data class WorkoutSetSnapshot(
    val weightKg: Float,
    val reps: Int,
    val rest: String,
    val restSeconds: Int,
    val notes: String,
    val completed: Boolean
)

/** Immutable input used by the UI and repository so Realm objects never escape a write. */
data class WorkoutSetInput(
    val weightKg: Float = 0f,
    val reps: Int = 0,
    val rest: String = "",
    val restSeconds: Int = 0,
    val notes: String = "",
    val completed: Boolean = false
)

/** Converts common inputs such as `90`, `1:30`, `1:30 min`, `2 min` and `1m 30s` to seconds. */
fun parseRestSeconds(value: String): Int {
    val text = value.trim().lowercase().replace(',', '.')
    if (text.isEmpty()) return 0
    text.toIntOrNull()?.let { return it.coerceIn(0, 86_400) }

    Regex("^(\\d+):(\\d{1,2})(?:\\s*(?:m|min|mins|minute|minutes))?$").matchEntire(text)?.let { match ->
        val minutes = match.groupValues[1].toIntOrNull() ?: 0
        val seconds = match.groupValues[2].toIntOrNull() ?: 0
        return (minutes * 60 + seconds).coerceIn(0, 86_400)
    }

    val minutes = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:m|min|mins|minute|minutes)")
        .find(text)
        ?.groupValues
        ?.get(1)
        ?.toDoubleOrNull()
        ?: 0.0
    val seconds = Regex("(\\d+)\\s*(?:s|sec|secs|second|seconds)")
        .find(text)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
        ?: 0
    return (minutes * 60.0).toInt().plus(seconds).coerceIn(0, 86_400)
}
