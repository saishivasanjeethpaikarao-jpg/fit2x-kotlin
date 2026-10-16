package com.fit2x.app.diet

import android.content.Context
import com.fit2x.app.data.local.Fit2xDatabase
import com.fit2x.app.data.local.FoodEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * Diet data layer.
 *
 * - Food search runs against a bundled Indian food database ([assets/foods.json]),
 *   so it works fully offline.
 * - Logging goes to Room via [FoodEntry].
 * - Barcode lookup tries Open Food Facts when online, with an honest
 *   [BarcodeLookupResult.Offline] signal when there is no connectivity.
 */
class FoodRepository(
    private val context: Context,
    db: Fit2xDatabase
) {
    private val foodEntryDao = db.foodEntryDao()
    private val userProfileDao = db.userProfileDao()

    // ------------------------------------------------------------------
    // Bundled food database
    // ------------------------------------------------------------------

    private val foods: List<Food> by lazy { loadFoods() }

    private fun loadFoods(): List<Food> {
        return try {
            val json = context.assets.open("foods.json").bufferedReader().use { it.readText() }
            val array = JSONObject(json).getJSONArray("foods")
            return buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        Food(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            serving = o.optString("serving", ""),
                            proteinGrams = o.optDouble("protein", 0.0),
                            calories = o.optInt("calories", 0),
                            category = FoodCategory.fromRaw(o.optString("category", "OTHER")),
                            tags = buildList {
                                val t = o.optJSONArray("tags")
                                if (t != null) for (j in 0 until t.length()) add(t.getString(j))
                            }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Database is a nice-to-have; a corrupt/missing file must never crash the app.
            android.util.Log.w("FoodRepository", "Failed to load foods.json", e)
            emptyList()
        }
    }

    /** All bundled foods, e.g. for category browsing. */
    fun allFoods(): List<Food> = foods

    /**
     * Case-insensitive search over name + tags.
     * Ranked: name prefix > name contains > tag contains.
     */
    fun searchFoods(query: String, limit: Int = 30): List<Food> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return foods.take(limit)
        return foods
            .mapNotNull { food ->
                val name = food.name.lowercase()
                val score = when {
                    name.startsWith(q) -> 3
                    name.contains(q) -> 2
                    food.tags.any { it.lowercase().contains(q) } -> 1
                    else -> return@mapNotNull null
                }
                score to food
            }
            .sortedWith(compareByDescending<Pair<Int, Food>> { it.first }.thenBy { it.second.name })
            .take(limit)
            .map { it.second }
    }

    // ------------------------------------------------------------------
    // Logging (Room)
    // ------------------------------------------------------------------

    fun today(): String = LocalDate.now().toString() // yyyy-MM-dd

    fun getEntries(date: String = today()): Flow<List<FoodEntry>> =
        foodEntryDao.observeDay(date)

    fun getTodayProtein(date: String = today()): Flow<Double> =
        foodEntryDao.observeDay(date).map { entries -> entries.sumOf { it.proteinGrams } }

    fun getTodayCalories(date: String = today()): Flow<Int> =
        foodEntryDao.observeDay(date).map { entries -> entries.sumOf { it.calories } }

    /** Daily protein target, from profile or the 70g default. */
    fun observeProteinGoal(): Flow<Int> =
        userProfileDao.observeProfile().map { it?.proteinGoalGrams ?: 70 }

    /** Daily calorie target, from profile or the 2200 default. */
    fun observeCalorieGoal(): Flow<Int> =
        userProfileDao.observeProfile().map { it?.calorieGoal ?: 2_200 }

    /**
     * Log [food] for [date]. [quantity] scales the serving, e.g. 2.0 = double portion.
     */
    suspend fun logFood(
        food: Food,
        mealType: MealType = MealType.defaultForNow(),
        quantity: Double = 1.0,
        date: String = today()
    ): Long {
        val qty = quantity.coerceAtLeast(0.25)
        val label = if (qty == 1.0) {
            food.serving.ifBlank { "1 serving" }
        } else {
            "${formatQty(qty)} x ${food.serving.ifBlank { "serving" }}"
        }
        return foodEntryDao.insert(
            FoodEntry(
                date = date,
                name = food.name,
                proteinGrams = (food.proteinGrams * qty * 10).roundToInt() / 10.0,
                calories = (food.calories * qty).toInt(),
                quantityLabel = label,
                mealType = mealType.name
            )
        )
    }

    suspend fun deleteEntry(entry: FoodEntry) = foodEntryDao.delete(entry)

    /** Manual one-off entry (food not in the bundled database). */
    suspend fun logManualFood(
        name: String,
        proteinGrams: Double,
        calories: Int,
        mealType: com.fit2x.app.diet.MealType,
        date: String = today()
    ): Long {
        return foodEntryDao.insert(
            FoodEntry(
                date = date,
                name = name.trim(),
                proteinGrams = proteinGrams.coerceAtLeast(0.0),
                calories = calories.coerceAtLeast(0),
                quantityLabel = "1 serving",
                mealType = mealType.name
            )
        )
    }

    private fun formatQty(qty: Double): String =
        if (qty == qty.toInt().toDouble()) qty.toInt().toString() else "%.1f".format(qty)

    // ------------------------------------------------------------------
    // Barcode nutrition lookup
    // ------------------------------------------------------------------

    sealed interface BarcodeLookupResult {
        data class Found(val food: Food) : BarcodeLookupResult
        /** Barcode scanned fine, but no nutrition data for it. */
        data object NotFound : BarcodeLookupResult
        /** No network — bundled database search still works offline. */
        data object Offline : BarcodeLookupResult
    }

    /**
     * Look up nutrition for a barcode. Uses Open Food Facts (free, no key)
     * when online. Never throws — returns [BarcodeLookupResult.Offline]
     * when the network is unavailable.
     */
    suspend fun lookupBarcode(barcode: String): BarcodeLookupResult = withContext(Dispatchers.IO) {
        val code = barcode.trim()
        if (code.isEmpty()) return@withContext BarcodeLookupResult.NotFound
        try {
            val url = URL(
                "https://world.openfoodfacts.org/api/v2/product/" +
                    "$code.json?fields=product_name,nutriments,serving_size,quantity"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", "FIT2X/1.0 (Android)")
            }
            if (conn.responseCode != 200) return@withContext BarcodeLookupResult.NotFound
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            parseOpenFoodFacts(code, body) ?: BarcodeLookupResult.NotFound
        } catch (e: Exception) {
            android.util.Log.w("FoodRepository", "Barcode lookup failed for $code", e)
            BarcodeLookupResult.Offline
        }
    }

    private fun parseOpenFoodFacts(code: String, body: String): BarcodeLookupResult.Found? {
        return try {
            val root = JSONObject(body)
            if (root.optInt("status", 0) != 1) return null
            val product = root.getJSONObject("product")
            val name = product.optString("product_name", "").ifBlank { "Scanned product" }
            val nutriments = product.optJSONObject("nutriments") ?: return null

            val protein100 = nutriments.optDouble("proteins_100g", Double.NaN)
            val kcal100 = nutriments.optDouble("energy-kcal_100g", Double.NaN)
            if (protein100.isNaN() || kcal100.isNaN()) return null

            // Prefer an explicit serving size; fall back to 100g.
            val servingText = product.optString("serving_size", "").ifBlank {
                product.optString("quantity", "100g")
            }
            val servingGrams = parseGrams(servingText) ?: 100.0
            val factor = servingGrams / 100.0

            BarcodeLookupResult.Found(
                Food(
                    id = "barcode-$code",
                    name = name,
                    serving = servingText.ifBlank { "100g" },
                    proteinGrams = (protein100 * factor * 10).roundToInt() / 10.0,
                    calories = (kcal100 * factor).toInt(),
                    category = FoodCategory.OTHER,
                    tags = listOf("barcode", "packaged")
                )
            )
        } catch (e: Exception) {
            android.util.Log.w("FoodRepository", "Failed to parse OFF response", e)
            null
        }
    }

    /** Extracts grams from strings like "30g", "250 ml", "1 L". Null when unparseable. */
    private fun parseGrams(text: String): Double? {
        val m = Regex("""([\d.]+)\s*(kg|g|ml|l)\b""", RegexOption.IGNORE_CASE).find(text) ?: return null
        val value = m.groupValues[1].toDoubleOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "kg" -> value * 1000
            "l" -> value * 1000 // assume density ~ water for drinks
            "ml" -> value
            else -> value // g
        }
    }
}
