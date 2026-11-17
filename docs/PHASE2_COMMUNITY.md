# Phase 2: opt-in community (design, not built)

Goal: help people raise themselves up. The CSE is pass/fail at 80, not a competition, so the app compares each person with **their own past**. Other users appear only in ways that encourage.

## What is already in the app (offline, phase 1)

- **Readiness meter**: an estimated % correct (`Planner.estimate`), shown against the 80 passing rating, plus "your weakest area is …". It appears after 20 answers.
- **"+N points since last week"**: from `readiness_snapshots`, one row per day.
- **Real exam result**: asked after exam day (passed / not this time / still waiting) and stored in `exam_outcomes`.
- Both tables carry `uuid`, `updatedAt` or `reportedAt`, and a `synced` flag, so phase 2 can upload them without a migration.

## Phase 2 features

| Feature | Rule |
|---|---|
| Opt-in | Off by default. A separate consent screen in plain Taglish explains what is sent. Consent is logged with the policy version (PH Data Privacy Act, RA 10173). "Delete my data" is always available. |
| Identity | Nickname only. No contacts, no location, no phone number. |
| Data sent | Daily readiness snapshots and the self-reported exam outcome. Never raw answers. |
| Leagues | About 20 people at a similar readiness level. The ranking is **most improved this week**, never raw score. |
| Rank display | Never show anyone a bottom rank. Show "top 30%" only when it is true; otherwise show the user's own weekly gain. |
| Cohort hope line | "People who started at your level and practised ~20 min/day reached 80% in ~X weeks." Shown **only** once the server has enough real users in that cohort (minimum N to be decided). X is never invented. |
| Calibration | "Users with your score passed X%." Built from reported real results. Hidden until there are enough reports per score band. |

## Server (Steve's, same box as the tutor stub)

- `POST /sync`: unsynced snapshots and outcomes for a device id plus the nickname.
- `GET /league`: returns `TopShare(percent)` or `OwnProgress(points)`, never a list with bottom positions.
- `GET /calibration?score=68`: returns a pass rate or `null`.
- No AI calls are needed for any of this. The cost is a small VPS.

Interface: `app/.../domain/CommunitySync.kt`. This build uses `CommunityOff`, which never sends anything.
