"""The app's words in other languages (see I18n.kt).

Finds the English words the app shows (string literals in its Kotlin files; "$x" parts become {0}, {1}...), keeps the
list in tools/i18n/en.json, and has Claude translate the ones a language does not have yet into
app/src/main/assets/i18n/<code>.json. Run by .github/workflows/translate.yml on a commit with [translate].
"""
import json
import pathlib
import re
import sys
from concurrent.futures import ThreadPoolExecutor

import anthropic

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/src/main/java/com/bolkhata/app"
OUT = ROOT / "app/src/main/assets/i18n"
LIST = ROOT / "tools/i18n/en.json"

LANGS = {
    "ur": "Urdu (Urdu script)", "hi": "Hindi (Devanagari)", "ar": "Arabic", "bn": "Bengali", "es": "Spanish",
    "fr": "French", "de": "German", "pt": "Portuguese (Brazil)", "tr": "Turkish",
}

# Files with no words for people to read (the AI's instructions, word lists for reading messages, network code).
SKIP = {"BankParser.kt", "AiAgent.kt", "Ai.kt", "Groq.kt", "Brain.kt", "Eleven.kt", "ElevenAgent.kt", "ChatLog.kt", "Spell.kt", "Lexicon.kt",
        "NumberWords.kt", "Normalizer.kt", "Translit.kt", "Understand.kt", "I18n.kt", "Lingo.kt"}

LITERAL = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
TEMPLATE = re.compile(r'\$\{(?:[^{}]|\{[^{}]*\})*\}|\$[A-Za-z_][A-Za-z0-9_.]*(?:\(\))?')


def words():
    """Every English line the app may show, with {n} where a value goes."""
    found = set()
    for f in sorted(SRC.glob("*.kt")):
        if f.name in SKIP:
            continue
        text = re.sub(r'"""[\s\S]*?"""', "", f.read_text(encoding="utf-8"))
        for m in LITERAL.finditer(text):
            s = m.group(1)
            n = iter(range(10))
            s = TEMPLATE.sub(lambda _: "{%d}" % next(n, 9), s).replace('\\"', '"').replace("\\$", "$")
            plain = re.sub(r"\{\d\}", "", s)
            letters = sum(c.isalpha() for c in plain)
            if letters < 2 or len(s) > 140 or "\\" in s or "http" in s or "://" in s:
                continue
            if re.fullmatch(r"[a-z0-9_.:=/#@-]+", plain.strip()) or re.search(r"[=%|^\[\]]", plain):
                continue
            if re.fullmatch(r"[A-Z]{2,4}", plain.strip()):  # currency and other codes
                continue
            # A shown line starts with a capital (or a mark); lower-case ones are sample messages, word lists and pieces.
            first = next((c for c in plain.strip() if not c.isspace()), "")
            if first.isalpha() and not first.isupper():
                continue
            if re.search(r"\d{3}", plain) or re.fullmatch(r"[EdMyHhmsaz ,.:/'-]+", plain):  # samples, date formats
                continue
            if re.search(r"\b(ko|mein|ka|ki|ke|hai|diye|daal|karo|wala|kitna)\b", plain):  # Roman Urdu samples the readers use
                continue
            if "{" in s and sum(c.isalpha() for c in plain) < 3:
                continue
            found.add(s)
    return sorted(found)


SYSTEM = """You translate the screens of Smart Expense, an Android app for keeping a shop's or a family's accounts: money \
spent and received, people who owe or are owed (customers, suppliers, staff), a cashbook, reports, and a chat assistant.
Rules:
- Write it the way a well-made app in that language says it, in plain everyday words a shopkeeper uses.
- Keep it as short as the English: buttons and labels stay short.
- Keep {0}, {1}... exactly, once each, where the sentence needs them.
- Keep emojis, symbols (› ‹ · ✓ ✕ ▾ + − ⤓ ⭐), numbers, currency codes (SAR, PKR) and "Smart Expense" as they are.
- A thing's name (Petrol, Rent, Groceries) is the common word for it.
Translate every item; give back each item's number with its translation."""

SCHEMA = {
    "type": "object",
    "properties": {"items": {"type": "array", "items": {
        "type": "object", "properties": {"i": {"type": "integer"}, "t": {"type": "string"}},
        "required": ["i", "t"], "additionalProperties": False}}},
    "required": ["items"], "additionalProperties": False,
}


def translate(client, lang, todo):
    """[todo] English lines in [lang], 150 at a time."""
    out, used = {}, [0, 0]
    for start in range(0, len(todo), 150):
        part = todo[start:start + 150]
        listing = "\n".join(f"{i}. {s}" for i, s in enumerate(part))
        r = client.messages.create(
            model="claude-opus-5-5", max_tokens=16000, system=SYSTEM,
            output_config={"effort": "low", "format": {"type": "json_schema", "schema": SCHEMA}},
            messages=[{"role": "user", "content": f"Into {LANGS[lang]}:\n{listing}"}],
        )
        used[0] += r.usage.input_tokens
        used[1] += r.usage.output_tokens
        if r.stop_reason != "end_turn":
            print(f"{lang}: part at {start} stopped ({r.stop_reason}), left in English")
            continue
        text = next(b.text for b in r.content if b.type == "text")
        for item in json.loads(text)["items"]:
            i, t = item["i"], item["t"].strip()
            if not (0 <= i < len(part)) or not t:
                continue
            en = part[i]
            # The values must all still be there, or the line stays in English.
            if sorted(re.findall(r"\{\d\}", en)) != sorted(re.findall(r"\{\d\}", t)):
                continue
            # A piece of a longer line keeps its spaces at the ends.
            out[en] = re.match(r"\s*", en).group() + t + re.search(r"\s*$", en).group()
    return out, used


def main():
    keys = words()
    LIST.parent.mkdir(parents=True, exist_ok=True)
    LIST.write_text(json.dumps(keys, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print(f"{len(keys)} English lines")
    OUT.mkdir(parents=True, exist_ok=True)
    client = anthropic.Anthropic(max_retries=8)

    def one(lang):
        """Translates [lang]'s missing lines and writes its table; returns the tokens used."""
        path = OUT / f"{lang}.json"
        have = json.loads(path.read_text(encoding="utf-8")) if path.exists() else {}
        todo = [k for k in keys if k not in have]
        used = [0, 0]
        if todo:
            got, used = translate(client, lang, todo)
            have.update(got)
            print(f"{lang}: {len(got)} of {len(todo)} new lines", flush=True)
        kept = {k: have[k] for k in keys if k in have}
        path.write_text(json.dumps(kept, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
        return used

    # The languages side by side, so a full run takes minutes, not half an hour.
    with ThreadPoolExecutor(max_workers=9) as pool:
        used = list(pool.map(one, sys.argv[1:] or list(LANGS)))
    total = [sum(u[0] for u in used), sum(u[1] for u in used)]
    # Claude Opus 5.5: $4 a million tokens in, $20 out.
    print(f"Tokens: {total[0]} in, {total[1]} out, about ${(total[0] * 4 + total[1] * 20) / 1e6:.2f}")


if __name__ == "__main__":
    main()
