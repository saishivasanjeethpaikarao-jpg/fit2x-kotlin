# FIT2X Native — Project Spec

## Stack
- **Language**: Kotlin
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM
- **DI**: Manual (no Hilt to keep it simple)
- **Local DB**: Room
- **Backend**: Firebase Auth + Firestore
- **Steps**: Health Connect API
- **Notifications**: WorkManager + AlarmManager

## Package
`com.fit2x.app`

## Min SDK
26 (Android 8.0)

## Themes (4)
- Electric (cobalt blue — default)
- Ember (orange/red)
- Noir (black/white)
- Earth (green/brown)

## Screens (bottom nav)
1. **Today** — Steps card, protein card, exercises card, streak card, today's workout
2. **Steps** — Step history, charts, goal setting
3. **Week** — Weekly workout schedule (Mon-Sun editable)
4. **Diet** — Food logging, protein tracking, barcode scan
5. **Stats** — Progress charts, history
6. **Settings** — Theme switcher, reminders, auth, avatar

## Key Features
- Google Sign-In (native, not web)
- Health Connect step sync (with permission flow)
- Local notifications (gym 7:30 AM, lunch 12:15 PM, snack 5 PM, dinner 8:30 PM)
- Offline-first with Firestore sync
- 4 theme switcher
- Workout plans (gym + home)
- Diet tracker with Indian food database
- Avatar system
- Streak tracking

## Code Standards
- Kotlin coroutines for async
- StateFlow for UI state
- Repository pattern
- No hardcoded strings (use string resources where practical)
- Dark theme optimized (primary use case)
