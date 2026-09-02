package com.example.floating.caloriecounter

import com.example.floating.caloriecounter.Model.toRealmModel
import com.example.floating.caloriecounter.backup.BackupData
import com.example.floating.caloriecounter.backup.BackupFormatException
import com.example.floating.caloriecounter.backup.ExpectedPlanBackup
import com.example.floating.caloriecounter.backup.FoodBackup
import com.example.floating.caloriecounter.backup.TotalsBackup
import com.example.floating.caloriecounter.backup.WeightEntryBackup
import com.example.floating.caloriecounter.backup.WorkoutEntryBackup
import com.example.floating.caloriecounter.backup.WorkoutNameBackup
import com.example.floating.caloriecounter.backup.WorkoutSetBackup
import com.example.floating.caloriecounter.backup.withGeneratedMissingPrimaryKeys
import com.example.floating.caloriecounter.utils.BACKUP_FOODS_FILE
import com.example.floating.caloriecounter.utils.BACKUP_MANIFEST_FILE
import com.example.floating.caloriecounter.utils.BACKUP_PLAN_FILE
import com.example.floating.caloriecounter.utils.BACKUP_TOTALS_FILE
import com.example.floating.caloriecounter.utils.BACKUP_WEIGHTS_FILE
import com.example.floating.caloriecounter.utils.BACKUP_WORKOUT_ENTRIES_FILE
import com.example.floating.caloriecounter.utils.BACKUP_WORKOUT_NAMES_FILE
import com.example.floating.caloriecounter.utils.decodeBackupZip
import com.example.floating.caloriecounter.utils.encodeBackupZip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupArchiveTest {

    @Test
    fun roundTripPreservesEveryModelAndExcludedTotal() {
        val original = completeBackupData()
        val bytes = ByteArrayOutputStream().also { output ->
            encodeBackupZip(
                outputStream = output,
                data = original,
                appVersion = "1.2.3-test",
                createdAtEpochMillis = 1_700_000_000_000L
            )
        }.toByteArray()

        val archive = decodeBackupZip(ByteArrayInputStream(bytes))

        assertEquals(original, archive.data)
        assertEquals(2, archive.manifest?.formatVersion)
        assertEquals("1.2.3-test", archive.manifest?.appVersion)
        assertEquals(1_700_000_000_000L, archive.manifest?.createdAtEpochMillis)
        assertFalse(archive.isLegacy)
        assertFalse(archive.data.totals.single().included)
        assertFalse(archive.data.totals.single().toRealmModel().included)
        assertFalse(archive.data.workoutEntries.single().completed)
        assertEquals("A", archive.data.workoutEntries.single().supersetGroupId)
        assertEquals(90, archive.data.workoutEntries.single().sets.single().restSeconds)
        assertTrue(archive.data.workoutEntries.single().sets.single().completed)
        assertEquals("Čokolada 🍫", archive.data.foods.single().name)
    }

    @Test
    fun legacyTotalWithoutIncludedImportsAsIncluded() {
        val archive = decodeBackupZip(
            ByteArrayInputStream(
                zipOf(
                    legacyEntries(
                        totalsJson = """
                            [{
                              "id":"legacy-total",
                              "name":"Legacy",
                              "totalCalories":123.0,
                              "timestamp":1700000000000
                            }]
                        """.trimIndent()
                    )
                )
            )
        )

        assertTrue(archive.isLegacy)
        assertTrue(archive.data.totals.single().included)
        assertTrue(archive.data.totals.single().toRealmModel().included)
        assertEquals(null, archive.data.expectedPlan)
    }

    @Test
    fun versionOneWorkoutWithoutNewFieldsImportsAsCompletedHistory() {
        val entries = legacyEntries(
            workoutsJson = """
                [{
                  "id":"legacy-workout",
                  "name":"Legacy squat",
                  "dateMillis":1700000000000,
                  "notes":"Old history",
                  "updatedAt":1700000000100,
                  "sets":[{"weightKg":100.0,"reps":5,"rest":"2 min"}]
                }]
            """.trimIndent()
        ).toMutableMap().apply {
            put(
                BACKUP_MANIFEST_FILE,
                """{"formatVersion":1,"createdAtEpochMillis":1,"appVersion":"old"}"""
            )
        }

        val archive = decodeBackupZip(ByteArrayInputStream(zipOf(entries)))
        val workout = archive.data.workoutEntries.single()
        val realmWorkout = workout.toRealmModel()

        assertFalse(archive.isLegacy)
        assertTrue(workout.completed)
        assertEquals(0, workout.position)
        assertEquals("", workout.supersetGroupId)
        assertTrue(workout.sets.single().completed)
        assertEquals(0, workout.sets.single().restSeconds)
        assertTrue(realmWorkout.completed)
        assertTrue(realmWorkout.sets.single().completed)
        assertEquals(120, realmWorkout.sets.single().restSeconds)
    }

    @Test
    fun futureFormatIsRejectedBeforeRestore() {
        val entries = legacyEntries().toMutableMap().apply {
            put(
                BACKUP_MANIFEST_FILE,
                """{"formatVersion":999,"createdAtEpochMillis":1,"appVersion":"future"}"""
            )
        }

        val error = assertThrows(BackupFormatException::class.java) {
            decodeBackupZip(ByteArrayInputStream(zipOf(entries)))
        }

        assertTrue(error.message.orEmpty().contains("Unsupported backup format"))
    }

    @Test
    fun incompleteArchiveIsRejectedBeforeRestore() {
        val error = assertThrows(BackupFormatException::class.java) {
            decodeBackupZip(
                ByteArrayInputStream(
                    zipOf(mapOf(BACKUP_FOODS_FILE to "[]"))
                )
            )
        }

        assertTrue(error.message.orEmpty().contains("missing"))
    }

    @Test
    fun duplicatePrimaryKeysAreRejectedDuringExport() {
        val duplicate = TotalsBackup(id = "same-id")
        val invalidData = BackupData(totals = listOf(duplicate, duplicate.copy(name = "Other")))

        val error = assertThrows(BackupFormatException::class.java) {
            encodeBackupZip(ByteArrayOutputStream(), invalidData, "test")
        }

        assertTrue(error.message.orEmpty().contains("duplicate total IDs"))
    }

    @Test
    fun legacyBlankPrimaryKeysAreGeneratedInSnapshotAndRemainImportable() {
        val generatedIds = listOf(
            "",
            "existing-food-id",
            "generated-food-id",
            "generated-total-id",
            "generated-weight-id",
            "generated-workout-id"
        ).iterator()
        val legacySnapshot = BackupData(
            foods = listOf(
                FoodBackup(id = "existing-food-id", name = "Existing"),
                FoodBackup(id = "", name = "Legacy food")
            ),
            totals = listOf(TotalsBackup(id = "", name = "Legacy total")),
            weights = listOf(WeightEntryBackup(id = "", weightKg = 80f)),
            workoutEntries = listOf(WorkoutEntryBackup(id = "", name = "Legacy workout"))
        )

        val normalized = legacySnapshot.withGeneratedMissingPrimaryKeys {
            generatedIds.next()
        }

        assertEquals("existing-food-id", normalized.foods.first().id)
        assertEquals("generated-food-id", normalized.foods.last().id)
        assertEquals("generated-total-id", normalized.totals.single().id)
        assertEquals("generated-weight-id", normalized.weights.single().id)
        assertEquals("generated-workout-id", normalized.workoutEntries.single().id)

        val bytes = ByteArrayOutputStream().also { output ->
            encodeBackupZip(output, normalized, "test")
        }.toByteArray()
        assertEquals(
            normalized,
            decodeBackupZip(ByteArrayInputStream(bytes)).data
        )
    }

    private fun completeBackupData() = BackupData(
        foods = listOf(
            FoodBackup(
                id = "food-1",
                name = "Čokolada 🍫",
                weight = 100f,
                caloriesPer100g = 535f,
                proteinsPer100g = 7.5f,
                fatPer100g = 30f,
                carbsPer100g = 59f,
                lastUsed = 1_700_000_000_100L,
                price = 2.49f,
                priceGrams = 100f
            )
        ),
        totals = listOf(
            TotalsBackup(
                id = "total-1",
                name = "Čokolada 🍫",
                weight = 20f,
                totalCalories = 107f,
                totalProteins = 1.5f,
                totalFat = 6f,
                totalCarbs = 11.8f,
                timestamp = 1_700_000_000_200L,
                included = false,
                cost = 0.5f
            )
        ),
        expectedPlan = ExpectedPlanBackup(
            startDateMillis = 1_700_000_000_300L,
            baselineWeightKg = 82.5f,
            dailyDeltaKg = -0.05f
        ),
        weights = listOf(
            WeightEntryBackup(
                id = "weight-1",
                timestamp = 1_700_000_000_400L,
                weightKg = 81.9f
            )
        ),
        workoutEntries = listOf(
            WorkoutEntryBackup(
                id = "workout-1",
                name = "Počep",
                dateMillis = 1_700_000_000_500L,
                notes = "Dober trening",
                position = 3,
                completed = false,
                supersetGroupId = "A",
                updatedAt = 1_700_000_000_600L,
                sets = listOf(
                    WorkoutSetBackup(
                        weightKg = 100f,
                        reps = 5,
                        rest = "1:30",
                        restSeconds = 90,
                        notes = "Controlled eccentric",
                        completed = true
                    )
                )
            )
        ),
        workoutNames = listOf(
            WorkoutNameBackup(name = "Počep", lastUsed = 1_700_000_000_600L)
        )
    )

    private fun legacyEntries(
        totalsJson: String = "[]",
        workoutsJson: String = "[]"
    ) = linkedMapOf(
        BACKUP_FOODS_FILE to "[]",
        BACKUP_TOTALS_FILE to totalsJson,
        BACKUP_PLAN_FILE to "{}",
        BACKUP_WEIGHTS_FILE to "[]",
        BACKUP_WORKOUT_ENTRIES_FILE to workoutsJson,
        BACKUP_WORKOUT_NAMES_FILE to "[]"
    )

    private fun zipOf(entries: Map<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(StandardCharsets.UTF_8))
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
