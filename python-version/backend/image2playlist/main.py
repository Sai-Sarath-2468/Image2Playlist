import logging
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from typing import Literal
from uuid import uuid4

from fastapi import Depends, FastAPI, File, Header, Query, UploadFile
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session
from starlette.exceptions import HTTPException as StarletteHTTPException

from image2playlist.database import Base, engine, get_db
from image2playlist.errors import AppError, ProviderError
from image2playlist.models import UploadedImage
from image2playlist.schemas import CreatePlaylistRequest, UpdateSongRequest
from image2playlist.services.image_storage import store_image
from image2playlist.services.ocr import extract_text
from image2playlist.services.playlist import create_playlist
from image2playlist.services.processing import get_session, process_image, remove_song, search_again, search_one, update_song
from image2playlist.settings import settings

logger = logging.getLogger("image2playlist")


@asynccontextmanager
async def lifespan(app: FastAPI):
    Base.metadata.create_all(bind=engine)
    yield


app = FastAPI(title="Image2Playlist API", version="1.0.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=[settings.frontend_origin],
    allow_credentials=False,
    allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
    allow_headers=["Content-Type", "Authorization", "Music-User-Token"],
)


def _error(code: str, message: str, status: int, path: str):
    return JSONResponse(status_code=status, content={
        "error": code, "message": message,
        "timestamp": datetime.now(timezone.utc).isoformat(), "path": path,
    })


@app.exception_handler(AppError)
async def app_error_handler(request, exc: AppError):
    if exc.status_code >= 500:
        logger.exception("Application request failed code=%s path=%s", exc.code, request.url.path, exc_info=exc)
    return _error(exc.code, exc.message, exc.status_code, request.url.path)


@app.exception_handler(RequestValidationError)
async def request_validation_handler(request, exc: RequestValidationError):
    return _error("VALIDATION_ERROR", "The request is invalid", 400, request.url.path)


@app.exception_handler(StarletteHTTPException)
async def http_error_handler(request, exc: StarletteHTTPException):
    return _error("HTTP_ERROR", str(exc.detail), exc.status_code, request.url.path)


@app.exception_handler(Exception)
async def unexpected_error_handler(request, exc: Exception):
    logger.exception("Unhandled application error at %s", request.url.path)
    return _error("INTERNAL_ERROR", "An unexpected error occurred", 500, request.url.path)


def _access_token(authorization: str | None, music_user_token: str | None) -> str | None:
    if authorization and authorization.lower().startswith("bearer "):
        return authorization[7:].strip()
    return music_user_token


@app.get("/api/health")
def health():
    return {"status": "ok"}


@app.get("/api/providers")
def providers():
    return ["APPLE_MUSIC", "SPOTIFY"]


@app.get("/api/providers/apple-music/config")
def apple_music_config():
    if not settings.apple_music_developer_token:
        raise ProviderError("MUSIC_PROVIDER_NOT_CONFIGURED", "Apple Music requires APPLE_MUSIC_DEVELOPER_TOKEN", 503)
    return {"developerToken": settings.apple_music_developer_token,
            "storefront": settings.apple_music_storefront}


@app.post("/api/images/upload")
def upload_image(image: UploadFile = File(...), db: Session = Depends(get_db)):
    content = image.file.read(10 * 1024 * 1024 + 1)
    stored = store_image(image.filename, image.content_type, content)
    image_id = str(uuid4())
    db.add(UploadedImage(
        id=image_id, original_filename=stored["original_filename"],
        stored_filename=stored["stored_filename"], content_type=stored["content_type"], size=stored["size"],
    ))
    db.commit()
    logger.info("Image upload accepted imageId=%s contentType=%s sizeBytes=%d",
                image_id, stored["content_type"], stored["size"])
    return {"imageId": image_id, "originalFileName": stored["original_filename"],
            "contentType": stored["content_type"], "size": stored["size"]}


@app.post("/api/images/{image_id}/ocr")
def ocr_image(image_id: str, db: Session = Depends(get_db)):
    image = db.get(UploadedImage, image_id)
    if image is None:
        raise AppError("IMAGE_NOT_FOUND", "Image was not found", 404)
    path = settings.upload_dir.resolve() / image.stored_filename
    if path.parent != settings.upload_dir.resolve() or not path.is_file():
        raise AppError("IMAGE_NOT_FOUND", "Stored image was not found", 404)
    return {"imageId": image_id, "rawText": extract_text(path)}


@app.post("/api/images/{image_id}/process")
def process(
    image_id: str,
    provider: Literal["APPLE_MUSIC", "SPOTIFY"] = Query(default="APPLE_MUSIC"),
    storefront: str | None = None,
    authorization: str | None = Header(default=None),
    music_user_token: str | None = Header(default=None, alias="Music-User-Token"),
    db: Session = Depends(get_db),
):
    return process_image(db, image_id, provider, _storefront(storefront), _access_token(authorization, music_user_token))


@app.get("/api/sessions/{session_id}")
def get_session_route(session_id: str, db: Session = Depends(get_db)):
    return get_session(db, session_id)


@app.post("/api/sessions/{session_id}/search")
def search_all(
    session_id: str, storefront: str | None = None,
    authorization: str | None = Header(default=None),
    music_user_token: str | None = Header(default=None, alias="Music-User-Token"),
    db: Session = Depends(get_db),
):
    return search_again(db, session_id, _storefront(storefront), _access_token(authorization, music_user_token))


@app.post("/api/sessions/{session_id}/songs/{candidate_id}/search")
def search_single(
    session_id: str, candidate_id: str, storefront: str | None = None,
    authorization: str | None = Header(default=None),
    music_user_token: str | None = Header(default=None, alias="Music-User-Token"),
    db: Session = Depends(get_db),
):
    return search_one(db, session_id, candidate_id, _storefront(storefront),
                      _access_token(authorization, music_user_token))


@app.put("/api/sessions/{session_id}/songs/{candidate_id}")
def update_song_route(session_id: str, candidate_id: str, request: UpdateSongRequest,
                      db: Session = Depends(get_db)):
    return update_song(db, session_id, candidate_id, request)


@app.delete("/api/sessions/{session_id}/songs/{candidate_id}")
def remove_song_route(session_id: str, candidate_id: str, db: Session = Depends(get_db)):
    return remove_song(db, session_id, candidate_id)


@app.post("/api/sessions/{session_id}/playlist")
def create_playlist_route(
    session_id: str, request: CreatePlaylistRequest,
    authorization: str | None = Header(default=None),
    music_user_token: str | None = Header(default=None, alias="Music-User-Token"),
    db: Session = Depends(get_db),
):
    return create_playlist(db, session_id, request.playlistName, request.description,
                           _access_token(authorization, music_user_token))


def _storefront(value: str | None) -> str:
    result = value or settings.apple_music_storefront
    if len(result) != 2 or not result.isalpha():
        raise AppError("VALIDATION_ERROR", "Storefront must be a two-letter country code")
    return result.lower()
