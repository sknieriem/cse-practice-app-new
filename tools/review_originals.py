"""
Review pass over the ORIGINAL question bank (source="original"). Run with the PyCharm green ▶.

  STEP = "pick"  -> picks N_PICK originals (weighted to topics most likely wrong or dated) from the built
                    app/src/main/assets/questions.json and writes, per subject:
                      content/review/batches/<batch>.json  full items (key, explanation, ...)
                      content/review/blind/<batch>.json    same items WITHOUT key/explanation (for a blind re-solve)
  STEP = "apply" -> reads content/review/reviewed/<batch>.json (reviewer output) and writes:
                      content/review/overrides.json        {id: {field: new value}} merged by build_content.py
                      content/review/excluded.json         ids flagged for Pearl -> kept OUT of the app
                      content/review/review_log.md / .csv  one line per question: verdict + why
                      content/flagged_for_pearl_review.md / .csv
Question ids never change (build_content.py computes them from the original source text before overrides),
so a user's progress on an edited question is kept. Pearl clears a flagged item by adding its id (as a string)
to content/generated/pearl_cleared.json; its proposed fix in overrides.json then goes live.
"""
import csv
import json
import pathlib
import random
from collections import Counter

STEP = "apply"   # "pick" or "apply"
N_PICK = 100
SEED = 20261117

ROOT = pathlib.Path(__file__).resolve().parents[1]
REV = ROOT / "content" / "review"
BATCHES, BLIND, REVIEWED = REV / "batches", REV / "blind", REV / "reviewed"
ASSET = ROOT / "app" / "src" / "main" / "assets" / "questions.json"

# subject -> (how many, subtopic weights). Higher weight = more likely wrong or dated.
PLAN = {
    "geninfo": ("General Information", 40, {"gi_general": 3, "gi_ph": 3, "gi_constitution": 3, "gi_ethics": 5,
                                            "gi_rights": 5, "gi_environment": 5}),
    "numerical": ("Numerical Ability", 25, {"num_word": 3, "num_geometry": 3, "num_stats": 4, "num_work": 2,
                                            "num_rates": 2, "num_series": 2, "num_percent": 1, "num_algebra": 1,
                                            "num_fractions": 1}),
    "analytical": ("Analytical Ability", 20, {"ana_oddone": 3, "ana_assumption": 3, "ana_logic": 3, "ana_data": 3,
                                              "ana_family": 2, "ana_codes": 2, "ana_series": 2, "ana_analogy": 1}),
    "verbal": ("Verbal Ability", 15, {"verb_reading": 4, "verb_ordering": 3, "verb_grammar": 3, "verb_antonym": 2,
                                      "verb_analogy": 2, "verb_spelling": 2, "verb_vocab": 1}),
}
EDITABLE = ["text", "passage", "optionA", "optionB", "optionC", "optionD", "correctAnswer", "explanation", "steps",
            "hint", "whyA", "whyB", "whyC", "whyD", "explanationTl", "stepsTl", "hintTl", "difficulty",
            "timeSensitive"]
VERDICTS = ["unchanged", "minor edit", "key fixed", "rewritten", "flagged"]


def load(p):
    return json.loads(p.read_text(encoding="utf-8-sig"))


def pick():
    rng = random.Random(SEED)
    allq = [q for q in load(ASSET) if q.get("source") == "original"]
    for d in (BATCHES, BLIND):
        d.mkdir(parents=True, exist_ok=True)
    for batch, (cat, n, weights) in PLAN.items():
        pool = [q for q in allq if q["category"] == cat]
        chosen = [q for q in pool if q.get("timeSensitive")]  # dated items always reviewed
        rest = [q for q in pool if q not in chosen]
        while len(chosen) < n and rest:
            w = [weights.get(q["subtopic"], 1) * (1.5 if not q.get("steps") else 1) for q in rest]
            q = rng.choices(rest, weights=w)[0]
            rest.remove(q)
            chosen.append(q)
        chosen.sort(key=lambda q: (q["subtopic"], q["id"]))
        (BATCHES / f"r_{batch}.json").write_text(json.dumps(chosen, ensure_ascii=False, indent=1), encoding="utf-8")
        blind = [{k: q[k] for k in ("id", "category", "subtopic", "passage", "text", "optionA", "optionB", "optionC",
                                    "optionD") if q.get(k)} for q in chosen]
        (BLIND / f"r_{batch}.json").write_text(json.dumps(blind, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"r_{batch}: {len(chosen)}  {dict(Counter(q['subtopic'] for q in chosen))}")


def apply():
    cleared = set(map(str, load(ROOT / "content" / "generated" / "pearl_cleared.json"))) \
        if (ROOT / "content" / "generated" / "pearl_cleared.json").exists() else set()
    overrides, excluded, log = {}, [], []
    # second blind pass over every item whose stem/options/key the reviewer changed (solved/recheck.json)
    recheck = {s["id"]: s for s in load(REV / "solved" / "recheck.json")} \
        if (REV / "solved" / "recheck.json").exists() else {}
    for p in sorted(BATCHES.glob("r_*.json")):
        orig = {q["id"]: q for q in load(p)}
        rev = {r["id"]: r for r in load(REVIEWED / p.name)} if (REVIEWED / p.name).exists() else {}
        for qid, q in orig.items():
            r = rev.get(qid)
            if r is None:
                log.append((q, "flagged", "not reviewed", "", {}))
                excluded.append(qid)
                continue
            verdict = r["verdict"]
            assert verdict in VERDICTS, (qid, verdict)
            changes = {k: v for k, v in (r.get("fields") or {}).items() if k in EDITABLE and v != q.get(k)}
            if "correctAnswer" in changes:
                assert changes["correctAnswer"] in ("A", "B", "C", "D"), qid
            if changes:
                overrides[str(qid)] = changes
            if set(changes) & {"text", "passage", "optionA", "optionB", "optionC", "optionD", "correctAnswer"}:
                s = recheck.get(qid)
                key = changes.get("correctAnswer", q["correctAnswer"])
                why = ("edited item not re-checked" if s is None else
                       f"re-check chose {s['answer']}, new key {key}" if s["answer"] != key else
                       "re-check: ambiguous" if s.get("ambiguous") else
                       "re-check: not sure" if s.get("confidence") != "sure" else "")
                if why and verdict != "flagged":
                    note = (s or {}).get("note", "")
                    r = dict(r, reason=f"{r.get('reason', '')} [was {verdict}; {why}: {note}]")
                    verdict = "flagged"
            if verdict == "flagged" and str(qid) not in cleared:
                excluded.append(qid)
            log.append((q, verdict, r.get("reason", ""), r.get("blind", ""), changes))

    REV.mkdir(parents=True, exist_ok=True)
    (REV / "overrides.json").write_text(json.dumps(overrides, ensure_ascii=False, indent=1), encoding="utf-8")
    (REV / "excluded.json").write_text(json.dumps(excluded, indent=1), encoding="utf-8")

    with open(REV / "review_log.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["id", "category", "subtopic", "verdict", "blind_answer", "old_key", "new_key", "why",
                    "question", "fields_changed"])
        for q, v, why, blind, ch in log:
            w.writerow([q["id"], q["category"], q["subtopic"], v, blind, q["correctAnswer"],
                        ch.get("correctAnswer", q["correctAnswer"]), why, q["text"], ",".join(ch)])
    md = ["# Review of 100 original questions", "", "| verdict | count |", "|---|---|"]
    md += [f"| {v} | {sum(1 for x in log if x[1] == v)} |" for v in VERDICTS]
    md += ["", "| id | subject | verdict | why |", "|---|---|---|---|"]
    md += [f"| {q['id']} | {q['category']} | {v} | {why.replace('|', '/')} |" for q, v, why, _, _ in
           sorted(log, key=lambda x: VERDICTS.index(x[1]), reverse=True)]
    (REV / "review_log.md").write_text("\n".join(md), encoding="utf-8")

    fl = [(q, why, ch) for q, v, why, _, ch in log if v == "flagged"]
    md = ["# Original questions flagged for Pearl", "",
          "A blind re-solve + review found these disputed. They are NOT in the app until cleared: add the id",
          "(as a string) to `content/generated/pearl_cleared.json`. The proposed fix (if any) then goes live.", ""]
    for q, why, ch in fl:
        md += [f"## {q['id']} · {q['category']} · {q['subtopic']}", ""]
        if q.get("passage"):
            md += [f"> {q['passage']}", ""]
        md += [q["text"], ""] + [f"- {L}. {q['option' + L]}" + ("  ✅ key" if L == q["correctAnswer"] else "")
                                  for L in "ABCD"]
        md += ["", f"**Why flagged:** {why}", ""]
        if ch:
            md += ["**Proposed fix:**", ""] + [f"- {k}: {v}" for k, v in ch.items()] + [""]
    (ROOT / "content" / "flagged_for_pearl_review.md").write_text("\n".join(md), encoding="utf-8")
    with open(ROOT / "content" / "flagged_for_pearl_review.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["id", "category", "subtopic", "question", "A", "B", "C", "D", "key", "why_flagged",
                    "proposed_fix"])
        for q, why, ch in fl:
            w.writerow([q["id"], q["category"], q["subtopic"], q["text"], q["optionA"], q["optionB"], q["optionC"],
                        q["optionD"], q["correctAnswer"], why, json.dumps(ch, ensure_ascii=False)])

    print(Counter(v for _, v, _, _, _ in log))
    print("by subject:", {c: dict(Counter(v for q, v, *_ in log if q["category"] == c))
                          for c in sorted({q["category"] for q, *_ in log})})
    print(f"overrides: {len(overrides)}  excluded: {len(excluded)}")


if __name__ == "__main__":
    pick() if STEP == "pick" else apply()
