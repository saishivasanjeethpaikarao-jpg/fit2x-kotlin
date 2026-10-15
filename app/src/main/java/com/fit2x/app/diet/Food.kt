package com.fit2x.app.diet

/** A food from the bundled database (or a barcode lookup result). */
data class Food(
    val id: String,
    val name: String,
    val serving: String,
    val proteinGrams: Double,
    val calories: Int,
    val category: FoodCategory,
    val tags: List<String> = emptyList()
)

enum class FoodCategory {
    BREAKFAST,
    STAPLE,
    DAL,
    PROTEIN,
    DAIRY,
    EGG,
    FRUIT,
    SNACK,
    SABZI,
    SUPPLEMENT,
    OTHER;

    companion object {
        fun fromRaw(raw: String): FoodCategory =
            runCatching { valueOf(raw.uppercase()) }.getOrDefault(OTHER)
    }
}

/** Meal slots shown in the Diet screen. Stored as [name] in [com.fit2x.app.data.local.FoodEntry.mealType]. */
enum class MealType(val label: String) {
    BREAKFAST("Breakfast"),
    LUNCH("Lunch"),
    SNACK("Snack"),
    DINNER("Dinner");

    companion object {
        fun fromRaw(raw: String): MealType =
            runCatching { valueOf(raw.uppercase()) }.getOrDefault(SNACK)

        /** Picks a sensible default based on the current hour of day. */
        fun defaultForNow(): MealType {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            return when (hour) {
                in 5..10 -> BREAKFAST
                in 11..15 -> LUNCH
                in 16..18 -> SNACK
                else -> DINNER
            }
        }
    }
}
