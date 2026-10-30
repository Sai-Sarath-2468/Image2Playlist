const MUSIC_KIT_URL = 'https://js-cdn.music.apple.com/musickit/v3/musickit.js'
let configurationPromise

function loadMusicKit() {
  if (window.MusicKit) return Promise.resolve(window.MusicKit)

  return new Promise((resolve, reject) => {
    let script = document.querySelector(`script[src="${MUSIC_KIT_URL}"]`)
    if (!script) {
      script = document.createElement('script')
      script.src = MUSIC_KIT_URL
      script.async = true
      document.head.appendChild(script)
    }
    script.addEventListener('load', () => resolve(window.MusicKit), { once: true })
    script.addEventListener('error', () => reject(new Error('Apple Music authorization could not load.')), { once: true })
  })
}

export async function authorizeAppleMusic(developerToken) {
  const MusicKit = await loadMusicKit()
  if (!MusicKit) throw new Error('Apple Music authorization is unavailable in this browser.')

  if (!configurationPromise) {
    configurationPromise = MusicKit.configure({
      developerToken,
      app: { name: 'Image2Playlist', build: '1.0.0' },
    })
  }
  await configurationPromise

  const music = MusicKit.getInstance()
  const token = await music.authorize()
  const musicUserToken = typeof token === 'string' ? token : music.musicUserToken
  if (!musicUserToken) throw new Error('Apple Music did not return a user authorization token.')
  return musicUserToken
}
