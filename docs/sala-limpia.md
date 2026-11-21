# Reglas de sala limpia (obligatorias en cada sesión)

Es lo único que hace que la licencia nueva sea válida. Si una sesión las rompe, ese código hay que tirarlo.

**Se puede abrir y traducir:**
1. `antigravity/dezzy-desktop/` entero (17 commits, todos de Dezzy; dependencias MIT/ISC).
2. Los archivos de `archivos-propios.txt` (124 archivos `.kt` que creó Dezzy), **salvo** las excepciones de §1.1.
3. Los bocetos de `OpenTune/design/*.html` (son de Dezzy).
4. `PLAN.md` y los documentos que cuelguen de `docs/migracion-kmp/`.
5. Documentación pública de APIs y protocolos (LRCLIB, Last.fm, Nostr NIPs, Gemini, Media3, Compose…).
6. Librerías con licencia Apache-2.0, MIT, BSD, ISC o LGPL enlazada dinámicamente.

**No se puede abrir mientras se implementa** (ni para «mirar cómo lo hacía»):
- Cualquier archivo de `OpenTune/` que **no** esté en `archivos-propios.txt`: módulos `innertube`, `kugou`, `lrclib`, `lastfm`, `simpmusic`, `betterlyrics`, `kizzy`, `canvas`, `shazamkit`, `artistvideo` y el grueso de `app/`.
- Código de NewPipe / NewPipeExtractor, Metrolist / metroserver, OuterTune, InnerTune, SimpMusic, Echo-Music, ytmdesktop (todos GPL).
- Liner (`.gemini/antigravity/scratch/liner-desktop`), salvo `crates/dezzy-core` (ver §1.1).

**Cómo se trabaja:**
- Lo que hay que hacer se describe por **comportamiento** (§3 del plan), nunca pegando código de la app vieja.
- Si una función sólo existe en la parte GPL, se reimplementa a partir de la descripción de §3, de la API pública o de Dezzy Desktop.
- Cada commit dice de dónde sale lo que trae: `Origen: desktop/src/main/connect.ts`, `Origen: propio (archivos-propios: ai/DjTaste.kt)` o `Origen: nuevo`.
- Ante la duda sobre un archivo, **no se usa** y se escribe de nuevo.

### 1.1 Excepciones y cosas a revisar

| Pieza | Situación | Qué hacer |
|---|---|---|
| `connect/LinerConnectManager.kt` | Habla el protocolo de Liner | **No usar.** Connect se hace con el protocolo propio (§3.8) |
| `innertube/.../LibraryContinuationPage.kt`, `PlaylistContinuationPage.kt` | Propios, pero dentro de un módulo GPL y sobre sus tipos | Sólo como referencia de comportamiento |
| `playback/potoken/*` (BotGuard) | Port de NewPipe (GPL) | **No usar.** Rehacer como en el escritorio (`bgutils-js`, MIT) |
| `crates/dezzy-core` (Rust) | Autor Dezzy, GPL-3.0, vive dentro del repo de Liner | Comprobar de dónde salen `cipher.rs` y `potoken.rs`; si son propios, cambiarle la licencia y sacarlo a su propio repo |
| Servidor Listen Together (`antigravity/listen-together`) | GPL y usa el `.proto` de Metrolist (`metroserver`) | Para la app nueva: protocolo propio v3 (§3.9) y servidor nuevo o reescrito |
| `resolver/` (Node, lossless) | De Dezzy; comprobar dependencias | Reutilizable como servicio aparte |
| Canal de Roku (`antigravity/dezzy-roku-channel`) | Imita «fielmente» la interfaz y el verde de Spotify | Cambiar a diseño propio antes de publicarlo |
| `TOGETHER_BEARER_TOKEN` en el APK actual | Secreto del dueño dentro de cada APK | En la app nueva, ningún secreto dentro del binario |
