// Motor de streaming de Dezzy para la WebView oculta de Android.
// Mismo método que Dezzy Desktop (src/main/stream.ts): sesión anónima de youtubei.js (MIT)
// con visitorData real del servidor, reproductor para descifrar firmas y parámetro n.
// La red NO sale de la WebView: cada fetch se pasa a Kotlin por window.DezzyNet y vuelve
// por window.__dezzyNet.resolve(...). Las respuestas a Kotlin van por DezzyBgBridge.reply.
import { Innertube, Platform } from 'youtubei.js/web'

const pendingFetch = new Map()
let nextFetchId = 1

window.__dezzyNet = {
  resolve(id, status, headersJson, bodyB64) {
    const p = pendingFetch.get(id)
    if (!p) return
    pendingFetch.delete(id)
    const bin = atob(bodyB64 || '')
    const bytes = new Uint8Array(bin.length)
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i)
    const nullBody = status === 204 || status === 304
    p.resolve(new Response(nullBody ? null : bytes, { status, headers: JSON.parse(headersJson || '{}') }))
  },
  reject(id, message) {
    const p = pendingFetch.get(id)
    if (!p) return
    pendingFetch.delete(id)
    p.reject(new TypeError(message))
  },
}

function headersToObject(h) {
  const out = {}
  if (!h) return out
  if (typeof h.forEach === 'function') h.forEach((v, k) => (out[k] = v))
  else Object.assign(out, h)
  return out
}

// fetch para youtubei.js: no se usa new Request() porque el navegador borraría
// cabeceras «prohibidas» (user-agent, origin, referer) que YouTube necesita.
async function bridgeFetch(input, init = {}) {
  const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url
  const method = init.method || (typeof input === 'object' && input.method) || 'GET'
  const headers = Object.assign(
    {},
    typeof input === 'object' && input.headers ? headersToObject(input.headers) : {},
    headersToObject(init.headers),
  )
  let body = ''
  if (init.body != null) body = typeof init.body === 'string' ? init.body : await new Response(init.body).text()
  else if (typeof input === 'object' && input.method && input.method !== 'GET' && typeof input.text === 'function') body = await input.text()
  const id = nextFetchId++
  return new Promise((resolve, reject) => {
    pendingFetch.set(id, { resolve, reject })
    window.DezzyNet.fetch(id, url, method, JSON.stringify(headers), body)
  })
}

// Descifrado de firmas y n: igual que el escritorio, con el código del reproductor.
Platform.shim.eval = async (data, env) => {
  const props = []
  if (env.n) props.push(`n: exportedVars.nFunction(${JSON.stringify(env.n)})`)
  if (env.sig) props.push(`sig: exportedVars.sigFunction(${JSON.stringify(env.sig)})`)
  return new Function(`${data.output}\nreturn { ${props.join(', ')} }`)()
}

let yt = null

function reply(id, ok, data) {
  window.DezzyBgBridge.reply(id, ok, typeof data === 'string' ? data : JSON.stringify(data ?? null))
}

window.dezzyYt = {
  // Crea (o rehace) la sesión anónima. Devuelve el visitorData.
  async session(id) {
    try {
      yt = await Innertube.create({ retrieve_player: true, generate_session_locally: false, fetch: bridgeFetch })
      reply(id, true, yt.session.context.client.visitorData || '')
    } catch (e) {
      yt = null
      reply(id, false, String(e?.message ?? e))
    }
  },
  // Formatos de sólo audio del cliente pedido, con la URL ya descifrada y el pot puesto.
  async audio(id, videoId, client, pot) {
    try {
      if (!yt) throw new Error('sin sesión')
      const info = await yt.getBasicInfo(videoId, { client, po_token: pot })
      const status = info.playability_status?.status
      if (status && status !== 'OK') throw new Error(`${client}: ${status} ${info.playability_status?.reason ?? ''}`.trim())
      const formats = (info.streaming_data?.adaptive_formats ?? []).filter((f) => f.has_audio && !f.has_video)
      const out = []
      for (const f of formats) {
        let url = f.url && client === 'IOS' ? f.url : await f.decipher(yt.session.player)
        if (!url) continue
        if (!/[?&]pot=/.test(url)) url += `&pot=${encodeURIComponent(pot)}`
        out.push({
          itag: f.itag,
          mimeType: f.mime_type,
          bitrate: f.bitrate ?? 0,
          averageBitrate: f.average_bitrate ?? f.bitrate ?? 0,
          contentLength: Number(f.content_length ?? 0),
          loudnessDb: typeof f.loudness_db === 'number' ? f.loudness_db : null,
          url,
        })
      }
      reply(id, true, out)
    } catch (e) {
      reply(id, false, String(e?.message ?? e))
    }
  },
}
