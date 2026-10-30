const API_BASE = import.meta.env.VITE_API_URL || ''

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, options)
  const body = response.status === 204 ? null : await response.json().catch(() => null)
  if (!response.ok) {
    throw new Error(body?.message || `Request failed (${response.status})`)
  }
  return body
}

export const api = {
  upload: (file) => {
    const data = new FormData()
    data.append('image', file)
    return request('/api/images/upload', { method: 'POST', body: data })
  },
  process: (imageId, provider = 'APPLE_MUSIC', accessToken = '') => request(
    `/api/images/${imageId}/process?provider=${encodeURIComponent(provider)}`,
    { method: 'POST', headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {} },
  ),
  getAppleMusicConfig: () => request('/api/providers/apple-music/config'),
  searchAgain: (sessionId, storefront, accessToken = '') => request(
    `/api/sessions/${sessionId}/search?storefront=${encodeURIComponent(storefront)}`,
    { method: 'POST', headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {} },
  ),
  searchSong: (sessionId, songId, storefront = 'us', accessToken = '') => request(
    `/api/sessions/${sessionId}/songs/${songId}/search?storefront=${encodeURIComponent(storefront)}`,
    { method: 'POST', headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {} },
  ),
  updateSong: (sessionId, songId, body) => request(
    `/api/sessions/${sessionId}/songs/${songId}`,
    { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) },
  ),
  removeSong: (sessionId, songId) => request(
    `/api/sessions/${sessionId}/songs/${songId}`, { method: 'DELETE' },
  ),
  createPlaylist: (sessionId, payload, provider, accessToken) => request(
    `/api/sessions/${sessionId}/playlist`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(provider === 'SPOTIFY' ? { Authorization: `Bearer ${accessToken}` } : { 'Music-User-Token': accessToken }),
      },
      body: JSON.stringify(payload),
    },
  ),
}
