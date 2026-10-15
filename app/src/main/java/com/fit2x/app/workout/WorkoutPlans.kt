package com.fit2x.app.workout

/**
 * A single movement inside a [WorkoutDay].
 *
 * @param reps free-form so plans can express "8-12", "AMRAP", "30s", "20 min",
 *             "10/leg" etc. without a second numeric field.
 */
data class Exercise(
    val name: String,
    val sets: Int,
    val reps: String,
    val muscleGroup: String = "",
    val instructions: String = "",
    val videoUrl: String? = null
) {
    /** "4 × 8-12" style label for the UI. */
    fun setsRepsLabel(): String = "$sets × $reps"
}

data class WorkoutDay(
    /** "MONDAY".."SUNDAY" (java.time.DayOfWeek names). */
    val dayOfWeek: String,
    val title: String,
    val subtitle: String = "",
    val exercises: List<Exercise> = emptyList(),
    val isRest: Boolean = false,
    val estimatedMinutes: Int = 0
)

data class WorkoutTemplate(
    val id: String, // STRENGTH, HYPERTROPHY, HOME, RECOVERY
    val name: String,
    val description: String,
    /** Monday-first, exactly 7 entries. */
    val days: List<WorkoutDay>
) {
    init {
        require(days.size == 7) { "Template $id must define 7 days, got ${days.size}" }
    }
}

// ---------------------------------------------------------------------------
// Shared days
// ---------------------------------------------------------------------------

private fun activeRecoveryDay() = WorkoutDay(
    dayOfWeek = "THURSDAY",
    title = "Active Recovery",
    subtitle = "Easy walk and mobility. Keep it light.",
    exercises = listOf(
        Exercise(
            name = "Easy Walk",
            sets = 1,
            reps = "20 min",
            muscleGroup = "Cardio",
            instructions = "Conversational pace. No phone scrolling — just walk."
        ),
        Exercise(
            name = "Full-Body Mobility Flow",
            sets = 1,
            reps = "10 min",
            muscleGroup = "Mobility",
            instructions = "Cat-cow, world's greatest stretch, deep squat hold, arm circles. 60s each."
        )
    ),
    estimatedMinutes = 30
)

// ---------------------------------------------------------------------------
// Templates
// ---------------------------------------------------------------------------

private val strengthTemplate = WorkoutTemplate(
    id = "STRENGTH",
    name = "Strength",
    description = "Barbell-first 5×5 program. Built for the big lifts.",
    days = listOf(
        WorkoutDay(
            dayOfWeek = "MONDAY",
            title = "Push Strength",
            subtitle = "Chest, shoulders, triceps. Heavy compounds.",
            exercises = listOf(
                Exercise("Barbell Bench Press", 5, "5", "Chest", "Touch chest, drive up. 2-3 min rest between sets."),
                Exercise("Overhead Press", 5, "5", "Shoulders", "Standing. Squeeze glutes, press in a straight line."),
                Exercise("Dips", 3, "8", "Triceps", "Lean slightly forward. Add weight when 3×8 is easy.")
            ),
            estimatedMinutes = 60
        ),
        WorkoutDay(
            dayOfWeek = "TUESDAY",
            title = "Pull Strength",
            subtitle = "Back and biceps. Pull heavy.",
            exercises = listOf(
                Exercise("Deadlift", 5, "5", "Back", "Flat back, push the floor away. Warm up in small jumps."),
                Exercise("Barbell Row", 5, "5", "Back", "Torso near-parallel to floor. Pull to lower ribs."),
                Exercise("Pull-ups", 3, "AMRAP", "Back", "Full hang to chin over bar. Use assistance if needed.")
            ),
            estimatedMinutes = 60
        ),
        WorkoutDay(
            dayOfWeek = "WEDNESDAY",
            title = "Legs Strength",
            subtitle = "Squat day. The king of lifts.",
            exercises = listOf(
                Exercise("Back Squat", 5, "5", "Quads", "Hip crease below knee. Brace hard before each rep."),
                Exercise("Romanian Deadlift", 3, "8", "Hamstrings", "Hinge at hips, slight knee bend. Feel the stretch."),
                Exercise("Leg Press", 3, "10", "Quads", "Full range without lower back rounding."),
                Exercise("Standing Calf Raise", 4, "15", "Calves", "Pause 1s at the top of every rep.")
            ),
            estimatedMinutes = 70
        ),
        activeRecoveryDay(),
        WorkoutDay(
            dayOfWeek = "FRIDAY",
            title = "Push Strength",
            subtitle = "Chest, shoulders, triceps. Second wave.",
            exercises = listOf(
                Exercise("Incline Barbell Press", 4, "6", "Chest", "30° incline. Control the negative."),
                Exercise("Overhead Press", 4, "6", "Shoulders", "Seated or standing. No leg drive."),
                Exercise("Close-Grip Bench Press", 3, "8", "Triceps", "Hands shoulder-width. Elbows tucked.")
            ),
            estimatedMinutes = 60
        ),
        WorkoutDay(
            dayOfWeek = "SATURDAY",
            title = "Pull Strength",
            subtitle = "Back thickness and arms.",
            exercises = listOf(
                Exercise("Weighted Pull-ups", 4, "6", "Back", "Add weight via belt or dumbbell between feet."),
                Exercise("Barbell Row", 4, "6", "Back", "Heavier than Tuesday. Keep form strict."),
                Exercise("Barbell Curl", 3, "10", "Biceps", "No swinging. Squeeze at the top.")
            ),
            estimatedMinutes = 60
        ),
        WorkoutDay(
            dayOfWeek = "SUNDAY",
            title = "Legs Strength",
            subtitle = "Unilateral focus + calves.",
            exercises = listOf(
                Exercise("Back Squat", 4, "6", "Quads", "Slightly lighter than Wednesday. Move fast."),
                Exercise("Bulgarian Split Squat", 3, "8/leg", "Quads", "Rear foot on bench. Torso tall."),
                Exercise("Seated Calf Raise", 4, "15", "Calves", "Deep stretch at the bottom.")
            ),
            estimatedMinutes = 60
        )
    )
)

private val hypertrophyTemplate = WorkoutTemplate(
    id = "HYPERTROPHY",
    name = "Hypertrophy",
    description = "8–15 rep pump work. Machines, cables and dumbbells.",
    days = listOf(
        WorkoutDay(
            dayOfWeek = "MONDAY",
            title = "Chest + Triceps",
            subtitle = "Push volume day.",
            exercises = listOf(
                Exercise("Incline Dumbbell Press", 4, "8-12", "Chest", "30° incline. Stretch at the bottom."),
                Exercise("Cable Fly", 3, "12-15", "Chest", "Slight bend in elbows. Squeeze 1s."),
                Exercise("Rope Pushdown", 3, "10-12", "Triceps", "Elbows pinned. Full lockout."),
                Exercise("Overhead DB Extension", 3, "10-12", "Triceps", "Deep stretch behind head.")
            ),
            estimatedMinutes = 60
        ),
        WorkoutDay(
            dayOfWeek = "TUESDAY",
            title = "Back + Biceps",
            subtitle = "Pull volume day.",
            exercises = listOf(
                Exercise("Lat Pulldown", 4, "8-12", "Back", "Chest up. Pull to upper chest."),
                Exercise("Seated Cable Row", 4, "10-12", "Back", "Squeeze shoulder blades together."),
                Exercise("Face Pull", 3, "12-15", "Shoulders", "Pull to forehead. External rotation at end."),
                Exercise("Dumbbell Curl", 3, "10-12", "Biceps", "Alternate arms. No momentum.")
            ),
            estimatedMinutes = 60
        ),
        WorkoutDay(
            dayOfWeek = "WEDNESDAY",
            title = "Legs",
            subtitle = "Quad and hamstring volume.",
            exercises = listOf(
                Exercise("Leg Press", 4, "10-12", "Quads", "Feet mid-platform. Deep reps."),
                Exercise("Walking Lunge", 3, "12/leg", "Quads", "Long strides. Torso tall."),
                Exercise("Lying Leg Curl", 3, "12-15", "Hamstrings", "Control the negative."),
                Exercise("Standing Calf Raise", 4, "12-15", "Calves", "Full range every rep.")
            ),
            estimatedMinutes = 65
        ),
        activeRecoveryDay(),
        WorkoutDay(
            dayOfWeek = "FRIDAY",
            title = "Shoulders + Arms",
            subtitle = "Delts and arm pump.",
            exercises = listOf(
                Exercise("Seated DB Press", 4, "8-10", "Shoulders", "No arching. Full lockout."),
                Exercise("Lateral Raise", 4, "12-15", "Shoulders", "Lead with elbows. Stop at shoulder height."),
                Exercise("Hammer Curl", 3, "10-12", "Biceps", "Neutral grip. Slow negatives."),
                Exercise("Dips", 3, "8-12", "Triceps", "Upright torso to bias triceps.")
            ),
            estimatedMinutes = 55
        ),
        WorkoutDay(
            dayOfWeek = "SATURDAY",
            title = "Back + Chest",
            subtitle = "Upper body mix.",
            exercises = listOf(
                Exercise("One-Arm DB Row", 4, "10-12", "Back", "Knee on bench. Pull to hip."),
                Exercise("Incline DB Fly", 3, "12-15", "Chest", "Deep stretch, soft elbows."),
                Exercise("Straight-Arm Pulldown", 3, "12-15", "Back", "Keep arms straight. Feel the lats.")
            ),
            estimatedMinutes = 50
        ),
        WorkoutDay(
            dayOfWeek = "SUNDAY",
            title = "Legs + Core",
            subtitle = "Lower body + abs finisher.",
            exercises = listOf(
                Exercise("Goblet Squat", 4, "12-15", "Quads", "Dumbbell at chest. Sit between heels."),
                Exercise("Romanian Deadlift", 3, "10-12", "Hamstrings", "Dumbbells. Hinge, don't squat."),
                Exercise("Hanging Knee Raise", 3, "12-15", "Core", "No swinging. Curl pelvis up.")
            ),
            estimatedMinutes = 55
        )
    )
)

private val homeTemplate = WorkoutTemplate(
    id = "HOME",
    name = "Home",
    description = "No equipment. Bodyweight only — perfect for missed-gym days.",
    days = listOf(
        WorkoutDay(
            dayOfWeek = "MONDAY",
            title = "Push (Home)",
            subtitle = "Chest, shoulders, triceps. No equipment.",
            exercises = listOf(
                Exercise("Push-ups", 4, "AMRAP", "Chest", "Chest to floor. Scale to knees if needed."),
                Exercise("Pike Push-ups", 3, "8-10", "Shoulders", "Hips high. Head toward floor."),
                Exercise("Chair Dips", 3, "10-12", "Triceps", "Hands on sturdy chair edge.")
            ),
            estimatedMinutes = 35
        ),
        WorkoutDay(
            dayOfWeek = "TUESDAY",
            title = "Pull + Core (Home)",
            subtitle = "Back and abs with zero gear.",
            exercises = listOf(
                Exercise("Doorway Rows", 4, "10-12", "Back", "Feet under door frame, pull chest to hands. Use a sturdy table edge as alternative."),
                Exercise("Superman Pulls", 3, "12-15", "Back", "Lying prone. Lift chest, pull elbows back."),
                Exercise("Dead Bug", 3, "8/side", "Core", "Lower back pressed into floor throughout.")
            ),
            estimatedMinutes = 30
        ),
        WorkoutDay(
            dayOfWeek = "WEDNESDAY",
            title = "Legs (Home)",
            subtitle = "Bodyweight leg burner.",
            exercises = listOf(
                Exercise("Bodyweight Squat", 4, "15-20", "Quads", "Full depth. Knees track over toes."),
                Exercise("Walking Lunge", 3, "10/leg", "Quads", "Across the room and back."),
                Exercise("Glute Bridge", 3, "12-15", "Glutes", "Squeeze 2s at top."),
                Exercise("Calf Raise", 4, "15-20", "Calves", "On a step for extra range.")
            ),
            estimatedMinutes = 35
        ),
        activeRecoveryDay(),
        WorkoutDay(
            dayOfWeek = "FRIDAY",
            title = "Push + Core (Home)",
            subtitle = "Upper push and abs.",
            exercises = listOf(
                Exercise("Diamond Push-ups", 3, "8-10", "Triceps", "Hands in diamond under chest."),
                Exercise("Plank", 3, "45s", "Core", "Straight line head to heels."),
                Exercise("Mountain Climbers", 3, "30s", "Core", "Fast knees. Hips low.")
            ),
            estimatedMinutes = 30
        ),
        WorkoutDay(
            dayOfWeek = "SATURDAY",
            title = "Full Body (Home)",
            subtitle = "Conditioning circuit.",
            exercises = listOf(
                Exercise("Burpees", 3, "8-10", "Full Body", "Chest to floor each rep."),
                Exercise("Jump Squat", 3, "10-12", "Quads", "Soft landing. Explode up."),
                Exercise("Push-ups", 3, "AMRAP", "Chest", "Whatever is left in the tank.")
            ),
            estimatedMinutes = 30
        ),
        WorkoutDay(
            dayOfWeek = "SUNDAY",
            title = "Mobility (Home)",
            subtitle = "Recover and reset.",
            exercises = listOf(
                Exercise("Yoga Flow", 1, "20 min", "Mobility", "Sun salutations at your own pace."),
                Exercise("Deep Stretch", 1, "10 min", "Mobility", "Hip flexors, hamstrings, chest doorway stretch.")
            ),
            estimatedMinutes = 30
        )
    )
)

private val recoveryTemplate = WorkoutTemplate(
    id = "RECOVERY",
    name = "Recovery Week",
    description = "Deload week. Movement without fatigue.",
    days = listOf(
        WorkoutDay(
            dayOfWeek = "MONDAY",
            title = "Gentle Walk",
            subtitle = "Deload day 1.",
            exercises = listOf(
                Exercise("Easy Walk", 1, "25 min", "Cardio", "Flat route. Nasal breathing."),
                Exercise("Neck + Shoulder Mobility", 1, "10 min", "Mobility", "Slow circles, chin tucks, band pull-aparts without band.")
            ),
            estimatedMinutes = 35
        ),
        WorkoutDay(
            dayOfWeek = "TUESDAY",
            title = "Easy Movement",
            subtitle = "Deload day 2.",
            exercises = listOf(
                Exercise("Easy Walk", 1, "20 min", "Cardio", "Conversational pace."),
                Exercise("Hip Opener Flow", 1, "10 min", "Mobility", "90/90 switches, frog stretch, deep squat hold.")
            ),
            estimatedMinutes = 30
        ),
        WorkoutDay(
            dayOfWeek = "WEDNESDAY",
            title = "Light Cardio",
            subtitle = "Deload day 3.",
            exercises = listOf(
                Exercise("Easy Walk", 1, "25 min", "Cardio", "Slightly brisker than Monday."),
                Exercise("Spine Mobility", 1, "10 min", "Mobility", "Cat-cow, thread the needle, side bends.")
            ),
            estimatedMinutes = 35
        ),
        activeRecoveryDay(),
        WorkoutDay(
            dayOfWeek = "FRIDAY",
            title = "Gentle Yoga",
            subtitle = "Deload day 5.",
            exercises = listOf(
                Exercise("Gentle Yoga", 1, "20 min", "Mobility", "Follow any beginner flow."),
                Exercise("Breathing + Stretch", 1, "10 min", "Recovery", "Box breathing 4-4-4-4, then full-body stretch.")
            ),
            estimatedMinutes = 30
        ),
        WorkoutDay(
            dayOfWeek = "SATURDAY",
            title = "Nature Walk",
            subtitle = "Deload day 6.",
            exercises = listOf(
                Exercise("Nature Walk", 1, "30 min", "Cardio", "Park or quiet streets. Leave the playlist off."),
                Exercise("Ankle + Wrist Circles", 1, "5 min", "Mobility", "Both directions, slow.")
            ),
            estimatedMinutes = 35
        ),
        WorkoutDay(
            dayOfWeek = "SUNDAY",
            title = "Full Rest",
            subtitle = "Deload day 7. Earn it.",
            exercises = listOf(
                Exercise("Restorative Stretch", 1, "15 min", "Recovery", "Long holds. Breathe into the tight spots."),
                Exercise("Foam Rolling", 1, "10 min", "Recovery", "Quads, lats, upper back. Skip if sore.")
            ),
            estimatedMinutes = 25,
            isRest = true
        )
    )
)

/** All available templates, in onboarding order. */
val WORKOUT_TEMPLATES: List<WorkoutTemplate> = listOf(
    strengthTemplate,
    hypertrophyTemplate,
    homeTemplate,
    recoveryTemplate
)

/** Monday-first day names matching java.time.DayOfWeek. */
val WEEK_DAYS: List<String> = listOf(
    "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"
)

fun templateById(id: String): WorkoutTemplate =
    WORKOUT_TEMPLATES.firstOrNull { it.id == id } ?: strengthTemplate
