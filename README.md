# Dezzy

**Toda tu música, en un sitio.**

Dezzy es un reproductor de música que une YouTube Music y Spotify en una sola app. Trae tu biblioteca, tus listas y tus me gusta, y le suma letras sincronizadas, Canvas, un DJ que aprende de tus gustos y un motor de audio propio.

Este repositorio contiene la nueva base de Dezzy, escrita en **Kotlin Multiplatform** con **Compose Multiplatform**.

| Plataforma | Estado |
|---|---|
| Android | En desarrollo activo |
| Escritorio (JVM) | Compila; la interfaz se comparte con Android |
| iOS | Previsto |

## Funciones

**Biblioteca unificada**
- Inicio de sesión con YouTube Music y con Spotify.
- Sincroniza listas, me gusta, álbumes y artistas.

**Reproductor**
- Varios estilos de diseño y visualizadores.
- Fondos dinámicos o una imagen propia, con vista previa.

**Letras**
- Sincronizadas verso a verso.
- Fondo configurable y modo Canvas.

**Compartir**
- Tarjetas de canción, Polaroid, lista, álbum y artista.
- Vídeo vertical del Canvas para Historias y WhatsApp.

**Audio**
- Motor DSP nativo `dezzy_dsp` (C++20): ecualizador, normalización y efectos.
- Descargas, incluidas las de alta calidad.

**Escuchar juntos**
- Salas compartidas en tiempo real.
- Control entre dispositivos con Dezzy Connect.

**DJ y estadísticas**
- Mezclas que se adaptan a lo que escuchas.
- Historial, rachas y tus artistas más escuchados.

## Requisitos

- JDK 21
- Android SDK 36 (`minSdk` 23)
- Android NDK con CMake 3.22.1, para el motor de audio nativo

## Compilar

```bash
# APK de depuración
./gradlew :composeApp:assembleDebug

# APK universal de release (arm64-v8a, armeabi-v7a y x86_64)
./gradlew :composeApp:assembleRelease

# Comprobar el objetivo de escritorio
./gradlew :composeApp:compileKotlinDesktop
```

Los APK quedan en `composeApp/build/outputs/apk/`. La release se genera sin firmar y hay que firmarla antes de instalarla.

## Estructura

```
composeApp/src/
├── commonMain/   código compartido
│   ├── core/       cuentas, base de datos, preferencias, red y modelos
│   ├── source/     YouTube Music, Spotify y resolución de audio
│   ├── feature/    reproductor, letras, Canvas, DJ, descargas, Juntos, Connect…
│   ├── ui/         navegación, pantallas, reproductor y tema
│   └── di/         inyección de dependencias (Koin)
├── androidMain/  integración con Android (Media3, servicio de reproducción, DSP nativo)
└── desktopMain/  integración con escritorio
```

Para saber en qué punto está el proyecto, consulta [`docs/ESTADO.md`](docs/ESTADO.md).

## Tecnologías

- Kotlin 2.2
- Compose Multiplatform 1.9
- Koin
- Ktor
- DataStore
- Media3
- Coil 3
- kotlinx.serialization

## Contribuir

Lee [`CONTRIBUTING.md`](CONTRIBUTING.md) antes de abrir un cambio.

## Licencia

El código se distribuye bajo la [PolyForm Strict License 1.0.0](LICENSE.md).

El nombre, el logotipo y los iconos de Dezzy son marcas de Dezzy y no están incluidos en esa licencia: consulta [`TRADEMARKS.md`](TRADEMARKS.md).

© 2026 Dezzy
