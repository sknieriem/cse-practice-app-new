"""
Fuzzy duplicate + language-coverage scan over the whole bank (app/src/main/assets/questions.json).
Run with the PyCharm green ▶ after build_content.py. No network, read-only.

Duplicates: pairs whose stem (+ passage) is >= STEM_RATIO similar AND that share >= SHARED_OPTIONS option
values. Each hit is labelled:
  SAME      identical stem after normalising punctuation/case -> a real duplicate, rewrite one
  VARIANT   same template, different numbers/words -> usually fine (practice variants), eyeball the list
Coverage: every question must have non-empty explanation, explanationTl, hint, hintTl; items with steps
must also have stepsTl.
"""
import difflib
import json
import pathlib
import re

STEM_RATIO = 0.9
SHARED_OPTIONS = 2
SHOW_VARIANTS = False   # True prints every VARIANT pair too

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSET = ROOT / "app" / "src" / "main" / "assets" / "questions.json"
REQUIRED = ["explanation", "explanationTl", "hint", "hintTl"]


def norm(s):
    return re.sub(r"\W+", " ", (s or "").lower()).strip()


def raw(s):   # keeps math operators so "1/2 + 1/4" != "1/2 - 1/4"
    return re.sub(r"\s+", " ", (s or "").lower()).strip(" ?.")


def main():
    qs = json.loads(ASSET.read_text(encoding="utf-8"))
    stem = {q["id"]: norm(q["text"] + " " + (q.get("passage") or "")) for q in qs}
    exact = {q["id"]: raw(q["text"] + " " + (q.get("passage") or "")) for q in qs}
    opts = {q["id"]: frozenset(norm(q["option" + L]) for L in "ABCD") for q in qs}
    by_cat = {}
    for q in qs:
        by_cat.setdefault(q["category"], []).append(q)
    same, variant = [], []
    for cat, group in by_cat.items():
        for i, a in enumerate(group):
            for b in group[i + 1:]:
                if len(opts[a["id"]] & opts[b["id"]]) < SHARED_OPTIONS:
                    continue
                r = difflib.SequenceMatcher(None, stem[a["id"]], stem[b["id"]]).ratio()
                if r < STEM_RATIO:
                    continue
                (same if exact[a["id"]] == exact[b["id"]] else variant).append((r, a, b))
    for label, hits in (("SAME", same), ("VARIANT", variant if SHOW_VARIANTS else [])):
        for r, a, b in sorted(hits, key=lambda x: -x[0]):
            print(f"{label} {r:.2f}  {a['id']} | {a['text'][:80]}\n{'':13}{b['id']} | {b['text'][:80]}")
    print(f"duplicates: {len(same)} SAME, {len(variant)} VARIANT (same template, different values)")

    gaps = {f: [q["id"] for q in qs if not str(q.get(f) or "").strip()] for f in REQUIRED}
    gaps["stepsTl"] = [q["id"] for q in qs if q.get("steps") and not q.get("stepsTl")]
    print(f"coverage over {len(qs)} questions:",
          {f: f"{len(qs) - len(v) if f != 'stepsTl' else sum(1 for q in qs if q.get('steps')) - len(v)}"
              f" ok, {len(v)} missing" for f, v in gaps.items()})
    for f, v in gaps.items():
        if v:
            print(f"  missing {f}: {v[:20]}")


if __name__ == "__main__":
    main()
