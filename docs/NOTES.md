# NOTES G�� Bulwark

Append-only. 3-5 lines per task: what was built, why, alternatives. Keep both sides on conflict.

## [feat-android-ui-files] Task 1 G�� Skeleton + Material 3 theme
- Built the Android Gradle project under `android/`: AGP 8.7.3 + Gradle 8.9 + Kotlin 2.0.21, `id.bulwark`, minSdk 26 / compileSdk 35, Compose BOM 2024.10.01, Room 2.6.1 (KSP).
- Theme in `ui/theme/` uses the fixed palette (white/navy/gradient/surface/selected/line); Typography falls back to the system font because no Inter `.ttf` is bundled yet (PRD N4 permits fallback).
- Declared every manifest permission the later tasks need (INTERNET, FOREGROUND_SERVICE[_SYSTEM_EXEMPTED], POST_NOTIFICATIONS, SYSTEM_ALERT_WINDOW, MANAGE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE). `BIND_VPN_SERVICE` is applied on the `<service>` element in task 2, since apps do not request it.
- API base URL is a single `BuildConfig.API_BASE_URL` resolved from a Gradle property / `BULWARK_API_BASE_URL` env var with an obvious `.invalid` placeholder, so the real Railway URL is not invented/hardcoded.
- Alternative considered: XML theme via `com.google.android.material`; dropped it to avoid an extra dependency and keep Compose as the single Material 3 source.

## [feat-android-ui-files] Task 2 G�� DNS-only tunnel, parser, cache, overlay
- `vpn/DnsParser.kt` reads QNAME/QTYPE from a query (follows compression pointers) and builds an NXDOMAIN answer; `vpn/Ipv4Udp.kt` does minimal IPv4+UDP parse/build with correct header+UDP checksums. Both are tested against real packet byte vectors (12 tests, 0 failures).
- `vpn/BulwarkVpnService.kt` is DNS-only per the PRD decision: it routes only the virtual DNS address `10.111.222.2`, answers flagged domains with NXDOMAIN, and forwards the rest through a `protect()`ed DatagramSocket. It excludes its own package and does not forward TCP.
- `data/DomainHasher.kt` is SHA-256 (never keccak256), normalizes lowercase + no trailing dot, and exposes the 8-byte prefix used for API/registry lookups. `domain_cache`/`link_event` Room tables hold verdicts and the events behind the "link diblokir" counter.
- `overlay/OverlayController.kt` shows the non-dismissable warning via SYSTEM_ALERT_WINDOW with the two required choices; built with plain Views so it can run from the service without a Compose lifecycle owner.
- Decision: link events are recorded on a blocked drop (3 s dedupe for page-load bursts) and on a fresh (cache-miss) safe/unknown verdict, so counters stay meaningful instead of counting every repeated query. Alternative was to log every query; rejected as noisy.
- Open: IPv6 DNS is not routed to the tunnel yet, and the upstream resolver is a fixed `1.1.1.1` (`// VERIFY:`) rather than the underlying network's DNS.

## [feat-android-ui-files] Task 4 G�� MediationApi + report flow (client)
- `net/MediationApi.kt` implements the PRD endpoints exactly (`POST /v1/check/domain`, `/v1/check/apk`, `/v1/report`, `GET /v1/health`) over `HttpURLConnection` + `org.json`; only the 8-byte prefix is sent for checks, never the full URL.
- It implements `MediationGateway`, which now also carries `report(kind, hash)`. `BulwarkVpnService` builds one `MediationApi` and injects it, so a cache miss is resolved through the real API and never faked.
- Report flow added in `data/`: `report` table (`ReportEntity`/`ReportDao`), `Repository.reportDomain`/`reportApk` write `pending` first then set the real tx hash (`sent`) or `failed`. `ReportValidator` rejects empty/malformed input. AppDatabase is version 2.
- `BuildConfig.API_SHARED_TOKEN` (from Gradle property / env, empty by default) is sent as `x-api-token` on `POST /v1/report` per PRD decision 7; header name matches backend `tokenGuard` (backend/src/app.js).
- Alternative: OkHttp + kotlinx.serialization. Rejected to avoid extra dependencies; `HttpURLConnection`/`org.json` are already on the platform. The Lapor Compose screen itself stays with the UI owner (out of scope here).


# NOTES G�� Bulwark

Append-only. Keep both sides on conflict. After each task add: what, why, alternatives.

## 2026-11-21 G�� android-ui-files: `files/` + `ui/` core

What was built
- `files/HashUtil.kt` G�� streaming SHA-256 (`sha256File`, `sha256Stream`, byte/buffer-based), hex helper, 8-byte `prefix()` for lookups, and `normalizeDomain()` (lowercase, no trailing dot) so client and API hash the same string. `sha256OfDomain()` applies normalize-then-hash.
- `files/Quarantine.kt` G�� moves a finished APK into the app-private `filesDir/quarantine` so the installer can no longer open it. Falls back to copy+delete when `renameTo` fails across volumes; resolves name collisions with `name-1.ext`.
- `files/ApkWatcher.kt` G�� `FileObserver` on Download + WhatsApp media dirs, fires only for `*.apk` on `CLOSE_WRITE`/`MOVED_TO`, and hands the finished `File` to a caller callback (the hash/verdict step lives in `data/`, out of this area).
- `ui/theme/` G�� palette and reusable components from AGENTS.md Design: pills (56dp, gradient primary / surface secondary / disabled), segmented toggle, info card, selectable option card with purple badge, navy status hero, stat tiles, top bar, bottom bar.
- `ui/onboarding/`, `ui/home/`, `ui/report/` G�� Compose screens with simple Indonesian copy. Numbers come from `BulwarkRepository.observeHomeStats()`; never hardcoded.

Why this approach
- Hash + move logic stay platform-light so they run as plain JVM unit tests. `Quarantine(File)` constructor (plus a `Context` convenience) lets tests use a temp dir.
- Screens are stateless composables with hoisted state; `*Route` wrappers collect the repository flow. Wiring navigation/permissions/SAF picker is left to `android-core`/MainActivity to avoid touching Gradle, manifest, and other packages.
- The repository is an interface because `data/` (Room) is owned by `feat/android-core`. UI only depends on the contract.

Alternatives considered
- Bundling Inter font files: skipped, only `FontFamily.Default` wired until `res/font` is available (VERIFY inline).
- `data/`-backed ViewModel in this branch: rejected, that package is another area.
- Hashing inside `ApkWatcher`: rejected, keeps observer cheap and the verdict lookup in `data/`.

Not built here (no toolchain in this worktree): `./gradlew assembleDebug` / `./gradlew test` could not run G�� `<android>/` has no Gradle skeleton yet. Unit tests are written and ready for when the skeleton lands.

## Request G�� for branch `feat/android-core`

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
- Wire `ApkWatcher.onApkFinished` G�� compute `HashUtil.sha256File` G�� check cache/API G�� `Quarantine.move` G�� write `apk_event` + overlay.
- Better: expose an `ApkGuard` (or similar) owned by `data/`, so the UI/files layer does not fetch verdicts itself. `files/` only promises the callback and `Quarantine`.
- Caller of `OnboardingScreen` should supply `PermissionStatusReader` backed by `VpnService.prepare`, file-access check, and `Settings.canDrawOverlays` (status read live from the system).
- Provide an implementation of `BulwarkRepository` and pass it into `HomeRoute` / `ReportRoute`.

Open items carried from PRD
- V1 Safe Browsing endpoint shape (backend).
- V2 WhatsApp folder access on Android 11+ G�� `ApkWatcher.defaultDirectories()` currently guesses paths, needs the real decision.

## [feat-android-ui-files] Wire screens to real data (merge integration)
- After merging the skeleton + UI/files branches, `./gradlew assembleDebug` and `testDebugUnitTest` already passed; no old theme-name references remained (`ui/theme` is self-consistent), so step 2 needed no code change.
- Implemented the `ui.BulwarkRepository` request in `data/RoomBulwarkRepository.kt`: `blockedLinkCount` = `link_event`, `quarantinedApkCount` = new `apk_event` table, `lastSyncAt` = new `app_state` table, `protectionActive` = live `ProtectionState` published by `BulwarkVpnService`. Added `apk_cache`/`apk_event`/`app_state` entities + DAOs and bumped `AppDatabase` to v3.
- Wired screens in `ui/BulwarkRoot.kt` + `MainActivity`: 3-tab nav (Beranda/Lapor/Izin). Beranda collects the Room flow; Onboarding reads status live via `SystemPermissionStatusReader` (`VpnService.prepare`, All-files access, `canDrawOverlays`) and re-reads on resume; Lapor calls `MediationApi` and shows the tx hash from the real response only, opening `amoy.polygonscan.com/tx/<hash>`.
- Added `data/ApkGuard.kt` as requested: `ApkWatcher` -> `HashUtil.sha256File` -> `checkApk` (cache/API) -> `Quarantine.move` -> `apk_event` + `OverlayController.showFileBlocked`. No fake verdicts or counts anywhere.
- Alternative: give the UI a ViewModel. Rejected to keep the request's interface (`observeHomeStats` Flow + `report`) and avoid new dependencies. Not yet run on a device: no emulator in this worktree, so evidence is the build + 28 unit tests.

## [feat-android-ui-files] Config: real API base URL + token from local.properties
- Set the Mediation API base URL default to `https://bulwark-production-d29c2d86.up.railway.app`; all `MediationApi` calls (check domain / check apk / report / health) already build on `BuildConfig.API_BASE_URL`.
- `BuildConfig.API_SHARED_TOKEN` now comes from the gitignored `local.properties` key `API_SHARED_TOKEN` (read via `java.util.Properties`), defaulting to empty when absent; never hardcoded.
- `local.properties` stays ignored by `android/.gitignore`. Verified `assembleDebug` + `testDebugUnitTest`: BUILD SUCCESSFUL, 28 tests / 0 failures.
