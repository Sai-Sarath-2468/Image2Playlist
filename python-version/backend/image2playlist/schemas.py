from pydantic import BaseModel, Field
from image2playlist.models import SongCandidate, TrackMatch


class UpdateSongRequest(BaseModel):
    title: str | None = Field(default=None, max_length=500)
    artist: str | None = Field(default=None, max_length=500)
    selectedMatchId: str | None = None


class CreatePlaylistRequest(BaseModel):
    playlistName: str = Field(min_length=1, max_length=150)
    description: str | None = None


def track_response(match):
    return {
        "matchId": match.match_id,
        "providerTrackId": match.provider_track_id,
        "title": match.title,
        "artist": match.artist,
        "album": match.album,
        "artworkUrl": match.artwork_url,
        "trackUrl": match.track_url,
        "matchScore": round(match.match_score, 1),
        "selected": match.selected,
    }


def session_response(db, session):
    songs = db.query(SongCandidate).filter_by(session_id=session.session_id).order_by(SongCandidate.position).all()
    return {
        "sessionId": session.session_id,
        "imageId": session.image_id,
        "provider": session.provider,
        "status": session.status,
        "rawOcrText": session.raw_ocr_text,
        "songs": [
            {
                "candidateId": song.candidate_id,
                "rawText": song.original_ocr_text,
                "title": song.title,
                "artist": song.artist,
                "extractionConfidence": song.extraction_confidence,
                "status": song.status,
                "matches": [track_response(match) for match in db.query(TrackMatch)
                    .filter_by(candidate_id=song.candidate_id)
                    .order_by(TrackMatch.match_score.desc()).all()],
            }
            for song in songs
        ],
    }
