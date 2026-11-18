"""
Build the app's question bank: content/*.json  ->  app/src/main/assets/questions.json + topics.json

Run with the PyCharm green button. No arguments. Options are the constants below.

Pipeline (file in -> file out, deterministic, no network):
  1. load content/source_questions.json (the original bank)
  2. drop broken items, apply fixes from FIXES below, de-duplicate on text+options
  3. give each question a stable id (crc32 of text+options) and a subtopic tag
  4. add rule-based worked solutions / hints / why-wrong / Taglish for templated math items
  5. merge hand-authored items (content/authored_items.json)
  6. merge AI-written fields (content/ai_content.json, produced by tools/generate_ai_content.py)
  7. write the assets and print a coverage report
"""
import json
import re
import sys
import zlib
from collections import Counter, defaultdict
from fractions import Fraction
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "content" / "source_questions.json"
AUTHORED = ROOT / "content" / "authored_items.json"
AI_CONTENT = ROOT / "content" / "ai_content.json"
TOPICS_SRC = ROOT / "content" / "topics.json"
OUT_QUESTIONS = ROOT / "app" / "src" / "main" / "assets" / "questions.json"
OUT_TOPICS = ROOT / "app" / "src" / "main" / "assets" / "topics.json"
SHOW_EXAMPLES = 0  # print N generated examples per rule, for eyeballing
REVIEW_DIR = ROOT / "content" / "review"
REVIEW_OVERRIDES = json.loads((REVIEW_DIR / "overrides.json").read_text(encoding="utf-8")) \
    if (REVIEW_DIR / "overrides.json").exists() else {}
REVIEW_EXCLUDED = set(json.loads((REVIEW_DIR / "excluded.json").read_text(encoding="utf-8"))) \
    if (REVIEW_DIR / "excluded.json").exists() else set()

sys.stdout.reconfigure(encoding="utf-8")

# --------------------------------------------------------------------------------------
# 2. Broken items and fixes
# --------------------------------------------------------------------------------------
DROP_IF_TEXT_STARTS = [
    "What is the main idea of the passage? (Assume a passage)",
    "The main idea of the passage? (Assume a passage)",
    "Data interpretation: (Assume graph)",
    "What is the main topic of the passage? (Mental health awareness)",
    "What is the main topic of the passage? (Climate change)",
    "Which legislation is mentioned in the passage?",
    "In reading comprehension, if a passage states",
]

# text-prefix -> dict of field overrides. Gender-ambiguous family items get the missing fact.
FIXES = {
    "If X is Y's brother, Y is Z's sister, Z is X's:": {
        "text": "X is Y's brother, Y is Z's sister, and Z is a boy. Z is X's:"},
    "If A is B’s mother, B is C’s sister, C is A’s:": {
        "text": "A is B's mother, B is C's sister, and C is a girl. C is A's:"},
    "If D is E’s mother, E is F’s sister, F is D’s:": {
        "text": "D is E's mother, E is F's sister, and F is a girl. F is D's:"},
    "If J is K’s brother, K is L’s sister, L is J’s:": {
        "text": "J is K's brother, K is L's sister, and L is a girl. L is J's:"},
    # source key said D (17/12); 5/6 + 1/4 = 10/12 + 3/12 = 13/12
    "5/6 + 1/4 = ?": {"correctAnswer": "B",
                      "explanation": "Common denominator 12: 10/12 + 3/12 = 13/12."},
}

TIME_SENSITIVE = [
    "current President", "current Chief Justice", "Number of regions", "tallest building",
    "Commander-in-Chief of all armed forces of the Philippines in",
]

# Verbal items that are a bare sentence ("The task was daunting.") with no question.
# The key word is quoted at the start of the explanation: "'Daunting' means ...".
def fix_bare_vocab(q):
    t = q["text"].strip()
    if q["category"] != "Verbal Ability" or "?" in t or ":" in t or "_" in t:
        return
    if t.lower().startswith(("rearrange", "choose", "which", "identify", "fill", "complete")):
        return
    m = re.match(r"^'([^']+)'", q["explanation"]) or re.search(r"'([^']+)' means", q["explanation"])
    if not m:
        return
    word = m.group(1)
    if word.lower() not in t.lower():
        return
    q["text"] = f"{t}\n\nIn this sentence, '{word.lower()}' most nearly means:"


# --------------------------------------------------------------------------------------
# 3. Subtopics
# --------------------------------------------------------------------------------------
def subtopic(q):
    c, t = q["category"], q["text"]
    tl = t.lower()
    if c == "Numerical Ability":
        if re.search(r"discount|% of|percent|%", tl): return "num_percent"
        if re.search(r"sequence|next in|____\?", tl): return "num_series"
        if re.search(r"solve for x|value of x|^if [a-z] = |^if [a-z]=|equation|sum of x", tl): return "num_algebra"
        if re.search(r"\d+/\d+|decimal|fraction", tl): return "num_fractions"
        if re.search(r"km|speed|travels|flies|sails|rides|runs", tl): return "num_rates"
        if re.search(r"how many (days|hours|minutes|months)|workers|cost .* pesos", tl): return "num_work"
        if re.search(r"area|volume|perimeter|circumference|hypotenuse|angle|sides in|triangle", tl): return "num_geometry"
        if re.search(r"average|mean|median|mode|range", tl): return "num_stats"
        return "num_word"
    if c == "Analytical Ability":
        if re.match(r"if \d+=\d+", tl): return "ana_codes"
        if "data interpretation" in tl: return "ana_data"
        if re.search(r"next in series|sequence", tl): return "ana_series"
        if re.search(r"mother|father|brother|sister|daughter", tl): return "ana_family"
        if "assumption" in tl: return "ana_assumption"
        if "odd one out" in tl: return "ana_oddone"
        if re.search(r" is to |::|: [a-z_]+ :|:\s*_+", tl): return "ana_analogy"
        return "ana_logic"
    if c == "Verbal Ability":
        if "rearrange" in tl: return "verb_ordering"
        if "spell" in tl: return "verb_spelling"
        if "antonym" in tl or "opposite" in tl: return "verb_antonym"
        if "analogy" in tl: return "verb_analogy"
        if re.search(r"implied|passage|main idea", tl): return "verb_reading"
        if re.search(r"error|grammatical|tense|voice|parallel|_{3,}|correct word usage|sentence is correct|agreement", tl):
            return "verb_grammar"
        return "verb_vocab"
    if c == "General Information":
        if re.search(r"6713|ethical|conduct|public official|public office", tl): return "gi_ethics"
        if re.search(r"human rights|peace|bill of rights", tl): return "gi_rights"
        if re.search(r"environment|ecolog|clean air|clean water|climate|solid waste", tl): return "gi_environment"
        if re.search(r"constitution|senat|congress|president|supreme court|representatives|ombudsman|"
                     r"commission|branches|executive power|legislative|taxation|house speaker|"
                     r"no person shall|state policy|chief justice|national language", tl):
            return "gi_constitution"
        if re.search(r"philippine|filipino|rizal|bonifacio|manila|cebu|luzon|edsa|independence|"
                     r"national (flower|fruit|animal|tree|dance|sport|costume|emblem|hero|bird)|festival|"
                     r"province|island", tl):
            return "gi_ph"
        return "gi_general"
    return "other"


# --------------------------------------------------------------------------------------
# 4. Rule-based worked solutions for templated math items
# --------------------------------------------------------------------------------------
def num(s):
    s = str(s).replace(",", "").replace("Php", "").replace("₱", "").strip()
    try:
        return float(Fraction(s)) if "/" in s else float(s)
    except (ValueError, ZeroDivisionError):
        return None


def fmt(x):
    if x is None:
        return "?"
    if abs(x - round(x)) < 1e-9:
        return f"{int(round(x)):,}"
    return f"{x:,.4f}".rstrip("0").rstrip(".")


def letters_wrong(q):
    return [L for L in "ABCD" if L != q["correctAnswer"]]


def rule_percent_of(q, m):
    p, n = float(m[1]), float(m[2])
    ans = p / 100 * n
    steps = (f"1. Turn {fmt(p)}% into a decimal: {fmt(p)} ÷ 100 = {fmt(p/100)}.\n"
             f"2. Multiply: {fmt(p/100)} × {fmt(n)} = {fmt(ans)}.")
    tl = (f"1. Gawing decimal ang {fmt(p)}%: {fmt(p)} ÷ 100 = {fmt(p/100)}.\n"
          f"2. I-multiply sa {fmt(n)}: {fmt(p/100)} × {fmt(n)} = {fmt(ans)}. Yan ang sagot.")
    why = {}
    for L in letters_wrong(q):
        v = num(q["option" + L])
        if v is not None and n:
            why[L] = f"{fmt(v)} is {fmt(v / n * 100)}% of {fmt(n)}, not {fmt(p)}%."
    return dict(answer=ans, steps=steps, stepsTl=tl, hint="“Percent” means “out of 100”. Change the % to a decimal, then multiply.",
                hintTl="Ang “percent” ay “sa bawat 100”. Gawing decimal muna, tapos i-multiply.", why=why)


def rule_discount(q, m):
    price, p = float(m[1]), float(m[2])
    off = price * p / 100
    ans = price - off
    steps = (f"1. Discount amount: {fmt(p)}% of ₱{fmt(price)} = {fmt(p/100)} × {fmt(price)} = ₱{fmt(off)}.\n"
             f"2. Subtract it: ₱{fmt(price)} − ₱{fmt(off)} = ₱{fmt(ans)}.\n"
             f"Shortcut: pay {fmt(100-p)}% of the price → {fmt((100-p)/100)} × {fmt(price)} = ₱{fmt(ans)}.")
    tl = (f"1. Magkano ang discount? {fmt(p)}% ng ₱{fmt(price)} = ₱{fmt(off)}.\n"
          f"2. Ibawas sa presyo: ₱{fmt(price)} − ₱{fmt(off)} = ₱{fmt(ans)}.\n"
          f"Shortcut: babayaran mo lang ang {fmt(100-p)}% → {fmt((100-p)/100)} × {fmt(price)} = ₱{fmt(ans)}.")
    why = {}
    for L in letters_wrong(q):
        v = num(q["option" + L])
        if v is None:
            continue
        if abs(v - off) < 1e-6:
            why[L] = f"₱{fmt(v)} is only the discount. The question asks for the final price."
        elif abs(v - price) < 1e-6:
            why[L] = "That is the original price — the discount was not taken off."
        else:
            why[L] = f"₱{fmt(v)} would mean a {fmt((price - v) / price * 100)}% discount, not {fmt(p)}%."
    return dict(answer=ans, steps=steps, stepsTl=tl, hint="Final price = original price × (100% − discount%).",
                hintTl="Final price = presyo × (100% − discount%).", why=why)


def rule_linear(q, a, b, c, op):
    # a*x (op) b = c
    rhs = c - b if op == "+" else c + b
    x = rhs / a
    opname = {"+": f"Subtract {fmt(b)} from both sides", "-": f"Add {fmt(b)} to both sides"}
    lines = []
    if b:
        lines.append(f"1. {opname[op]}: {fmt(a)}x = {fmt(rhs)}.")
    lines.append(f"{len(lines)+1}. Divide both sides by {fmt(a)}: x = {fmt(rhs)} ÷ {fmt(a)} = {fmt(x)}." if a != 1
                 else f"{len(lines)+1}. So x = {fmt(x)}.")
    lines.append(f"Check: {fmt(a)}({fmt(x)}) {op} {fmt(b)} = {fmt(c)} ✓" if b else f"Check: {fmt(a)} × {fmt(x)} = {fmt(c)} ✓")
    tl_lines = []
    if b:
        tl_lines.append(f"1. {'Ibawas' if op == '+' else 'Idagdag'} ang {fmt(b)} sa magkabilang side: {fmt(a)}x = {fmt(rhs)}.")
    tl_lines.append(f"{len(tl_lines)+1}. Hatiin sa {fmt(a)}: x = {fmt(x)}." if a != 1 else f"{len(tl_lines)+1}. Kaya x = {fmt(x)}.")
    tl_lines.append("I-check palagi: ibalik ang sagot sa equation.")
    why = {}
    for L in letters_wrong(q):
        v = num(q["option" + L])
        if v is None:
            continue
        got = a * v + b if op == "+" else a * v - b
        why[L] = f"Put x = {fmt(v)} back in: you get {fmt(got)}, not {fmt(c)}."
    return dict(answer=x, steps="\n".join(lines), stepsTl="\n".join(tl_lines),
                hint="Undo the + or − first, then undo the multiplication. Check by plugging your answer back in.",
                hintTl="Unahin ang + o −, saka ang multiplication. I-plug-in ang sagot para ma-check.", why=why)


def rule_fraction_op(q, m):
    a, b, op, c, d = Fraction(int(m[1]), int(m[2])), None, m[3], Fraction(int(m[4]), int(m[5])), None
    ops = {"+": a + c, "-": a - c, "*": a * c, "×": a * c, "÷": a / c, "/": a / c}
    if op not in ops:
        return None
    r = ops[op]
    if op in "+-":
        den = (a.denominator * c.denominator) // __import__("math").gcd(a.denominator, c.denominator)
        na, nc = a.numerator * den // a.denominator, c.numerator * den // c.denominator
        steps = (f"1. Common denominator of {a.denominator} and {c.denominator} is {den}.\n"
                 f"2. Rewrite: {na}/{den} {op} {nc}/{den} = {na + nc if op == '+' else na - nc}/{den}.\n"
                 f"3. Simplify: {r.numerator}/{r.denominator}" + ("" if r.denominator != 1 else f" = {r.numerator}") + ".")
        tl = (f"1. Hanapin ang common denominator ({den}).\n2. I-{'add' if op == '+' else 'subtract'} ang numerators.\n"
              f"3. I-simplify: {r.numerator}/{r.denominator}.")
        hint = "Same denominator first, then add or subtract the tops."
    elif op in "*×":
        steps = (f"1. Multiply tops: {a.numerator} × {c.numerator} = {a.numerator * c.numerator}.\n"
                 f"2. Multiply bottoms: {a.denominator} × {c.denominator} = {a.denominator * c.denominator}.\n"
                 f"3. Simplify: {r.numerator}/{r.denominator}.")
        tl = "1. I-multiply ang numerators.\n2. I-multiply ang denominators.\n" + f"3. I-simplify: {r.numerator}/{r.denominator}."
        hint = "Top × top, bottom × bottom, then simplify."
    else:
        steps = (f"1. Keep {a}, change ÷ to ×, flip {c} to {c.denominator}/{c.numerator}.\n"
                 f"2. Multiply: {a} × {c.denominator}/{c.numerator} = {r.numerator}/{r.denominator}.")
        tl = f"1. Keep-change-flip: {a} × {c.denominator}/{c.numerator}.\n2. I-multiply at i-simplify: {r.numerator}/{r.denominator}."
        hint = "Dividing by a fraction = multiplying by its flip (keep, change, flip)."
    why = {}
    for L in letters_wrong(q):
        v = num(q["option" + L])
        if v is not None and abs(v - float(r)) > 1e-9:
            if op in "+-" and abs(v - float(Fraction(a.numerator + (c.numerator if op == '+' else -c.numerator), a.denominator + c.denominator))) < 1e-9:
                why[L] = "This adds the denominators too — only the numerators are added once the denominators match."
            else:
                why[L] = f"{q['option' + L]} = {fmt(v)}, but the answer is {r.numerator}/{r.denominator} = {fmt(float(r))}."
    return dict(answer=float(r), steps=steps, stepsTl=tl, hint=hint, hintTl="Fractions: sundan ang tamang rule (same denominator para sa + at −).", why=why)


def rule_to_decimal(q, m):
    n, d = int(m[1]), int(m[2])
    v = n / d
    steps = f"Divide the top by the bottom: {n} ÷ {d} = {fmt(v)}."
    tl = f"Hatiin ang numerator sa denominator: {n} ÷ {d} = {fmt(v)}."
    why = {L: f"{q['option' + L]} ≠ {n} ÷ {d}." for L in letters_wrong(q)}
    return dict(answer=v, steps=steps, stepsTl=tl, hint="A fraction bar means “divided by”.",
                hintTl="Ang fraction bar ay ibig sabihin ay “divided by”.", why=why)


def analyse_series(nums):
    d1 = [b - a for a, b in zip(nums, nums[1:])]
    if len(set(d1)) == 1:
        return nums[-1] + d1[0], f"Each term goes up by {fmt(d1[0])}" if d1[0] >= 0 else f"Each term goes down by {fmt(-d1[0])}", \
            f"Pare-pareho ang dagdag ({fmt(d1[0])}) bawat hakbang."
    if all(a != 0 for a in nums):
        r = [b / a for a, b in zip(nums, nums[1:])]
        if len(set(round(x, 9) for x in r)) == 1:
            return nums[-1] * r[0], f"Each term is multiplied by {fmt(r[0])}", f"Bawat term ay i-multiply sa {fmt(r[0])}."
    d2 = [b - a for a, b in zip(d1, d1[1:])]
    if len(d2) >= 1 and len(set(d2)) == 1:
        nd = d1[-1] + d2[0]
        return nums[-1] + nd, (f"The gaps are {', '.join(fmt(x) for x in d1)} — they grow by {fmt(d2[0])} each time, "
                               f"so the next gap is {fmt(nd)}"), f"Tingnan ang pagitan: {', '.join(fmt(x) for x in d1)}. Lumalaki ng {fmt(d2[0])}."
    return None, None, None


def rule_series(q, nums):
    nxt, why_en, why_tl = analyse_series(nums)
    if nxt is None:
        return None
    correct = num(q["option" + q["correctAnswer"]])
    if correct is None or abs(correct - nxt) > 1e-9:
        return None  # our rule disagrees with the key -> leave for a human / AI
    steps = f"1. {why_en}.\n2. Next: {fmt(nxt)}."
    tl = f"1. {why_tl}\n2. Kaya ang susunod ay {fmt(nxt)}."
    return dict(steps=steps, stepsTl=tl,
                hint="Write the difference between neighbours. If that isn't constant, try ratios or the differences of differences.",
                hintTl="Isulat ang pagitan ng bawat numero. Kung hindi pare-pareho, subukan ang ratio.", why={})


def rule_speed(q, m):
    dist, t, unit = float(m[1]), float(m[2]), m[3]
    hours = t / 60 if unit.startswith("min") else t
    v = dist / hours
    conv = f"{fmt(t)} minutes = {fmt(hours)} hours. " if unit.startswith("min") else ""
    steps = f"Speed = distance ÷ time. {conv}{fmt(dist)} km ÷ {fmt(hours)} h = {fmt(v)} km/h."
    tl = f"Speed = distance ÷ oras. {conv}{fmt(dist)} ÷ {fmt(hours)} = {fmt(v)} km/h."
    why = {}
    for L in letters_wrong(q):
        o = num(q["option" + L])
        if o is not None and abs(o - dist * hours) < 1e-9:
            why[L] = "That multiplies distance by time. Speed is distance DIVIDED by time."
    return dict(answer=v, steps=steps, stepsTl=tl, hint="Speed = distance ÷ time (time in hours for km/h).",
                hintTl="Bilis = layo ÷ oras.", why=why)


def rule_substitute(q):
    # "If a = b + c and b=6, c=9, what is a?"  /  "If y = 3x + 2 and x=4, what is y?"
    m = re.match(r"If (\w) = (.+?) and (.+?), what is \1\?", q["text"])
    if not m:
        return None
    target, expr, assigns = m[1], m[2], m[3]
    env = {}
    for k, v in re.findall(r"(\w)\s*=\s*(-?\d+(?:\.\d+)?)", assigns):
        env[k] = float(v)
    py = re.sub(r"(\d)([a-z])", r"\1*\2", expr).replace("^", "**")
    try:
        val = eval(py, {"__builtins__": {}}, env)
    except Exception:
        return None
    shown = expr
    for k, v in env.items():
        shown = re.sub(rf"(\d)({k})\b", rf"\1×({fmt(v)})", shown)
        shown = re.sub(rf"\b{k}\b", fmt(v), shown)
    correct = num(q["option" + q["correctAnswer"]])
    if correct is None or abs(correct - val) > 1e-9:
        return None
    steps = f"Replace each letter with its value: {target} = {shown} = {fmt(val)}."
    return dict(steps=steps, stepsTl=f"Palitan ang bawat letra ng value nito: {target} = {shown} = {fmt(val)}.",
                hint="Substitute the given values, then compute.", hintTl="I-substitute ang values, tapos i-compute.", why={})


def rule_work(q):
    # "If 4 tailors sew 4 suits in 4 days, how many days for 2 tailors to sew 2 suits?"
    m = re.match(r"If (\d+) \w+ \w+ (\d+) \w+ in (\d+) (\w+), how many \w+ for (\d+) \w+ to \w+ (\d+) \w+\?", q["text"])
    if not m:
        return None
    w1, j1, t1, unit, w2, j2 = int(m[1]), int(m[2]), int(m[3]), m[4], int(m[5]), int(m[6])
    t2 = Fraction(t1) * Fraction(w1, w2) * Fraction(j2, j1)
    correct = num(q["option" + q["correctAnswer"]])
    if correct is None or abs(correct - float(t2)) > 1e-9:
        return None
    rate = Fraction(j1, w1 * t1)
    one = unit[:-1] if unit.endswith('s') else unit
    steps = (f"1. One worker does {j1} ÷ ({w1} × {t1}) = {rate} job per {one}.\n"
             f"2. {w2} workers do {w2} × {rate} = {w2 * rate} per {one}.\n"
             f"3. Time for {j2}: {j2} ÷ {w2 * rate} = {fmt(float(t2))} {unit}.")
    tl = (f"1. Isa: {rate} kada {one}.\n2. {w2}: {w2 * rate} kada {one}.\n"
          f"3. Para sa {j2}: {j2} ÷ {w2 * rate} = {fmt(float(t2))} {unit}.")
    return dict(steps=steps, stepsTl=tl,
                hint="Find how much ONE worker does per unit of time first.",
                hintTl="Alamin muna ang kaya ng ISANG tao kada oras/araw.", why={})


def rule_average(q):
    m = re.match(r"(?:Find the average of|Mean of|What is the average of) ([\d, ]+(?:and \d+)?)[.?]?$", q["text"])
    if not m:
        return None
    nums = [float(x) for x in re.findall(r"\d+(?:\.\d+)?", m[1])]
    avg = sum(nums) / len(nums)
    steps = f"Add them: {' + '.join(fmt(x) for x in nums)} = {fmt(sum(nums))}. Divide by {len(nums)}: {fmt(avg)}."
    return dict(answer=avg, steps=steps, stepsTl=f"I-add lahat ({fmt(sum(nums))}), tapos hatiin sa bilang nila ({len(nums)}): {fmt(avg)}.",
                hint="Average = total ÷ how many.", hintTl="Average = kabuuan ÷ ilan sila.", why={})


RULES_HIT = Counter()
KEY_DISAGREE = []


def enrich_rule_based(q):
    t = q["text"].strip()
    res = None
    name = None
    if (m := re.match(r"What is (\d+(?:\.\d+)?)% of (\d+(?:\.\d+)?)\?$", t)):
        res, name = rule_percent_of(q, m), "percent_of"
    elif (m := re.search(r"Php ?([\d,]+(?:\.\d+)?) with (\d+(?:\.\d+)?)% discount", t)) or \
            (m := re.search(r"(\d+(?:\.\d+)?)% discount on (?:a )?Php ?([\d,]+)", t)):
        if "with" in m.group(0):
            res = rule_discount(q, [None, m[1].replace(",", ""), m[2]])
        else:
            res = rule_discount(q, [None, m[2].replace(",", ""), m[1]])
        name = "discount"
    elif (m := re.match(r"(?:Solve for x: |Given the equation )(\d*)x ([+-]) (\d+) = (\d+)", t)):
        res, name = rule_linear(q, float(m[1] or 1), float(m[3]), float(m[4]), m[2]), "linear"
    elif (m := re.match(r"Solve for x: (\d*)x = (\d+)\.?$", t)):
        res, name = rule_linear(q, float(m[1] or 1), 0.0, float(m[2]), "+"), "linear"
    elif (m := re.match(r"(\d+)/(\d+) ([+\-*×÷/]) (\d+)/(\d+) = \?$", t)):
        res, name = rule_fraction_op(q, m), "fraction_op"
    elif (m := re.match(r"Convert (\d+)/(\d+) to (?:a )?decimal\.?$", t)):
        res, name = rule_to_decimal(q, m), "to_decimal"
    elif (m := re.search(r"(?:sequence|series|Next in series)[^:]*: ([\d, ]+)", t)) or \
            (m := re.match(r"^([\d, ]+), _+\?$", t)):
        nums = [float(x) for x in re.findall(r"\d+", m[1])]
        if len(nums) >= 3:
            res, name = rule_series(q, nums), "series"
    elif (m := re.search(r"(\d+(?:\.\d+)?) km in (\d+(?:\.\d+)?) (hours?|minutes?)", t)) and "speed" in t.lower():
        res, name = rule_speed(q, m), "speed"
    elif t.startswith("If ") and " what is " in t and "=" in t:
        res, name = rule_substitute(q), "substitute"
    elif "how many" in t and t.startswith("If "):
        res, name = rule_work(q), "work"
    else:
        res, name = rule_average(q), "average"
    if not res:
        return False
    if res.get("answer") is not None:
        key = num(q["option" + q["correctAnswer"]])
        if key is None or abs(key - res["answer"]) > 1e-6:
            KEY_DISAGREE.append((q["text"], q["option" + q["correctAnswer"]], res["answer"]))
            return False
    RULES_HIT[name] += 1
    q["steps"] = res["steps"]
    q["stepsTl"] = res["stepsTl"]
    q["hint"] = res["hint"]
    q["hintTl"] = res["hintTl"]
    for L, w in res["why"].items():
        q["why" + L] = w
    if SHOW_EXAMPLES and RULES_HIT[name] <= SHOW_EXAMPLES:
        print(f"\n--- rule {name}: {t}\n{res['steps']}\n  TL: {res['stepsTl']}\n  why: {res['why']}")
    return True


# --------------------------------------------------------------------------------------
# main
# --------------------------------------------------------------------------------------
FIELDS = ["id", "category", "subtopic", "passage", "text", "optionA", "optionB", "optionC", "optionD",
          "correctAnswer", "explanation", "steps", "hint", "whyA", "whyB", "whyC", "whyD",
          "explanationTl", "stepsTl", "hintTl", "difficulty", "timeSensitive", "source",
          # current-events items only: topic "current_events" + as_of "YYYY-MM" (date the facts were verified).
          # Not in the Room entity (Gson skips them); kept in questions.json so stale items can be found and refreshed.
          "topic", "as_of"]


def qid(q):
    key = "|".join([q["text"].strip(), q["optionA"], q["optionB"], q["optionC"], q["optionD"]])
    return zlib.crc32(key.encode("utf-8")) & 0x7FFFFFFF


def main():
    src = json.loads(SOURCE.read_text(encoding="utf-8"))
    topics = json.loads(TOPICS_SRC.read_text(encoding="utf-8"))
    topic_ids = {t["id"] for t in topics}
    out, seen = [], set()
    dropped = review_excluded = review_fixed = 0
    for raw in src:
        q = dict(raw)
        if any(q["text"].startswith(p) for p in DROP_IF_TEXT_STARTS):
            dropped += 1
            continue
        # stable id from the ORIGINAL text, so later fixes don't orphan a user's history
        q["id"] = qid(q)
        if q["id"] in seen:
            dropped += 1
            continue
        seen.add(q["id"])
        for prefix, fix in FIXES.items():
            if q["text"].startswith(prefix):
                q.update(fix)
        if q["text"].startswith("If 4 men paint a house"):
            q["category"] = "Numerical Ability"
        fix_bare_vocab(q)
        q["subtopic"] = subtopic(q)
        q["timeSensitive"] = any(s in q["text"] for s in TIME_SENSITIVE)
        q["source"] = "original"
        if q["category"] in ("Numerical Ability", "Analytical Ability"):
            enrich_rule_based(q)
        # reviewed originals (tools/review_originals.py): fixes keep the id; disputed items stay out until cleared
        if q["id"] in REVIEW_EXCLUDED:
            review_excluded += 1
            continue
        if str(q["id"]) in REVIEW_OVERRIDES:
            q.update(REVIEW_OVERRIDES[str(q["id"])])
            q.pop("why" + q["correctAnswer"], None)  # a rule-written why-wrong for the new key would be wrong
            review_fixed += 1
        out.append(q)

    print(f"  review pass: {review_fixed} originals updated, {review_excluded} held out for Pearl")
    authored = json.loads(AUTHORED.read_text(encoding="utf-8")) if AUTHORED.exists() else []
    for a in authored:
        a = dict(a)
        a["id"] = qid(a)
        a["source"] = "authored"
        a.setdefault("timeSensitive", False)
        assert a["id"] not in seen, a["text"]
        assert a["correctAnswer"] in "ABCD"
        seen.add(a["id"])
        out.append(a)

    # new Opus-written questions: only those that passed the blind re-solve gate (tools/question_gate.py)
    gen_dir = ROOT / "content" / "generated"
    approved = set(json.loads((gen_dir / "approved.json").read_text(encoding="utf-8"))) if (gen_dir / "approved.json").exists() else set()
    generated = 0
    for p in sorted((gen_dir / "batches").glob("*.json")) if approved else []:
        for g in json.loads(p.read_text(encoding="utf-8-sig")):
            if g.get("uid") not in approved:
                continue
            g = dict(g)
            g["id"] = qid(g)
            g["source"] = "generated"
            g.setdefault("timeSensitive", False)
            if g["id"] in seen:
                print(f"  duplicate generated item skipped: {g['uid']}")
                continue
            seen.add(g["id"])
            out.append(g)
            generated += 1
    print(f"  merged {generated} gate-approved generated questions")

    ai = json.loads(AI_CONTENT.read_text(encoding="utf-8")) if AI_CONTENT.exists() else {}
    ai_used = 0
    for q in out:
        extra = ai.get(str(q["id"]))
        if not extra:
            continue
        if extra.get("keyIsCorrect") is False:
            print(f"  AI disputes the key, not merged: {q['id']} {q['text'][:60]!r} — {extra.get('keyNote', '')}")
            continue
        for k, v in extra.items():
            if k in FIELDS and v and not q.get(k):  # never overwrite rule-based or authored text
                q[k] = v
                ai_used += 1

    bad_topics = {q["subtopic"] for q in out} - topic_ids
    assert not bad_topics, f"subtopics missing from topics.json: {bad_topics}"
    for q in out:
        q.setdefault("difficulty", 2)
    clean = [{k: q[k] for k in FIELDS if q.get(k) not in (None, "")} for q in out]
    OUT_QUESTIONS.write_text(json.dumps(clean, ensure_ascii=False, indent=1), encoding="utf-8")
    OUT_TOPICS.write_text(json.dumps(topics, ensure_ascii=False, indent=1), encoding="utf-8")

    # ---- report
    print(f"\n==== {len(clean)} questions written ({dropped} dropped from {len(src)} source, {len(authored)} authored)")
    print("rule-based solutions:", dict(RULES_HIT), "total", sum(RULES_HIT.values()))
    print("rule disagrees with answer key (left untouched, CHECK THESE):", len(KEY_DISAGREE))
    for d in KEY_DISAGREE:
        print("   ", d)
    print("AI fields merged:", ai_used)
    by_cat = Counter(q["category"] for q in clean)
    print("by category:", dict(by_cat))
    by_topic = Counter(q["subtopic"] for q in clean)
    for t in topics:
        print(f"  {t['id']:<16} {by_topic.get(t['id'], 0):>4}  {t['name']}")
    cov = defaultdict(int)
    for q in clean:
        for f in ("steps", "hint", "explanationTl", "stepsTl", "whyA"):
            if f == "whyA":
                cov["any why"] += any(q.get("why" + L) for L in "ABCD")
            else:
                cov[f] += bool(q.get(f))
    print("coverage:", {k: f"{v}/{len(clean)}" for k, v in cov.items()})
    print("time-sensitive:", sum(q.get("timeSensitive", False) for q in clean))
    print("answer key:", dict(Counter(q["correctAnswer"] for q in clean)))


if __name__ == "__main__":
    main()
