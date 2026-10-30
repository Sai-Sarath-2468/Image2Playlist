import httpx

from image2playlist.extraction import Candidate
from image2playlist.providers import AppleMusicProvider, SpotifyProvider


def response(status: int, body: dict, url: str) -> httpx.Response:
    return httpx.Response(status, json=body, request=httpx.Request("GET", url))


def test_apple_catalog_search_maps_track_and_sends_developer_token(monkeypatch):
    def fake_get(url, *, params, headers, timeout):
        assert "/v1/catalog/us/search" in url
        assert params["term"] == "Blinding Lights The Weeknd"
        assert headers["Authorization"] == "Bearer developer-token"
        return response(200, {"results": {"songs": {"data": [{"id": "apple-1", "attributes": {
            "name": "Blinding Lights", "artistName": "The Weeknd", "albumName": "After Hours",
            "url": "https://music.apple.com/song/apple-1",
            "artwork": {"url": "https://example.test/{w}x{h}.jpg"},
        }}]}}}, url)

    monkeypatch.setattr("image2playlist.providers.httpx.get", fake_get)
    result = AppleMusicProvider("developer-token").search_tracks(
        Candidate(1, "Blinding Lights", "The Weeknd", "", 0.8), "us", None
    )
    assert result[0]["providerTrackId"] == "apple-1"
    assert result[0]["artworkUrl"] == "https://example.test/500x500.jpg"


def test_spotify_search_uses_user_token_and_maps_catalog_result(monkeypatch):
    def fake_get(url, *, params, headers, timeout):
        assert params["type"] == "track"
        assert params["limit"] == 10
        assert headers["Authorization"] == "Bearer access-token"
        return response(200, {"tracks": {"items": [{"id": "spotify-1", "name": "Starboy",
            "artists": [{"name": "The Weeknd"}], "album": {"name": "Starboy", "images": []},
            "external_urls": {"spotify": "https://open.spotify.com/track/spotify-1"}}]}}, url)

    monkeypatch.setattr("image2playlist.providers.httpx.get", fake_get)
    result = SpotifyProvider().search_tracks(Candidate(1, "Starboy", "The Weeknd", "", 0.8), "us", "access-token")
    assert result[0]["providerTrackId"] == "spotify-1"
    assert result[0]["artist"] == "The Weeknd"
