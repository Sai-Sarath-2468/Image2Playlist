# Image2Playlist

Image2Playlist turns screenshots, posters, and song lists into a playlist. It uploads and validates an image, reads text with Tesseract, extracts song candidates, finds catalog matches on Apple Music or Spotify, and lets the user review every match before creating a playlist.

> **Project status: Incomplete.** The application code and provider integrations are present, but live music-service use is not ready until the external credentials are generated and configured. Apple Music still needs a valid developer token generated from an Apple Music API key. Spotify still needs a registered Spotify developer app and its client ID configured. Until those steps are completed, provider search and playlist creation cannot be fully verified. See the setup sections below and `python-version/README.md` for Python edition instructions.

## Current features

- Upload PNG, JPEG, and WEBP images up to 10 MB.
- Store files under server-generated UUID names; keep metadata in MySQL.
- Verify file signatures before saving uploads.
- Extract raw text with Tess4J/Tesseract.
- Parse delimited lines and adjacent title/artist lines into ordered candidates.
- Remove exact normalized duplicates without merging merely similar titles.
- Search Apple Music or Spotify catalogs and rank results with an application-level title/artist similarity score.
- Persist processing sessions, candidates, and matches in MySQL.
- Edit or remove candidates, choose matches, and create a playlist only after review.
- Use MusicKit on the Web for Apple Music authorization and OAuth Authorization Code with PKCE for Spotify. Access tokens stay in browser memory; the app never asks for music-service passwords.

Spotify catalog search, private playlist creation, and playlist item insertion are implemented through the same provider interface as Apple Music.

## Architecture

```text
React + Vite
    │ REST
Spring Boot controllers
    ├── Image validation and filesystem storage
    ├── Tess4J OCR → song candidate extraction
    ├── Apple Music / Spotify catalog search → app-side match scoring
    ├── MySQL session and review persistence
    └── Provider playlist operations (MusicKit user token or Spotify OAuth access token)
```

OCR, candidate extraction, provider search, matching, and playlist creation are separate services. Uploaded image bytes stay on disk; MySQL stores filenames and other metadata.

## Requirements

- Java 21
- Maven 3.9+ (or the included Maven Wrapper)
- Node.js 24+ and npm
- MySQL 8.4+ for local application use
- Tesseract OCR with English language data
- An Apple Music developer token for catalog search and playlist requests
- A Spotify app client ID for Spotify login (no client secret is used by the browser PKCE flow)

## Local setup

### 1. Configure environment variables

Copy `.env.example` values into your IntelliJ run configuration or shell environment. Configure the Spotify client ID separately in `frontend/.env`. Do not commit real tokens or passwords.

Required for the backend:

| Variable | Purpose |
| --- | --- |
| `MYSQL_URL` | JDBC URL, for example `jdbc:mysql://localhost:3306/image2playlist` |
| `MYSQL_USERNAME` | MySQL application user |
| `MYSQL_PASSWORD` | MySQL application password |
| `TESSDATA_PATH` | Directory containing `eng.traineddata`; on the supplied Windows setup, `C:/Program Files/Tesseract-OCR/tessdata` |
| `APPLE_MUSIC_DEVELOPER_TOKEN` | Signed Apple Music developer token |

Optional variables include `APPLE_MUSIC_STOREFRONT` (defaults to `us`) and `FRONTEND_ORIGIN` (defaults to `http://127.0.0.1:5173`). The configured database must exist and the application user needs permission to create/update tables during development.

Create an Apple Music developer token through Apple's developer account and keep its signing private key out of this project. The signed developer token is supplied to the browser for MusicKit configuration; the private key is never sent to the browser. Apple Music's web authorization returns a Music User Token for user-specific playlist operations.

For Spotify, create an app in the Spotify Developer Dashboard. Copy `frontend/.env.example` to `frontend/.env`, set `VITE_SPOTIFY_CLIENT_ID`, register the exact redirect URI `http://127.0.0.1:5173/` in the Spotify app, then restart Vite. Spotify does not accept `localhost` as a redirect URI; the app uses the explicit loopback IP. The browser uses Authorization Code with PKCE and does not need a client secret. For Docker Compose, set `VITE_SPOTIFY_CLIENT_ID` and `VITE_SPOTIFY_REDIRECT_URI=http://127.0.0.1:8081/` in the root `.env`, and register that URI too. For a deployed site, use and register its exact HTTPS callback URI.

### 2. Run the backend

From the project root:

```powershell
.\mvnw.cmd spring-boot:run
```

The API listens on `http://localhost:8080`.

### 3. Run the frontend

```powershell
cd frontend
npm install
npm run dev
```

Open `http://127.0.0.1:5173`. Vite proxies `/api` calls to the backend.

## Docker Compose

Copy `.env.example` to `.env`, fill in MySQL credentials and the Apple Music developer token, and set the Spotify client ID if you plan to use Spotify, then run:

```powershell
docker compose up --build
```

The frontend is served at `http://127.0.0.1:8081`, the backend at `http://localhost:8080`, and MySQL data and uploaded files use persistent Docker volumes. Use the `127.0.0.1` frontend address so Spotify’s popup can return to the same browser origin. The backend image installs Tesseract and English language data.

## API

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/health` | Basic health check |
| `GET` | `/api/providers` | List configured providers |
| `GET` | `/api/providers/apple-music/config` | MusicKit browser configuration |
| `POST` | `/api/images/upload` | Validate and save an image; returns `imageId` |
| `POST` | `/api/images/{imageId}/ocr` | Return raw OCR text |
| `POST` | `/api/images/{imageId}/process` | Create a processing and review session |
| `GET` | `/api/sessions/{sessionId}` | Retrieve OCR text, candidates, and matches |
| `POST` | `/api/sessions/{sessionId}/search` | Search again after editing candidate text |
| `POST` | `/api/sessions/{sessionId}/songs/{candidateId}/search` | Search alternatives for one edited song |
| `PUT` | `/api/sessions/{sessionId}/songs/{candidateId}` | Edit a song and/or select a match |
| `DELETE` | `/api/sessions/{sessionId}/songs/{candidateId}` | Remove a candidate |
| `POST` | `/api/sessions/{sessionId}/playlist` | Create a playlist from selected matches |

Spotify requests send the short-lived OAuth access token as `Authorization: Bearer ...`. Apple Music playlist requests use `Music-User-Token`. Neither token is stored by the backend.

## Tests and builds

Backend tests use H2 and mock OCR/provider network calls:

```powershell
.\mvnw.cmd clean test
```

Frontend production build:

```powershell
cd frontend
npm run build
```

## Notes and next work

- OCR output depends on screenshot quality and may include noise; every extracted candidate and provider match remains editable.
- The matching score is calculated by Image2Playlist and is not an Apple-provided confidence value.
- MySQL schema creation is set to Hibernate `update` for local development. Use versioned migrations before a production deployment.
- Apple catalog results and playlist behavior require valid developer and user authorization. Spotify calls require valid OAuth authorization and are mocked in tests.
- Richer extraction patterns, migrations, and deployment hardening remain future work.
