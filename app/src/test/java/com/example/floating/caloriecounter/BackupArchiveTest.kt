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
        assertEquals(1, archive.manifest?.formatVersion)
        assertEquals("1.2.3-test", archive.manifest?.appVersion)
        assertEquals(1_700_000_000_000L, archive.manifest?.createdAtEpochMillis)
        assertFalse(archive.isLegacy)
        assertFalse(archive.data.totals.single().included)
        assertFalse(archive.data.totals.single().toRealmModel().included)
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
                updatedAt = 1_700_000_000_600L,
                sets = listOf(WorkoutSetBackup(weightKg = 100f, reps = 5, rest = "2 min"))
            )
        ),
        workoutNames = listOf(
            WorkoutNameBackup(name = "Počep", lastUsed = 1_700_000_000_600L)
        )
    )

    private fun legacyEntries(totalsJson: String = "[]") = linkedMapOf(
        BACKUP_FOODS_FILE to "[]",
        BACKUP_TOTALS_FILE to totalsJson,
        BACKUP_PLAN_FILE to "{}",
        BACKUP_WEIGHTS_FILE to "[]",
        BACKUP_WORKOUT_ENTRIES_FILE to "[]",
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

