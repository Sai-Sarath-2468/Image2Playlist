from pathlib import Path

import pytesseract
from PIL import Image

from image2playlist.errors import AppError
from image2playlist.settings import settings


def extract_text(image_path: Path) -> str:
    try:
        command = Path(settings.tesseract_cmd)
        if command.is_file():
            pytesseract.pytesseract.tesseract_cmd = str(command)
        config = f'--tessdata-dir "{settings.tessdata_path}"'
        with Image.open(image_path) as image:
            return pytesseract.image_to_string(image, lang="eng", config=config)
    except Exception as exc:
        raise AppError("OCR_FAILED", "Failed to extract text from image", 500) from exc
