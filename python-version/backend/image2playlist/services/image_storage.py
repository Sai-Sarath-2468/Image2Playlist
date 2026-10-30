from io import BytesIO
from pathlib import Path, PureWindowsPath
from uuid import uuid4

from PIL import Image, UnidentifiedImageError

from image2playlist.errors import AppError
from image2playlist.settings import settings

MAX_IMAGE_SIZE = 10 * 1024 * 1024
FORMATS = {"PNG": ("image/png", ".png"), "JPEG": ("image/jpeg", ".jpg"), "WEBP": ("image/webp", ".webp")}


def store_image(original_filename: str | None, content_type: str | None, data: bytes) -> dict:
    if not data:
        raise AppError("INVALID_IMAGE", "Image file must not be empty")
    if len(data) > MAX_IMAGE_SIZE:
        raise AppError("IMAGE_TOO_LARGE", "Image must be 10 MB or smaller", 413)
    try:
        image = Image.open(BytesIO(data))
        image_format = image.format
        image.verify()
    except (UnidentifiedImageError, OSError, ValueError):
        raise AppError("INVALID_IMAGE", "The uploaded file is not a supported image") from None
    if image_format not in FORMATS:
        raise AppError("UNSUPPORTED_IMAGE_TYPE", "Only PNG, JPEG and WEBP images are supported", 415)
    expected_type, extension = FORMATS[image_format]
    if content_type != expected_type:
        raise AppError("UNSUPPORTED_IMAGE_TYPE", "Image content type does not match its file format", 415)

    root = settings.upload_dir.resolve()
    root.mkdir(parents=True, exist_ok=True)
    stored_filename = f"{uuid4()}{extension}"
    path = (root / stored_filename).resolve()
    if path.parent != root:
        raise AppError("INVALID_IMAGE", "Invalid generated image path")
    path.write_bytes(data)
    original = PureWindowsPath(original_filename or "image").name
    original = Path(original).name[:255] or "image"
    return {"path": path, "stored_filename": stored_filename, "original_filename": original,
            "content_type": expected_type, "size": len(data)}
