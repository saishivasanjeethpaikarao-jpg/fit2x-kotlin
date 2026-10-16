# FIT2X Professional Polish Pass — v1.0.3 plan

Prepared 2026-10-16 ~18:30 IST. Shiv's ask (main chat, 2026-10-16 ~17:05 IST): "I need my app as professional app like how healthify and top apps are best than them." Bug fixes (v1.0.2, build #16, all 25) landed and the APK was delivered ~17:25 IST. This plan is the researched, scoped groundwork so the polish pass can start the moment he says "go" — same structure he already approved: parallel agent teams, then a verification agent, then final check + build + APK.

## Verified audit findings (codebase at ~/workspace/fit2x-kotlin, checked 2026-10-16)

- **Zero animations.** 37 Kotlin files, 0 uses of `animate*`, `AnimatedVisibility`, `AnimatedContent`, `Crossfade`, shimmer or placeholder (grep verified). ProgressRing.kt draws a *static* arc — no animated sweep.
- **Zero haptics.** 0 `performHapticFeedback` calls.
- **Loading = bare spinners.** CircularProgressIndicator only in LoginScreen:107 and DietScreen:359; LinearProgressIndicator in WorkoutDetailScreen:274 and StatCard:68. No skeleton/shimmer anywhere — data loads hit blank flashes.
- **Empty states are an afterthought.** Only DietScreen has one (DietScreen.kt:169–185), and it's a plain gray card saying "no foods logged". WeekScreen, StatsScreen, WorkoutDetailScreen have none.
- **No splash screen.** themes.xml is just a black `windowBackground` — no Android 12 SplashScreen API.
- **Foundation is good:** 4 hand-built dark-first themes (Electric/Ember/Noir + 1), Material 3, native Kotlin. Don't touch the themes — polish must work under all four.

## What "feels like HealthifyMe" actually means (researched, not guessed)

- HealthifyMe (40M+ installs, Indian market leader): Ria AI coach, Snap photo meal logging, personalized insights, Indian-food focus (Play Store listing; indianexpress; newsbytes Dec 2025). **Out of scope for polish:** Ria/Snap are product features with backend cost, not UX polish. FIT2X's real edge is native-first (offline, Health Connect) — the polish goal is *feel*, not feature cloning.
- Premium feel comes from: consistent motion grammar with real easing/timing (thenewstack.io); celebratory moments when goals are hit, animated transitions instead of hard cuts, graceful loading (mowe.studio); skeleton screens over spinners, shimmer as an "animated nudge" (medium.com/@focotik.agency, Dec 2025); micro-interactions for instant feedback and confetti-style delight on goal completion (fastercapital.com); haptics + light animation used sparingly (dataconomy.com, Nov 2025).

## Pre-step (fixes today's freeze pain): persistent signing

v1.0.2's installer freeze was diagnosed as a **signature mismatch** — each CI build generated a fresh debug key, so installs hang unless the old app is uninstalled first. For v1.0.3: commit a persistent debug keystore (or reuse the v1.0.2 key) so **future APKs install cleanly over the old app**. This is part of "professional app" feel — no user should ever uninstall-to-update again.

## Work packages (parallel-safe: each owns different files)

**Package A — Motion core + animated components** (`ui/motion/Motion.kt` new: easing FastOutSlowInEmphasized for entrances, spring for celebrations; durations short 150ms / medium 300ms / long 500ms; stagger helper). Apply to: ProgressRing animated sweep (`animateFloatAsState`), StatCard count-up + bar animation, TodayScreen stat tick-up. *Every other package reads Motion.kt — ship it first.*

**Package B — Skeleton/shimmer loading.** New `ShimmerCard`/`ShimmerList` components; apply to TodayScreen initial load, DietScreen, WeekScreen, StatsScreen. Keep spinners only where indeterminate is honest (login, barcode scan).

**Package C — Haptics + micro-interactions + celebrations.** Haptic-feedback util wired to primary CTAs (log food, start/complete workout, streak save); subtle press-scale on primary buttons; celebration overlay on workout completion (animated check-mark); streak milestones 7/30/100-day glow hooks into the avatar system's glow frames; protein-goal-hit ring burst.

**Package D — Transitions + intentional empty states.** Tab-switch fade/slide (AnimatedContent); staggered card entrances on first load; empty states for WeekScreen, StatsScreen, WorkoutDetailScreen, StepsScreen chart, and a redesigned DietScreen one — icon + headline + CTA button, never gray boxes.

**Package E — Splash + consistency audit.** Android 12 SplashScreen API with the FIT2X mark + theme accent; typography audit (all screens on MaterialTheme.typography, no hardcoded sp); 48dp touch-target check; 4/8/16/24 spacing audit.

**Package F — Verification agent.** Re-checks all 25 v1.0.2 bug fixes still hold; animation perf pass (no recomposition storms — the v1.0.2 freeze was exactly that; animations must use `animate*` APIs, never per-frame state writes); APK size delta check; install-over-old-app test on the persistent keystore.

## Exclusions (budget discipline)

- No Lottie or new animation dependencies — Compose APIs only (APK size).
- No new features (no Ria/Snap clones — those are product decisions with real cost).
- The 4 dark themes stay untouched; polish must look right under all of them.

## Done-when

Build #17 (v1.0.3) on GitHub releases, direct-download APK link, on-device verification checklist — pushed the moment it's ready, per his standing preference.
