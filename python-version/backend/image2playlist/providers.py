from typing import Protocol
from urllib.parse import quote

import httpx

from image2playlist.errors import ProviderError

HTTP_TIMEOUT = 20.0


def _provider_error(response: httpx.Response, message: str) -> ProviderError:
    status = response.status_code
    if status in (401, 403):
        return ProviderError("MUSIC_AUTH_REQUIRED", message, 401)
    if status == 429:
        retry = response.headers.get("Retry-After")
        suffix = f" Try again in {retry} seconds." if retry else ""
        return ProviderError("MUSIC_RATE_LIMITED", message + suffix, 429)
    return ProviderError("MUSIC_PROVIDER_ERROR", f"{message} (HTTP {status})", 502)


class MusicProvider(Protocol):
    provider_type: str
    def search_tracks(self, candidate, storefront: str, access_token: str | None) -> list[dict]: ...
    def create_playlist(self, name: str, description: str | None, access_token: str | None) -> dict: ...
    def add_tracks(self, playlist_id: str, track_ids: list[str], access_token: str | None) -> None: ...


class AppleMusicProvider:
    provider_type = "APPLE_MUSIC"
    base_url = "https://api.music.apple.com"

    def __init__(self, developer_token: str):
        self.developer_token = developer_token

    def _developer_headers(self) -> dict[str, str]:
        if not self.developer_token:
            raise ProviderError("MUSIC_PROVIDER_NOT_CONFIGURED", "Apple Music requires APPLE_MUSIC_DEVELOPER_TOKEN", 503)
        return {"Authorization": f"Bearer {self.developer_token}"}

    def _user_headers(self, token: str | None) -> dict[str, str]:
        if not token:
            raise ProviderError("MUSIC_AUTH_REQUIRED", "Apple Music user authorization is required", 401)
        return {**self._developer_headers(), "Music-User-Token": token}

    def search_tracks(self, candidate, storefront: str, access_token: str | None) -> list[dict]:
        query = candidate.title + (f" {candidate.artist}" if candidate.artist else "")
        try:
            response = httpx.get(
                f"{self.base_url}/v1/catalog/{storefront.lower()}/search",
                params={"term": query, "types": "songs", "limit": 10},
                headers=self._developer_headers(), timeout=HTTP_TIMEOUT,
            )
        except httpx.HTTPError as exc:
            raise ProviderError("MUSIC_PROVIDER_ERROR", "Apple Music catalog search failed", 502) from exc
        if not response.is_success:
            raise _provider_error(response, "Apple Music catalog search failed")
        data = response.json().get("results", {}).get("songs", {}).get("data", [])
        results = []
        for song in data:
            attrs = song.get("attributes", {})
            artwork = attrs.get("artwork", {}).get("url")
            if artwork:
                artwork = artwork.replace("{w}", "500").replace("{h}", "500")
            results.append({
                "provider": self.provider_type, "providerTrackId": song.get("id"),
                "title": attrs.get("name"), "artist": attrs.get("artistName"),
                "album": attrs.get("albumName"), "artworkUrl": artwork,
                "trackUrl": attrs.get("url"),
            })
        return results

    def create_playlist(self, name: str, description: str | None, access_token: str | None) -> dict:
        try:
            response = httpx.post(
                f"{self.base_url}/v1/me/library/playlists",
                headers=self._user_headers(access_token),
                json={"attributes": {"name": name, **({"description": description} if description else {})}},
                timeout=HTTP_TIMEOUT,
            )
        except httpx.HTTPError as exc:
            raise ProviderError("PLAYLIST_CREATION_FAILED", "Apple Music playlist creation failed", 502) from exc
        if not response.is_success:
            raise _provider_error(response, "Apple Music playlist creation failed")
        items = response.json().get("data", [])
        if not items or not items[0].get("id"):
            raise ProviderError("PLAYLIST_CREATION_FAILED", "Apple Music did not return a playlist ID", 502)
        playlist_id = items[0]["id"]
        return {"id": playlist_id, "url": f"https://music.apple.com/library/playlist/{playlist_id}"}

    def add_tracks(self, playlist_id: str, track_ids: list[str], access_token: str | None) -> None:
        if not track_ids:
            return
        try:
            response = httpx.post(
                f"{self.base_url}/v1/me/library/playlists/{quote(playlist_id, safe='')}/tracks",
                headers=self._user_headers(access_token),
                json={"data": [{"id": track_id, "type": "songs"} for track_id in track_ids]},
                timeout=HTTP_TIMEOUT,
            )
        except httpx.HTTPError as exc:
            raise ProviderError("PLAYLIST_CREATION_FAILED", "Apple Music could not add tracks", 502) from exc
        if not response.is_success:
            raise _provider_error(response, "Apple Music could not add tracks")


class SpotifyProvider:
    provider_type = "SPOTIFY"
    base_url = "https://api.spotify.com/v1"

    @staticmethod
    def _headers(token: str | None) -> dict[str, str]:
        if not token:
            raise ProviderError("MUSIC_AUTH_REQUIRED", "Spotify authorization is required", 401)
        return {"Authorization": f"Bearer {token}"}

    def search_tracks(self, candidate, storefront: str, access_token: str | None) -> list[dict]:
        query = f"track:{candidate.title} artist:{candidate.artist}" if candidate.artist else candidate.title
        try:
            response = httpx.get(
                f"{self.base_url}/search", params={"q": query, "type": "track", "limit": 10, "market": storefront.upper()},
                headers=self._headers(access_token), timeout=HTTP_TIMEOUT,
            )
        except httpx.HTTPError as exc:
            raise ProviderError("MUSIC_PROVIDER_ERROR", "Spotify catalog search failed", 502) from exc
        if not response.is_success:
            raise _provider_error(response, "Spotify catalog search failed")
        results = []
        for track in response.json().get("tracks", {}).get("items", []):
            album = track.get("album", {})
            images = album.get("images", [])
            results.append({
                "provider": self.provider_type, "providerTrackId": track.get("id"),
                "title": track.get("name"),
                "artist": ", ".join(item.get("name", "") for item in track.get("artists", [])),
                "album": album.get("name"), "artworkUrl": images[0].get("url") if images else None,
                "trackUrl": track.get("external_urls", {}).get("spotify"),
            })
        return results

    def create_playlist(self, name: str, description: str | None, access_token: str | None) -> dict:
        try:
            response = httpx.post(
                f"{self.base_url}/me/playlists", headers=self._headers(access_token),
                json={"name": name, "public": False, **({"description": description} if description else {})},
                timeout=HTTP_TIMEOUT,
            )
        except httpx.HTTPError as exc:
            raise ProviderError("PLAYLIST_CREATION_FAILED", "Spotify playlist creation failed", 502) from exc
        if not response.is_success:
            raise _provider_error(response, "Spotify playlist creation failed")
        body = response.json()
        playlist_id = body.get("id")
        if not playlist_id:
            raise ProviderError("PLAYLIST_CREATION_FAILED", "Spotify did not return a playlist ID", 502)
        return {"id": playlist_id, "url": body.get("external_urls", {}).get("spotify")}

    def add_tracks(self, playlist_id: str, track_ids: list[str], access_token: str | None) -> None:
        if not track_ids:
            return
        uris = [item if item.startswith("spotify:track:") else f"spotify:track:{item}" for item in dict.fromkeys(track_ids)]
        for start in range(0, len(uris), 100):
            try:
                response = httpx.post(
                    f"{self.base_url}/playlists/{quote(playlist_id, safe='')}/items",
                    headers=self._headers(access_token),
                    json={"uris": uris[start:start + 100]}, timeout=HTTP_TIMEOUT,
                )
            except httpx.HTTPError as exc:
                raise ProviderError("PLAYLIST_CREATION_FAILED", "Spotify could not add playlist items", 502) from exc
            if not response.is_success:
                raise _provider_error(response, "Spotify could not add playlist items")


def get_provider(provider_type: str, developer_token: str) -> MusicProvider:
    if provider_type == "APPLE_MUSIC":
        return AppleMusicProvider(developer_token)
    if provider_type == "SPOTIFY":
        return SpotifyProvider()
    raise ProviderError("VALIDATION_ERROR", "Unsupported music provider", 400)
