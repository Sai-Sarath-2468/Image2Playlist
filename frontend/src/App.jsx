import { useEffect, useMemo, useRef, useState } from 'react'
import { api } from './api.js'
import { authorizeAppleMusic } from './musicKit.js'
import { authorizeSpotify, completeSpotifyPopup } from './spotify.js'

const ACCEPTED = ['image/png', 'image/jpeg', 'image/webp']
const MAX_BYTES = 10 * 1024 * 1024
const SPOTIFY_CLIENT_ID = import.meta.env.VITE_SPOTIFY_CLIENT_ID || ''

export default function App() {
  const fileInput = useRef(null)
  const [file, setFile] = useState(null)
  const [session, setSession] = useState(null)
  const [playlistName, setPlaylistName] = useState('Image2Playlist Mix')
  const [musicUserToken, setMusicUserToken] = useState('')
  const [spotifyAccessToken, setSpotifyAccessToken] = useState('')
  const [provider, setProvider] = useState('APPLE_MUSIC')
  const [playlist, setPlaylist] = useState(null)
  const [busy, setBusy] = useState('')
  const [dragging, setDragging] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [showRawText, setShowRawText] = useState(false)
  const [edits, setEdits] = useState({})

  const selectedCount = useMemo(
    () => session?.songs?.filter((song) => song.matches?.some((match) => match.selected)).length || 0,
    [session],
  )

  useEffect(() => completeSpotifyPopup(), [])

  function chooseFile(nextFile) {
    setError('')
    setNotice('')
    setSession(null)
    setPlaylist(null)
    if (!nextFile) return
    if (!ACCEPTED.includes(nextFile.type)) {
      setError('Choose a PNG, JPEG, or WEBP image.')
      return
    }
    if (nextFile.size > MAX_BYTES) {
      setError('The image must be 10 MB or smaller.')
      return
    }
    setFile(nextFile)
  }

  async function startProcessing() {
    if (!file) return
    setBusy('processing')
    setError('')
    setNotice('Uploading image…')
    try {
      const uploaded = await api.upload(file)
      setNotice('Reading text and finding songs…')
      const result = await api.process(uploaded.imageId, provider,
        provider === 'SPOTIFY' ? spotifyAccessToken : '')
      setSession(result)
      setEdits(Object.fromEntries(result.songs.map((song) => [song.candidateId, {
        title: song.title,
        artist: song.artist || '',
      }])))
      setFile(null)
      const providerLabel = provider === 'SPOTIFY' ? 'Spotify' : 'Apple Music'
      setNotice(result.status === 'AWAITING_PROVIDER_CONFIGURATION'
        ? `Text was extracted. ${providerLabel} search needs provider configuration.`
        : result.status === 'AWAITING_USER_AUTHORIZATION'
          ? `Connect ${providerLabel}, then search again.`
          : `${result.songs.length} song candidates ready to review.`)
    } catch (cause) {
      setError(cause.message)
      setNotice('')
    } finally {
      setBusy('')
    }
  }

  function editSong(song, field, value) {
    setEdits((current) => ({
      ...current,
      [song.candidateId]: { ...current[song.candidateId], [field]: value },
    }))
  }

  async function saveSong(song, selectedMatchId = null) {
    const values = edits[song.candidateId] || { title: song.title, artist: song.artist || '' }
    const updated = await api.updateSong(session.sessionId, song.candidateId, {
      title: values.title,
      artist: values.artist,
      selectedMatchId,
    })
    setSession(updated)
  }

  async function chooseMatch(song, matchId) {
    setBusy(song.candidateId)
    setError('')
    try {
      await saveSong(song, matchId)
    } catch (cause) {
      setError(cause.message)
    } finally {
      setBusy('')
    }
  }

  async function removeSong(song) {
    setBusy(song.candidateId)
    setError('')
    try {
      setSession(await api.removeSong(session.sessionId, song.candidateId))
    } catch (cause) {
      setError(cause.message)
    } finally {
      setBusy('')
    }
  }

  async function searchSong(song) {
    setBusy(song.candidateId)
    setError('')
    try {
      const values = edits[song.candidateId] || { title: song.title, artist: song.artist || '' }
      await api.updateSong(session.sessionId, song.candidateId, {
        title: values.title,
        artist: values.artist,
        selectedMatchId: null,
      })
      setSession(await api.searchSong(session.sessionId, song.candidateId, 'us',
        provider === 'SPOTIFY' ? spotifyAccessToken : ''))
    } catch (cause) {
      setError(cause.message)
    } finally {
      setBusy('')
    }
  }

  async function searchAgain() {
    if (!session) return
    setBusy('search')
    setError('')
    setNotice('Saving edits and searching Apple Music…')
    try {
      let latest = session
      for (const song of session.songs) {
        const values = edits[song.candidateId] || { title: song.title, artist: song.artist || '' }
        latest = await api.updateSong(session.sessionId, song.candidateId, {
          title: values.title,
          artist: values.artist,
          selectedMatchId: null,
        })
      }
      const result = await api.searchAgain(session.sessionId, 'us',
        provider === 'SPOTIFY' ? spotifyAccessToken : '')
      setSession(result)
      setNotice('New suggestions are ready. Review and select the matches you want.')
    } catch (cause) {
      setError(cause.message)
      setNotice('')
    } finally {
      setBusy('')
    }
  }

  async function connectAppleMusic() {
    setBusy('authorize')
    setError('')
    try {
      const config = await api.getAppleMusicConfig()
      const token = await authorizeAppleMusic(config.developerToken)
      setMusicUserToken(token)
      setNotice('Apple Music is connected for this session.')
    } catch (cause) {
      setError(cause.message)
    } finally {
      setBusy('')
    }
  }

  async function connectSpotify() {
    setBusy('authorize')
    setError('')
    try {
      setSpotifyAccessToken(await authorizeSpotify(SPOTIFY_CLIENT_ID))
      setNotice('Spotify is connected for this session.')
    } catch (cause) {
      setError(cause.message)
    } finally {
      setBusy('')
    }
  }

  async function createPlaylist() {
    const accessToken = provider === 'SPOTIFY' ? spotifyAccessToken : musicUserToken
    if (!session || !accessToken) return
    setBusy('playlist')
    setError('')
    try {
      const result = await api.createPlaylist(session.sessionId, { playlistName }, provider, accessToken)
      setPlaylist(result)
      setNotice('Your playlist is ready.')
    } catch (cause) {
      setError(cause.message)
    } finally {
      setBusy('')
    }
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="#top" aria-label="Image2Playlist home">
          <span className="brand-mark">♫</span><span>image<span className="brand-accent">2</span>playlist</span>
        </a>
        <span className="provider-pill"><span className="status-dot" /> {provider === 'SPOTIFY' ? 'Spotify' : 'Apple Music'}</span>
      </header>

      <section className="hero" id="top">
        <div className="eyebrow"><span className="eyebrow-line" /> FROM SCREENSHOT TO SOUND</div>
        <h1>Your next playlist<br /><span>is already in a picture.</span></h1>
        <p className="hero-copy">Drop in a screenshot, poster, or song list. We’ll find the tracks and let you choose what makes the mix.</p>
      </section>

      {!session && (
        <section className="upload-card">
          <div
            className={`dropzone ${dragging ? 'is-dragging' : ''} ${file ? 'has-file' : ''}`}
            onDragOver={(event) => { event.preventDefault(); setDragging(true) }}
            onDragLeave={() => setDragging(false)}
            onDrop={(event) => { event.preventDefault(); setDragging(false); chooseFile(event.dataTransfer.files?.[0]) }}
            onClick={() => fileInput.current?.click()}
            onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') fileInput.current?.click() }}
            role="button"
            tabIndex={0}
          >
            <input ref={fileInput} type="file" accept="image/png,image/jpeg,image/webp" hidden onChange={(event) => chooseFile(event.target.files?.[0])} />
            <div className="upload-icon"><span>↑</span></div>
            {file ? <><strong>{file.name}</strong><span className="drop-hint">{(file.size / 1024 / 1024).toFixed(2)} MB · click to change</span></>
              : <><strong>Drop an image here</strong><span className="drop-hint">or click to browse your files</span></>}
          </div>
          <div className="upload-footer">
            <div className="upload-provider">
              <label>Music provider<select value={provider} onChange={(event) => setProvider(event.target.value)} disabled={!!busy}>
                <option value="APPLE_MUSIC">Apple Music</option><option value="SPOTIFY">Spotify</option>
              </select></label>
              {provider === 'SPOTIFY' && <button className="button button-quiet" onClick={connectSpotify} disabled={!!busy || !!spotifyAccessToken}>
                {spotifyAccessToken ? '✓ Spotify connected' : 'Connect Spotify'}
              </button>}
              <span>PNG, JPG, or WEBP <span className="separator">·</span> Up to 10 MB</span>
            </div>
            <button className="button button-primary" disabled={!file || !!busy || (provider === 'SPOTIFY' && !spotifyAccessToken)} onClick={startProcessing}>
              {busy === 'processing' ? <><span className="spinner" /> Working…</> : <>Find my songs <span aria-hidden="true">↗</span></>}
            </button>
          </div>
          {notice && <div className="inline-notice"><span className="notice-dot" />{notice}</div>}
        </section>
      )}

      {error && <div className="alert" role="alert"><span>!</span>{error}<button onClick={() => setError('')} aria-label="Dismiss">×</button></div>}

      {session && !playlist && (
        <section className="review-section">
          <div className="review-heading">
            <div><div className="eyebrow"><span className="eyebrow-line" /> YOUR TRACKLIST</div><h2>Review the finds</h2></div>
            <button className="button button-quiet" onClick={searchAgain} disabled={!!busy || !session.songs.length}>
              {busy === 'search' ? <><span className="spinner" /> Searching…</> : '↻  Search again'}
            </button>
          </div>
          <p className="review-subtitle">Choose a match for each song. Nothing gets added to a playlist until you confirm below.</p>
          <div className="session-meta"><span>{session.songs.length} candidates</span><span>{selectedCount} selected</span><span className="session-id">Session {session.sessionId.slice(0, 8)}</span></div>

          {session.status === 'AWAITING_PROVIDER_CONFIGURATION' && (
            <div className="configuration-note"><strong>{provider === 'SPOTIFY' ? 'Spotify' : 'Apple Music'} search isn’t configured yet.</strong><span>OCR is complete. Configure the selected provider, then choose “Search again.”</span></div>
          )}
          {session.status === 'AWAITING_USER_AUTHORIZATION' && <div className="configuration-note"><strong>Connect {provider === 'SPOTIFY' ? 'Spotify' : 'Apple Music'} to search.</strong><span>Authorize the provider, then choose “Search again.”</span></div>}
          {session.status === 'MATCHING_FAILED' && <div className="configuration-note"><strong>{provider === 'SPOTIFY' ? 'Spotify' : 'Apple Music'} search needs attention.</strong><span>Check authorization and provider settings, then try searching again.</span></div>}

          <div className="song-list">
            {session.songs.map((song, index) => {
              const values = edits[song.candidateId] || { title: song.title, artist: song.artist || '' }
              const selectedId = song.matches?.find((match) => match.selected)?.matchId
              return (
                <article className="song-card" key={song.candidateId}>
                  <div className="song-card-head"><span className="song-number">{String(index + 1).padStart(2, '0')}</span><span className="candidate-tag">SONG CANDIDATE</span><button className="remove-button" onClick={() => removeSong(song)} disabled={!!busy} aria-label={`Remove ${song.title}`}>×</button></div>
                  <div className="song-edit-grid">
                    <label>Title<input value={values.title} onChange={(event) => editSong(song, 'title', event.target.value)} maxLength={500} /></label>
                    <label>Artist<input value={values.artist} onChange={(event) => editSong(song, 'artist', event.target.value)} maxLength={500} placeholder="Unknown artist" /></label>
                  </div>
                  <button className="button button-quiet song-search-button" onClick={() => searchSong(song)} disabled={!!busy}>
                    {busy === song.candidateId ? <><span className="spinner" /> Searching…</> : 'Search this song'}
                  </button>
                  <div className="matches-heading"><span>{provider === 'SPOTIFY' ? 'SPOTIFY SUGGESTIONS' : 'APPLE MUSIC SUGGESTIONS'}</span>{selectedId && <span className="selected-label">✓ Selected</span>}</div>
                  {song.matches?.length ? (
                    <div className="match-list">
                      {song.matches.slice(0, 5).map((match) => (
                        <button className={`match-option ${match.selected ? 'match-selected' : ''}`} key={match.matchId} onClick={() => chooseMatch(song, match.matchId)} disabled={!!busy}>
                          {match.artworkUrl ? <img src={match.artworkUrl} alt="" className="artwork" /> : <span className="artwork artwork-placeholder">♫</span>}
                          <span className="match-info"><strong>{match.title}</strong><span>{match.artist}{match.album ? ` · ${match.album}` : ''}</span></span>
                          <span className="match-score"><strong>{match.matchScore}%</strong><small>app match</small></span>
                          <span className="select-indicator">{match.selected ? '✓' : '+'}</span>
                        </button>
                      ))}
                    </div>
                  ) : <div className="no-matches">No suggestions yet. Edit the details if needed, then search again.</div>}
                  <div className="song-card-foot"><span>Text read: <em>{song.rawText}</em></span><span>{Math.round(song.extractionConfidence * 100)}% extraction</span></div>
                </article>
              )
            })}
          </div>

          <details className="raw-text-panel" open={showRawText} onToggle={(event) => setShowRawText(event.currentTarget.open)}>
            <summary>See all text recognized in the image <span>↘</span></summary>
            <pre>{session.rawOcrText || 'No text was recognized.'}</pre>
          </details>

          <section className="playlist-panel">
            <div className="playlist-step"><span>02</span><div><h3>Make it yours</h3><p>Only your selected matches will be added.</p></div></div>
            <label className="playlist-name-label">Playlist name<input value={playlistName} onChange={(event) => setPlaylistName(event.target.value)} maxLength={150} /></label>
            <div className="playlist-actions">
              {provider === 'APPLE_MUSIC' && <button className="button button-quiet" onClick={connectAppleMusic} disabled={!!busy}>
                {busy === 'authorize' ? <><span className="spinner" /> Connecting…</> : musicUserToken ? '✓  Apple Music connected' : 'Connect Apple Music'}
              </button>}
              {provider === 'SPOTIFY' && <button className="button button-quiet" onClick={connectSpotify} disabled={!!busy}>
                {busy === 'authorize' ? <><span className="spinner" /> Connecting…</> : spotifyAccessToken ? 'Reconnect Spotify' : 'Connect Spotify'}
              </button>}
              <button className="button button-primary" onClick={createPlaylist} disabled={!musicUserToken || !selectedCount || !!busy}>
                {busy === 'playlist' ? <><span className="spinner" /> Creating…</> : <>Create playlist <span aria-hidden="true">↗</span></>}
              </button>
            </div>
            <p className="privacy-note">{provider === 'SPOTIFY' ? 'Spotify uses secure PKCE authorization. Your access token stays in memory, and your password is never shared with Image2Playlist.' : 'Apple Music authorization is handled by MusicKit. Your Apple ID password is never shared with Image2Playlist.'}</p>
          </section>
        </section>
      )}

      {playlist && (
        <section className="success-card">
          <div className="success-icon">✓</div>
          <div className="eyebrow"><span className="eyebrow-line" /> PLAYLIST CREATED</div>
          <h2>{playlist.playlistName}</h2>
          <p>{playlist.addedTracks} tracks added{playlist.skippedTracks ? ` · ${playlist.skippedTracks} skipped` : ''}{playlist.failedTracks?.length ? ` · ${playlist.failedTracks.length} could not be added` : ''}.</p>
          <a className="button button-primary" href={playlist.playlistUrl} target="_blank" rel="noreferrer">Open in {provider === 'SPOTIFY' ? 'Spotify' : 'Apple Music'} ↗</a>
          {playlist.failedTracks?.length > 0 && <ul className="failed-list">{playlist.failedTracks.map((track, i) => <li key={`${track.title}-${i}`}>{track.title} — {track.reason}</li>)}</ul>}
          <button className="button button-quiet start-over" onClick={() => { setSession(null); setPlaylist(null); setFile(null); setMusicUserToken(''); setSpotifyAccessToken('') }}>Start another mix</button>
        </section>
      )}

      <footer className="footer"><span>Made for the songs you almost forgot to save.</span><span>Image2Playlist <span className="footer-dot">·</span> Apple Music & Spotify</span></footer>
    </main>
  )
}
