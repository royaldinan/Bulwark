# AGENTS.md — Bulwark

Android app (Kotlin/Compose) that blocks scam links and malicious APKs on-device, backed by an append-only registry on Polygon Amoy and a Node.js mediation API.

## Source of truth
- `docs/PRD.md` describes the product scope and the technical decisions behind it.
- `docs/TEAM.md` lists contributors, areas and branching rules.
- Work on ONE task per session and stop when its acceptance criteria are met.

## Repo layout
- `android/` Kotlin + Jetpack Compose + Room
- `backend/` Node.js (Express) mediation API, deployed on Railway
- `contracts/` Solidity + Hardhat, deployed on Polygon Amoy (chain ID 80002)
- `docs/` architecture, screenshots, PRD, TEAM, NOTES

## Hard rules
1. No hardcoded data, no fake or mock results anywhere a user can see. Counts come from Room, verdicts from cache/API/registry, tx hashes from real transactions.
2. Out of scope, do not add: TLS interception, ML, iOS, AccessibilityService, extra heuristics, tokens/coins.
3. Never commit secrets (private keys, API keys, `.env`). Read them from environment variables. Keep `.env.example` with names only.
4. Do not invent APIs, permissions, endpoints or class names. If unsure, say so and check official docs. Mark uncertain code with `// VERIFY:`.
5. Never print or log private keys.
6. UI text is simple Indonesian. The word "VPN" must not appear in Bulwark's own UI; use "filter koneksi lokal".
7. Keep changes small. Do not refactor or rename code outside the current task.
8. Run `./gradlew testDebugUnitTest assembleDebug` before proposing a change to `android/`, and `npx hardhat test` before touching `contracts/`.

## Android notes
- The tunnel is DNS-only: route only the virtual DNS address into the TUN, answer blocked domains with NXDOMAIN, forward safe queries through a `protect()`ed socket.
- Exclude our own package from the tunnel (`addDisallowedApplication`).
- Hash domains and APKs with SHA-256. Normalize domains first (lowercase, no trailing dot). Domain lookups send the 8-byte prefix and full hash so the backend can require an exact match for both registry and OpenPhish feed verdicts.
- Blocked domains: one decision per domain from `BlockGate`, one warning activity, one `link_event`, then a 15-second cooldown measured from when the warning closes.
- The backend expects `0x` + 64 hex characters; the app normalises hashes before every call.

## Backend notes
- Validate every body: `HEX32 = /^0x[0-9a-f]{64}$/i`, prefix `0x` + 16 hex characters.
- `/v1/report` requires `x-api-token`; `/v1/check/*` is read-only and cached.
- Reads go to the registry contract on Amoy; writes wait for the receipt and return the real `txHash`.

## Style
- Kotlin: four-space indent, explicit types on public functions, no wildcard imports.
- JavaScript: two-space indent, `const` by default, async/await over callbacks.
- Commits: `type(scope): summary`, present tense, one logical change per commit.
