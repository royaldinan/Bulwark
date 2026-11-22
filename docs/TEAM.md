# Team and engineering workflow

## Contributors

| GitHub | Person | Area |
| --- | --- | --- |
| [`royaldinan`](https://github.com/royaldinan) | Ferdinand Manurung (team lead) | Android protection core, DNS decisions, Room cache, warning experience, on-device verification |
| [`anzuky1`](https://github.com/anzuky1) | Abyan Nur Zaky | `Registry.sol`, Hardhat deployment, Express mediation API, OpenPhish integration, Railway |
| [`MarioGess`](https://github.com/MarioGess) | Mario Gabriel Santoso | Compose UI, onboarding, home and report flows, APK monitoring and quarantine |

Each module has an owner who can explain its design and verification during Q&A.

## Development workflow

- `main` is the deployable, reviewable branch.
- Feature work uses `feat/<area>` and fixes use `fix/<area>`.
- Changes are submitted through focused pull requests. Existing history is preserved; updates are added through new commits and merge commits.
- Commit messages follow `type(scope): short summary`, for example `feat(feed): index OpenPhish domains` or `fix(vpn): retry unavailable verdicts`.
- User-visible values come from Room, the mediation API, or the registry—not hardcoded demo results.

## Build and test

```bash
cd android
./gradlew testDebugUnitTest assembleDebug

cd ../backend
npm test

cd ../contracts
npx hardhat test
```

The current Android suite contains 60 JVM tests and the backend suite contains 33 tests. The Android build requires JDK 21 and Android SDK 36.

Backend configuration is read from `PORT`, `AMOY_RPC_URL`, `REGISTRY_ADDRESS`, `API_SHARED_TOKEN`, and `PRIVATE_KEY`. Android report configuration is read from the gitignored `android/local.properties`.

Secrets never enter the repository: `local.properties`, `.env`, private keys, and tokens stay out of Git. Only variable names and placeholders belong in example files and documentation.

## Deployed facts

- Repository: <https://github.com/royaldinan/Bulwark>
- Registry chain: Polygon Amoy, chain ID `80002`
- Contract: [`0xbdf4a4db5C00f2172435940efD8f460ff8f15EEe`](https://amoy.polygonscan.com/address/0xbdf4a4db5C00f2172435940efD8f460ff8f15EEe)
- API: <https://bulwark-production-d29c2d86.up.railway.app>
