"""
Review pass over the ORIGINAL question bank (source="original"), one round of BATCH_SIZE questions at a time.
Run with the PyCharm green ▶. Each round is three steps, chosen by STEP:

  STEP = "pick"    -> next round r<N>: picks BATCH_SIZE originals NOT reviewed in any earlier round (weighted to the
                      subjects/topics most likely wrong) from app/src/main/assets/questions.json and writes per subject:
                        content/review/batches/r<N>_<subject>.json  full items (key, explanation, ...)
                        content/review/blind/r<N>_<subject>.json    same items WITHOUT key/explanation (blind re-solve)
                      Reviewers (Opus) then write solved/r<N>_<subject>.json and reviewed/r<N>_<subject>.json.
  STEP = "recheck" -> writes content/review/blind/recheck_r<N>.json: every item of the latest round whose stem,
                      options or key the reviewer changed, in its NEW form, for a second blind solver
                      (who writes solved/recheck_r<N>.json).
  STEP = "apply"   -> reads ALL rounds and writes:
                        content/review/overrides.json        {id: {field: new value}} merged by build_content.py
                        content/review/excluded.json         ids flagged for Pearl -> kept OUT of the app
                        content/review/review_log.md / .csv  one line per reviewed question (all rounds)
                        content/flagged_for_pearl_review.md / .csv
Every step prints how many originals are still unreviewed.
Question ids never change (build_content.py computes them from the original source text before overrides),
so a user's progress on an edited question is kept. Pearl clears a flagged item by adding its id (as a string)
to content/generated/pearl_cleared.json; its proposed fix in overrides.json then goes live.
"""
import csv
import json
import pathlib
import random
import re
from collections import Counter

STEP = "apply"      # "pick", "recheck" or "apply"
BATCH_SIZE = 100    # questions per round
SEED = 20261117

ROOT = pathlib.Path(__file__).resolve().parents[1]
REV = ROOT / "content" / "review"
BATCHES, BLIND, SOLVED, REVIEWED = REV / "batches", REV / "blind", REV / "solved", REV / "reviewed"
ASSET = ROOT / "app" / "src" / "main" / "assets" / "questions.json"

# subject -> (category, share weight per remaining question, subtopic weights inside the subject).
# Round 1: Analytical had the most wrong/ambiguous keys, Numerical had none (but trivia items).
PLAN = {
    "geninfo": ("General Information", 1.5, {"gi_ethics": 5, "gi_rights": 5, "gi_environment": 5,
                                             "gi_constitution": 3, "gi_ph": 3, "gi_general": 2}),
    "numerical": ("Numerical Ability", 0.5, {"num_word": 3, "num_geometry": 3, "num_stats": 4, "num_work": 2,
                                             "num_rates": 2, "num_series": 2}),
    "analytical": ("Analytical Ability", 2.0, {"ana_oddone": 3, "ana_assumption": 3, "ana_logic": 3, "ana_data": 3,
                                               "ana_family": 2, "ana_codes": 2, "ana_series": 2}),
    "verbal": ("Verbal Ability", 1.0, {"verb_reading": 4, "verb_ordering": 3, "verb_grammar": 3, "verb_antonym": 2,
                                       "verb_analogy": 2, "verb_spelling": 2}),
}
CORE = ["text", "passage", "optionA", "optionB", "optionC", "optionD", "correctAnswer"]
EDITABLE = CORE + ["explanation", "steps", "hint", "whyA", "whyB", "whyC", "whyD", "explanationTl", "stepsTl",
                   "hintTl", "difficulty", "timeSensitive"]
VERDICTS = ["unchanged", "minor edit", "key fixed", "rewritten", "flagged"]


def load(p):
    return json.loads(p.read_text(encoding="utf-8-sig"))


def rounds():
    return sorted({int(m.group(1)) for p in BATCHES.glob("r*_*.json") if (m := re.match(r"r(\d+)_", p.name))})


def reviewed_ids():
    return {q["id"] for p in BATCHES.glob("r*_*.json") for q in load(p)}


def originals():
    return [q for q in load(ASSET) if q.get("source") == "original"]


def remaining_report():
    done = reviewed_ids()
    allq = originals()
    left = Counter(q["category"] for q in allq if q["id"] not in done)
    print(f"originals reviewed: {sum(q['id'] in done for q in allq)} / {len(allq)}   "
          f"still unreviewed: {sum(left.values())}  {dict(left)}")


def pick():
    done = reviewed_ids()
    rnd = (rounds() or [0])[-1] + 1
    rng = random.Random(SEED + rnd)
    pools = {b: [q for q in originals() if q["category"] == cat and q["id"] not in done]
             for b, (cat, _, _) in PLAN.items()}
    # share of the round per subject = remaining count x subject weight
    w = {b: len(pools[b]) * PLAN[b][1] for b in PLAN}
    tot = sum(w.values()) or 1
    n = {b: min(len(pools[b]), round(BATCH_SIZE * w[b] / tot)) for b in PLAN}
    while sum(n.values()) < min(BATCH_SIZE, sum(map(len, pools.values()))):   # rounding / small pools
        b = max(PLAN, key=lambda b: len(pools[b]) - n[b])
        n[b] += 1
    for d in (BATCHES, BLIND):
        d.mkdir(parents=True, exist_ok=True)
    for b, (cat, _, weights) in PLAN.items():
        pool, chosen = list(pools[b]), [q for q in pools[b] if q.get("timeSensitive")][:n[b]]
        pool = [q for q in pool if q not in chosen]
        while len(chosen) < n[b] and pool:
            ws = [weights.get(q["subtopic"], 1) * (1.5 if not q.get("steps") else 1) for q in pool]
            q = rng.choices(pool, weights=ws)[0]
            pool.remove(q)
            chosen.append(q)
        if not chosen:
            continue
        chosen.sort(key=lambda q: (q["subtopic"], q["id"]))
        name = f"r{rnd}_{b}.json"
        (BATCHES / name).write_text(json.dumps(chosen, ensure_ascii=False, indent=1), encoding="utf-8")
        blind = [{k: q[k] for k in ["id", "category", "subtopic"] + CORE[:-1] if q.get(k)} for q in chosen]
        (BLIND / name).write_text(json.dumps(blind, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"{name}: {len(chosen)}  {dict(Counter(q['subtopic'] for q in chosen))}")
    remaining_report()


def merged(q, r):
    """The reviewer's changes (only real changes to editable fields) and the item after them."""
    changes = {k: v for k, v in (r.get("fields") or {}).items() if k in EDITABLE and v != q.get(k)}
    return changes, {**q, **changes}


def recheck():
    rnd = rounds()[-1]
    out = []
    for p in sorted(BATCHES.glob(f"r{rnd}_*.json")):
        rev = {r["id"]: r for r in load(REVIEWED / p.name)}
        for q in load(p):
            changes, new = merged(q, rev[q["id"]])
            if set(changes) & set(CORE):
                out.append({k: new[k] for k in ["id", "category"] + CORE[:-1] if new.get(k)})
    (BLIND / f"recheck_r{rnd}.json").write_text(json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    print(f"recheck_r{rnd}.json: {len(out)} changed items for the second blind pass")


def apply():
    cleared = set(map(str, load(ROOT / "content" / "generated" / "pearl_cleared.json"))) \
        if (ROOT / "content" / "generated" / "pearl_cleared.json").exists() else set()
    overrides, excluded, log = {}, [], []
    recheck_ans = {s["id"]: s for p in SOLVED.glob("recheck_r*.json") for s in load(p)}
    for p in sorted(BATCHES.glob("r*_*.json")):
        rnd = p.name.split("_")[0]
        rev = {r["id"]: r for r in load(REVIEWED / p.name)} if (REVIEWED / p.name).exists() else {}
        for q in load(p):
            qid, r = q["id"], rev.get(q["id"])
            if r is None:
                log.append((rnd, q, "flagged", "not reviewed", "", {}))
                excluded.append(qid)
                continue
            verdict, reason = r["verdict"], r.get("reason", "")
            assert verdict in VERDICTS, (qid, verdict)
            changes, new = merged(q, r)
            assert new["correctAnswer"] in ("A", "B", "C", "D"), qid
            if changes:
                overrides[str(qid)] = changes
            if set(changes) & set(CORE):   # edited items must pass the second blind solve too
                s = recheck_ans.get(qid)
                why = ("edited item not re-checked" if s is None else
                       f"re-check chose {s['answer']}, new key {new['correctAnswer']}" if s["answer"] != new["correctAnswer"] else
                       "re-check: ambiguous" if s.get("ambiguous") else
                       "re-check: not sure" if s.get("confidence") != "sure" else "")
                if why and verdict != "flagged":
                    reason = f"{reason} [was {verdict}; {why}: {(s or {}).get('note', '')}]"
                    verdict = "flagged"
            if verdict == "flagged" and str(qid) not in cleared:
                excluded.append(qid)
            log.append((rnd, q, verdict, reason, r.get("blind", ""), changes))

    # a rewrite must not duplicate another question in the bank (same stem, same option set)
    sig = lambda q: (q["text"].strip().lower(), frozenset(q["option" + L].strip().lower() for L in "ABCD"))
    bank = {}
    for q in load(ASSET):
        bank.setdefault(sig({**q, **overrides.get(str(q["id"]), {})}), []).append(q["id"])
    for ids in bank.values():
        if len(ids) > 1 and any(str(i) in overrides for i in ids):
            print(f"  ⚠ DUPLICATE after review: {ids} — rewrite one of them")

    REV.mkdir(parents=True, exist_ok=True)
    (REV / "overrides.json").write_text(json.dumps(overrides, ensure_ascii=False, indent=1), encoding="utf-8")
    (REV / "excluded.json").write_text(json.dumps(excluded, indent=1), encoding="utf-8")

    with open(REV / "review_log.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["round", "id", "category", "subtopic", "verdict", "blind_answer", "old_key", "new_key", "why",
                    "question", "fields_changed"])
        for rnd, q, v, why, blind, ch in log:
            w.writerow([rnd, q["id"], q["category"], q["subtopic"], v, blind, q["correctAnswer"],
                        ch.get("correctAnswer", q["correctAnswer"]), why, q["text"], ",".join(ch)])
    md = [f"# Review of original questions ({len(log)} reviewed)", "",
          "| round | " + " | ".join(VERDICTS) + " |", "|---|" + "---|" * len(VERDICTS)]
    for rnd in sorted({x[0] for x in log}, key=lambda s: int(s[1:])):
        md.append(f"| {rnd} | " + " | ".join(str(sum(1 for x in log if x[0] == rnd and x[2] == v))
                                             for v in VERDICTS) + " |")
    md += ["", "| round | id | subject | verdict | why |", "|---|---|---|---|---|"]
    md += [f"| {rnd} | {q['id']} | {q['category']} | {v} | {why.replace('|', '/')} |" for rnd, q, v, why, _, _ in
           sorted(log, key=lambda x: (int(x[0][1:]), -VERDICTS.index(x[2])))]
    (REV / "review_log.md").write_text("\n".join(md), encoding="utf-8")

    fl = [(q, why, ch) for _, q, v, why, _, ch in log if v == "flagged"]
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

    for rnd in sorted({x[0] for x in log}, key=lambda s: int(s[1:])):
        sub = [x for x in log if x[0] == rnd]
        print(f"{rnd}: {dict(Counter(x[2] for x in sub))}")
        print("    by subject:", {c: dict(Counter(x[2] for x in sub if x[1]["category"] == c))
                                  for c in sorted({x[1]["category"] for x in sub})})
    print(f"overrides: {len(overrides)}  excluded (flagged, not cleared): {len(excluded)}")
    remaining_report()


if __name__ == "__main__":
    {"pick": pick, "recheck": recheck, "apply": apply}[STEP]()
