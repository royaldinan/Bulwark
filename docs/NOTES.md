# NOTES — Bulwark

Append-only. 3-5 lines per task: what was built, why, alternatives. Keep both sides on conflict.

## [feat-android-ui-files] Task 1 — Skeleton + Material 3 theme
- Built the Android Gradle project under `android/`: AGP 8.7.3 + Gradle 8.9 + Kotlin 2.0.21, `id.bulwark`, minSdk 26 / compileSdk 35, Compose BOM 2024.10.01, Room 2.6.1 (KSP).
- Theme in `ui/theme/` uses the fixed palette (white/navy/gradient/surface/selected/line); Typography falls back to the system font because no Inter `.ttf` is bundled yet (PRD N4 permits fallback).
- Declared every manifest permission the later tasks need (INTERNET, FOREGROUND_SERVICE[_SYSTEM_EXEMPTED], POST_NOTIFICATIONS, SYSTEM_ALERT_WINDOW, MANAGE_EXTERNAL_STORAGE, READ_EXTERNAL_STORAGE). `BIND_VPN_SERVICE` is applied on the `<service>` element in task 2, since apps do not request it.
- API base URL is a single `BuildConfig.API_BASE_URL` resolved from a Gradle property / `BULWARK_API_BASE_URL` env var with an obvious `.invalid` placeholder, so the real Railway URL is not invented/hardcoded.
- Alternative considered: XML theme via `com.google.android.material`; dropped it to avoid an extra dependency and keep Compose as the single Material 3 source.

## [feat-android-ui-files] Task 2 — DNS-only tunnel, parser, cache, overlay
- `vpn/DnsParser.kt` reads QNAME/QTYPE from a query (follows compression pointers) and builds an NXDOMAIN answer; `vpn/Ipv4Udp.kt` does minimal IPv4+UDP parse/build with correct header+UDP checksums. Both are tested against real packet byte vectors (12 tests, 0 failures).
- `vpn/BulwarkVpnService.kt` is DNS-only per the PRD decision: it routes only the virtual DNS address `10.111.222.2`, answers flagged domains with NXDOMAIN, and forwards the rest through a `protect()`ed DatagramSocket. It excludes its own package and does not forward TCP.
- `data/DomainHasher.kt` is SHA-256 (never keccak256), normalizes lowercase + no trailing dot, and exposes the 8-byte prefix used for API/registry lookups. `domain_cache`/`link_event` Room tables hold verdicts and the events behind the "link diblokir" counter.
- `overlay/OverlayController.kt` shows the non-dismissable warning via SYSTEM_ALERT_WINDOW with the two required choices; built with plain Views so it can run from the service without a Compose lifecycle owner.
- Decision: link events are recorded on a blocked drop (3 s dedupe for page-load bursts) and on a fresh (cache-miss) safe/unknown verdict, so counters stay meaningful instead of counting every repeated query. Alternative was to log every query; rejected as noisy.
- Open: IPv6 DNS is not routed to the tunnel yet, and the upstream resolver is a fixed `1.1.1.1` (`// VERIFY:`) rather than the underlying network's DNS.

## [feat-android-ui-files] Task 4 — MediationApi + report flow (client)
- `net/MediationApi.kt` implements the PRD endpoints exactly (`POST /v1/check/domain`, `/v1/check/apk`, `/v1/report`, `GET /v1/health`) over `HttpURLConnection` + `org.json`; only the 8-byte prefix is sent for checks, never the full URL.
- It implements `MediationGateway`, which now also carries `report(kind, hash)`. `BulwarkVpnService` builds one `MediationApi` and injects it, so a cache miss is resolved through the real API and never faked.
- Report flow added in `data/`: `report` table (`ReportEntity`/`ReportDao`), `Repository.reportDomain`/`reportApk` write `pending` first then set the real tx hash (`sent`) or `failed`. `ReportValidator` rejects empty/malformed input. AppDatabase is version 2.
- `BuildConfig.API_SHARED_TOKEN` (from Gradle property / env, empty by default) is sent as `X-Bulwark-Token` on `POST /v1/report` per PRD decision 7; `// VERIFY:` the header name matches the backend.
- Alternative: OkHttp + kotlinx.serialization. Rejected to avoid extra dependencies; `HttpURLConnection`/`org.json` are already on the platform. The Lapor Compose screen itself stays with the UI owner (out of scope here).
