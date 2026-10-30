from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict
from sqlalchemy.engine import URL


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    mysql_url: str = ""
    mysql_username: str = ""
    mysql_password: str = ""
    mysql_database: str = "image2playlist"
    mysql_host: str = "localhost"
    mysql_port: int = 3306
    tessdata_path: str = r"C:\Program Files\Tesseract-OCR\tessdata"
    tesseract_cmd: str = r"C:\Program Files\Tesseract-OCR\tesseract.exe"
    apple_music_developer_token: str = ""
    apple_music_storefront: str = "us"
    frontend_origin: str = "http://127.0.0.1:5173"
    upload_dir: Path = Path("uploads")

    @property
    def database_url(self) -> URL | str:
        if self.mysql_url:
            return self.mysql_url
        if not self.mysql_username or not self.mysql_password:
            raise RuntimeError("Set MYSQL_USERNAME and MYSQL_PASSWORD before starting the backend.")
        return URL.create(
            "mysql+pymysql", username=self.mysql_username, password=self.mysql_password,
            host=self.mysql_host, port=self.mysql_port, database=self.mysql_database,
            query={"charset": "utf8mb4"},
        )


settings = Settings()
