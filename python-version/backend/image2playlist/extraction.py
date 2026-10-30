import re
import unicodedata
from dataclasses import dataclass

NUMBERED = re.compile(r"^\s*(?:\d+[.)]|[-*•])\s*")
SEPARATOR = re.compile(r"\s+[-–—]\s+")


@dataclass(frozen=True)
class Candidate:
    index: int
    title: str
    artist: str | None
    raw_text: str
    confidence: float


def _key(value: str | None) -> str:
    value = unicodedata.normalize("NFKC", value or "").casefold()
    return re.sub(r"[^\w]", "", value, flags=re.UNICODE)


def extract_candidates(raw_text: str | None) -> list[Candidate]:
    if not raw_text or not raw_text.strip():
        return []
    lines = [NUMBERED.sub("", line.strip()).strip() for line in raw_text.splitlines() if line.strip()]
    lines = [line for line in lines if line]
    result: list[Candidate] = []
    seen: set[str] = set()

    def add(title: str, artist: str | None, raw: str, confidence: float) -> None:
        key = _key(title) + "|" + _key(artist)
        if key in seen:
            return
        seen.add(key)
        result.append(Candidate(len(result) + 1, title, artist, raw, confidence))

    index = 0
    while index < len(lines):
        line = lines[index]
        pair = SEPARATOR.split(line, maxsplit=1)
        if len(pair) == 2 and pair[0].strip() and pair[1].strip():
            add(pair[0].strip(), pair[1].strip(), line, 0.85)
            index += 1
        elif index + 1 < len(lines) and len(SEPARATOR.split(lines[index + 1], maxsplit=1)) == 1:
            add(line, lines[index + 1], line + "\n" + lines[index + 1], 0.70)
            index += 2
        else:
            add(line, None, line, 0.35)
            index += 1
    return result
