from image2playlist.extraction import Candidate
from image2playlist.matching import rank_matches


def test_exact_match_ranks_first_and_uses_application_score():
    candidate = Candidate(1, "Blinding Lights", "The Weeknd", "", 0.8)
    results = [
        {"title": "Blinding Lights (Live)", "artist": "The Weeknd"},
        {"title": "Blinding Lights", "artist": "The Weeknd"},
    ]
    ranked = rank_matches(candidate, results)
    assert ranked[0]["title"] == "Blinding Lights"
    assert ranked[0]["matchScore"] == 100.0
    assert "matchScore" in ranked[1]
