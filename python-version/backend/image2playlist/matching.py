import re
import unicodedata
from difflib import SequenceMatcher


def normalize(text: str | None) -> str:
    text = unicodedata.normalize("NFKD", text or "").casefold()
    text = "".join(char for char in text if not unicodedata.combining(char))
    return re.sub(r"[^a-z0-9]+", " ", text).strip()


def _similarity(left: str | None, right: str | None) -> float:
    a, b = normalize(left), normalize(right)
    if not a or not b:
        return 0.0
    if a == b:
        return 1.0
    sequence = SequenceMatcher(None, a, b).ratio()
    a_tokens, b_tokens = set(a.split()), set(b.split())
    overlap = 2 * len(a_tokens & b_tokens) / (len(a_tokens) + len(b_tokens)) if a_tokens | b_tokens else 0
    return 0.7 * sequence + 0.3 * overlap


def rank_matches(candidate, results: list[dict]) -> list[dict]:
    for item in results:
        title = _similarity(candidate.title, item.get("title"))
        artist = _similarity(candidate.artist, item.get("artist")) if candidate.artist else title
        item["matchScore"] = round((0.7 * title + 0.3 * artist) * 100, 1)
    return sorted(results, key=lambda item: item["matchScore"], reverse=True)
