# Question / answer / explanation screens: layout findings (review pass, NOT built)

Checked against the 360dp screenshots at 1.0x, 1.3x and 2.0x system text (docs/screenshots/03, 04, 40_*, 41_*)
and against the bank's text lengths (1,315 items: stem median 46 chars, max 464; 19 reading passages, median
542 chars, max 806; option max 122 chars, 21 items with an option over 60 chars; steps max 404 chars).

| # | Problem | Where | Proposed fix |
|---|---|---|---|
| 1 | The "Why ___ is wrong" label pastes in the chosen option cut at 40 characters, so a long option reads "Why The President may not be removed fr is wrong". | QuizScreen.kt `ExplanationBody` (`q.options()[chosen].take(40)`) | Label it by letter: "Why B is wrong". If the text is kept, cut it on a word boundary and add "…". |
| 2 | Taglish is missing on almost the whole original bank (it existed on only 153 of 1,315 items before this pass). Tapping "Taglish" mostly shows "coming soon — showing English". | Feedback card language toggle | Content fix, not a layout fix: run this review on the rest of the bank. Until then, grey out the toggle (or hide it) on questions with no Taglish, rather than offering it and falling back. |
| 3 | At 2.0x text the feedback card fills the screen, and the question and the user's chosen option scroll away. The learner reads "Explanation" without seeing what they picked or what was right. | QuizScreen feedback card | Put one compact line at the top of the card: "You chose B · Answer: D — 6 years". |
| 4 | Reading passages open above the stem. An 800-character passage at 2.0x is about 3 screens before the question appears, and the options sit below that again. | `PassageCard` (open by default, above `q.text`) | Show the question stem first and the passage under it, or pin a small "Read passage" chip that jumps back. Keep the tap-to-hide behaviour. |
| 5 | Steps render as one text block with no indent. On 400-character solutions at large text, wrapped lines line up with the step numbers, so the steps blur together. | `ExplanationBody` "Solution" | Split on newlines and render each step as its own row: number column plus a hanging indent, with 4dp between steps. |
| 6 | The "Arrange the sentences" items put 4 numbered sentences inside the stem (up to 464 characters) in the same bold titleLarge style. A long bold block is hard to read at 2.0x. | Stem `Text(q.text, titleLarge SemiBold)` | Above about 200 characters, or when the stem has line breaks, use bodyLarge regular and set the numbered lines as a list. |
| 7 | The topic tip uses onSurfaceVariant grey bodyMedium on the green or red card. Contrast is weaker than the explanation, and at 2.0x it's a long grey block. | `ExplanationBody` tip | Keep the colour but put the tip behind a "Topic tip ›" expander after the first few sessions. |

No word broke mid-word on these screens in the 1.3x and 2.0x tests (NarrowFont130Test / NarrowFont200Test).
