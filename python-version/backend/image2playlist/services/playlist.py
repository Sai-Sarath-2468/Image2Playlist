from image2playlist.errors import AppError, ProviderError
from image2playlist.models import ImageSession, SongCandidate, TrackMatch
from image2playlist.providers import get_provider
from image2playlist.settings import settings


def create_playlist(db, session_id: str, playlist_name: str, description: str | None, access_token: str | None):
    name = playlist_name.strip()
    if not name or len(name) > 150:
        raise AppError("VALIDATION_ERROR", "Playlist name must contain 1 to 150 characters")
    session = db.get(ImageSession, session_id)
    if session is None:
        raise AppError("SESSION_NOT_FOUND", "Session was not found", 404)
    candidates = db.query(SongCandidate).filter_by(session_id=session_id).order_by(SongCandidate.position).all()
    selected = []
    for candidate in candidates:
        match = db.query(TrackMatch).filter_by(candidate_id=candidate.candidate_id, selected=True).first()
        if match:
            selected.append(match)
    if not selected:
        raise AppError("VALIDATION_ERROR", "Select at least one match before creating a playlist")

    seen = set()
    tracks = []
    skipped = len(candidates) - len(selected)
    for match in selected:
        if match.provider_track_id in seen:
            skipped += 1
            continue
        seen.add(match.provider_track_id)
        tracks.append(match)

    provider = get_provider(session.provider, settings.apple_music_developer_token)
    created = provider.create_playlist(name, description, access_token)
    failures = []
    added = 0
    for index, match in enumerate(tracks):
        try:
            provider.add_tracks(created["id"], [match.provider_track_id], access_token)
            added += 1
        except ProviderError as exc:
            failures.append({"title": match.title, "artist": match.artist, "reason": exc.message})
            if exc.code == "MUSIC_RATE_LIMITED":
                failures.extend({"title": remaining.title, "artist": remaining.artist, "reason": exc.message}
                                for remaining in tracks[index + 1:])
                break
    session.status = "PLAYLIST_CREATED" if not failures and skipped == 0 else "PLAYLIST_PARTIAL"
    db.commit()
    return {
        "playlistId": created["id"], "playlistName": name, "playlistUrl": created.get("url"),
        "provider": session.provider, "addedTracks": added, "skippedTracks": skipped,
        "failedTracks": failures,
    }
