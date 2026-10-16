# FIT2X Native — Bug Hunt Report

**Date:** 2026-10-16
**Scope:** All 37 Kotlin files under `app/src/main/java/com/fit2x/app/`
**Build status:** Green (GitHub Actions #14), Google Sign-In verified on device
**Method:** Static code review — runtime bugs, logic errors, and issues the compiler won't catch

> Severity: **CRITICAL** = crash/data loss · **HIGH** = broken feature · **MEDIUM** = wrong behavior · **LOW** = minor

---

## CRITICAL

### C1. Notification permission is never requested — reminders silently never fire on Android 13+
**File:** `app/src/main/java/com/fit2x/app/notifications/ReminderSettingsViewModel.kt` (defines) · `NotificationHelper.kt:59`
**Lines:** `ReminderSettingsViewModel.kt:77-100`, `NotificationHelper.kt:59`

`rememberNotificationPermissionRequest()` is a fully-written Compose helper for requesting `POST_NOTIFICATIONS` on Android 13+, but **it is never called from any screen**. Meanwhile:

```kotlin
// NotificationHelper.showNotification()
if (!hasNotificationPermission(context)) return   // silent no-op on API 33+
```

On Android 13+ (the large majority of active devices), `POST_NOTIFICATIONS` defaults to denied. The WorkManager schedule runs, `ReminderWorker.doWork()` executes, `showNotification()` is called — and returns silently. The user gets **zero reminders, forever**, with no UI ever asking for the permission and no error surfaced anywhere.

**Fix:** Call `rememberNotificationPermissionRequest` in `SettingsScreen` (or `Fit2xApp` first launch) and launch it when `!granted`. Show a persistent "enable notifications" card in Settings when denied.

---

### C2. Two Room database instances on the same file — cross-feature observers go stale
**File:** `app/src/main/java/com/fit2x/app/workout/WorkoutViewModel.kt`
**Lines:** 38–62 (`WorkoutGraph.provideDatabase`)

```kotlin
fun provideDatabase(context: Context): Fit2xDatabase =
    database ?: synchronized(this) {
        database ?: Room.databaseBuilder(
            context.applicationContext,
            Fit2xDatabase::class.java,
            "fit2x-db"   // same file as AppGraph — but a SECOND RoomDatabase object
        ).build().also { database = it }
    }
```

Despite the comment claiming "one database file for the whole app", this builds a **second `RoomDatabase` instance** on `fit2x-db`. Room's invalidation tracker is per-instance, so writes through one instance do **not** notify `Flow` observers on the other. Concrete victim:

- `StatsViewModel` observes `workoutSessionDao().observeRecent(400)` via **`AppGraph`'s** instance (`context.appGraph().database`)
- `WorkoutRepository` writes sessions via **`WorkoutGraph`'s** instance

→ Completing a workout does **not** refresh StatsScreen. Stats go stale until the screen is destroyed/recreated (the `WhileSubscribed(5_000)` sharing means navigating away and back). Same hazard applies to any future cross-feature observation.

**Fix:** Delete the `Room.databaseBuilder` call; return `context.appGraph().database` from `provideDatabase()`. Keep the single instance owned by `AppGraph`.

---

## HIGH

### H1. Reminder times are not editable — `setTime` is dead code
**File:** `app/src/main/java/com/fit2x/app/ui/screens/SettingsScreen.kt`
**Lines:** 200–246 (reminders section)

The reminders UI renders toggles and the time label, but tapping a row only flips the enabled switch. There is **no time picker anywhere in the app**. `ReminderSettingsViewModel.setTime()` → `NotificationHelper.reschedule()` is fully implemented and never invoked (verified by grep — zero call sites outside the ViewModel). "Editable reminder schedule" was an explicit requirement; users are stuck with defaults.

**Fix:** Add a time-picker dialog (Material3 `TimePicker`) on tapping the time label, wired to `reminderViewModel.setTime()`.

### H2. Barcode scanner is fully built but unreachable from any UI
**File:** `app/src/main/java/com/fit2x/app/diet/BarcodeScanner.kt` · `DietScreen.kt`
**Lines:** whole `BarcodeScanner.kt` (134 lines), `AndroidManifest.xml` declares `BarcodeScannerActivity`

`BarcodeScannerActivity`, `ScanBarcodeContract`, `rememberBarcodeScanLauncher`, the ZXing integration, the CAMERA permission, and `FoodRepository.lookupBarcode()` (Open Food Facts) are all implemented — but **nothing launches the scanner**. Zero call sites for `rememberBarcodeScanLauncher` / `ScanBarcodeContract` / `onBarcodeScanned` outside their own files (verified by grep). The "Add food" dialog has search + manual entry only. "Diet search/logging and barcode camera" was an explicit verification requirement.

**Fix:** Add a barcode button to `AddFoodDialog` that launches `rememberBarcodeScanLauncher { code -> viewModel.onBarcodeScanned(code) }` and surfaces `barcodeState`.

### H3. WorkManager cannot deliver exact-time reminders — expect drift, especially under Doze
**File:** `app/src/main/java/com/fit2x/app/notifications/NotificationHelper.kt`
**Lines:** 108–121 (`enqueueReminder`)

```kotlin
val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
    .setInitialDelay(millisUntilNext(hour, minute), TimeUnit.MILLISECONDS)
```

`PeriodicWorkRequest` is **inexact by design**. The repeat cadence is anchored to execution completion, not wall-clock time, so the schedule drifts day over day; under Doze/battery optimization a 7:30 AM gym reminder can fire 30+ minutes late (or be deferred hours on some OEMs). Android's own docs say to use `AlarmManager` for exact-time reminders. The manifest even declares the unused `SCHEDULE_EXACT_ALARM` permission (see L3), suggesting exact alarms were intended but never implemented.

**Fix (proper):** Replace the periodic-work schedule with `AlarmManager.setExactAndAllowWhileIdle()` + `setRepeating`-style re-arms (or one exact alarm per reminder re-scheduled daily from the receiver), gating on `AlarmManager.canScheduleExactAlarms()` on API 31+. Keep WorkManager only as a fallback.

### H4. `ExistingPeriodicWorkPolicy.UPDATE` may not re-anchor the schedule on time changes
**File:** `app/src/main/java/com/fit2x/app/notifications/NotificationHelper.kt`
**Lines:** 114–119

When the user changes a reminder time, `reschedule()` → `enqueueReminder()` uses `UPDATE`. For periodic work, `UPDATE` has historically not reliably reset the 24h anchor/initial delay (there are long-standing WorkManager issues on this); the documented-safe way to change a periodic schedule is `CANCEL_AND_REENQUEUE`. Combined with H3, editing a time may appear to save (DataStore updates, UI updates) while the worker keeps firing on the old cadence.

**Fix:** Use `ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE` in `enqueueReminder` (or move to AlarmManager per H3, which moots this).

---

## MEDIUM

### M1. Step-goal stepper has a read-modify-write race — rapid taps lose increments
**File:** `app/src/main/java/com/fit2x/app/ui/screens/StepsScreen.kt`
**Lines:** 302–315 (`updateStepGoal`)

```kotlin
private fun updateStepGoal(context: Context, goal: Int) {
    val clamped = goal.coerceIn(1_000, 50_000)
    val dao = context.appGraph().database.userProfileDao()
    CoroutineScope(Dispatchers.IO).launch {          // unstructured scope, never cancelled
        val row = dao.observeProfile().firstOrNull() // read
        if (row != null) dao.upsert(row.copy(stepGoal = clamped))  // modify+write
        ...
    }
}
```

Two rapid taps on +/− each read the same profile row, then both write — one increment is lost (e.g. 10,000 → tap twice → 11,000 instead of 12,000). The ad-hoc `CoroutineScope(Dispatchers.IO)` is also unstructured (leaks past Activity destroy).

**Fix:** Serialize with a `Mutex`, or better, add a `@Query("UPDATE user_profile SET stepGoal = :goal WHERE id = 'me'")` DAO method and write the computed value directly (single atomic write, no read needed).

### M2. `startWorkout()` double-tap race creates two sessions
**File:** `app/src/main/java/com/fit2x/app/workout/WorkoutViewModel.kt`
**Lines:** 123–138

```kotlin
fun startWorkout() {
    if (_activeSessionId.value != null) return   // check on Main
    val workout = todayWorkout.value ?: return
    viewModelScope.launch {                      // suspends at startSession()
        val id = repository.startSession(...)    // second tap also passes the check
```

The guard is outside the coroutine. Two taps in quick succession both see `null`, both launch, both insert a `WorkoutSession` row → duplicate sessions in history, and `_activeSessionId` ends up pointing at the second while the first row lingers as an incomplete session (which also pollutes the streak calc's `observeRecent`).

**Fix:** Move the guard inside a `Mutex`-protected block, or disable the button while `starting` (add a `_starting: StateFlow<Boolean>`).

### M3. `foods.json` is parsed on the Main thread on first search
**File:** `app/src/main/java/com/fit2x/app/diet/FoodRepository.kt` · `DietViewModel.kt`
**Lines:** `FoodRepository.kt:44` (`by lazy { loadFoods() }`), `DietViewModel.kt:44-46`

```kotlin
val searchResults = searchQuery
    .flatMapLatest { q -> flow { emit(repository.searchFoods(q)) } }  // Main dispatcher
```

`searchFoods()` touches `foods`, whose `lazy` initializer does **asset file I/O + full JSON parse**. The first keystroke in the food search therefore does disk I/O on the Main thread → jank/StrictMode violation (and a potential ANR on slow devices).

**Fix:** Warm the lazy in `init` on `Dispatchers.IO`, or add `.flowOn(Dispatchers.Default)` to `searchResults`.

### M4. Calorie goal is hardcoded to 2200 — no BMR/TDEE, not editable, profile has no field
**File:** `app/src/main/java/com/fit2x/app/ui/screens/DietScreen.kt`
**Lines:** 110 (`val calorieGoal = 2_200`)

The protein goal comes from the profile (`proteinGoalGrams`, default 70g) but calories are a magic constant. The web app computes BMR/TDEE (Mifflin-St Jeor) with goal-based targets; the native app has no equivalent, no profile field, and no UI to change it. The ring is misleading for anyone whose TDEE isn't 2200.

**Fix:** Add `calorieGoal` to `UserProfile` (or compute BMR/TDEE from profile height/weight/activity — fields that don't exist yet either) and make it editable in Settings → Goals.

### M5. "Most protein in a day" record can never appear
**File:** `app/src/main/java/com/fit2x/app/ui/screens/StatsScreen.kt`
**Lines:** 68–74, 128–136

```kotlin
database.foodEntryDao().let { dao ->
    // FoodEntryDao has no observeRecent; approximate via today flow.
    kotlinx.coroutines.flow.flowOf(emptyList<FoodEntry>())  // always empty
}
```

The third personal record is hardcoded to `"Workouts logged"`, which duplicates the "WORKOUTS" stat card two inches above it, while the default state's `"Most protein in a day" → "—"` placeholder is silently replaced. Dead-end code admitted inline.

**Fix:** Add `@Query("SELECT date, SUM(proteinGrams) ... GROUP BY date")` to `FoodEntryDao` and compute the real max-protein day.

### M6. Today screen always shows "0/N moves" — workout progress never reflected
**File:** `app/src/main/java/com/fit2x/app/ui/screens/TodayScreen.kt`
**Lines:** 95–96

```kotlin
val completedMoves = 0 // sessions are tracked in WorkoutDetail; today shows plan size
```

The exercises stat card and the "X/Y moves" label under Today's Workout are hardcoded to 0, even right after finishing a workout. The data exists (`workoutSessionDao.observeDay(today)`); it's just not wired.

**Fix:** Observe today's sessions and sum `completedMoves` (or track the latest session's progress).

### M7. `clearDayOverride` has no UI — day edits can't be undone except by switching templates
**File:** `app/src/main/java/com/fit2x/app/ui/screens/WeekScreen.kt`

The edit dialog offers "Rest" + every workout, but no "Reset to template default". `WorkoutViewModel.clearDayOverride()` exists and is never called from UI (verified by grep). A user who customizes Monday can never get the template's Monday back without switching templates (which wipes *all* overrides).

**Fix:** Add a "Reset day" option in the edit dialog calling `clearDayOverride(dayOfWeek)`.

### M8. New `Flow` instance per recomposition restarts Room collection (recurring pattern)
**Files:** `MainActivity.kt:88-91`, `StepsScreen.kt:94-97`, `TodayScreen.kt:79-82`, `SettingsScreen.kt:77-80`
**Example:**
```kotlin
val profile by graph.database.userProfileDao()
    .observeProfile()          // new Flow object every recomposition
    .map { it?.themeId ?: ... }
    .collectAsState(initial = ...)
```

`collectAsState` keys its `LaunchedEffect` on the flow instance, so **every recomposition cancels and restarts the Room observation**. No visible flicker (the `State` itself isn't keyed, so the last value persists), but it's churn: repeated query re-subscription on each recomposition, wasted work, and a window where emissions can be missed.

**Fix (all four sites):** `val profileFlow = remember { graph.database.userProfileDao().observeProfile() }` then collect that.

### M9. LoginScreen SnackbarHost is not in a Scaffold/Box — error snackbars misposition
**File:** `app/src/main/java/com/fit2x/app/ui/screens/LoginScreen.kt`
**Lines:** 140 (`SnackbarHost(hostState = snackbarHostState)`)

The `SnackbarHost` is emitted as a bare sibling after the `Column`, with no positioning parent. It will not overlay the bottom of the screen; in practice it stacks/measures oddly at the composition root, so sign-in errors ("No internet connection…", "Google sign-in isn't configured yet…") may appear at the top, be clipped, or push layout. The standard pattern is `Scaffold(snackbarHost = { SnackbarHost(...) })`.

**Fix:** Wrap in `Scaffold` or a `Box` with `Alignment.BottomCenter`.

---

## LOW

### L1. `SCHEDULE_EXACT_ALARM` declared but never used
**File:** `app/src/main/AndroidManifest.xml:11`
The permission is requested but the app never calls `AlarmManager`. On API 31+ this permission ideally shouldn't be declared unless used (it triggers a special Settings page). Remove, or keep only if implementing H3's AlarmManager fix.

### L2. `val activity = context as Activity` is dead code
**File:** `app/src/main/java/com/fit2x/app/MainActivity.kt:169`
Assigned but never read inside `Fit2xApp`. Remove.

### L3. `StepSyncWorker.cancel()` never called
**File:** `app/src/main/java/com/fit2x/app/health/StepSyncWorker.kt:77-79`
The cancel helper exists; sign-out never invokes it. Harmless (the worker syncs local-only data and skips without permission), but dead.

### L4. Stale KDoc: LoginScreen says "Credential Manager"
**File:** `app/src/main/java/com/fit2x/app/ui/screens/LoginScreen.kt:24-27`
Docs still describe the abandoned Credential Manager integration; the app uses classic `play-services-auth`. Update the comment.

### L5. `observeCompletedExercises` is convoluted dead weight
**File:** `app/src/main/java/com/fit2x/app/workout/WorkoutRepository.kt:125-130`
Holds a `Mutex` inside a `Flow.map` and isn't called from any UI (the ViewModel keeps its own `_completedExercises`). Verify no caller, then delete.

### L6. Unused parameters in StatsScreen signature
**File:** `app/src/main/java/com/fit2x/app/ui/screens/StatsScreen.kt:142-143`
`healthViewModel` and `workoutViewModel` are accepted but never used (the screen builds its own `StatsViewModel`). Remove to avoid confusion.

### L7. `BootReceiver` scope is never cancelled
**File:** `app/src/main/java/com/fit2x/app/notifications/BootReceiver.kt:13`
Field-level `CoroutineScope(SupervisorJob() + Dispatchers.IO)` outlives the single `onReceive`. Cancel the job in `finally` after `pending.finish()`.

### L8. Protein truncation instead of rounding in `logFood`
**File:** `app/src/main/java/com/fit2x/app/diet/FoodRepository.kt:130`
`(protein * qty * 10).toInt() / 10.0` truncates (18.59 → 18.5). Use `roundToInt()` for accuracy.

### L9. `getWeeklySteps` performs 7 separate permission IPCs
**File:** `app/src/main/java/com/fit2x/app/health/HealthConnectManager.kt:100-110`
Each day's aggregation re-checks `hasPermissions()` (an IPC). Check once, then loop.

### L10. `HealthViewModel.observeLocalSteps` pins "today" at init
**File:** `app/src/main/java/com/fit2x/app/health/HealthViewModel.kt:158-171`
`today` is computed once when collection starts; if the app stays open past midnight it keeps observing yesterday's row. Recompute the date inside the collector or restart collection on day change. Same for `DietViewModel.date` (mitigated by `refreshToday()`, but nothing calls it on day change either).

---

## What was checked and found clean

- **Null safety:** No `!!` operators anywhere; casts are guarded (`isAssignableFrom`/`require` first) or on system services. `AuthRepository` handles null `Intent`, null ID token, null Firebase user.
- **Auth flow:** Cancel (12501) → `SignedOut` silently; network/auth exceptions mapped to user messages; Firestore/Room persistence is best-effort and can't fail sign-in; sign-out clears identity but preserves theme.
- **Coroutines:** ViewModel scopes used throughout; `MainActivity.activityScope` cancelled in `onDestroy`; `callbackFlow` listener removed in `awaitClose`; no Main-thread DB writes (all DAO calls are `suspend` on Room's dispatcher).
- **Workout streak math:** Verified correct — counts back from today/yesterday over fully-completed sessions; `bestStreak` scans the full history. Templates validated to 7 days (`init { require }`), so `first { it.dayOfWeek == dayName }` can't throw.
- **Health Connect:** Time ranges use system zone consistently; manual override never clobbered by auto-sync (checked in both `HealthViewModel.persistToday` and `StepSyncWorker`); weekly aggregation bounds correct.
- **DataStore:** Separate `preferencesDataStore` names (`reminder_prefs`, `workout_prefs`) — no collision; keys namespaced per reminder.
- **Navigation:** `popUpTo(saveState=true)` + `launchSingleTop` + `restoreState` is the correct bottom-nav pattern; `WorkoutDetail` back-stack pops cleanly.

---

## Fix priority (suggested order)

1. **C1** — request `POST_NOTIFICATIONS` (reminders are 100% broken on Android 13+ without it)
2. **C2** — single Room instance via `AppGraph` (stale Stats, latent corruption risk)
3. **H3/H4** — exact alarms via `AlarmManager` (or accept inexact reminders explicitly)
4. **H1** — reminder time picker UI
5. **H2** — wire the barcode scanner into the Add-food dialog
6. **M1, M2** — the two races (step goal, double-tap session)
7. **M3–M9** — correctness/completeness pass
8. **L1–L10** — cleanup
