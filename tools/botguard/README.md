# Puente BotGuard (WebView de Android)

`entry.js` envuelve `bgutils-js` (MIT) y expone `window.dezzyBg` para la WebView oculta.
Para regenerar `composeApp/src/androidMain/assets/botguard/dezzy-bg.js`:

```
npm i bgutils-js@4.0.3
npx esbuild@0.24.0 tools/botguard/entry.js --bundle --minify --format=iife --target=chrome58 --platform=browser --outfile=dezzy-bg.js
```

y añade la cabecera de licencia de la primera línea del archivo actual.

Lo mismo para `dezzy-yt.js` desde `yt-entry.js` (con `youtubei.js@18.3.0`, `--target=chrome70`). Licencias de terceros en `assets/botguard/LICENSES/`.
