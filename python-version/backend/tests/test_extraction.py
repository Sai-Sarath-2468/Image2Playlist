from image2playlist.extraction import extract_candidates


def test_extracts_numbered_and_adjacent_entries_and_removes_exact_duplicates():
    rows = extract_candidates("""
    1. Blinding Lights - The Weeknd
    2. Starboy — The Weeknd
    Blinding Lights - The Weeknd
    As It Was
    Harry Styles
    """)
    assert [(row.title, row.artist) for row in rows] == [
        ("Blinding Lights", "The Weeknd"),
        ("Starboy", "The Weeknd"),
        ("As It Was", "Harry Styles"),
    ]
    assert [row.index for row in rows] == [1, 2, 3]


def test_preserves_uncertain_text_as_a_candidate():
    row = extract_candidates("Only a song title")[0]
    assert row.title == "Only a song title"
    assert row.artist is None
    assert row.raw_text == "Only a song title"
