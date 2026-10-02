# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

고양이지갑 (project/package name MEOW) is a single-module Android app (`:app`, package/applicationId `com.myfamily.meow`) written in Kotlin with Jetpack Compose and Material 3. The product spec (Korean) and UI mockups live in `명세서및UI참고/`: a swipe-to-review expense tracker fed by payment notifications. Development targets only the owner's Galaxy S25+ for now.

## Commands

Run from the repo root. On Windows use `.\gradlew.bat` (PowerShell) or `./gradlew` (Git Bash).

- Build debug APK: `./gradlew assembleDebug`
- Install on connected device/emulator: `./gradlew installDebug`
- Unit tests (JVM): `./gradlew testDebugUnitTest`
- Single unit test: `./gradlew testDebugUnitTest --tests "com.myfamily.meow.ExampleUnitTest.addition_isCorrect"`
- Instrumented tests (needs device/emulator): `./gradlew connectedDebugAndroidTest`
- Single instrumented test: `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.myfamily.meow.ExampleInstrumentedTest`
- Android Lint: `./gradlew lintDebug`

No ktlint/detekt is configured; `kotlin.code.style=official` is set in `gradle.properties`.

## Build configuration

- All dependency and plugin versions live in the version catalog `gradle/libs.versions.toml`; reference them as `libs.*` in `app/build.gradle.kts` rather than hard-coding coordinates. Compose library versions come from the Compose BOM (no per-artifact versions).
- AGP 8.13, Kotlin 2.3 (required by LiteRT-LM's Kotlin metadata; don't downgrade) with the Kotlin Compose compiler plugin (`kotlin.compose`) — there is no separate `composeOptions.kotlinCompilerExtensionVersion`. JVM target is set via `kotlin { compilerOptions { } }`; the old `kotlinOptions` block is an error on Kotlin 2.3.
- compileSdk/targetSdk 36, minSdk 26, Java/JVM target 11.
- `settings.gradle.kts` uses `RepositoriesMode.FAIL_ON_PROJECT_REPOS`: add repositories there, not in module build files.
- `android.nonTransitiveRClass=true`: reference resources via the owning module's `R` class.

## Architecture

Pipeline: payment notification → `notification.PaymentNotificationListener` → `NotificationParser` → Room (`RawPaymentEvent` + PENDING `ExpenseTransaction`) → swipe review sets INCLUDED/EXCLUDED → calendar shows INCLUDED only.

- `MeowApplication` holds the `AppDatabase` and `TransactionRepository` (manual DI; `Context.repository` extension). ViewModels are built with `viewModelFactory { initializer { } }` in the screen composable.
- Raw events are never deleted; excluded candidates stay as EXCLUDED rows. Only INCLUDED rows count toward totals. `RawPaymentEvent.fingerprint` (package|title|text|minute, unique index) drops notification reposts.
- `notification.PaymentSources` is the package allowlist. Messaging apps (KakaoTalk, SMS) additionally need a financial-looking sender. Notifications without a payment keyword are dropped without being stored.
- `notification.parser.NotificationParser` is a single generic parser for now; spec §5 wants per-app parsers once real formats are known. The DEV screen lists captured raw notifications for this.
- Debug builds only: the listener also accepts this app's own notifications, so `debug.FakePaymentNotifier` (DEV button in the top bar) can drive the pipeline end to end. The fake scenarios double as parser unit-test fixtures.
- UI: `ui/MeowApp.kt` gates onboarding (SharedPreferences flag) and hosts two tabs, 검토 (`ui/review`) and 내역 (`ui/calendar`). There is no navigation library. The theme is fixed dark with mint accent, colors sampled from the mockups (`ui/theme/Color.kt`), and has no dynamic color.
- Classification chain, run when a candidate is stored (`TransactionRepository.recordNotification`): ① `CorrectionHistory` lookup by `merchantKey` (first token, so branches share history), then ③ `RuleClassifier` keyword rules. Anything left as ETC is handled by ⑤ `ai.AiCategorizer` (app-scoped, runs Gemma when the review screen sees new pending rows; transfer-like rows are skipped). Step ② (foreground app via UsageStats) and ④ (location) are not implemented.
- A user category edit or manual entry writes a `CorrectionHistory` row and re-labels other pending rows with the same merchant key. Those rows get `classificationSource = USER` with `finalCategory` null. Only a direct user choice sets `finalCategory`.
- Duplicate/transfer hints (spec §9) are badges only: same amount within ±3 min ⇒ shared `duplicateGroupId`; keywords in title/text ⇒ `transferLikely`. Nothing is auto-excluded.
- Room DB is at version 2 with a hand-written `MIGRATION_1_2`. For schema changes, add a migration and copy CREATE statements from the KSP-generated `AppDatabase_Impl` (exportSchema is off). Never use destructive migration: the owner's phone holds real data.
- Daily review reminder: `reminder.DailyReviewScheduler` chains one-shot WorkManager jobs, rescheduled on app start, after each run, and on settings change. Settings live in SharedPreferences (`ReminderSettings`), not Room.
- `ai.GemmaClassifier` runs Gemma 4 E2B via LiteRT-LM (GPU first, CPU fallback). The model is NOT bundled or in git: it lives at `/sdcard/Android/data/com.myfamily.meow/files/gemma-4-E2B-it.litertlm`, pushed with `adb push` (local copy in `C:\coding\models\`). Uninstalling the app deletes it; re-push after reinstall. ML Kit Gemini Nano Prompt API is unsupported on the S25+ (AICore `606-FEATURE_NOT_FOUND`). A cloud LLM implementation is planned for a public demo build.
- Real notification formats seen so far are kept as fixtures in `FakePaymentNotifier` (e.g. Toss: title `10,000원 출금`, text `내 토스뱅크 통장 → 받는곳`). Add new real formats there and to the parser tests.
