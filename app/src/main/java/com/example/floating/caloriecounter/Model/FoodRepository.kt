package com.example.floating.caloriecounter.Model

import com.example.floating.caloriecounter.backup.BackupData
import com.example.floating.caloriecounter.backup.TotalsBackup
import com.example.floating.caloriecounter.backup.WeightEntryBackup
import com.example.floating.caloriecounter.backup.WorkoutEntryBackup
import com.example.floating.caloriecounter.backup.WorkoutNameBackup
import com.example.floating.caloriecounter.backup.withGeneratedMissingPrimaryKeys
import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.dynamic.getValue
import io.realm.kotlin.ext.query
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import java.util.UUID

class FoodRepository : AutoCloseable {
    private val config = RealmConfiguration.Builder(
        schema = setOf(
            Food::class,
            Totals::class,
            WeightEntry::class,
            ExpectedPlan::class,
            WorkoutEntry::class,
            WorkoutSet::class,
            WorkoutName::class
        )
    )
        .schemaVersion(7)
        .migration(
            AutomaticSchemaMigration { ctx ->
                val oldVersion = ctx.oldRealm.schemaVersion()
                if (oldVersion < 6) {
                    ctx.enumerate(className = "Totals") { _: DynamicRealmObject, newObj: DynamicMutableRealmObject? ->
                        newObj?.set("totalCost", 0f)
                    }
                }

                if (oldVersion < 7 && ctx.oldRealm.schema()["WorkoutEntry"] != null) {
                    // Everything already stored in Workout tracking is historical/performed data.
                    ctx.enumerate(className = "WorkoutEntry") { oldObj: DynamicRealmObject, newObj: DynamicMutableRealmObject? ->
                        newObj?.set("position", 0L)
                        newObj?.set("completed", true)
                        newObj?.set("supersetGroupId", "")
                        val oldSets = oldObj.getObjectList("sets")
                        val newSets = newObj?.getObjectList("sets")
                        oldSets.forEachIndexed { index, oldSet ->
                            val newSet = newSets?.getOrNull(index) ?: return@forEachIndexed
                            val legacyRest = oldSet.getValue<String>("rest")
                            newSet.set("restSeconds", parseRestSeconds(legacyRest).toLong())
                            newSet.set("notes", "")
                            newSet.set("completed", true)
                        }
                    }
                }
            }
        )
        .build()

    private val realm: Realm = Realm.open(config)

    fun getAllFoods(): RealmResults<Food> = realm.query<Food>().find()

    /**
     * Reads every table while holding one Realm transaction and immediately detaches it into
     * serializable values. This prevents live Realm objects or different database versions from
     * leaking into the archive writer.
     */
    suspend fun createBackupData(): BackupData = realm.write {
        BackupData(
            foods = query<Food>().find().map(Food::toBackupModel).sortedBy { it.id },
            totals = query<Totals>().find().map(Totals::toBackupModel)
                .sortedWith(compareBy<TotalsBackup> { it.timestamp }.thenBy { it.id }),
            expectedPlan = query<ExpectedPlan>("id == $0", "expected_plan_singleton")
                .first()
                .find()
                ?.toBackupModel(),
            weights = query<WeightEntry>().find().map(WeightEntry::toBackupModel)
                .sortedWith(compareBy<WeightEntryBackup> { it.timestamp }.thenBy { it.id }),
            workoutEntries = query<WorkoutEntry>().find().map(WorkoutEntry::toBackupModel)
                .sortedWith(
                    compareBy<WorkoutEntryBackup> { it.dateMillis }
                        .thenBy { it.position }
                        .thenBy { it.id }
                ),
            workoutNames = query<WorkoutName>().find().map(WorkoutName::toBackupModel)
                .sortedWith(compareBy<WorkoutNameBackup> { it.lastUsed }.thenBy { it.name })
        ).withGeneratedMissingPrimaryKeys()
    }

    /** Replaces all app data atomically after the archive has been decoded and validated. */
    suspend fun restoreBackup(data: BackupData) {
        realm.write {
            // Delete owners before embedded WorkoutSet objects.
            delete(query<WorkoutEntry>())
            delete(query<WorkoutName>())
            delete(query<ExpectedPlan>())
            delete(query<WeightEntry>())
            delete(query<Totals>())
            delete(query<Food>())

            data.foods.forEach { food ->
                copyToRealm(food.toRealmModel())
            }
            data.totals.forEach { total ->
                copyToRealm(total.toRealmModel())
            }
            data.expectedPlan?.let { plan ->
                copyToRealm(plan.toRealmModel())
            }
            data.weights.forEach { weight ->
                copyToRealm(weight.toRealmModel())
            }
            data.workoutEntries.forEach { workout ->
                copyToRealm(workout.toRealmModel())
            }
            data.workoutNames.forEach { workoutName ->
                copyToRealm(workoutName.toRealmModel())
            }
        }
    }

    override fun close() {
        realm.close()
    }

    // touch food usage time (call when user selects/saves a food)
    suspend fun touchFood(name: String) {
        realm.write {
            query<Food>("name == $0", name).first().find()?.let {
                it.lastUsed = System.currentTimeMillis()
            }
        }
    }

    suspend fun deleteFood(name: String) {
        realm.write {
            val food = query<Food>("name == $0", name).first().find()
            if (food != null) {
                delete(food)
            }
        }
    }

    fun getSuggestions(query: String): List<String> {
        /*return realm.query<Food>("name CONTAINS[c] $0", query)
            .find()
            .take(5)
            .map { it.name }*/

        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        // Build: name CONTAINS[c] word1 AND name CONTAINS[c] word2 ...
        var q = realm.query<Food>("name CONTAINS[c] $0", words.first())
        words.drop(1).forEach { w ->
            q = q.query("name CONTAINS[c] $0", w)
        }

        return q
            .sort("lastUsed", Sort.DESCENDING)   // most recently used first
            .find()
            .map { it.name }
            .distinct()                           // in case you have duplicate names
            .take(20)                             // limit to 20
    }

    fun getFoodByName(name: String): Food? {
        return realm.query<Food>("name == $0", name).first().find()
    }

    suspend fun updateFood(existingFood: Food, newFood: Food) {
        realm.write {
            // Fetch the live, managed object within the write transaction
            val foodToUpdate = query<Food>("id == $0", existingFood.id).first().find()
            if (foodToUpdate != null) {
                foodToUpdate.apply {
                    weight = newFood.weight
                    calories = newFood.calories
                    proteins = newFood.proteins
                    fat = newFood.fat
                    carbs = newFood.carbs
                    lastUsed = System.currentTimeMillis() // mark as used on update
                    price = newFood.price
                    priceGrams = newFood.priceGrams
                }
            }
        }
    }



    suspend fun saveFood(food: Food) {
        realm.write {
            val existingFood = query<Food>("name == $0", food.name).first().find()
            if (existingFood != null) {
                existingFood.apply {
                    this.weight = food.weight
                    this.calories = food.calories
                    this.proteins = food.proteins
                    this.fat = food.fat
                    this.carbs = food.carbs
                    this.lastUsed = System.currentTimeMillis() // mark as used
                    this.price = food.price
                    this.priceGrams = food.priceGrams
                }
            } else {
                copyToRealm(food)
            }

            /*// Update totals
            val totals = query<Totals>("id == $0", "totals").first().find() ?: copyToRealm(Totals())
            totals.apply {
                totalCalories += food.calories
                totalProteins += food.proteins
                totalFat += food.fat
                totalCarbs += food.carbs
            }*/
        }
    }

    fun getAllTotals(): List<Totals> {
        val (start, end) = getDayBounds()
        return realm.query<Totals>("timestamp >= $0 AND timestamp < $1", start, end).find()

        //return realm.query<Totals>().find()
    }

    // Save calculated totals to the Totals table
    suspend fun saveToTotals(name: String, calories: Float, proteins: Float, fat: Float, carbs: Float, weight: Float, dateMillis: Long, cost: Float = 0f) {
        realm.write {
            copyToRealm(Totals().apply {
                this.id = UUID.randomUUID().toString() // Generate a unique ID
                this.name = name
                this.weight = weight
                this.totalCalories = calories
                this.totalProteins = proteins
                this.totalFat = fat
                this.totalCarbs = carbs
                //this.timestamp = System.currentTimeMillis() // save timestamp
                this.timestamp = dateMillis // ← use selected date
                this.included = true
                this.cost = cost
            })
        }
    }

    fun getAggregatedTotals(): Totals {
        /*val allTotals = realm.query<Totals>().find()
        return Totals().apply {
            totalCalories = allTotals.map { it.totalCalories ?: 0f }.sum()
            totalProteins = allTotals.map { it.totalProteins ?: 0f }.sum()
            totalFat = allTotals.map { it.totalFat ?: 0f }.sum()
            totalCarbs = allTotals.map { it.totalCarbs ?: 0f }.sum()
        }*/
        val (start, end) = getDayBounds()
        val todayTotals = realm.query<Totals>("timestamp >= $0 AND timestamp < $1", start, end).find()
        return Totals().apply {
            totalCalories = todayTotals.sumOf { it.totalCalories.toDouble() }.toFloat()
            totalProteins = todayTotals.sumOf { it.totalProteins.toDouble() }.toFloat()
            totalFat = todayTotals.sumOf { it.totalFat.toDouble() }.toFloat()
            totalCarbs = todayTotals.sumOf { it.totalCarbs.toDouble() }.toFloat()
        }
    }


    // Clear the Totals table
    suspend fun clearTotals() {
        /*realm.write {
            delete(query<Totals>())
        }*/
        val (start, end) = getDayBounds()
        realm.write {
            delete(query<Totals>("timestamp >= $0 AND timestamp < $1", start, end))
        }
    }
    suspend fun deleteTotals(id: String) {
        realm.write {
            val totalToDelete = query<Totals>("id == $0", id).first().find()
            if (totalToDelete != null) {
                delete(totalToDelete)
            }
        }
    }

    suspend fun updateTotals(updatedTotal: Totals) {
        realm.write {
            val existingTotal = query<Totals>("id == $0", updatedTotal.id).first().find()
            if (existingTotal != null) {
                existingTotal.name = updatedTotal.name
                existingTotal.weight = updatedTotal.weight
                existingTotal.totalCalories = updatedTotal.totalCalories
                existingTotal.totalProteins = updatedTotal.totalProteins
                existingTotal.totalFat = updatedTotal.totalFat
                existingTotal.totalCarbs = updatedTotal.totalCarbs
                existingTotal.cost = updatedTotal.cost
            }
        }
    }

    private fun getDayBounds(): Pair<Long, Long> {
        val now = java.time.LocalDate.now()
        val startOfDay = now.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = now.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return startOfDay to endOfDay
    }

    private fun getDayBounds(date: java.time.LocalDate): Pair<Long, Long> {
        val startOfDay = date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return startOfDay to endOfDay
    }

    fun getAllTotalsForDate(date: java.time.LocalDate): List<Totals> {
        val (start, end) = getDayBounds(date)
        return realm.query<Totals>("timestamp >= $0 AND timestamp < $1", start, end).find()
    }

    fun getAggregatedTotalsForDate(date: java.time.LocalDate): Totals {
        val (start, end) = getDayBounds(date)
        val totals = realm.query<Totals>("timestamp >= $0 AND timestamp < $1 AND included == true", start, end).find()
        return Totals().apply {
            totalCalories = totals.sumOf { it.totalCalories.toDouble() }.toFloat()
            totalProteins = totals.sumOf { it.totalProteins.toDouble() }.toFloat()
            totalFat = totals.sumOf { it.totalFat.toDouble() }.toFloat()
            totalCarbs = totals.sumOf { it.totalCarbs.toDouble() }.toFloat()
            totalCost     = totals.sumOf { it.cost.toDouble() }.toFloat()
        }
    }

    // FoodRepository.kt
    suspend fun copyTotalsToToday(ids: Set<String>) {
        val now = System.currentTimeMillis()
        realm.write {
            ids.forEach { id ->
                val src = query<Totals>("id == $0", id).first().find()
                if (src != null) {
                    copyToRealm(Totals().apply {
                        this.id = java.util.UUID.randomUUID().toString()
                        this.name = src.name
                        this.weight = src.weight
                        this.totalCalories = src.totalCalories
                        this.totalProteins = src.totalProteins
                        this.totalFat = src.totalFat
                        this.totalCarbs = src.totalCarbs
                        this.timestamp = now // ← copies to "today"
                        this.included = src.included
                        this.cost = src.cost          // ✅ NEW: includes price-derived value
                    })
                }
            }
        }
    }

    // --- Weight tracking API ---
    fun getAllWeightsNewestFirst(): List<WeightEntry> =
        realm.query<WeightEntry>().sort("timestamp", Sort.DESCENDING).find()

    suspend fun addOrUpdateWeight(weightKg: Float, dateMillis: Long) {
        realm.write {
            // Check if entry already exists for that date
            val existing = query<WeightEntry>("timestamp == $0", dateMillis).first().find()
            if (existing != null) {
                existing.weightKg = weightKg
            } else {
                copyToRealm(WeightEntry().apply {
                    this.timestamp = dateMillis
                    this.weightKg = weightKg
                })
            }
        }
    }

    suspend fun deleteWeight(dateMillis: Long) {
        realm.write {
            val existing = query<WeightEntry>("timestamp == $0", dateMillis).first().find()
            if (existing != null) delete(existing)
        }
    }

    suspend fun clearAllWeights() {
        realm.write {
            delete(query<WeightEntry>())
        }
    }


    fun getAllWeightsAscending(): List<WeightEntry> =
        realm.query<WeightEntry>().sort("timestamp", Sort.ASCENDING).find()

    // Expected-plan API
    fun getExpectedPlan(): ExpectedPlan? =
        realm.query<ExpectedPlan>("id == $0", "expected_plan_singleton").first().find()

    suspend fun setExpectedPlan(startMillis: Long, baseline: Float, dailyDelta: Float) {
        realm.write {
            query<ExpectedPlan>().find().forEach { delete(it) }
            copyToRealm(ExpectedPlan().apply {
                id = "expected_plan_singleton"
                startDateMillis = startMillis   // ← no clash now
                baselineWeightKg = baseline
                dailyDeltaKg = dailyDelta
            })
        }
    }

    suspend fun clearExpectedPlan() {
        realm.write { delete(query<ExpectedPlan>()) }
    }

    suspend fun setTotalsIncluded(id: String, included: Boolean) {
        realm.write {
            query<Totals>("id == $0", id).first().find()?.let {
                it.included = included
            }
        }
    }

    // --- Date-based workout planning and history API ---
    suspend fun saveWorkoutEntry(
        name: String,
        dateMillis: Long,
        notes: String,
        completed: Boolean,
        supersetGroupId: String,
        sets: List<WorkoutSetInput>
    ) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Exercise name is required." }
        realm.write {
            val now = System.currentTimeMillis()
            val nextPosition = query<WorkoutEntry>("dateMillis == $0", dateMillis)
                .find()
                .maxOfOrNull { it.position }
                ?.plus(1)
                ?: 0
            val entry = WorkoutEntry().apply {
                this.name = normalizedName
                this.dateMillis = dateMillis
                this.notes = notes.trimEnd()
                this.position = nextPosition
                this.completed = completed
                this.supersetGroupId = supersetGroupId.trim()
                this.updatedAt = now
                sets.forEach { set -> this.sets.add(set.toRealmSet()) }
            }
            copyToRealm(entry)
            val knownName = query<WorkoutName>("name == $0", normalizedName).first().find()
            if (knownName != null) {
                knownName.lastUsed = now
            } else {
                copyToRealm(WorkoutName().apply {
                    this.name = normalizedName
                    lastUsed = now
                })
            }
        }
    }

    suspend fun updateWorkoutEntry(
        id: String,
        name: String,
        dateMillis: Long,
        notes: String,
        completed: Boolean,
        supersetGroupId: String,
        sets: List<WorkoutSetInput>
    ) {
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Exercise name is required." }
        realm.write {
            val now = System.currentTimeMillis()
            val existing = query<WorkoutEntry>("id == $0", id).first().find()
                ?: throw IllegalArgumentException("Exercise no longer exists.")
            val previousDateMillis = existing.dateMillis
            if (previousDateMillis != dateMillis) {
                existing.position = query<WorkoutEntry>("dateMillis == $0", dateMillis)
                    .find()
                    .maxOfOrNull { it.position }
                    ?.plus(1)
                    ?: 0
            }
            existing.name = normalizedName
            existing.dateMillis = dateMillis
            existing.notes = notes.trimEnd()
            existing.completed = completed
            existing.supersetGroupId = supersetGroupId.trim()
            existing.updatedAt = now
            existing.sets.clear()
            sets.forEach { set -> existing.sets.add(set.toRealmSet()) }

            if (previousDateMillis != dateMillis) {
                query<WorkoutEntry>("dateMillis == $0", previousDateMillis)
                    .find()
                    .sortedWith(workoutDayComparator)
                    .forEachIndexed { index, workout -> workout.position = index }
            }

            val knownName = query<WorkoutName>("name == $0", normalizedName).first().find()
            if (knownName != null) {
                knownName.lastUsed = now
            } else {
                copyToRealm(WorkoutName().apply {
                    this.name = normalizedName
                    lastUsed = now
                })
            }
        }
    }

    suspend fun deleteWorkoutEntry(id: String) {
        realm.write {
            val entry = query<WorkoutEntry>("id == $0", id).first().find() ?: return@write
            val dateMillis = entry.dateMillis
            delete(entry)
            query<WorkoutEntry>("dateMillis == $0", dateMillis)
                .find()
                .sortedWith(workoutDayComparator)
                .forEachIndexed { index, remaining -> remaining.position = index }
        }
    }

    suspend fun setWorkoutEntryCompleted(id: String, completed: Boolean) {
        realm.write {
            query<WorkoutEntry>("id == $0", id).first().find()?.let { entry ->
                entry.completed = completed
                entry.updatedAt = System.currentTimeMillis()
            }
        }
    }

    suspend fun reorderWorkoutEntries(dateMillis: Long, orderedIds: List<String>) {
        realm.write {
            val existing = query<WorkoutEntry>("dateMillis == $0", dateMillis)
                .find()
                .sortedWith(workoutDayComparator)
            val byId = existing.associateBy { it.id }
            val ordered = buildList<WorkoutEntry> {
                orderedIds.distinct().forEach { id -> byId[id]?.let { add(it) } }
                existing.forEach { entry -> if (none { it.id == entry.id }) add(entry) }
            }
            ordered.forEachIndexed { index, workout -> workout.position = index }
        }
    }

    fun getWorkoutEntriesForDate(dateMillis: Long): List<WorkoutEntrySnapshot> =
        realm.query<WorkoutEntry>("dateMillis == $0", dateMillis)
            .find()
            .sortedWith(workoutDayComparator)
            .map { it.toSnapshot() }

    fun getRecentWorkoutDays(excludingDateMillis: Long, limit: Int = 10): List<WorkoutDaySummary> {
        return realm.query<WorkoutEntry>()
            .find()
            .asSequence()
            .filter { it.dateMillis != excludingDateMillis }
            .groupBy { it.dateMillis }
            .entries
            .sortedByDescending { it.key }
            .take(limit.coerceAtLeast(0))
            .map { (dateMillis, entries) ->
                WorkoutDaySummary(
                    dateMillis = dateMillis,
                    exerciseNames = entries.sortedWith(workoutDayComparator).map { it.name }
                )
            }
    }

    suspend fun copyWorkoutDay(
        sourceDateMillis: Long,
        targetDateMillis: Long,
        includeDetails: Boolean,
        replaceExisting: Boolean
    ): Int {
        require(sourceDateMillis != targetDateMillis) { "Source and target workout days must differ." }
        return realm.write {
            val source = query<WorkoutEntry>("dateMillis == $0", sourceDateMillis)
                .find()
                .sortedWith(workoutDayComparator)
            if (source.isEmpty()) return@write 0

            if (replaceExisting) {
                delete(query<WorkoutEntry>("dateMillis == $0", targetDateMillis))
            }
            val firstPosition = query<WorkoutEntry>("dateMillis == $0", targetDateMillis)
                .find()
                .maxOfOrNull { it.position }
                ?.plus(1)
                ?: 0
            val now = System.currentTimeMillis()

            source.forEachIndexed { index, original ->
                copyToRealm(WorkoutEntry().apply {
                    name = original.name
                    dateMillis = targetDateMillis
                    notes = if (includeDetails) original.notes else ""
                    position = firstPosition + index
                    completed = false
                    supersetGroupId = original.supersetGroupId
                    updatedAt = now
                    original.sets.forEach { originalSet ->
                        sets.add(WorkoutSet().apply {
                            weightKg = if (includeDetails) originalSet.weightKg else 0f
                            reps = if (includeDetails) originalSet.reps else 0
                            rest = if (includeDetails) originalSet.rest else ""
                            restSeconds = if (includeDetails) originalSet.restSeconds else 0
                            notes = if (includeDetails) originalSet.notes else ""
                            completed = false
                        })
                    }
                })

                val knownName = query<WorkoutName>("name == $0", original.name).first().find()
                if (knownName != null) {
                    knownName.lastUsed = now
                } else {
                    copyToRealm(WorkoutName().apply {
                        name = original.name
                        lastUsed = now
                    })
                }
            }
            source.size
        }
    }

    fun getWorkoutEntriesNewestFirst(
        searchQuery: String,
        completedOnly: Boolean = true
    ): List<WorkoutEntrySnapshot> {
        val trimmed = searchQuery.trim()
        var results = if (completedOnly) {
            realm.query<WorkoutEntry>("completed == $0", true)
        } else {
            realm.query<WorkoutEntry>()
        }
        if (trimmed.isNotEmpty()) {
            val words = trimmed.split(Regex("\\s+")).filter { it.isNotEmpty() }
            results = results.query("name CONTAINS[c] $0", words.first())
            words.drop(1).forEach { w ->
                results = results.query("name CONTAINS[c] $0", w)
            }
        }
        return results.find()
            .sortedWith(workoutHistoryComparator)
            .map { it.toSnapshot() }
    }

    fun getCompletedWorkoutEntriesForExercise(name: String): List<WorkoutEntrySnapshot> {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return emptyList()

        return realm.query<WorkoutEntry>(
            "completed == $0 AND name == $1",
            true,
            normalizedName
        )
            .find()
            .sortedWith(workoutHistoryComparator)
            .map { it.toSnapshot() }
    }

    fun hasCompletedWorkoutHistory(name: String): Boolean {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return false

        return realm.query<WorkoutEntry>(
            "completed == $0 AND name == $1",
            true,
            normalizedName
        ).first().find() != null
    }

    fun getLatestWorkoutEntryByName(name: String): WorkoutEntrySnapshot? {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return null

        return realm.query<WorkoutEntry>("name == $0", normalizedName)
            .find()
            .maxWithOrNull(
                compareBy<WorkoutEntry> { it.dateMillis }
                    .thenBy { it.updatedAt }
            )
            ?.toSnapshot()
    }

    fun getWorkoutNameSuggestions(query: String): List<String> {
        val words = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()

        var q = realm.query<WorkoutName>("name CONTAINS[c] $0", words.first())
        words.drop(1).forEach { w ->
            q = q.query("name CONTAINS[c] $0", w)
        }

        return q.sort("lastUsed", Sort.DESCENDING)
            .find()
            .map { it.name }
            .distinct()
            .take(20)
    }

    suspend fun touchWorkoutName(name: String) {
        val trimmed = name.trimEnd()
        if (trimmed.isEmpty()) return
        realm.write {
            val existing = query<WorkoutName>("name == $0", trimmed).first().find()
            if (existing != null) {
                existing.lastUsed = System.currentTimeMillis()
            } else {
                copyToRealm(WorkoutName().apply {
                    this.name = trimmed
                    this.lastUsed = System.currentTimeMillis()
                })
            }
        }
    }

    suspend fun deleteWorkoutName(name: String) {
        val trimmed = name.trimEnd()
        if (trimmed.isEmpty()) return
        realm.write {
            query<WorkoutName>("name == $0", trimmed).first().find()?.let { delete(it) }
        }
    }

    private fun WorkoutSetInput.toRealmSet() = WorkoutSet().apply {
        weightKg = this@toRealmSet.weightKg
        reps = this@toRealmSet.reps
        rest = this@toRealmSet.rest.trimEnd()
        restSeconds = if (this@toRealmSet.restSeconds > 0) {
            this@toRealmSet.restSeconds
        } else {
            parseRestSeconds(this@toRealmSet.rest)
        }
        notes = this@toRealmSet.notes.trimEnd()
        completed = this@toRealmSet.completed
    }

    private fun WorkoutEntry.toSnapshot() = WorkoutEntrySnapshot(
        id = id,
        name = name,
        dateMillis = dateMillis,
        notes = notes,
        sets = sets.map { it.toSnapshot() },
        position = position,
        completed = completed,
        supersetGroupId = supersetGroupId,
        updatedAt = updatedAt
    )

    private fun WorkoutSet.toSnapshot() = WorkoutSetSnapshot(
        weightKg = weightKg,
        reps = reps,
        rest = rest,
        restSeconds = restSeconds,
        notes = notes,
        completed = completed
    )

    private companion object {
        val workoutDayComparator = compareBy<WorkoutEntry> { it.position }
            .thenBy { it.updatedAt }
            .thenBy { it.id }
        val workoutHistoryComparator = compareByDescending<WorkoutEntry> { it.dateMillis }
            .thenBy { it.position }
            .thenByDescending { it.updatedAt }
    }

}

