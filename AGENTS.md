# AGENTS.md — Bulwark

Android app (Kotlin/Compose) that blocks scam links and malicious APKs on-device, backed by an append-only registry on Polygon Amoy and a Node.js mediation API.

## Source of truth
- Read `docs/PRD.md` before starting. Its "Keputusan teknis" section overrides everything else in it.
- Read `docs/TEAM.md` for contributors, branches, and the commit schedule.
- Build in the order of "Urutan pengerjaan 24 jam", not the Fase numbers.
- Work on ONE task per session. Stop when its "Selesai bila" criteria are met.

## Repo layout
- `android/` Kotlin + Jetpack Compose + Room
- `backend/` Node.js (Express) mediation API, deployed on Railway
- `contracts/` Solidity + Hardhat, deployed on Polygon Amoy (chain ID 80002)
- `docs/` PRD.md, TEAM.md, NOTES.md

## Hard rules
1. No hardcoded data, no fake or mock results anywhere a user can see. Counts come from Room, verdicts from cache/API/registry, tx hashes from real transactions.
2. Out of scope, do not add: TLS interception, ML, iOS, AccessibilityService, extra heuristics, tokens/coins.
3. Never commit secrets (private keys, API keys, .env). Read them from environment variables. Keep `.env.example` with names only.
4. Do not invent APIs, permissions, endpoints or class names. If unsure, say so and check official docs or ask. Mark uncertain code with `// VERIFY:`.
5. Never print or log private keys.
6. UI text is simple Indonesian. The word "VPN" must not appear in Bulwark's own UI; use "filter koneksi lokal".
7. Keep changes small. Do not refactor or rename code outside the current task.
8. Do NOT run git add, commit, pull, push, rebase or any other git write command. Leave changes uncommitted in the working tree. Commits are made by a separate tool (Hermes).

## Android notes
- The tunnel is DNS-only: route only the virtual DNS address into the TUN, answer blocked domains with NXDOMAIN, forward safe queries through a `protect()`ed socket.
- Exclude our own package from the tunnel (`addDisallowedApplication`).
- Hash everything with SHA-256, use an 8-byte prefix for lookups. Normalize domains first (lowercase, no trailing dot).
- Safe/unknown verdict TTL <= 30 s.

## Design
White #FFFFFF, navy #0B1B4D, gradient #2456E8 -> #6A3FE0, surface #F3F4F9, selected card #ECEBFF, line #E3E6F0. Cards radius 24-28dp, pill buttons 56dp. Inter font, system font fallback.

## Workflow
- Build and test locally before saying a task is done: `./gradlew assembleDebug` (android), `npm test` / run the server (backend), `npx hardhat test` (contracts).
- Do not claim success without evidence. Report the actual command output or observed behavior.
- When a task is done, report: changed files, real build/test output, and a suggested commit message in the format `feat(vpn): parse DNS query`.
- After each task, append 3-5 lines to `docs/NOTES.md`: what was built, why this approach, and what the alternatives were. The team must be able to explain every module to the judges.
- If stuck or repeating the same error twice, stop and report the error instead of looping.
