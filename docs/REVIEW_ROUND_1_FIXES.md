# Review round 1: what was fixed (branch `fix/review-round-1`)

This responds to the first outside review of `OVERVIEW_FOR_REVIEW.md`. Each item names the file and the change, so it can be checked in the diff.

## Play Store blockers
| Finding | Change |
|---|---|
| `targetSdk = 35` rejected from 31 Aug 2026 | `app/build.gradle.kts`: `targetSdk = 36` |
| Destructive Room migration would wipe paying users | `AppDatabase.kt`: `exportSchema = true` (schemas go to `app/schemas/`, via a ksp arg), a `MIGRATIONS` list, and `fallbackToDestructiveMigration` **only** when `BuildConfig.DB_RESET_ON_SCHEMA_CHANGE` is set (debug and beta builds). Release builds never reset. |
| Empty backup rules | `backup_rules.xml` and `data_extraction_rules.xml`: database, files (DataStore and the inspiration photo) and shared prefs are included in cloud backup and device transfer; cache (share cards) is not |
| `applicationId` placeholder, Play Billing, Data Safety form | **Not changed.** These are owner decisions and later work |

## Logic
| Finding | Change |
|---|---|
| Readiness: one easy subject showed a passing score | `Planner.estimate`: an untried subject counts at `min(50, the user's own level)`, and the overall score is capped at 79 until all 4 subjects are tried. `Encourage.readinessLine` names the untried subjects instead of saying "You're there". |
| `finish()` not atomic | `QuestionRepository.finish`: all Room writes (attempts, review cards, session, category results) run in one `withTransaction`. The saved quiz is cleared right after the commit, then XP is saved, so a crash can lose a session's XP but never double it. |
| Same-day retry jumped the review schedule; unanswered exam items ignored | A card moves up a box only when `dueDay <= today`. In exam mode an unanswered question counts as wrong (box 1). |
| A backward clock wiped the streak | `Game.reward`: the streak only changes when `today > lastActiveDay`; `lastActiveDay` never moves backward; `daysAway` is never negative |
| Badge hour gap | Night Owl is now 22:00–03:59 and Early Bird 04:00–06:59, with the badge text updated to match |
| Same topic praised twice | `Encourage.praise`: the "all right" line skips topics already praised for improvement |
| `AnimatedContent` drew the new overlay in both slots | `MainActivity`: `targetState = overlay`, `contentKey = { it::class }`, and the lambda draws its own `o` |

## Android and UX
| Finding | Change |
|---|---|
| Share-sheet URI grant | `ShareCards.launch`: `clipData = ClipData.newRawUri("", uri)` |
| PNG encoding on the main thread | `ShareCards.prepare` (render, save, `recycle()`) runs on `Dispatchers.IO` from `AppViewModel.shareNow`; `launch` runs on main |
| Sideways portrait photos | `setInspirationPhoto` reads EXIF orientation (`android.media.ExifInterface`, API 24+) and rotates before saving |
| `topics()` asset read on main | `topicProgress` flow uses `flowOn(Dispatchers.IO)` |
| `tutor_server.py` hardening | **Not changed.** It is switched off; this is Phase 2 work |

## Question bank (1,380 → 1,378 questions, `QUESTIONS_VERSION` 8 → 9)
- **Letter references, which break once options are shuffled.** The reviewer's 2 are fixed, plus 4 more found by scanning the whole bank: `238952314` (TL), `310914045` (steps, stepsTl, explanationTl), `1503535450` (steps, stepsTl), and `1762188624` (whyD). They now quote the sentence text instead of a letter. The fixes live in `content/review/overrides.json` (originals) and `content/generated/batches/pilot_verbal.json` (generated items).
- **Duplicates:** `465554006` (copy of `36851876`) and `1520624502` (copy of `979404327`) are held out through `content/review/excluded.json`.
- **"No error." pinned last:** `Quiz.optionOrder` keeps it in the last position, like "None of the above".

## Tests
New file: `app/src/test/.../ReviewRound1Test.kt`. It covers readiness with one subject, three subjects and a weak user; a backward clock; badge hours; and praising a topic only once. The full suite runs with `./gradlew :app:testDebugUnitTest`.

## Not done (needs a decision or later work)
- Final `applicationId`; Play Billing and the matching Data Safety answers; the first real `Migration` once v1 has shipped; tutor server security (Phase 2).
- The content build still reports 4 "rule disagrees with answer key" items for human review. This review didn't touch them.
