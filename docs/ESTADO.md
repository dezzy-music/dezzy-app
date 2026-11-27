# Estado del proyecto (27-11-2026)

Este documento está pensado para quien llegue nuevo a `dezzy-app`.

`dezzy-app` es la reescritura de Dezzy en Kotlin Multiplatform. La meta es que sea **la misma app** que la Dezzy de Android actual (OpenTune): mismas pantallas, mismos textos y mismo comportamiento, pero con código propio bajo la licencia PolyForm Strict.

## Ramas

- **`main`**: rama principal, al día. Crea tus ramas desde aquí.
- **`fase-5-ui`**: rama donde se hizo la fase de interfaz; ya está fusionada en `main`.

## Cómo compilar

| Qué | Comando |
|---|---|
| Debug Android | `./gradlew :composeApp:assembleDebug` → `composeApp/build/outputs/apk/debug/composeApp-debug.apk` |
| Release universal | `./gradlew :composeApp:assembleRelease` → `composeApp/build/outputs/apk/release/composeApp-release-unsigned.apk` |
| Escritorio (sólo comprobar que compila) | `./gradlew :composeApp:compileKotlinDesktop` |

Notas sobre la release:

- Es un APK universal con `arm64-v8a`, `armeabi-v7a` y `x86_64`, porque no hay splits por ABI.
- Sale **sin firmar**: todavía no hay `signingConfig`. Para instalarla hay que firmarla con `apksigner`.

Requisitos para compilar:

- JDK 21.
- Android SDK 36.
- NDK con CMake 3.22.1, para el motor de audio nativo `dezzy_dsp` (C++20, en `src/androidMain/cpp`).

El id de la app es `com.dezzy.app`, así que convive en el móvil con la app actual (`com.dezzy.music`).

## Estructura (`composeApp/src/commonMain/kotlin/com/dezzy/app`)

**`core/`**
- Cuentas, base de datos y preferencias (DataStore).
- Red, cifrado y modelos.
- Comprobador de actualizaciones.

**`source/`**
- `youtube`: cliente de YouTube Music.
- `spotify`: inicio de sesión, gustos y búsqueda.
- `stream`: resolución de URLs de audio.

**`feature/`**
- Reproductor y audio (DSP).
- Letras, Canvas, DJ y descargas, incluidas las lossless.
- Juntos (*Listen Together*), Connect y estadísticas.
- Social (oculto, ver más abajo), etc.

**`ui/`**
- `AppShell.kt`: raíz con las pestañas, el mini reproductor y la bienvenida.
- `navigation`: `Navigator`, con una pila por pestaña.
- `screens`, `player` (estilos del reproductor y su kit), `share` y `theme`.

**Inyección de dependencias:** Koin, en `di/`.

**Plataformas:** el código propio de cada una va en `androidMain` y `desktopMain`, con `expect`/`actual`.

## Lo último que se ha hecho

1. **Motor de audio.**
   - La cadena DSP de `dezzy_dsp` va siempre activa. Antes sólo funcionaba con «Hi-Res» activado.
   - Se cablearon 8 ajustes de reproducción que no hacían nada.
2. **Social.** Amigos y mensajería están ocultos con `SocialVisibility.FRIENDS_AND_MESSAGES`. El código sigue en el repositorio.
3. **Hoja de Compartir** (`ui/share/DezzyShareSheet.kt`).
   - Tarjetas para canción (Canción, Canvas y Polaroid), lista, álbum (lista o portada) y artista.
   - Destinos: guardar, copiar enlace de YouTube, enlace de Spotify, Historias de Instagram, WhatsApp y el menú del sistema.
   - El Canvas se exporta como vídeo vertical MP4 con Media3 Transformer, en `ShareTargets.android.kt`.
4. **Pantalla de bienvenida** (`ui/screens/WelcomeScreen.kt`).
   - Sale una sola vez por instalación (`welcomeLoginShown`).
   - Tiene botones para conectar Spotify y YouTube Music. Al volver del inicio de sesión, la tarjeta pasa a «Conectado».
   - «Continuar» lleva a Tu biblioteca.
5. **Fondo del reproductor.**
   - Vale para todos los estilos y también va detrás de las letras.
   - «Personalizado» muestra la imagen nítida, con el desenfoque, la atenuación y el contraste que se elijan.
   - En Ajustes › Apariencia hay un editor con vista previa en vivo (`PlayerBackgroundEditor.kt`).

## Sin probar todavía en un móvil

- Exportar el vídeo del Canvas, y compartir en Historias o WhatsApp.
- Al cambiar a la pestaña «Canvas» en la hoja de Compartir hubo un comportamiento raro: pareció cambiar el color en lugar de la pestaña.
- La pantalla de bienvenida y el nuevo fondo/editor. Compilan en Android y escritorio, pero aún no se han visto en el dispositivo.

## Normas

Antes de tocar nada, lee [`CONTRIBUTING.md`](../CONTRIBUTING.md): código limpio de licencias, formato de los commits, bocetos antes de cualquier cambio visual y nada de secretos dentro de la app.
