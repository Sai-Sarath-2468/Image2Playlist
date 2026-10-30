from datetime import datetime, timezone

from sqlalchemy import Boolean, DateTime, Float, ForeignKey, Integer, String, Text
from sqlalchemy.orm import Mapped, mapped_column

from image2playlist.database import Base


def now_utc() -> datetime:
    return datetime.now(timezone.utc)


class UploadedImage(Base):
    __tablename__ = "uploaded_images"
    id: Mapped[str] = mapped_column(String(36), primary_key=True)
    original_filename: Mapped[str] = mapped_column(String(255))
    stored_filename: Mapped[str] = mapped_column(String(80), unique=True)
    content_type: Mapped[str] = mapped_column(String(40))
    size: Mapped[int] = mapped_column(Integer)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)


class ImageSession(Base):
    __tablename__ = "image_sessions"
    session_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    image_id: Mapped[str] = mapped_column(ForeignKey("uploaded_images.id"))
    provider: Mapped[str] = mapped_column(String(30))
    status: Mapped[str] = mapped_column(String(40))
    raw_ocr_text: Mapped[str] = mapped_column(Text, default="")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), default=now_utc)


class SongCandidate(Base):
    __tablename__ = "song_candidates"
    candidate_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    session_id: Mapped[str] = mapped_column(ForeignKey("image_sessions.session_id", ondelete="CASCADE"), index=True)
    position: Mapped[int] = mapped_column(Integer)
    original_ocr_text: Mapped[str] = mapped_column(Text)
    title: Mapped[str] = mapped_column(String(500))
    artist: Mapped[str | None] = mapped_column(String(500), nullable=True)
    extraction_confidence: Mapped[float] = mapped_column(Float)
    status: Mapped[str] = mapped_column(String(30), default="NEEDS_REVIEW")


class TrackMatch(Base):
    __tablename__ = "track_matches"
    match_id: Mapped[str] = mapped_column(String(36), primary_key=True)
    candidate_id: Mapped[str] = mapped_column(ForeignKey("song_candidates.candidate_id", ondelete="CASCADE"), index=True)
    provider: Mapped[str] = mapped_column(String(30))
    provider_track_id: Mapped[str] = mapped_column(String(255))
    title: Mapped[str] = mapped_column(String(500))
    artist: Mapped[str] = mapped_column(String(500))
    album: Mapped[str | None] = mapped_column(String(500), nullable=True)
    artwork_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    track_url: Mapped[str | None] = mapped_column(Text, nullable=True)
    match_score: Mapped[float] = mapped_column(Float)
    selected: Mapped[bool] = mapped_column(Boolean, default=False)
