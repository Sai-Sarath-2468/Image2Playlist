# Image2Playlist — Python edition

This is a separate Python backend version of Image2Playlist. The original Java/Spring project remains in the parent folder. This edition keeps the React/Vite user interface and replaces the Java backend with Python and FastAPI.

> **Project status: Incomplete.** The Python application and Apple Music/Spotify provider code are present, but the required provider credentials have not yet been generated and configured. Apple Music needs a valid developer token generated from an Apple Music API key. Spotify needs a registered Spotify developer app and its client ID configured. Until these external setup steps are complete, live provider search and playlist creation cannot be fully verified.

## What it does

1. Accepts PNG, JPEG, and WEBP screenshots up to 10 MB.
2. Verifies the image bytes and saves it with a generated UUID filename.
3. Runs Tesseract OCR through `pytesseract`.
4. Extracts ordered title/artist candidates and removes exact duplicates.
5. Searches Apple Music or Spotify and calculates an application-side match score.
6. Saves image metadata, sessions, candidates, and matches in MySQL.
7. Lets the user edit candidates, search again, select matches, and remove songs.
8. Creates a playlist only after the user reviews and selects tracks.

Apple Music uses MusicKit user authorization for playlist operations. Spotify uses browser Authorization Code with PKCE. User tokens stay in browser memory and are passed to the backend only for the current request. The app does not request service passwords or expose a Spotify client secret.

## Project structure

```text
python-version/
├── backend/
│   ├── image2playlist/       # FastAPI API, SQLAlchemy models, OCR, providers and services
│   ├── tests/                # Extraction, matching and image validation tests
│   ├── requirements.txt
│   └── Dockerfile
├── frontend/                 # Independent React/Vite UI
├── compose.yaml
└── README.md
```

The backend separates image storage, OCR, song extraction, matching, provider APIs, session review, and playlist operations. MySQL stores metadata and review state; uploaded image bytes remain in `backend/uploads/`.

## Requirements

- Python 3.12 or later (the current workstation has Python 3.14)
- Node.js 24+ and npm
- MySQL 8.4+ and a local application database/user
- Tesseract OCR with the English language data
- Apple Music developer token for Apple Music search and playlist operations
- Spotify app client ID if using Spotify

Tesseract is already installed at `C:\Program Files\Tesseract-OCR` on the current Windows workstation.

## Local setup

### 1. Prepare MySQL

Create the database and an application account once, using your own password:

```sql
CREATE DATABASE image2playlist CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'image2playlist_app'@'localhost' IDENTIFIED BY 'choose-a-local-password';
GRANT ALL PRIVILEGES ON image2playlist.* TO 'image2playlist_app'@'localhost';
```

### 2. Configure and run the Python backend

In PowerShell:

```powershell
cd D:\project\image2playlist\python-version\backend
py -3.14 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install --upgrade pip
pip install -r requirements.txt
Copy-Item .env.example .env
```

Edit `backend/.env` and set `MYSQL_USERNAME`, `MYSQL_PASSWORD`, and (for Apple Music) `APPLE_MUSIC_DEVELOPER_TOKEN`. The Tesseract defaults match the current Windows installation. Keep `.env` private; it is ignored by Git.

Start the API:

```powershell
python -m uvicorn image2playlist.main:app --reload --host 127.0.0.1 --port 8000
```

The health endpoint is `http://127.0.0.1:8000/api/health`.

### 3. Run the React frontend

Open a second PowerShell window:

```powershell
cd D:\project\image2playlist\python-version\frontend
npm install
```

For Spotify, copy `.env.example` to `.env`, set `VITE_SPOTIFY_CLIENT_ID`, and register this exact redirect URI in the Spotify Developer Dashboard:

```text
http://127.0.0.1:5173/
```

Then run:

```powershell
npm run dev
```

Open `http://127.0.0.1:5173`. Vite proxies `/api` to the Python backend on port 8000.

For Apple Music, the backend needs `APPLE_MUSIC_DEVELOPER_TOKEN`; the browser requests MusicKit authorization when you connect your account. For Spotify, only the client ID goes in the frontend configuration; no client secret is used.

## Docker Compose

From `python-version`, copy `.env.example` to `.env`, replace the MySQL passwords, add the Apple token and Spotify client ID if needed, then run:

```powershell
docker compose up --build
```

Open `http://127.0.0.1:8081`. The Python image installs Tesseract and English language data. MySQL and uploaded images use persistent volumes. The Spotify redirect URI for this setup is `http://127.0.0.1:8081/` and must be registered exactly.

## API routes

| Method | Route | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Health check |
| GET | `/api/providers` | List Apple Music and Spotify |
| GET | `/api/providers/apple-music/config` | MusicKit configuration |
| POST | `/api/images/upload` | Validate and store an image |
| POST | `/api/images/{imageId}/ocr` | Return recognized text |
| POST | `/api/images/{imageId}/process` | OCR, extract candidates, and search |
| GET | `/api/sessions/{sessionId}` | Load review session |
| POST | `/api/sessions/{sessionId}/search` | Refresh all candidate matches |
| POST | `/api/sessions/{sessionId}/songs/{candidateId}/search` | Refresh one candidate |
| PUT | `/api/sessions/{sessionId}/songs/{candidateId}` | Edit a candidate or select a match |
| DELETE | `/api/sessions/{sessionId}/songs/{candidateId}` | Remove a candidate |
| POST | `/api/sessions/{sessionId}/playlist` | Create from selected matches |

Provider calls use short-lived request tokens: Spotify uses `Authorization: Bearer ...`; Apple Music uses `Music-User-Token`. Tokens are not persisted.

## Development checks

```powershell
cd backend
pip install -r requirements-dev.txt
pytest
```

```powershell
cd frontend
npm run build
```

External music-service requests are designed to be mocked in tests. Live Apple Music or Spotify calls require the user's own provider configuration.
