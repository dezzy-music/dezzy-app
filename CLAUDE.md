# Dezzy App — Kotlin Multiplatform

App de música Dezzy en Kotlin Multiplatform (Android primero, iOS más adelante; el escritorio sigue en Electron).

- **Plan maestro**: `C:\Users\dezzy\antigravity\OpenTune\docs\migracion-kmp\PLAN.md`
- **Instrucciones del agente**: `C:\Users\dezzy\antigravity\OpenTune\docs\migracion-kmp\INSTRUCCIONES-AGENTE.md`
- **Reglas de sala limpia**: `docs/sala-limpia.md`

## Reglas obligatorias
1. **Sala limpia**: Ni una sola línea de código derivada de GPL. No abrir módulos de OpenTune que no estén en `archivos-propios.txt`.
2. **Sin firmas de IA**: Nada de `Co-Authored-By: Claude`, `Generated with...` ni menciones similares en commits, PRs o código.
3. **Commits**: Mensajes en español, concisos, con la línea final `Origen: ...` (`Origen: dezzy-desktop/...`, `Origen: propio (...)` o `Origen: nuevo`).
4. **Cambios visuales**: Siempre 3 bocetos HTML (A, B, C) en `OpenTune/design/` y esperar la elección del usuario antes de implementar en Compose.
5. **No subir ni publicar**: Subir a GitHub o releases requiere OK explícito de Dezzy.
