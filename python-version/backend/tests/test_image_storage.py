from io import BytesIO

import pytest
from PIL import Image

from image2playlist.errors import AppError
from image2playlist.services import image_storage


def png_bytes():
    output = BytesIO()
    Image.new("RGB", (2, 2), "white").save(output, format="PNG")
    return output.getvalue()


def test_stores_using_generated_png_filename_and_sanitized_original_name(tmp_path, monkeypatch):
    monkeypatch.setattr(image_storage.settings, "upload_dir", tmp_path)
    stored = image_storage.store_image(r"..\..\song.png", "image/png", png_bytes())
    assert stored["stored_filename"].endswith(".png")
    assert stored["original_filename"] == "song.png"
    assert stored["path"].parent == tmp_path.resolve()
    assert stored["path"].is_file()


def test_rejects_fake_image_bytes(tmp_path, monkeypatch):
    monkeypatch.setattr(image_storage.settings, "upload_dir", tmp_path)
    with pytest.raises(AppError) as error:
        image_storage.store_image("fake.png", "image/png", b"not actually a png")
    assert error.value.code == "INVALID_IMAGE"


@pytest.mark.parametrize("image_format,content_type,extension", [
    ("PNG", "image/png", ".png"),
    ("JPEG", "image/jpeg", ".jpg"),
    ("WEBP", "image/webp", ".webp"),
])
def test_accepts_supported_signatures(tmp_path, monkeypatch, image_format, content_type, extension):
    monkeypatch.setattr(image_storage.settings, "upload_dir", tmp_path)
    output = BytesIO()
    Image.new("RGB", (2, 2), "white").save(output, format=image_format)
    result = image_storage.store_image("upload", content_type, output.getvalue())
    assert result["stored_filename"].endswith(extension)


def test_rejects_uploads_over_ten_megabytes():
    with pytest.raises(AppError) as error:
        image_storage.store_image("large.png", "image/png", b"x" * (10 * 1024 * 1024 + 1))
    assert error.value.code == "IMAGE_TOO_LARGE"


@pytest.mark.parametrize("image_format,content_type,extension", [
    ("PNG", "image/png", ".png"),
    ("JPEG", "image/jpeg", ".jpg"),
    ("WEBP", "image/webp", ".webp"),
])
def test_accepts_supported_signatures(tmp_path, monkeypatch, image_format, content_type, extension):
    monkeypatch.setattr(image_storage.settings, "upload_dir", tmp_path)
    output = BytesIO()
    Image.new("RGB", (2, 2), "white").save(output, format=image_format)
    result = image_storage.store_image("upload", content_type, output.getvalue())
    assert result["stored_filename"].endswith(extension)


def test_rejects_uploads_over_ten_megabytes():
    with pytest.raises(AppError) as error:
        image_storage.store_image("large.png", "image/png", b"x" * (10 * 1024 * 1024 + 1))
    assert error.value.code == "IMAGE_TOO_LARGE"
