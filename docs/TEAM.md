# Team and workflow

## Contributors

| GitHub | Person | Area |
| --- | --- | --- |
| [`royaldinan`](https://github.com/royaldinan) | Ferdinand Manurung (team lead) | `android/`: VpnService, DNS parser and decision core, Room cache, warning activity, on-device testing |
| [`anzuky1`](https://github.com/anzuky1) | Abyan Nur Zaky | `backend/` and `contracts/`: `Registry.sol`, Hardhat deploy, Express API, Railway |
| [`MarioGess`](https://github.com/MarioGess) | Mario Gabriel Santoso | `android/`: Compose UI (theme, onboarding, home, report), FileObserver, SHA-256, quarantine |

Each person commits under their own GitHub account. Every module has an author who can explain it in Q&A.

## Branching

- `main` always builds and always runs.
- Feature work happens on a branch: `feat/<area>`, fixes on `fix/<area>`.
- Before pushing, rebase on `origin/main`. No force pushes, no history rewriting.
- Commit messages follow `type(scope): short summary`, for example `feat(vpn): parse DNS query` or `fix(api): read PORT from env`.

## Build and test

- `cd android && ./gradlew testDebugUnitTest assembleDebug` — 41 unit tests cover the DNS decision core, the block gate and hashing.
- `cd backend && npm start` — the API reads `PORT`, `AMOY_RPC_URL`, `REGISTRY_ADDRESS`, `API_SHARED_TOKEN` and `PRIVATE_KEY` from the environment.
- `cd contracts && npx hardhat test` — registry append and lookup behaviour.

Secrets never enter the repository: `local.properties`, `.env` and keys stay out of git, and only `.env.example` with variable names is committed.

## Fixed facts

- Repository: <https://github.com/royaldinan/Bulwark>
- Registry chain: Polygon Amoy, chain ID 80002
- Contract: `0xbdf4a4db5C00f2172435940efD8f460ff8f15EEe`
- API: `https://bulwark-production-d29c2d86.up.railway.app`
