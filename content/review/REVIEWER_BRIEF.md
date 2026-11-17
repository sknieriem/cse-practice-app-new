# Reviewer brief: original CSE questions (one batch per reviewer)

Goal: ZERO errors in questions learners will practise. Work only with the files named here, don't edit
anything else, and don't use the web. `<batch>` is your batch name, e.g. `r3_verbal`.

## Step 1 — blind re-solve (BEFORE opening the batches file)
Read `content/review/blind/<batch>.json` (no keys). Answer every item yourself and write
`content/review/solved/<batch>.json`: a JSON array of
`{"id": <int>, "answer": "A|B|C|D", "confidence": "sure|unsure", "ambiguous": true|false, "note": "short"}`.

## Step 2 — review
Read `content/review/batches/<batch>.json` (full items) and `content/generated/SPEC.md`. For each item check:
- **The key:** correct, with exactly ONE defensible answer. Compare it with your blind answer and work out who is right.
- **Stem and options:** the stem is clear and complete, distractors are plausible common mistakes, no giveaways. Banned: "all/none of the above" and "Both/None/True/False" option sets. No near-duplicates.
- **Stem matches options:** if you change an option, also update any list of options inside the stem.
- **Style:** difficulty and style match the real CSE.
- **Teaching fields:** every item needs
  - `explanation`: 1-3 sentences, consistent with the key. General Information items cite the law article or section.
  - `steps`: a numbered chain, one step per line, for math, series, codes, logic, family and data items. Check existing steps against the key.
  - `hint`: a nudge that doesn't give the answer away.
  - `whyX` for each of the 3 WRONG letters: the specific mistake behind that option.
  - `explanationTl`, `hintTl`, and `stepsTl` if the item has steps: natural Taglish, the way Filipinos really talk. Keep math, grammar and legal terms in English.

  Fill in what's missing and fix what's wrong or weak.

Traps found in earlier rounds (look hard for these):
- **Numerical:** arithmetic slips in keys or explanations ("explanation computes 54, key says 48"). Lost "×" signs ("322 = 64"). Trivia posing as math ("seconds in a minute"); rewrite it into a real CSE-style problem on the same subtopic. Missing units. Duplicates.
- **Analytical:** odd-one-out sets with a second valid answer. Series keyed wrong. Syllogisms keyed Valid that are Invalid. Family items with no stated gender. Assumption items with no clean assumption.
- **Verbal:** grammar items where a second form is also standard ("proficient at/of", "group was/were"). Word-order items where an inverted sentence is valid, or a capital letter gives the answer away. Stray pasted fragments. Bare sentences with no question. Synonym sets hiding a second near-synonym.
- **General Information:** wrong counts from the Constitution (the Supreme Court is the Chief Justice plus 14 Associate Justices). Wrong hero titles ("Brains of the Katipunan" = Emilio Jacinto). False "facts" (scuba tanks hold compressed air, not oxygen).
  - Anything that changes over time (current officials, number of regions or islands, "largest/tallest/top producer", "as of <year>") must be rewritten to a stable fact on the same subtopic.
  - Stable references: the 1987 Constitution, RA 6713, RA 9003, RA 8749, RA 9275, the UDHR.
  - Never guess a legal fact: flag it.

## Step 3 — write `content/review/reviewed/<batch>.json`
A JSON array with one object per item (all of them):
`{"id": <int, unchanged>, "blind": "<step-1 answer>", "verdict": "...", "reason": "one line", "fields": {<only fields you set or change>}}`

| verdict | meaning |
|---|---|
| unchanged | question, options and key are fine; you only ADDED missing teaching fields |
| minor edit | key correct; you fixed stem or option wording (e.g. to remove a second answer), a wrong explanation or steps, or the difficulty |
| key fixed | the key was wrong, or the keyed "fact" is false; set correctAnswer/options and fix every field that depends on them |
| rewritten | stem/options substantially replaced; same subtopic, fully correct, exactly one answer |
| flagged | genuinely disputable or uncertain, no clean fix; put a proposed fix in fields and a human (Pearl) checks it |

Allowed field names:
- `text`, `passage`, `optionA` to `optionD`, `correctAnswer`
- `explanation`, `steps`, `hint`, `whyA` to `whyD`
- `explanationTl`, `stepsTl`, `hintTl`
- `difficulty` (1 = Subprofessional, 2 = Professional, 3 = hard), `timeSensitive`

`whyX` goes ONLY on the wrong letters under the final key.

Check that the JSON parses:
`C:\ProgramData\Miniconda3\envs\torch_env_new\python.exe -c "import json;print(len(json.load(open(r'<path>',encoding='utf-8'))))"`

Final reply (under 120 words): verdict counts, plus every wrong key or false fact you found, with its id and one line each.
