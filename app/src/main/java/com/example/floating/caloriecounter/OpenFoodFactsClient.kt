package com.example.floating.caloriecounter

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

data class OffNutrition(
    val name: String = "",
    val kcal100: Float = 0f,
    val protein100: Float = 0f,
    val fat100: Float = 0f,
    val carbs100: Float = 0f
)

private val openFoodFactsHttp by lazy { OkHttpClient() }

suspend fun fetchOpenFoodFacts(barcode: String): OffNutrition? = withContext(Dispatchers.IO) {
    val normalizedBarcode = barcode.trim()
    if (normalizedBarcode.isEmpty() || normalizedBarcode.any { !it.isDigit() }) {
        return@withContext null
    }

    try {
        val url = "https://world.openfoodfacts.org/api/v2/product/$normalizedBarcode.json"
        val request = Request.Builder().url(url).get().build()
        openFoodFactsHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null

            val root = JSONObject(body)
            if (root.optString("status_verbose") != "product found") return@withContext null
            val product = root.optJSONObject("product") ?: return@withContext null
            val nutrients = product.optJSONObject("nutriments") ?: JSONObject()

            fun nutrient(key: String): Float = nutrients.optString(key, "")
                .replace(',', '.')
                .toFloatOrNull()
                ?: 0f

            val calories = nutrient("energy-kcal_100g").takeIf { it > 0f }
                ?: (nutrient("energy_100g") / 4.184f)

            OffNutrition(
                name = product.optString("product_name", product.optString("generic_name", "")),
                kcal100 = calories,
                protein100 = nutrient("proteins_100g"),
                fat100 = nutrient("fat_100g"),
                carbs100 = nutrient("carbohydrates_100g")
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

