"""
Quality gate for new questions (content/generated/batches/*.json).

Run with the PyCharm green ▶. Two steps, chosen by STEP:
  STEP = "blind"   -> writes content/generated/blind/<batch>.json: the questions WITHOUT the key or any explanation,
                      for a separate solver to answer.
  STEP = "compare" -> reads content/generated/solved/<batch>.json (solver answers) and writes:
                        content/generated/approved.json      uids that may go into the app
                        content/flagged_for_pearl.md / .csv  every disagreement, ambiguity or missing answer
Only approved uids are merged by tools/build_content.py. Pearl clears a flagged item by adding its uid to
content/generated/pearl_cleared.json (and fixing the item in its batch file if needed).
"""
import csv
import json
import pathlib

STEP = "compare"          # "blind" or "compare"
BATCH_GLOB = "*.json"     # which batches to process

ROOT = pathlib.Path(__file__).resolve().parents[1]
GEN = ROOT / "content" / "generated"
BATCHES, BLIND, SOLVED = GEN / "batches", GEN / "blind", GEN / "solved"
REQUIRED = ["uid", "category", "subtopic", "text", "optionA", "optionB", "optionC", "optionD", "correctAnswer",
            "explanation", "hint", "explanationTl"]


def load(p):
    return json.loads(p.read_text(encoding="utf-8-sig"))


def problems(q, topic_ids):
    out = [f"missing {k}" for k in REQUIRED if not q.get(k)]
    key = q.get("correctAnswer", "")
    if key not in ("A", "B", "C", "D"):
        out.append("bad key")
    else:
        for L in "ABCD":
            if L != key and not q.get("why" + L):
                out.append(f"missing why{L}")
    opts = [q.get("option" + L, "").strip().lower() for L in "ABCD"]
    if len(set(opts)) < 4:
        out.append("duplicate options")
    if q.get("subtopic") not in topic_ids:
        out.append(f"unknown subtopic {q.get('subtopic')}")
    return out


def blind():
    BLIND.mkdir(parents=True, exist_ok=True)
    for p in sorted(BATCHES.glob(BATCH_GLOB)):
        items = [{k: q[k] for k in ("uid", "category", "passage", "text", "optionA", "optionB", "optionC", "optionD")
                  if q.get(k)} for q in load(p)]
        (BLIND / p.name).write_text(json.dumps(items, ensure_ascii=False, indent=1), encoding="utf-8")
        print(f"{p.name}: {len(items)} blind items")


def compare():
    topic_ids = {t["id"] for t in load(ROOT / "content" / "topics.json")}
    cleared = set(load(GEN / "pearl_cleared.json")) if (GEN / "pearl_cleared.json").exists() else set()
    approved, flagged, per_cat = [], [], {}
    for p in sorted(BATCHES.glob(BATCH_GLOB)):
        solved_p = SOLVED / p.name
        solved = {s["uid"]: s for s in load(solved_p)} if solved_p.exists() else {}
        for q in load(p):
            s = solved.get(q["uid"])
            reasons = problems(q, topic_ids)
            if s is None:
                reasons.append("not solved by blind pass")
            else:
                if s.get("answer") != q["correctAnswer"]:
                    reasons.append(f"blind solver chose {s.get('answer')}, key is {q['correctAnswer']}")
                if s.get("ambiguous"):
                    reasons.append("solver: ambiguous / two defensible answers")
                if s.get("confidence") != "sure":
                    reasons.append("solver not sure")
            c = per_cat.setdefault(q["category"], [0, 0])
            c[0] += 1
            if reasons and q["uid"] not in cleared:
                c[1] += 1
                flagged.append((q, reasons, (s or {}).get("note", "")))
            else:
                approved.append(q["uid"])

    (GEN / "approved.json").write_text(json.dumps(approved, indent=1), encoding="utf-8")
    md = ["# Questions flagged for Pearl", "",
          "A separate blind pass re-solved every new question without seeing the key. These disagreed, looked",
          "ambiguous, or failed a format check. They are NOT in the app until cleared: fix the item in its batch file",
          "if needed, then add its uid to `content/generated/pearl_cleared.json`.", ""]
    for q, reasons, note in flagged:
        md += [f"## {q['uid']} · {q['category']} · {q['subtopic']}", ""]
        if q.get("passage"):
            md += [f"> {q['passage']}", ""]
        md += [q["text"], ""] + [f"- {L}. {q['option' + L]}" + ("  ✅ key" if L == q["correctAnswer"] else "")
                                  for L in "ABCD"]
        md += ["", f"**Why flagged:** {'; '.join(reasons)}", ""]
        if note:
            md += [f"**Solver's note:** {note}", ""]
        md += [f"**Author's explanation:** {q.get('explanation', '')}", ""]
    (ROOT / "content" / "flagged_for_pearl.md").write_text("\n".join(md), encoding="utf-8")
    with open(ROOT / "content" / "flagged_for_pearl.csv", "w", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        w.writerow(["uid", "category", "subtopic", "question", "A", "B", "C", "D", "key", "why_flagged", "solver_note",
                    "explanation"])
        for q, reasons, note in flagged:
            w.writerow([q["uid"], q["category"], q["subtopic"], q["text"], q["optionA"], q["optionB"], q["optionC"],
                        q["optionD"], q["correctAnswer"], "; ".join(reasons), note, q.get("explanation", "")])

    print(f"{'subject':22s} written  flagged  approved")
    for cat, (n, fl) in sorted(per_cat.items()):
        print(f"{cat:22s} {n:7d}  {fl:7d}  {n - fl:8d}")
    tot = sum(v[0] for v in per_cat.values())
    print(f"{'TOTAL':22s} {tot:7d}  {len(flagged):7d}  {len(approved):8d}")


if __name__ == "__main__":
    blind() if STEP == "blind" else compare()
