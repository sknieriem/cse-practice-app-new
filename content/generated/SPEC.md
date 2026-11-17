# Authoring spec — new CSE practice questions

Written at dev time by Claude Opus (Steve's Claude plan). Original questions only: never copy or closely paraphrase
published reviewers or past exam items.

## File format
Each batch is `content/generated/batches/<batch>.json`: a JSON array (UTF-8, no BOM) of items:

```json
{
  "uid": "<batch>-01",
  "category": "Verbal Ability | Numerical Ability | Analytical Ability | General Information",
  "subtopic": "<topic id from content/topics.json>",
  "level": "both | professional",
  "difficulty": 1,
  "passage": "only for reading items; omit otherwise",
  "text": "the question",
  "optionA": "...", "optionB": "...", "optionC": "...", "optionD": "...",
  "correctAnswer": "A|B|C|D",
  "explanation": "1-3 sentences: why the key is right",
  "steps": "numbered worked solution, one step per line (math/logic items); omit for pure recall",
  "hint": "a nudge that does not give the answer away",
  "whyA": "...", "whyB": "...", "whyC": "...", "whyD": "...",
  "explanationTl": "natural Taglish explanation (how Filipinos really talk; English terms kept for math/legal words)",
  "hintTl": "Taglish hint"
}
```
- `whyX` only for the three WRONG options (leave out the key's letter): one short, specific line each.
- `difficulty`: 1 = Subprofessional level, 2 = Professional standard, 3 = Professional hard. About 40% at 1.
- `level`: "both" for difficulty 1, "professional" for 2-3.
- Spread correct answers roughly evenly over A-D.

## Quality rules
- Exactly one defensible answer. Distractors plausible (common mistakes), never silly, never "trick" wording.
- Real CSE style: short stems, 4 options, Philippine context (pesos, barangays, local names) where natural.
- Math: re-compute every answer; numbers that come out clean.
- General Information: only stable facts you are certain of (1987 Constitution text, RA 6713, RA 9003, RA 8749,
  RA 9275, UDHR, etc.). No current office-holders, no amounts or dates that change, no contested history.
  Cite the article/section in the explanation.
- Filipino items: correct, standard Filipino (KWF usage).
- No "All of the above" / "None of the above".
