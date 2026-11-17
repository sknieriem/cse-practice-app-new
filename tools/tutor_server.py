"""
STUB of the optional online "Ask the tutor" server. Not used by the current app build
(BuildConfig.TUTOR_ENABLED = false). Run with the PyCharm green button to try it locally.

Why a server: the phone must never hold an API key. The app sends the question id + the user's
follow-up to THIS server; the server (on Steve's machine / VPS, with Steve's key) calls Claude and
enforces a monthly quota per device so the cost per subscriber has a hard ceiling.

Standard library only. POST /ask  {"device": "...", "questionId": 123, "chosen": 1, "message": "..."}
"""
import json
import os
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

# ------------------------------- options --------------------------------------------------
MOCK_MODE = True           # True = canned answers, no key, no cost
PORT = 8765
MONTHLY_QUOTA = 30         # questions per device per calendar month
MODEL = "claude-haiku-5-5" # cheapest current model; switch to claude-sonnet-5-5 if answers are weak
MAX_TOKENS = 600
# ------------------------------------------------------------------------------------------

ROOT = Path(__file__).resolve().parents[1]
BANK = {q["id"]: q for q in json.loads((ROOT / "app/src/main/assets/questions.json").read_text(encoding="utf-8"))}
QUOTA_FILE = ROOT / "content" / "tutor_quota.json"
sys.stdout.reconfigure(encoding="utf-8")

SYSTEM = ("You are a patient tutor for the Philippine Civil Service Exam. Answer the reviewee's follow-up "
          "about this one question in at most 120 words, in the same language they used (English, Tagalog "
          "or Taglish). Do not discuss anything unrelated to exam review.")


def month_key():
    return time.strftime("%Y-%m")


def use_quota(device):
    data = json.loads(QUOTA_FILE.read_text()) if QUOTA_FILE.exists() else {}
    k = f"{month_key()}:{device}"
    used = data.get(k, 0)
    if used >= MONTHLY_QUOTA:
        return None
    data[k] = used + 1
    QUOTA_FILE.write_text(json.dumps(data))
    return MONTHLY_QUOTA - used - 1


def answer(q, chosen, message):
    if MOCK_MODE:
        return f"[MOCK] About '{q['text'][:50]}': the correct answer is {q['correctAnswer']}. You asked: {message[:80]}"
    import anthropic
    client = anthropic.Anthropic()  # reads ANTHROPIC_API_KEY
    opts = "\n".join(f"{L}. {q['option' + L]}" for L in "ABCD")
    picked = "ABCD"[chosen] if 0 <= chosen <= 3 else "none"
    resp = client.messages.create(
        model=MODEL,
        max_tokens=MAX_TOKENS,
        system=SYSTEM,
        messages=[{"role": "user", "content":
                   f"Question: {q['text']}\n{opts}\nCorrect: {q['correctAnswer']}\nReviewee picked: {picked}\n"
                   f"Explanation shown: {q.get('steps') or q.get('explanation', '')}\n\nTheir follow-up: {message}"}],
    )
    if resp.stop_reason == "refusal":
        return "Sorry, I can't help with that one. Try asking about the exam question."
    return "".join(b.text for b in resp.content if b.type == "text")


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/ask":
            return self.send_error(404)
        try:
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
            q = BANK.get(int(body["questionId"]))
            if q is None:
                return self.reply(404, {"error": "unknown question"})
            message = str(body.get("message", ""))[:500]
            left = use_quota(str(body["device"])[:64])
            if left is None:
                return self.reply(429, {"error": f"monthly limit of {MONTHLY_QUOTA} reached"})
            self.reply(200, {"answer": answer(q, int(body.get("chosen", -1)), message), "remaining": left})
        except Exception as e:
            self.reply(400, {"error": str(e)[:200]})

    def reply(self, code, obj):
        data = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    if not MOCK_MODE and not os.environ.get("ANTHROPIC_API_KEY"):
        sys.exit("Set ANTHROPIC_API_KEY or use MOCK_MODE = True")
    print(f"Tutor stub on http://localhost:{PORT}/ask  (mock={MOCK_MODE}, quota={MONTHLY_QUOTA}/month)")
    HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
