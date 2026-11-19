# CSE Practice: overview for review

An offline Android reviewer app for the Philippine Civil Service Exam (CSE-PPT, passing rating 80). It is a paid app (about ₱100–200 a month), and the target is a Google Play launch in early January 2027, before the 14 March 2027 exam.

## Where it lives
- **Local:** `C:\Users\steve\PycharmProjects\cse-practice-app-new`
- **GitHub:** https://github.com/sknieriem/cse-practice-app-new (`main` is current; PR #3 merged the full content review)
- **Version:** 3.0-beta (versionCode 4). The tester APK is `dist/CSE-Practice-3.0-beta.apk`.

## Design choices
- **Offline first.** Many users have weak or costly data, so the phone makes no AI calls and has no INTERNET permission. All AI work happens once, on the content side, before release.
- **Personalized with plain code:** spaced repetition (a missed question returns after 1, 3, 7 and 14 days), weak-topic targeting, and an exam-day countdown. "Practice now" picks 15 questions: about 40% missed questions that are due again, 40% from weak topics, and 20% new.
- **Encouragement that is always earned.** Every message comes from the user's own results. There is no fake praise, no guilt and never a bottom rank. The readiness meter shows the estimated score against the passing 80.
- **Gamified:** XP, levels, streaks with streak freezes, 18 badges, a daily challenge, mock exams (40 or 100 items, timed), and "Pasado" share cards drawn on the phone.
- **English first.** The exam is in English and many users speak Bisaya, not Tagalog. Every question also has Taglish text that the user can switch to.
- **Correctness above all.** No question ships until it passes a check. New questions go through a blind solve (a separate solver answers without the key). Any disagreement goes to Pearl, the content expert, for review.

## Stack
Kotlin, Jetpack Compose (Material 3), Room, DataStore, Vico (charts), Gson. minSdk 26, targetSdk 35. Tests use JUnit and Robolectric, with Roborazzi screenshots. About 6,700 lines of Kotlin.

## Files
| Path | What it is |
|---|---|
| `app/src/main/java/com/pjcinc/csepractice/domain/` | `Quiz.kt` (question picking, scoring), `Planner.kt` (spaced repetition, weak topics), `Game.kt` (XP, badges, streaks), `Encourage.kt` (all encouragement messages); `Tutor.kt` and `CommunitySync.kt` are stubs that are switched off |
| `.../data/` | Room database (`Attempt`, `Progress`, `PracticeSession`), `Prefs` (DataStore) |
| `.../repository/QuestionRepository.kt` | Loads the question bank from assets (`QUESTIONS_VERSION`) |
| `.../viewmodel/AppViewModel.kt` | Single app ViewModel |
| `.../ui/screens/` | Home, Practice, Quiz, Result, Progress, Settings, Onboarding, Celebrate |
| `.../ui/share/` | Share-card drawing and preview |
| `app/src/test/` | 8 test files: app flow, encouragement, game and planner, moments, dark mode, narrow screens with large fonts, share cards |
| `app/src/main/assets/questions.json` | The shipped bank: **1,380 questions** (Numerical 557, General Information 318, Analytical 260, Verbal 245), each with an explanation, a hint, notes on why each wrong choice is wrong, and English and Taglish text |
| `content/` | Content sources: `source_questions.json` (original bank), `authored_items.json`, `topics.json` (31 topics), `generated/` (new batches, blind solves, approved list), `review/` (review rounds), `flagged_for_pearl*` |
| `tools/build_content.py` | Builds the shipped assets: drops broken items, applies fixes, tags topics, writes rule-based worked solutions and checks them against the key |
| `tools/generate_ai_content.py` | Writes explanations with Claude, once, at development time. It defaults to a mock mode, and items where Claude disagrees with the key are held back |
| `tools/question_gate.py` | Blind-solve quality check for new questions |
| `tools/review_originals.py`, `tools/dup_scan.py` | Review of the original questions and a duplicate finder |
| `tools/tutor_server.py` | Stub for a later online "Ask the tutor" feature |
| `docs/PHASE2_COMMUNITY.md` | Later plan: opt-in leagues and a "most improved" board, following the PH Data Privacy Act |

## Status
- All 1,162 original questions were reviewed. 21 wrong answer keys were fixed, and 65 checked current-events items were added.
- The beta APK is built. Next: Pearl's review, then the first testers, then the Play Store listing.
- `tools/question_gate.py` has one uncommitted change, which appears to be line endings only.

## What a reviewer should look at
1. **Wrong answers in the bank.** Any question with a wrong key or two defensible answers is the worst defect this app can have.
2. **The logic in `Planner.kt` and `Quiz.kt`:** question picking, spaced repetition, and the readiness estimate, which must not overstate how ready a user is.
3. **`Encourage.kt`:** every message must be true for the data it reads.
4. **Offline guarantees:** no network calls, and data survives Android killing the app.
5. **Anything that would block a Google Play release:** permissions, data-safety form, billing (not built yet).
