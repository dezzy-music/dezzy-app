// Puente de Dezzy para la WebView oculta de BotGuard (Android).
// Expone window.dezzyBg; cada operación contesta por DezzyBgBridge.reply(id, ok, data).
import { BotGuardClient, parseChallengeData } from 'bgutils-js/botguard'
import { WebPoMinter } from 'bgutils-js/webpo'

let signals = []
let minter = null

function reply(id, ok, data) {
  window.DezzyBgBridge.reply(id, ok, typeof data === 'string' ? data : JSON.stringify(data ?? null))
}

window.dezzyBg = {
  // raw: respuesta JSON de /Create. Devuelve { interpreterUrl, interpreterJavascript, program, globalName }.
  parse(id, raw) {
    try {
      const c = parseChallengeData(JSON.parse(raw))
      reply(id, true, {
        interpreterUrl: c.interpreterUrl?.privateDoNotAccessOrElseTrustedResourceUrlWrappedValue ?? null,
        interpreterJavascript: c.interpreterJavascript?.privateDoNotAccessOrElseSafeScriptWrappedValue ?? null,
        program: c.program,
        globalName: c.globalName,
      })
    } catch (e) { reply(id, false, String(e?.message ?? e)) }
  },
  async snapshot(id, interpreterJavascript, program, globalName) {
    try {
      minter = null
      new Function(interpreterJavascript)()
      const client = await BotGuardClient.create({ program, globalName, globalObject: globalThis })
      signals = []
      reply(id, true, await client.snapshot({ webPoSignalOutput: signals }))
    } catch (e) { reply(id, false, String(e?.message ?? e)) }
  },
  async minter(id, integrityToken) {
    try {
      minter = await WebPoMinter.create({ integrityToken }, signals)
      reply(id, true, 'ok')
    } catch (e) { reply(id, false, String(e?.message ?? e)) }
  },
  async mint(id, binding) {
    try {
      if (!minter) throw new Error('minter no listo')
      reply(id, true, await minter.mintAsWebsafeString(binding))
    } catch (e) { reply(id, false, String(e?.message ?? e)) }
  },
}
