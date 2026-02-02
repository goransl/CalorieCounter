import android.content.Context
import com.example.floating.caloriecounter.Model.FoodRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

suspend fun createBackupZip(context: Context, repository: FoodRepository): File = withContext(Dispatchers.IO) {
    // 1) Collect data
    val foods        = repository.getAllFoodsForBackup()                 // List<Food>
    val totalsAll    = repository.getAllTotalsAllDates()        // List<Totals>
    val plan         = repository.getExpectedPlanSingleton()    // ExpectedPlan?
    val weightLog    = repository.getAllWeightEntries()         // List<WeightEntry>
    val workoutEntries = repository.getAllWorkoutEntriesForBackup() // List<WorkoutEntry>
    val workoutNames   = repository.getAllWorkoutNamesForBackup()   // List<WorkoutName>

    // 2) Build JSON files in cache
    val workDir = File(context.cacheDir, "cc_backup_tmp").apply { mkdirs() }
    val foodJson = File(workDir, "foods.json")
    val totalsJson = File(workDir, "totals.json")
    val planJson = File(workDir, "expected_plan.json")
    val weightsJson = File(workDir, "weights.json")
    val workoutEntriesJson = File(workDir, "workout_entries.json")
    val workoutNamesJson   = File(workDir, "workout_names.json")



    // Foods
    run {
        val arr = JSONArray()
        foods.forEach { f ->
            arr.put(JSONObject().apply {
                put("id", f.id)
                put("name", f.name)
                put("weight", f.weight)
                put("calories_per_100g", f.calories)
                put("proteins_per_100g", f.proteins)
                put("fat_per_100g", f.fat)
                put("carbs_per_100g", f.carbs)
                put("lastUsed", f.lastUsed)
                put("price", f.price)
                put("priceGrams", f.priceGrams)
            })
        }
        foodJson.writeText(arr.toString(2))
    }

    // Totals (all dates)
    run {
        val arr = JSONArray()
        totalsAll.forEach { t ->
            arr.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("weight", t.weight)
                put("totalCalories", t.totalCalories)
                put("totalProteins", t.totalProteins)
                put("totalFat", t.totalFat)
                put("totalCarbs", t.totalCarbs)
                put("timestamp", t.timestamp)
                put("cost", t.cost)
            })
        }
        totalsJson.writeText(arr.toString(2))
    }

    // ExpectedPlan (singleton)
    run {
        val obj = JSONObject()
        if (plan != null) {
            obj.put("id", plan.id)
            obj.put("startDateMillis", plan.startDateMillis)
            obj.put("baselineWeightKg", plan.baselineWeightKg)
            obj.put("dailyDeltaKg", plan.dailyDeltaKg)
        }
        planJson.writeText(obj.toString(2))
    }

    // Weights
    run {
        val arr = JSONArray()
        weightLog.forEach { w ->
            arr.put(JSONObject().apply {
                put("id", w.id)
                put("timestamp", w.timestamp)
                put("weightKg", w.weightKg)
            })
        }
        weightsJson.writeText(arr.toString(2))
    }

    // WorkoutEntries (with embedded sets)
    run {
        val arr = JSONArray()
        workoutEntries.forEach { e ->
            val setsArr = JSONArray()
            e.sets.forEach { s ->
                setsArr.put(JSONObject().apply {
                    put("weightKg", s.weightKg)
                    put("reps", s.reps)
                    put("rest", s.rest)
                })
            }

            arr.put(JSONObject().apply {
                put("id", e.id)
                put("name", e.name)
                put("dateMillis", e.dateMillis)
                put("notes", e.notes)
                put("updatedAt", e.updatedAt)
                put("sets", setsArr)
            })
        }
        workoutEntriesJson.writeText(arr.toString(2))
    }

    // WorkoutNames
    run {
        val arr = JSONArray()
        workoutNames.forEach { w ->
            arr.put(JSONObject().apply {
                put("name", w.name)
                put("lastUsed", w.lastUsed)
            })
        }
        workoutNamesJson.writeText(arr.toString(2))
    }

    // 3) Zip them
    val outZip = File(context.cacheDir, "CalorieCounter_Backup_${System.currentTimeMillis()}.zip")
    ZipOutputStream(outZip.outputStream()).use { zos ->
        fun addFile(f: File, entryName: String = f.name) {
            zos.putNextEntry(ZipEntry(entryName))
            f.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
        addFile(foodJson)
        addFile(totalsJson)
        addFile(planJson)
        addFile(weightsJson)
        addFile(workoutEntriesJson)
        addFile(workoutNamesJson)
    }

    // Optional: clean temp JSONs
    foodJson.delete()
    totalsJson.delete()
    planJson.delete()
    weightsJson.delete()
    workDir.delete()
    workoutEntriesJson.delete()
    workoutNamesJson.delete()

    outZip
}
