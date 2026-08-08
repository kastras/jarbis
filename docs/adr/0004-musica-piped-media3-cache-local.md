# ADR-0004 — Módulo Música: reproducción en segundo plano (Piped + Media3) con caché local acotada

El **Módulo Música** debe reproducir la **búsqueda** pedida por la persona ("pon una canción de X") dentro de la propia app, en segundo plano, sin depender de la plataforma de vídeo, y encadenando canciones parecidas. Sustituye a la apertura externa de YouTube de la v1 (ADR-0003, **superseded**).

Se decide reproducir **in-app (100% interno)** con esta pila:

- **Búsqueda por nombre**: instancia pública de **Piped API** en tiempo real: `GET /search?q={query}&filter=music`. Sin API keys, sin cuentas, gratuito.
- **Stream de audio**: `GET /streams/{videoId}` → mejor entrada de `audioStreams` (m4a/opus), reproduce con **Media3/ExoPlayer**.
- **En segundo plano**: un `MediaSessionService` (`foregroundServiceType="mediaPlayback"`) con notificación de control (play/pause/skip) y reproducción con pantalla apagada/bloqueada.
- **Música parecida**: al terminar cada canción se pide `GET /next/{videoId}` (relatedStreams) y se encadena automáticamente la siguiente, sin parar la cola.
- **Caché local con cuota**: la canción reproducida se descarga a `externalFilesDir("music_cache")` y se reproduce local la próxima vez (funciona offline y sin re-stram). Límite: **1 GB** (constante `AppConfig.MUSIC_CACHE_MAX_BYTES`). Cada reproducción incrementa `playCount`; al superar la cuota se limpian las canciones con **menor `playCount`** (desempatada por `lastPlayed` más antigua). La canción que suena en ese momento nunca se evictúa. Si no hay red, la música "parecida" hace fallback a la mejor canción cacheada.
- **La escucha continua del asistente no se interrumpe** mientras suena la música: Vosk (offline) sigue escuchando y solo reacciona la activación. Durante la ventana de comando la música baja (duck, `AudioFocus` `TRANSIENT_MAY_DUCK`) y el TTS se pronuncia por encima.

Se usan una lista de instancias públicas de Piped con **fallback** (la primera sana que responda): si una cae, se prueba la siguiente.

**Status**: accepted. **Supersedes**: ADR-0003.

**Considered Options**:

- **Piped API + Media3 + caché local (elegido)**: gratis, sin pesos, encaja con la pila Retrofit/OkHttp/serialization existente, reproduce en segundo plano, música parecida vía `/next`. Depende de instancias públicas de terceros y hay delica ante cambios de Google.
- **`youtubedl-android` (yt-dlp embebido)**: literal a "yt-dlp estilo" pero peor en Android: APK pesado, Python embebido, se rompe con cada cambio de YouTube, mantenimiento continuo.
- **Invidious API**: en 2026 está más golpeado que Piped por los bloqueos de Google (CAPTCHA/blacklist en instancias públicas).
- **NewPipe Extractor (client-side)**: plan B si se quiere eliminar dependencia de instancias de terceros (extracción del lado del teléfono).

**Consecuencia**: la interfaz `CommandModule` no cambia (el `Música` módulo se mantiene pluggable); se añade el servicio de reproducción y la capa de caché como infraestructura propia. La respuesta hablada pasa a ser p.ej. "Reproduciendo: {título}" / "Playing: {title}". Se actualiza la definición de **Música** en el glosario (`CONTEXT.md`) y se elimina la ambiguidad de "v1 solo abre el reproductor externo".