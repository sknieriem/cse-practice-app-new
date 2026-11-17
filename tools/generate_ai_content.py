"""
Write the missing per-question study content with Claude, ONE TIME, at dev time.
The app never calls an AI — it ships the results inside questions.json and works offline.

Run with the PyCharm green button. Options are the constants below.

  MOCK_MODE = True   -> no network, no key, no cost. Writes content/ai_content.mock.json so you
                        can test the whole pipeline. build_content.py IGNORES the mock file.
  MOCK_MODE = False  -> calls the Claude API with YOUR key (env var ANTHROPIC_API_KEY) and
                        writes content/ai_content.json, which build_content.py merges.

Safe to stop and re-run: items already in the output file are skipped.
After a real run:  1) run tools/build_content.py   2) rebuild the app.
Every item where Claude disagrees with the answer key is NOT merged — it is listed for a human.
"""
import json
import os
import sys
import time
from pathlib import Path

# ------------------------------- options --------------------------------------------------
MOCK_MODE = True
LIMIT = 20                 # max questions this run (None = all missing). Start small, read the output.
MODEL = "claude-opus-5-5"  # current Opus. Prices per 1M tokens (Anthropic list, cached 2026-11-03; re-check):
                           #   claude-opus-5-5   $4 in / $20 out
                           #   claude-sonnet-5-5 $2 in / $10 out
                           #   claude-haiku-5-5  $0.10 in / $0.50 out (weaker at math explanations)
MAX_TOKENS = 4000
USE_REFUSAL_FALLBACK = True  # if a safety filter declines, the API retries on a fallback model
SLEEP_BETWEEN = 0.2          # seconds, to stay polite with rate limits
# ------------------------------------------------------------------------------------------

ROOT = Path(__file__).resolve().parents[1]
BANK = ROOT / "app" / "src" / "main" / "assets" / "questions.json"   # output of build_content.py
OUT = ROOT / "content" / ("ai_content.mock.json" if MOCK_MODE else "ai_content.json")
sys.stdout.reconfigure(encoding="utf-8")

SYSTEM = """You write study notes for Filipino reviewees preparing for the Philippine Civil Service Exam (CSE).
Readers are adults, often on a phone, often more comfortable in Taglish than in formal English.

For the multiple-choice item you are given, write:
- steps: a short worked solution or reasoning in plain English, numbered if there are several steps (max ~80 words).
- hint: one sentence that nudges toward the method WITHOUT giving the answer.
- why: for EACH wrong option letter, one sentence on why it is wrong or what mistake leads to it.
- stepsTl: the same solution in natural conversational Taglish (Tagalog-English mix as spoken in Metro Manila).
- hintTl: the hint in Taglish.
- explanationTl: a 1–2 sentence Taglish summary of why the correct answer is correct.
- keyIsCorrect: true if the given correct answer is really correct. If it is wrong, ambiguous, outdated or
  the question is unanswerable as written, set false and explain in keyNote.

Rules: be accurate; never invent laws, dates or article numbers you are not sure of — if unsure, keep the
explanation general. Do not mention that you are an AI."""

SCHEMA = {
    "type": "object",
    "properties": {
        "steps": {"type": "string"},
        "hint": {"type": "string"},
        "why": {"type": "object", "properties": {L: {"type": "string"} for L in "ABCD"},
                "additionalProperties": False},
        "stepsTl": {"type": "string"},
        "hintTl": {"type": "string"},
        "explanationTl": {"type": "string"},
        "keyIsCorrect": {"type": "boolean"},
        "keyNote": {"type": "string"},
    },
    "required": ["steps", "hint", "why", "stepsTl", "hintTl", "explanationTl", "keyIsCorrect", "keyNote"],
    "additionalProperties": False,
}


def needs_work(q):
    return not (q.get("steps") and q.get("hint") and q.get("explanationTl") and q.get("stepsTl"))


def prompt_for(q):
    lines = [f"Subject: {q['category']}  |  Topic: {q['subtopic']}"]
    if q.get("passage"):
        lines.append(f"Passage:\n{q['passage']}")
    lines.append(f"Question: {q['text']}")
    for L in "ABCD":
        lines.append(f"{L}. {q['option' + L]}")
    lines.append(f"Answer key: {q['correctAnswer']}")
    if q.get("explanation"):
        lines.append(f"Existing short explanation: {q['explanation']}")
    return "\n".join(lines)


def mock_reply(q):
    wrong = [L for L in "ABCD" if L != q["correctAnswer"]]
    return {
        "steps": f"[MOCK] Worked solution for: {q['text'][:60]}",
        "hint": "[MOCK] Hint.",
        "why": {L: f"[MOCK] Why {L} is wrong." for L in wrong},
        "stepsTl": "[MOCK] Taglish na solusyon.",
        "hintTl": "[MOCK] Hint sa Taglish.",
        "explanationTl": "[MOCK] Taglish na paliwanag.",
        "keyIsCorrect": True,
        "keyNote": "",
        "_usage": {"input_tokens": 0, "output_tokens": 0},
    }


def real_reply(client, q):
    kwargs = dict(
        model=MODEL,
        max_tokens=MAX_TOKENS,
        system=SYSTEM,
        messages=[{"role": "user", "content": prompt_for(q)}],
        output_config={"format": {"type": "json_schema", "schema": SCHEMA}},
    )
    if USE_REFUSAL_FALLBACK:
        kwargs["extra_headers"] = {"anthropic-beta": "server-side-fallback-2026-07-01"}
        kwargs["extra_body"] = {"fallbacks": "default"}
    resp = client.messages.create(**kwargs)
    if resp.stop_reason == "refusal":
        raise RuntimeError(f"refused: {getattr(resp, 'stop_details', None)}")
    if resp.stop_reason == "max_tokens":
        raise RuntimeError("hit max_tokens")
    text = "".join(b.text for b in resp.content if b.type == "text")
    data = json.loads(text)
    data["_usage"] = {"input_tokens": resp.usage.input_tokens, "output_tokens": resp.usage.output_tokens}
    return data


def to_fields(q, r):
    out = {
        "steps": r["steps"], "hint": r["hint"], "stepsTl": r["stepsTl"],
        "hintTl": r["hintTl"], "explanationTl": r["explanationTl"],
        "keyIsCorrect": r["keyIsCorrect"], "keyNote": r.get("keyNote", ""),
        "model": "mock" if MOCK_MODE else MODEL,
    }
    for L, w in (r.get("why") or {}).items():
        if L in "ABCD" and L != q["correctAnswer"] and w:
            out["why" + L] = w
    return out


def main():
    bank = json.loads(BANK.read_text(encoding="utf-8"))
    done = json.loads(OUT.read_text(encoding="utf-8")) if OUT.exists() else {}
    todo = [q for q in bank if needs_work(q) and str(q["id"]) not in done]
    if LIMIT:
        todo = todo[:LIMIT]
    print(f"{'MOCK' if MOCK_MODE else MODEL}: {len(todo)} to do, {len(done)} already in {OUT.name}")

    client = None
    if not MOCK_MODE:
        if not os.environ.get("ANTHROPIC_API_KEY"):
            sys.exit("Set the ANTHROPIC_API_KEY environment variable (PyCharm: Run > Edit Configurations > Environment).")
        import anthropic  # only needed for real runs
        client = anthropic.Anthropic()

    tin = tout = 0
    failures = []
    for n, q in enumerate(todo, 1):
        try:
            r = mock_reply(q) if MOCK_MODE else real_reply(client, q)
        except Exception as e:  # keep going; failures are listed at the end
            failures.append((q["id"], q["text"][:60], str(e)[:120]))
            print(f"  ! {q['id']}: {e}")
            continue
        tin += r["_usage"]["input_tokens"]
        tout += r["_usage"]["output_tokens"]
        done[str(q["id"])] = to_fields(q, r)
        if n % 10 == 0 or n == len(todo):
            OUT.write_text(json.dumps(done, ensure_ascii=False, indent=1), encoding="utf-8")
            print(f"  {n}/{len(todo)} saved")
        if not MOCK_MODE:
            time.sleep(SLEEP_BETWEEN)
    OUT.write_text(json.dumps(done, ensure_ascii=False, indent=1), encoding="utf-8")

    disagree = [(k, v["keyNote"]) for k, v in done.items() if not v.get("keyIsCorrect", True)]
    print(f"\nTokens this run: {tin:,} in / {tout:,} out (check the real bill in the Anthropic Console)")
    print(f"Failures: {len(failures)}")
    for f in failures:
        print("   ", f)
    print(f"Answer-key disagreements (NOT merged, review by hand): {len(disagree)}")
    for k, note in disagree:
        print(f"    {k}: {note}")
    print(f"\nNext: run tools/build_content.py" + ("  (mock output is ignored by the build)" if MOCK_MODE else ""))


if __name__ == "__main__":
    main()
