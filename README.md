# CSE Practice App

An Android study companion for the Philippine Civil Service Examination (CSE).
Pick categories, take timed practice sessions, review explanations, and track
your progress over time.

## Features

- **Four categories**: Numerical Ability, Verbal Ability, Analytical Ability,
  General Information
- **Configurable session length**: 10, 25, 50, or 100 questions
- **Per-question review** with explanations after submission
- **Practice Weak Areas** — auto-selects categories where your average is below 70%
- **Progress history** with score chart and per-category averages
- **Dark mode** and **Blue / Green / Default** color schemes
  (Material You dynamic colors on Android 12+)

## Tech stack

- Kotlin + Jetpack Compose + Material 3
- Room (questions + sessions) with KSP
- Preferences DataStore (settings + question version)
- MPAndroidChart for the line chart (via JitPack)
- Gson for the bundled questions JSON
- Min SDK 26 (Android 8.0), target / compile SDK 35

## Module layout

```
app/src/main/java/com/example/csepractice/
├── MainActivity.kt              # Practice screen (selector, in-progress, finished, review)
├── SplashActivity.kt            # Branded launch screen
├── SettingsActivity.kt          # Theme + reset data
├── ChartActivity.kt             # MPAndroidChart line chart
├── data/                        # Room entities + DAO + database
├── repository/QuestionRepository.kt   # DB facade + first-launch seeding
├── viewmodel/PracticeViewModel.kt     # UI state machine
└── ui/
    ├── components/HistoryPanel.kt     # Reused on the menu and finished screens
    └── theme/                         # Theme, colors, PreferencesDataStore
```

## Build & run

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Requires JDK 17.

## Questions data

Questions are bundled at `app/src/main/assets/questions.json` and seeded into
Room on first launch (and re-seeded whenever `QUESTIONS_VERSION` in
`QuestionRepository` is bumped). Each entry has the shape:

```json
{
  "text": "Which of the following is a synonym for 'ephemeral'?",
  "optionA": "Lasting",
  "optionB": "Fleeting",
  "optionC": "Permanent",
  "optionD": "Recurring",
  "correctAnswer": "B",
  "category": "Verbal Ability",
  "explanation": "Ephemeral means short-lived; fleeting is its closest synonym."
}
```

## Known limitations / follow-ups

- Per-category averages are computed by string-matching the session's
  `categories` column. For sessions that mix categories, the overall score is
  attributed to each category touched — fine as a rough signal but not exact.
  A `SessionCategoryResult` table is the right fix.
- R8/ProGuard isn't enabled in release builds yet (rules for Room / Gson /
  Compose / MPAndroidChart need to be added first).
- MPAndroidChart is unmaintained; consider migrating to a Compose-native chart
  library (Vico, KoalaPlot) to remove the AndroidView interop.
- `applicationId` is still `com.example.csepractice` and must be changed
  before a Play Store release.

## License

TBD.
