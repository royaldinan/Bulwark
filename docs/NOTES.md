# NOTES — Bulwark

Append-only. Keep both sides on conflict. After each task add: what, why, alternatives.

## 2026-11-21 — android-ui-files: `files/` + `ui/` core

What was built
- `files/HashUtil.kt` — streaming SHA-256 (`sha256File`, `sha256Stream`, byte/buffer-based), hex helper, 8-byte `prefix()` for lookups, and `normalizeDomain()` (lowercase, no trailing dot) so client and API hash the same string. `sha256OfDomain()` applies normalize-then-hash.
- `files/Quarantine.kt` — moves a finished APK into the app-private `filesDir/quarantine` so the installer can no longer open it. Falls back to copy+delete when `renameTo` fails across volumes; resolves name collisions with `name-1.ext`.
- `files/ApkWatcher.kt` — `FileObserver` on Download + WhatsApp media dirs, fires only for `*.apk` on `CLOSE_WRITE`/`MOVED_TO`, and hands the finished `File` to a caller callback (the hash/verdict step lives in `data/`, out of this area).
- `ui/theme/` — palette and reusable components from AGENTS.md Design: pills (56dp, gradient primary / surface secondary / disabled), segmented toggle, info card, selectable option card with purple badge, navy status hero, stat tiles, top bar, bottom bar.
- `ui/onboarding/`, `ui/home/`, `ui/report/` — Compose screens with simple Indonesian copy. Numbers come from `BulwarkRepository.observeHomeStats()`; never hardcoded.

Why this approach
- Hash + move logic stay platform-light so they run as plain JVM unit tests. `Quarantine(File)` constructor (plus a `Context` convenience) lets tests use a temp dir.
- Screens are stateless composables with hoisted state; `*Route` wrappers collect the repository flow. Wiring navigation/permissions/SAF picker is left to `android-core`/MainActivity to avoid touching Gradle, manifest, and other packages.
- The repository is an interface because `data/` (Room) is owned by `feat/android-core`. UI only depends on the contract.

Alternatives considered
- Bundling Inter font files: skipped, only `FontFamily.Default` wired until `res/font` is available (VERIFY inline).
- `data/`-backed ViewModel in this branch: rejected, that package is another area.
- Hashing inside `ApkWatcher`: rejected, keeps observer cheap and the verdict lookup in `data/`.

Not built here (no toolchain in this worktree): `./gradlew assembleDebug` / `./gradlew test` could not run — `<android>/` has no Gradle skeleton yet. Unit tests are written and ready for when the skeleton lands.

## Request — for branch `feat/android-core`

Please implement this interface in `data/` (Room + MediationApi) and keep it stable; the UI compiles against it.

```kotlin
package id.bulwark.ui

enum class ReportKind { DOMAIN, APK }

data class HomeStats(
    val protectionActive: Boolean,
    val blockedLinkCount: Int,      // COUNT(* ) from link_event WHERE action='blocked'
    val quarantinedApkCount: Int,   // COUNT(*) from apk_event WHERE action='quarantined'
    val lastSyncAt: Long?,          // app_state.last_sync_at, null if never
)

sealed interface ReportOutcome {
    data class Sent(val txHash: String) : ReportOutcome
    data class Failed(val message: String) : ReportOutcome
}

interface BulwarkRepository {
    fun observeHomeStats(): Flow<HomeStats>
    suspend fun report(kind: ReportKind, value: String): ReportOutcome
}
```

Also requested:
- Wire `ApkWatcher.onApkFinished` → compute `HashUtil.sha256File` → check cache/API → `Quarantine.move` → write `apk_event` + overlay.
- Better: expose an `ApkGuard` (or similar) owned by `data/`, so the UI/files layer does not fetch verdicts itself. `files/` only promises the callback and `Quarantine`.
- Caller of `OnboardingScreen` should supply `PermissionStatusReader` backed by `VpnService.prepare`, file-access check, and `Settings.canDrawOverlays` (status read live from the system).
- Provide an implementation of `BulwarkRepository` and pass it into `HomeRoute` / `ReportRoute`.

Open items carried from PRD
- V1 Safe Browsing endpoint shape (backend).
- V2 WhatsApp folder access on Android 11+ — `ApkWatcher.defaultDirectories()` currently guesses paths, needs the real decision.
