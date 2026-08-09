# Handoff 2026-08-09 — Módulo Música: variantes de búsqueda + controles de reproducción + ducking

## Estado

Completado e integrado el trabajo de expandir el **Módulo Música** del asistente Jarbis. Compila (`./gradlew :app:assembleDebug`) y todos los tests unitarios pasan (`./gradlew :app:testDebugUnitTest`).

Diseño y glosario documentados (no duplicar aquí):
- `docs/adr/0005-musica-control-y-variantes-de-busqueda.md` — ADR del cambio (nuevo).
- `docs/adr/0002-comandos-modulos-pluggables.md` — actualizado (dispatch dejó de ser subcadena).
- `docs/musica/CONTEXT.md` — comandos de música es/en + ducking.
- `CONTEXT.md` (raíz) — **Música** acepta **comando de control**; nuevo término **comando de control**.

## Cambios de código

- `commands/impl/MusicCommand.kt` — variantes de búsqueda (`busca/buscar/buscame`, `dame un tema de`, `quiero escuchar`, `suena`, `dale play`, `pasame`, `echame`, `saca`; en: `search`, `find`) en `keywords`/`triggers`/`noise`; nuevos `MusicControl` (detecta NEXT/STOP/VOLUME_UP/VOLUME_DOWN antes de tratar como búsqueda) y `MusicQueryParser`.
- `commands/CommandModule.kt` — `CommandContext` ampliado con `nextSong()`, `stopMusic()`, `volumeUp()`, `volumeDown()`.
- `commands/CommandRegistry.kt` — dispatch por **palabra completa** (`containsWordPhrase`); si varias keywords matchean gana la **más larga**.
- `voice/AssistantEngine.kt` — implementa los 4 controles; `unDuck()` moved to `finalizeCommand()` (la música se recupera al capturar la orden, antes de la respuesta TTS, **no** al terminar de hablar).
- `music/MusicPlayerService.kt` — nuevo `ACTION_NEXT` + flag `isPlaying` (en `MusicBridge`).
- `music/MusicBridge.kt` — `isPlaying` @Volatile.
- `ui/AssistantScreen.kt` — botones "Probar: siguiente / parar / subir volumen".

## Tests

- `commands/impl/MusicControlTest.kt` (nuevo) — control es/en.
- `commands/impl/MusicQueryParserTest.kt` y `commands/CommandRegistryTest.kt` — casos de variantes y dispatch por palabra.

## Gotchas (recordados)

- "para" a secas o "para la música" = parar; "para mañana" no es control.
- "mas alto" sin "volumen" no dispara control (exige la palabra volumen/sonido).
- Triggers en orden más-larga→más-corta (`reproducir` antes de `reproduce`).

## Pendiente / sugerencias próximas

- Probar flujos en emulador (siguiente/parar/volumen con música de verdad), incl. el encadenado "parecida" de `ACTION_NEXT` y la recuperación del volumen tras el comando.
- Revisar respuestas TTS de control para que el asistente lo confirme ("Siguiente canción." etc.) si el usuario quiere otro tono.
- Opcional: pronunciación/persistencia de "última canción reproducida" para `isPlaying` tras reboot del servicio.