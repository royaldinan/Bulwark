# TEAM.md — Bulwark (Hacksphere 2026)

Read this together with `AGENTS.md` and `docs/PRD.md`. Humans and agents both follow it.

## Contributors

| GitHub | Person | Branch | Area (initial split, swap if needed) |
|---|---|---|---|
| `royaldinan` (repo owner) | Ferdinand Manurung (team leader) | `feat/android-core` | `android/`: VpnService, DNS parser, Room cache, overlay controller |
| `AbyanIsGood` | Abyan Nur Zaky | `feat/backend-contracts` | `backend/` and `contracts/`: Registry.sol, Hardhat deploy, Node API, Railway |
| `MarioGes` | Mario Gabriel Santoso | `feat/android-ui-files` | `android/`: Compose UI (theme, onboarding, home, report), FileObserver, SHA-256, quarantine |

Rules:
- Every person commits under their OWN GitHub account and email. Check with `git config user.name` and `git config user.email` before the first commit. Never commit as someone else, and only commit work that person actually did.
- All three must have real commits in the history. Everyone must be able to explain every module in Q&A.
- Agents only write code. Commits and pushes are made by Hermes on behalf of the person whose worktree it runs in, using that worktree's git identity.

## Branches and merging

- `main` always builds and always runs. Never push broken code to `main`.
- Work on your own branch (one git worktree per branch when running agents in parallel).
- Merge into `main` at every sync point below, and sooner when a PRD task reaches its "Selesai bila" criteria.
- Before pushing: `git pull --rebase origin main`. No force push. No history rewriting.
- Commit message format: `type(scope): short summary`, for example `feat(vpn): parse DNS query`, `fix(api): read PORT from env`, `docs: update NOTES`.
- Conflicts in `docs/NOTES.md` are expected (append-only): keep both sides.

## Commit and push schedule (WIB)

Hackathon: Sat 21 Nov 10:00, code freeze Sun 22 Nov 10:00.

Commits and pushes are done by Hermes per worktree, following the rules below. Hermes commits real working changes only, never empty or filler commits.

Standing rules:
- Commit after every small working step, and at least every 30 minutes while working.
- Push at least every hour, and always before taking a break or sleeping.
- Each person pushes something every sync point. A long gap with no commits looks bad in the history.
- Before every commit: run `git status`, and refuse to commit `.env`, private keys, `local.properties`, or build output.

| Time | Sync point |
|---|---|
| Sat 10:00 | Start. Repo has `AGENTS.md`, `docs/PRD.md`, `docs/TEAM.md`, `.gitignore`. First commits in the first hour. |
| Sat 12:00 | Skeleton merged. API `/health` deployed on Railway (target). |
| Sat 15:00 | DNS filtering and overlay working on a real phone. Registry deployed on Amoy. |
| Sat 18:00 | Report flow end to end (tx hash real). Merge all branches. |
| Sat 21:00 | FileObserver and quarantine working. Merge all branches. |
| Sat 22:00-22:30 | Mandatory committee checkpoint. One representative goes. Push before going. |
| Sun 00:00 | Merge. Sleep rotation starts (at least two people awake at any time). |
| Sun 03:00 | Merge. Fix bugs only, no new features. |
| Sun 06:00 | Feature freeze. Slides done (PDF/PPT). Demo rehearsal with timer. Backup video recorded. |
| Sun 08:30 | Final merge to `main`. Repo set to PUBLIC. Verify live API URL and contract address in README. |
| Sun 09:30-10:00 | Final checkpoint window. Submit on compsphere12.id: public repo link, live deployment URL, slide deck. |
| Sun 10:00 | CODE FREEZE. No more pushes. |

## Repo visibility

The repo is private now. Judges need a PUBLIC GitHub link with the 24-hour commit history. Set it to public at the latest at Sun 08:30, after confirming nothing secret is in the history (no `.env`, no private key, no API key).

## Rules for agents

1. Read `AGENTS.md` and `docs/PRD.md` first. One task per session.
2. Do not change git identity or run any git write command (add, commit, pull, push, rebase, branch, reset). Hermes handles commits under the identity configured in this worktree.
3. When a task's "Selesai bila" criteria are met, stop and report the changed files, test output and a suggested commit message.
4. Never force push, never delete branches, never rewrite history (applies to Hermes too).
5. Never commit secrets. If a secret was committed by mistake, stop and report immediately so it can be rotated.
6. Append a short note to `docs/NOTES.md` after each task: what, why, alternatives.
7. Do not touch another person's area unless asked. If a change is needed there, write it in `docs/NOTES.md` under "Request".

## Fixed facts

- Repo: https://github.com/royaldinan/Bulwark
- Registry chain: Polygon Amoy, chain ID 80002
- API host: Railway (URL goes in README and in the Android config constant)
- Mandatory checkpoint: Sat 22:00-22:30. Final checkpoint window: Sun 09:30-10:00. Code freeze: Sun 10:00.
- Pitch: 10 minutes total (setup max 2 min, pitch and live demo 4-5 min, rest is Q&A).
