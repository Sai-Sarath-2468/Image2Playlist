const AUTH_URL = 'https://accounts.spotify.com/authorize'
const TOKEN_URL = 'https://accounts.spotify.com/api/token'
const SCOPES = 'playlist-modify-private'
let popupCallbackSent = false

function base64Url(bytes) {
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function randomValue(size = 64) {
  return base64Url(crypto.getRandomValues(new Uint8Array(size)))
}

export async function authorizeSpotify(clientId) {
  if (!clientId) throw new Error('Spotify is not configured. Set VITE_SPOTIFY_CLIENT_ID in frontend/.env.')
  const verifier = randomValue()
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier))
  const challenge = base64Url(new Uint8Array(digest))
  const state = randomValue(24)
  const redirectUri = import.meta.env.VITE_SPOTIFY_REDIRECT_URI || 'http://127.0.0.1:5173/'
  const params = new URLSearchParams({
    response_type: 'code', client_id: clientId, scope: SCOPES,
    redirect_uri: redirectUri, state, code_challenge_method: 'S256', code_challenge: challenge,
  })
  const popup = window.open(`${AUTH_URL}?${params}`, 'image2playlist-spotify', 'width=520,height=720,popup=yes')
  if (!popup) throw new Error('Allow pop-ups to connect your Spotify account.')

  const code = await new Promise((resolve, reject) => {
    let poll
    const finish = (error, value) => {
      window.clearTimeout(timeout)
      window.clearInterval(poll)
      window.removeEventListener('message', onMessage)
      if (!popup.closed) popup.close()
      error ? reject(error) : resolve(value)
    }
    const onMessage = (event) => {
      if (event.origin !== window.location.origin || event.source !== popup
          || event.data?.type !== 'spotify-oauth-callback') return
      if (event.data.state !== state) return finish(new Error('Spotify authorization state did not match.'))
      if (event.data.error) return finish(new Error(`Spotify authorization failed: ${event.data.error}`))
      if (!event.data.code) return finish(new Error('Spotify did not return an authorization code.'))
      finish(null, event.data.code)
    }
    const timeout = window.setTimeout(() => finish(new Error('Spotify authorization timed out.')), 180000)
    window.addEventListener('message', onMessage)
    poll = window.setInterval(() => {
      if (popup.closed) finish(new Error('Spotify authorization was closed.'))
    }, 500)
  })

  const body = new URLSearchParams({
    client_id: clientId, grant_type: 'authorization_code', code,
    redirect_uri: redirectUri, code_verifier: verifier,
  })
  const response = await fetch(TOKEN_URL, {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body,
  })
  const token = await response.json().catch(() => ({}))
  if (!response.ok || !token.access_token) throw new Error(token.error_description || 'Spotify could not issue an access token.')
  return token.access_token
}

export function completeSpotifyPopup() {
  if (!window.opener || popupCallbackSent) return
  const params = new URLSearchParams(window.location.search)
  if (!params.has('code') && !params.has('error')) return
  popupCallbackSent = true
  window.opener.postMessage({
    type: 'spotify-oauth-callback', code: params.get('code'),
    state: params.get('state'), error: params.get('error'),
  }, window.location.origin)
  window.close()
}
