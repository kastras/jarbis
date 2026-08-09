# ADR-0005 — Módulo Música: variantes de búsqueda, comandos de control y ducking en la activación

El usuario pidió que el **Módulo Música** acepte más formas de pedir una
**búsqueda** ("alexa pon x", "busca x", "reproduce x", …) y, además, **comandos
de control** de lo que suena (siguiente, parar, subir/bajar volumen) con una
**confirmación hablada**. También que la música baje (duck) **solo mientras el
asistente escucha el comando**, es decir durante la **activación**, no hasta el
final de la respuesta TTS.

## Decisiones

1. **Variantes de búsqueda** — se amplían las tres listas de
   `MusicQueryParser` por **idioma**:
   - `keywords` (dispatch → "¿a qué módulo va?"): es añade `busca`, `buscar`,
     `buscame`, `reproducir`, `dame`, `suena`, `escucha`, `quiero`, `dale`,
     `play`, `pasame`, `echame`, `saca`; en añade `search`, `find`.
   - `triggers` (qué verbo abre la frase): ordenados de **más larga a más
     corta** (`reproducir`→`reproduce`, `buscar`→`busca`) para que el
     desambiguador no se coma la consulta. Ej.: "busca queen" → "queen",
     "dame un tema de queen" → "queen".
   - `noise` (muletillas del comienzo): `la, el, los, las, de, en, un, una,
     algo, tema, play, a, …`.

2. **Controles de reproducción dentro del Módulo Música** (no módulo aparte)
   — detección previa a la búsqueda con `MusicControl.command()`:
   - **Siguiente**: `siguiente`, `adelante`, o `pasa/otra/skip + nombre de
     música` ("pasa canción"); "pasa" a secas NO es control (evita "pasa la
     sal").
   - **Parar**: `para` (frase completa) o `para la música`, `parar`,
     `detener`, `detente`, `stop`, `pause`.
   - **Volumen**: solo si se menciona `volumen`/`sonido`/`volume` + dirección
     (`sube/baja`, `mas alto/...`, ingl. `louder/quieter`). "mas alto" sin
     "volumen" no dispara control.
   - Cada control devuelve una **respuesta** de confirmación es/en
     ("Siguiente canción.", "Música parada.", "Subo el volumen."); si no hay
     nada sonando: "No hay música sonando ahora."
   - Nuevos servicios de `CommandContext`: `nextSong()`, `stopMusic()`,
     `volumeUp()`, `volumeDown()`.
   - `MusicPlayerService` gana `ACTION_NEXT` (salta a la "parecida" del tema
     actual) y `MusicBridge.isPlaying` marca si algo suena para poder
     contestar "no hay música" cuando proceda.
   - Volumen: `AudioManager.adjustStreamVolume(STREAM_MUSIC, ADJUST_RAISE/LOWER)`.

3. **Dispatch por límite de palabra** — `CommandRegistry.matching()` ya no
   matchea por subcadena (`"compone"` disparaba `"pon"`); matchea por
   **palabra completa normalizada** y, si hay varias keywords, **gana la más
   larga** ("tiempo para mañana" → `tiempo` gana a `para`). Permite añadir
   keywords cortas (`para`, `busca`, `dale`) con poco riesgo de falso
   positivo. **Supersedes** el comportamiento descrito en ADR-0002 (el diseño
   de módulos pluggables se mantiene; cambia el algorítmo de selección).

4. **Ducking solo en activación** — `AssistantEngine.finalizeCommand()`
   libera ahora el focus (`unDuck()`) **al capturar la orden**, antes de la
   respuesta TTS; la música se recuper tras la detección de comando, no al
   terminar de hablar el asistente (antes `afterSpeaking()`).

**Status**: proposed