package com.example.floating.caloriecounter.utils

import com.example.floating.caloriecounter.Model.FoodRepository
import com.example.floating.caloriecounter.backup.BackupArchive
import com.example.floating.caloriecounter.backup.BackupData
import com.example.floating.caloriecounter.backup.BackupFormatException
import com.example.floating.caloriecounter.backup.BackupManifest
import com.example.floating.caloriecounter.backup.CURRENT_BACKUP_FORMAT_VERSION
import com.example.floating.caloriecounter.backup.MIN_SUPPORTED_BACKUP_FORMAT_VERSION
import com.example.floating.caloriecounter.backup.ExpectedPlanBackup
import com.example.floating.caloriecounter.backup.FoodBackup
import com.example.floating.caloriecounter.backup.TotalsBackup
import com.example.floating.caloriecounter.backup.WeightEntryBackup
import com.example.floating.caloriecounter.backup.WorkoutEntryBackup
import com.example.floating.caloriecounter.backup.WorkoutNameBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

const val BACKUP_MANIFEST_FILE = "manifest.json"
const val BACKUP_FOODS_FILE = "foods.json"
const val BACKUP_TOTALS_FILE = "totals.json"
const val BACKUP_PLAN_FILE = "expected_plan.json"
const val BACKUP_WEIGHTS_FILE = "weights.json"
const val BACKUP_WORKOUT_ENTRIES_FILE = "workout_entries.json"
const val BACKUP_WORKOUT_NAMES_FILE = "workout_names.json"

private const val MAX_ZIP_ENTRIES = 32
private const val MAX_ENTRY_BYTES = 32L * 1024L * 1024L
private const val MAX_ARCHIVE_BYTES = 64L * 1024L * 1024L

private val backupJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}

private val knownBackupFiles = setOf(
    BACKUP_MANIFEST_FILE,
    BACKUP_FOODS_FILE,
    BACKUP_TOTALS_FILE,
    BACKUP_PLAN_FILE,
    BACKUP_WEIGHTS_FILE,
    BACKUP_WORKOUT_ENTRIES_FILE,
    BACKUP_WORKOUT_NAMES_FILE
)

private val requiredDataFiles = knownBackupFiles - BACKUP_MANIFEST_FILE

/**
 * Takes a consistent immutable snapshot from Realm and writes it directly to [outputStream].
 * This function takes ownership of and closes [outputStream].
 */
suspend fun exportBackupZip(
    outputStream: OutputStream,
    repository: FoodRepository,
    appVersion: String
) = withContext(Dispatchers.IO) {
    outputStream.use { stream ->
        val data = repository.createBackupData()
        encodeBackupZip(stream, data, appVersion)
    }
}

/** This function takes ownership of and closes [inputStream]. */
suspend fun importBackupZip(inputStream: InputStream): BackupArchive = withContext(Dispatchers.IO) {
    inputStream.use(::decodeBackupZip)
}

/** Pure ZIP encoder, kept separate from Realm so the archive contract can be unit tested. */
fun encodeBackupZip(
    outputStream: OutputStream,
    data: BackupData,
    appVersion: String,
    createdAtEpochMillis: Long = System.currentTimeMillis()
) {
    validateBackupData(data)
    ensure(appVersion.isNotBlank()) { "The app version cannot be blank." }

    val manifest = BackupManifest(
        createdAtEpochMillis = createdAtEpochMillis,
        appVersion = appVersion
    )

    ZipOutputStream(BufferedOutputStream(outputStream)).use { zip ->
        zip.writeJsonEntry(
            BACKUP_MANIFEST_FILE,
            BackupManifest.serializer(),
            manifest,
            createdAtEpochMillis
        )
        zip.writeJsonEntry(
            BACKUP_FOODS_FILE,
            ListSerializer(FoodBackup.serializer()),
            data.foods,
            createdAtEpochMillis
        )
        zip.writeJsonEntry(
            BACKUP_TOTALS_FILE,
            ListSerializer(TotalsBackup.serializer()),
            data.totals,
            createdAtEpochMillis
        )
        zip.writeTextEntry(
            BACKUP_PLAN_FILE,
            data.expectedPlan?.let {
                backupJson.encodeToString(ExpectedPlanBackup.serializer(), it)
            } ?: "null",
            createdAtEpochMillis
        )
        zip.writeJsonEntry(
            BACKUP_WEIGHTS_FILE,
            ListSerializer(WeightEntryBackup.serializer()),
            data.weights,
            createdAtEpochMillis
        )
        zip.writeJsonEntry(
            BACKUP_WORKOUT_ENTRIES_FILE,
            ListSerializer(WorkoutEntryBackup.serializer()),
            data.workoutEntries,
            createdAtEpochMillis
        )
        zip.writeJsonEntry(
            BACKUP_WORKOUT_NAMES_FILE,
            ListSerializer(WorkoutNameBackup.serializer()),
            data.workoutNames,
            createdAtEpochMillis
        )
    }
}

/**
 * Decodes both current archives and legacy archives produced before manifest.json existed.
 * Version 1 and legacy archives remain importable; missing workout tracking properties use
 * completed-history defaults so an old restore does not hide previously logged workouts.
 */
fun decodeBackupZip(inputStream: InputStream): BackupArchive {
    val entries = readZipEntries(inputStream)
    requiredDataFiles.forEach { fileName ->
        ensure(entries.containsKey(fileName)) { "Backup is missing $fileName." }
    }

    val manifest = entries[BACKUP_MANIFEST_FILE]?.let { text ->
        decodeJson(BACKUP_MANIFEST_FILE, BackupManifest.serializer(), text)
    }
    if (manifest != null) {
        ensure(manifest.formatVersion in MIN_SUPPORTED_BACKUP_FORMAT_VERSION..CURRENT_BACKUP_FORMAT_VERSION) {
            "Unsupported backup format ${manifest.formatVersion}; this app supports formats " +
                "$MIN_SUPPORTED_BACKUP_FORMAT_VERSION-$CURRENT_BACKUP_FORMAT_VERSION."
        }
    }

    val decodedPlan = decodeExpectedPlan(entries.getValue(BACKUP_PLAN_FILE))
    val data = BackupData(
        foods = decodeJson(
            BACKUP_FOODS_FILE,
            ListSerializer(FoodBackup.serializer()),
            entries.getValue(BACKUP_FOODS_FILE)
        ),
        totals = decodeJson(
            BACKUP_TOTALS_FILE,
            ListSerializer(TotalsBackup.serializer()),
            entries.getValue(BACKUP_TOTALS_FILE)
        ),
        expectedPlan = decodedPlan?.let { plan ->
            // Format 4 stored a positive weekly percentage as weight loss.
            if (manifest?.formatVersion == 4 && plan.calculationMode == "weekly_loss_percent") {
                plan.copy(weeklyLossPercent = -plan.weeklyLossPercent)
            } else {
                plan
            }
        },
        weights = decodeJson(
            BACKUP_WEIGHTS_FILE,
            ListSerializer(WeightEntryBackup.serializer()),
            entries.getValue(BACKUP_WEIGHTS_FILE)
        ),
        workoutEntries = decodeJson(
            BACKUP_WORKOUT_ENTRIES_FILE,
            ListSerializer(WorkoutEntryBackup.serializer()),
            entries.getValue(BACKUP_WORKOUT_ENTRIES_FILE)
        ),
        workoutNames = decodeJson(
            BACKUP_WORKOUT_NAMES_FILE,
            ListSerializer(WorkoutNameBackup.serializer()),
            entries.getValue(BACKUP_WORKOUT_NAMES_FILE)
        )
    )
    validateBackupData(data)
    return BackupArchive(manifest = manifest, data = data)
}

private fun readZipEntries(inputStream: InputStream): Map<String, String> {
    val entries = mutableMapOf<String, String>()
    var entryCount = 0
    var archiveBytes = 0L
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

    try {
        ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                ensure(entryCount <= MAX_ZIP_ENTRIES) { "Backup contains too many ZIP entries." }
                ensure(!entry.name.contains('/') && !entry.name.contains('\\')) {
                    "Backup contains an invalid ZIP entry name: ${entry.name}."
                }

                if (entry.isDirectory) {
                    zip.closeEntry()
                    continue
                }

                var entryBytes = 0L
                val keepEntry = entry.name in knownBackupFiles
                val output = if (keepEntry) ByteArrayOutputStream() else null
                while (true) {
                    val count = zip.read(buffer)
                    if (count < 0) break
                    entryBytes += count
                    archiveBytes += count
                    ensure(entryBytes <= MAX_ENTRY_BYTES) { "${entry.name} is too large." }
                    ensure(archiveBytes <= MAX_ARCHIVE_BYTES) { "Backup is too large." }
                    output?.write(buffer, 0, count)
                }
                zip.closeEntry()

                if (output != null) {
                    ensure(!entries.containsKey(entry.name)) {
                        "Backup contains duplicate ${entry.name} entries."
                    }
                    entries[entry.name] = output.toByteArray()
                        .toString(StandardCharsets.UTF_8)
                        .trimStart('\uFEFF')
                }
            }
        }
    } catch (error: BackupFormatException) {
        throw error
    } catch (error: Exception) {
        throw BackupFormatException("The selected file is not a readable backup ZIP.", error)
    }

    return entries
}

private fun decodeExpectedPlan(text: String): ExpectedPlanBackup? {
    return try {
        when (val element = backupJson.parseToJsonElement(text)) {
            JsonNull -> null
            is JsonObject -> if (element.isEmpty()) {
                null // Legacy backups represented an absent plan as {}.
            } else {
                backupJson.decodeFromJsonElement(ExpectedPlanBackup.serializer(), element)
            }
            else -> throw BackupFormatException("$BACKUP_PLAN_FILE must contain an object or null.")
        }
    } catch (error: BackupFormatException) {
        throw error
    } catch (error: Exception) {
        throw BackupFormatException("$BACKUP_PLAN_FILE is not valid JSON.", error)
    }
}

private fun <T> decodeJson(fileName: String, serializer: KSerializer<T>, text: String): T {
    return try {
        backupJson.decodeFromString(serializer, text)
    } catch (error: SerializationException) {
        throw BackupFormatException("$fileName is not valid backup JSON.", error)
    } catch (error: IllegalArgumentException) {
        throw BackupFormatException("$fileName contains invalid values.", error)
    }
}

private fun <T> ZipOutputStream.writeJsonEntry(
    fileName: String,
    serializer: KSerializer<T>,
    value: T,
    timestamp: Long
) {
    writeTextEntry(fileName, backupJson.encodeToString(serializer, value), timestamp)
}

private fun ZipOutputStream.writeTextEntry(fileName: String, text: String, timestamp: Long) {
    putNextEntry(ZipEntry(fileName).apply { time = timestamp })
    try {
        write(text.toByteArray(StandardCharsets.UTF_8))
    } finally {
        closeEntry()
    }
}

private fun validateBackupData(data: BackupData) {
    ensureUniqueNonBlank("food IDs", data.foods.map { it.id })
    ensureUniqueNonBlank("total IDs", data.totals.map { it.id })
    ensureUniqueNonBlank("weight IDs", data.weights.map { it.id })
    ensureUniqueNonBlank("workout entry IDs", data.workoutEntries.map { it.id })
    ensureUniqueNonBlank("workout names", data.workoutNames.map { it.name })

    data.expectedPlan?.let { plan ->
        ensure(plan.id == "expected_plan_singleton") { "Expected plan has an invalid ID." }
        ensureFinite("expected plan baselineWeightKg", plan.baselineWeightKg)
        ensureFinite("expected plan dailyDeltaKg", plan.dailyDeltaKg)
        ensureFinite("expected plan weeklyLossPercent", plan.weeklyLossPercent)
        ensure(plan.calculationMode in setOf("daily_change", "weekly_loss_percent")) {
            "Expected plan has an invalid calculation mode."
        }
        ensure(plan.weeklyLossPercent > -100f) {
            "Expected plan weeklyLossPercent must be greater than -100."
        }
    }
    data.foods.forEachIndexed { index, food ->
        ensureFinite("foods[$index].weight", food.weight)
        ensureFinite("foods[$index].calories_per_100g", food.caloriesPer100g)
        ensureFinite("foods[$index].proteins_per_100g", food.proteinsPer100g)
        ensureFinite("foods[$index].fat_per_100g", food.fatPer100g)
        ensureFinite("foods[$index].carbs_per_100g", food.carbsPer100g)
        ensureFinite("foods[$index].price", food.price)
        ensureFinite("foods[$index].priceGrams", food.priceGrams)
    }
    data.totals.forEachIndexed { index, total ->
        ensure(total.position >= 0) { "totals[$index].position cannot be negative." }
        ensureFinite("totals[$index].weight", total.weight)
        ensureFinite("totals[$index].totalCalories", total.totalCalories)
        ensureFinite("totals[$index].totalProteins", total.totalProteins)
        ensureFinite("totals[$index].totalFat", total.totalFat)
        ensureFinite("totals[$index].totalCarbs", total.totalCarbs)
        ensureFinite("totals[$index].cost", total.cost)
    }
    data.weights.forEachIndexed { index, weight ->
        ensureFinite("weights[$index].weightKg", weight.weightKg)
    }
    data.workoutEntries.forEachIndexed { entryIndex, entry ->
        ensure(entry.position >= 0) { "workoutEntries[$entryIndex].position cannot be negative." }
        entry.sets.forEachIndexed { setIndex, set ->
            ensureFinite("workoutEntries[$entryIndex].sets[$setIndex].weightKg", set.weightKg)
            ensureFinite("workoutEntries[$entryIndex].sets[$setIndex].oneRepMaxKg", set.oneRepMaxKg)
            ensure(set.oneRepMaxKg >= 0f) {
                "workoutEntries[$entryIndex].sets[$setIndex].oneRepMaxKg cannot be negative."
            }
            ensure(set.reps >= 0) {
                "workoutEntries[$entryIndex].sets[$setIndex].reps cannot be negative."
            }
            ensure(set.restSeconds >= 0) {
                "workoutEntries[$entryIndex].sets[$setIndex].restSeconds cannot be negative."
            }
        }
    }
}

private fun ensureUniqueNonBlank(label: String, values: List<String>) {
    ensure(values.none { it.isBlank() }) { "Backup contains blank $label." }
    ensure(values.size == values.toSet().size) { "Backup contains duplicate $label." }
}

private fun ensureFinite(label: String, value: Float) {
    ensure(value.isFinite()) { "$label must be a finite number." }
}

private inline fun ensure(condition: Boolean, lazyMessage: () -> String) {
    if (!condition) throw BackupFormatException(lazyMessage())
}
