package com.example.floating.caloriecounter.Model

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.ext.query
import io.realm.kotlin.migration.AutomaticSchemaMigration
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.Sort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class FoodRepository {


    private val config = RealmConfiguration.Builder(
        schema = setOf(Food::class, Totals::class, WeightEntry::class, ExpectedPlan::class)
    )
        .schemaVersion(4) // increment this (start at 1 if you never had one)
        .migration(
            AutomaticSchemaMigration { ctx ->
                ctx.enumerate(className = "Totals") { _: DynamicRealmObject, newObj: DynamicMutableRealmObject? ->
                    // Existing rows get default "true"
                    newObj?.set("totalCost", 0f) // ✅ NEW
                }

                /*ctx.enumerate(className = "Food") { _: DynamicRealmObject, newObj: DynamicMutableRealmObject? ->
                    newObj?.set("price", 0f)
                    newObj?.set("priceGrams", 0f)
                }*/
            }
        )
        .build()

    private val realm: Realm = Realm.open(config)

    // Provide realm instance for later use
    fun getRealm(): Realm = realm

    fun getAllFoods(): RealmResults<Food> = realm.query<Food>().find()


    suspend fun getAllFoodsForBackup(): List<Food> = withContext(Dispatchers.IO) {
        realm.query<Food>().find()
    }

    suspend fun getAllTotalsAllDates(): List<Totals> = withContext(Dispatchers.IO) {
        // If you want stable ordering in the export:
        realm.query<Totals>().sort("timestamp", Sort.ASCENDING).find()
    }

    suspend fun getExpectedPlanSingleton(): ExpectedPlan? = withContext(Dispatchers.IO) {
        realm.query<ExpectedPlan>("id == $0", "expected_plan_singleton").first().find()
    }

    suspend fun getAllWeightEntries(): List<WeightEntry> = withContext(Dispatchers.IO) {
        realm.query<WeightEntry>().sort("timestamp", Sort.ASCENDING).find()
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

}

