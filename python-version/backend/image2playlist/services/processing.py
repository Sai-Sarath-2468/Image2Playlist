from uuid import uuid4

from sqlalchemy.orm import Session

from image2playlist.errors import AppError, ProviderError
from image2playlist.extraction import Candidate, extract_candidates
from image2playlist.matching import rank_matches
from image2playlist.models import ImageSession, SongCandidate, TrackMatch, UploadedImage
from image2playlist.providers import get_provider
from image2playlist.schemas import session_response
from image2playlist.services.ocr import extract_text
from image2playlist.settings import settings


def _store_matches(db: Session, candidate_id: str, matches: list[dict]) -> None:
    for item in matches:
        db.add(TrackMatch(
            match_id=str(uuid4()), candidate_id=candidate_id, provider=item["provider"],
            provider_track_id=item.get("providerTrackId") or "", title=item.get("title") or "",
            artist=item.get("artist") or "", album=item.get("album"), artwork_url=item.get("artworkUrl"),
            track_url=item.get("trackUrl"), match_score=item.get("matchScore", 0), selected=False,
        ))


def _catalog_search(candidate: Candidate, provider_type: str, storefront: str, access_token: str | None) -> list[dict]:
    provider = get_provider(provider_type, settings.apple_music_developer_token)
    matches = provider.search_tracks(candidate, storefront, access_token)
    return rank_matches(candidate, matches)


def process_image(db: Session, image_id: str, provider_type: str, storefront: str, access_token: str | None):
    image = db.get(UploadedImage, image_id)
    if image is None:
        raise AppError("IMAGE_NOT_FOUND", "Image was not found", 404)
    file_path = settings.upload_dir.resolve() / image.stored_filename
    if file_path.parent != settings.upload_dir.resolve() or not file_path.is_file():
        raise AppError("IMAGE_NOT_FOUND", "Stored image was not found", 404)
    raw_text = extract_text(file_path)
    session = ImageSession(session_id=str(uuid4()), image_id=image_id, provider=provider_type,
                           status="PROCESSING", raw_ocr_text=raw_text)
    db.add(session)
    candidates = extract_candidates(raw_text)
    stored = []
    for item in candidates:
        candidate = SongCandidate(
            candidate_id=str(uuid4()), session_id=session.session_id, position=item.index,
            original_ocr_text=item.raw_text, title=item.title, artist=item.artist,
            extraction_confidence=item.confidence, status="NEEDS_REVIEW",
        )
        db.add(candidate)
        stored.append((item, candidate))
    db.flush()

    try:
        fetched = [(candidate, _catalog_search(item, provider_type, storefront, access_token))
                   for item, candidate in stored]
        for candidate, matches in fetched:
            _store_matches(db, candidate.candidate_id, matches)
        session.status = "READY_FOR_REVIEW"
    except ProviderError as exc:
        session.status = "AWAITING_PROVIDER_CONFIGURATION" if exc.code == "MUSIC_PROVIDER_NOT_CONFIGURED" else (
            "AWAITING_USER_AUTHORIZATION" if exc.code == "MUSIC_AUTH_REQUIRED" else "MATCHING_FAILED"
        )
    db.commit()
    return session_response(db, session)


def get_session(db: Session, session_id: str):
    session = db.get(ImageSession, session_id)
    if session is None:
        raise AppError("SESSION_NOT_FOUND", "Session was not found", 404)
    return session_response(db, session)


def search_again(db: Session, session_id: str, storefront: str, access_token: str | None):
    session = db.get(ImageSession, session_id)
    if session is None:
        raise AppError("SESSION_NOT_FOUND", "Session was not found", 404)
    candidates = db.query(SongCandidate).filter_by(session_id=session_id).order_by(SongCandidate.position).all()
    try:
        staged = []
        for item in candidates:
            data = Candidate(item.position, item.title, item.artist, item.original_ocr_text, item.extraction_confidence)
            staged.append((item, _catalog_search(data, session.provider, storefront, access_token)))
        for item, results in staged:
            db.query(TrackMatch).filter_by(candidate_id=item.candidate_id).delete(synchronize_session=False)
            _store_matches(db, item.candidate_id, results)
        session.status = "READY_FOR_REVIEW"
    except ProviderError as exc:
        session.status = "MATCHING_FAILED"
        db.commit()
        raise exc
    db.commit()
    return session_response(db, session)


def search_one(db: Session, session_id: str, candidate_id: str, storefront: str, access_token: str | None):
    session = db.get(ImageSession, session_id)
    candidate = db.get(SongCandidate, candidate_id)
    if session is None:
        raise AppError("SESSION_NOT_FOUND", "Session was not found", 404)
    if candidate is None or candidate.session_id != session_id:
        raise AppError("SONG_NOT_FOUND", "Song candidate was not found", 404)
    data = Candidate(candidate.position, candidate.title, candidate.artist,
                     candidate.original_ocr_text, candidate.extraction_confidence)
    results = _catalog_search(data, session.provider, storefront, access_token)
    db.query(TrackMatch).filter_by(candidate_id=candidate_id).delete(synchronize_session=False)
    _store_matches(db, candidate_id, results)
    candidate.status = "NEEDS_REVIEW"
    session.status = "READY_FOR_REVIEW"
    db.commit()
    return session_response(db, session)


def update_song(db: Session, session_id: str, candidate_id: str, request):
    session = db.get(ImageSession, session_id)
    candidate = db.get(SongCandidate, candidate_id)
    if session is None:
        raise AppError("SESSION_NOT_FOUND", "Session was not found", 404)
    if candidate is None or candidate.session_id != session_id:
        raise AppError("SONG_NOT_FOUND", "Song candidate was not found", 404)
    if request.title is not None:
        title = request.title.strip()
        if not title:
            raise AppError("VALIDATION_ERROR", "Song title must not be empty")
        candidate.title = title
    if request.artist is not None:
        candidate.artist = request.artist.strip() or None
    matches = db.query(TrackMatch).filter_by(candidate_id=candidate_id).all()
    if request.selectedMatchId and not any(item.match_id == request.selectedMatchId for item in matches):
        raise AppError("SONG_NOT_FOUND", "Selected match was not found for this song", 404)
    for item in matches:
        item.selected = item.match_id == request.selectedMatchId
    candidate.status = "MATCHED" if request.selectedMatchId else "NEEDS_REVIEW"
    db.commit()
    return session_response(db, session)


def remove_song(db: Session, session_id: str, candidate_id: str):
    session = db.get(ImageSession, session_id)
    candidate = db.get(SongCandidate, candidate_id)
    if session is None:
        raise AppError("SESSION_NOT_FOUND", "Session was not found", 404)
    if candidate is None or candidate.session_id != session_id:
        raise AppError("SONG_NOT_FOUND", "Song candidate was not found", 404)
    db.query(TrackMatch).filter_by(candidate_id=candidate_id).delete(synchronize_session=False)
    db.delete(candidate)
    db.commit()
    return session_response(db, session)
