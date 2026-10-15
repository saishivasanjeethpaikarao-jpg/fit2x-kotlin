package com.fit2x.app.notifications

/**
 * The four daily reminders, with Shiv's default times from his timetable.
 */
enum class ReminderType(
    val key: String,
    val title: String,
    val body: String,
    val defaultHour: Int,
    val defaultMinute: Int
) {
    GYM(
        key = "gym",
        title = "Time for gym! \uD83D\uDCAA",
        body = "Your workout is waiting. Let's get after it.",
        defaultHour = 7,
        defaultMinute = 30
    ),
    LUNCH(
        key = "lunch",
        title = "Log your lunch \uD83C\uDF71",
        body = "Quick one — log what you ate to stay on track.",
        defaultHour = 12,
        defaultMinute = 15
    ),
    SNACK(
        key = "snack",
        title = "Evening snack time \uD83E\uDD5C",
        body = "Fuel up smart: peanuts, soya chunks, or fruit.",
        defaultHour = 17,
        defaultMinute = 0
    ),
    DINNER(
        key = "dinner",
        title = "Log your dinner \uD83C\uDF7D\uFE0F",
        body = "Don't forget to log dinner before bed.",
        defaultHour = 20,
        defaultMinute = 30
    );

    companion object {
        fun fromKey(key: String): ReminderType? = entries.firstOrNull { it.key == key }
    }
}
