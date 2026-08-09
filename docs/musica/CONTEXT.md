# Contexto del Módulo Música

> Referencia para trabajar en el **Módulo Música** sin re-explorar el código.
> Última actualización: 2026-08-09. Glosario del dominio: `CONTEXT.md` (raíz).

## Qué es

El **Módulo Música** ejecuta el **comando** del tipo *"pon X"*: resuelve la
búsqueda a una pista reproducible, la descarga a una **caché local** con
**cuota** configurable y la reproduce en segundo plano, encadenando canciones
"parecidas" al terminar cada una. La reproducción no interfiere con la escucha
continua del asistente (Vosk), solo baja el volumen mientras el asistente
habla (duck).

## Arquitectura (archivos en `app/src/main/java/com/jarbis/alexa/music/`)

| Archivo | Responsabilidad |
|---|---|
| `MusicController.kt` | Orquesta "pon X": caché local primero → orígenes. Devuelve `MusicTrack`. Nunca lanza. |
| `MusicResolver.kt` | Contrato de orgén (`search`, `audioUrlFor`, `related`) + `MusicResolvers.all()` = [NewPipe, Piped]. |
| `NewPipeMusicResolver.kt` | Origen primario: NewPipeExtractor (sin servidor de terceros). |
| `PipedMusicResolver.kt` | Origen de respaldo sobre `PipedClient`. |
| `OkHttpNewPipeDownloader.kt` | implementa `Downloader` de NewPipeExtractor sobre OkHttp. |
| `PipedClient.kt` | API pública de Piped con failover entre instancias. |
| `CacheManager.kt` | Descarga streams a `externalFilesDir("music_cache")`, índice en `CacheStore`. |
| `CacheStore.kt` | Índice JSON (kotlinx), cuota/evicción por `playCount`. |
| `MusicPlayerService.kt` | `MediaSessionService` con ExoPlayer; encadena "parecidas" al terminar. |
| `MusicBridge.kt` | Estado compartido entre motor y servicio (`MusicBridgeState`). |
| `PlaybackController.kt` | Ducking `AUDIOFOCUS_TRANSIENT_MAY_DUCK` mientras responde el asistente. |

## Flujo de resolución

```
"probar: pon una de queen"
 └─ MusicController.resolve("queen")
     ├─ 1. caché local: CacheStore.bestLocalMatch("queen") estricto →
     │      solo si el título coincide de verdad (sin red)
     └─ 2. si no hay match → recorre resolvers en orden:
          NewPipeMusicResolver.search → audioUrlFor → CacheManager.obtain (descarga a caché)
          └─ si falla → PipedMusicResolver (fallback)
     ├─ 3. si todos los orígenes fallan (offline) → mostPlayed() como degradación
     └─ 4. si no hay nada → null → el asistente responde "No pude encontrar música."
```

- `nextLike(videoId)` (encadenar al terminar) recorre `related()` de cada
  origen y descarga la primera parecida reproducible; sin red cae a la mejor
  canción cacheada que no sea la actual.
- El servicio (`MusicPlayerService` → `MusicBridge.nextResolver`) pide la
  siguiente al terminar cada tema.

## Resolución remota: por qué hay dos orígenes

- **NewPipeExtractor** (primario): extrae directamente de YouTube sin servidor
  de terceros. Mantiene la librería `org.schabi.newpipe.extractor` (JitPack,
  hoy `v0.26.0`); hay que actualizarla cuando YouTube cambie su API.
- **Piped** (fallback): API pública de Piped. **Hoy todas las instancias
  públicas están caídas o devuelven `items:[]`** (comprobado 2026-08-09); se
  mantiene solo como red de seguridad. Lista en `PipedClient.DEFAULT_INSTANCES`.

### Cómo falla cada origen
- Error de red/crash → el método devuelve `null`/`emptyList`, nunca lanza →
  `MusicController` sigue con el siguiente origen → respuesta amable TTS.
- No bloquear: los resolvers corren en `Dispatchers.IO` y son `suspend`.

## Cache local (local-first)

- Forma de la caché: índice `index.json` en `externalFilesDir("music_cache")`
  (sin permiso de almacenamiento), ficheros `music_cache/<videoId>.m4a`.
- Decomiso: `AppConfig.MUSIC_CACHE_MAX_BYTES` (1 GB); evita la canción en
  reproducción; evictúa la de menor `playCount` (desempate `lastPlayed`).
- `CacheStore.bestLocalMatch(query)` usa **coincidencia difusa** (`FuzzyMatch`,
  en `music/FuzzyMatch.kt`): normaliza (minúsculas/sin acentos), acepta
  substrings, prefijos y typos (distancia Levenshtein ≤1-3 según longitud) por
  palabra, de modo que "rapers avion" encuentra "The Rappers Avion". Sin match
  parecido devuelve `null` → el controlador recurre a NewPipe. `mostPlayed()`
  queda solo como degradación offline (cuando todos los orígenes fallan).
  - Decisión de producto (2026-08-09): local-first **difuso** — local si hay
    algo parecido; si no, NewPipe; `mostPlayed()` únicamente offline.

## Integración con NewPipeExtractor

- Inicialización: `AlexaApp.onCreate` → `NewPipe.init(OkHttpNewPipeDownloader(),
  Localization.fromLocale(Locale.getDefault()))`. Sin servidor de terceros.
- Gradle: `org.schabi.newpipe:NewPipeExtractor:v0.26.0` (JitPack,
  `settings.gradle.kts`), desugaring `desugar_jdk_libs_nio` 2.1.4 habilitado,
  `coreLibraryDesugaring`.
- Proguard: keep de `mozilla.javascript` y `timeago` (`app/proguard-rules.pro`).

### Gotcha crítico (NPE en búsqueda NewPipe) — CORREGIDO
- Síntoma: "Probar: música" → `YoutubeMusicSearchExtractor.getInitialPage`
  lanza NPE (JsonUtils sobre `JsonObject null`). La búsqueda venía fallando.
- Causa: `NewPipeMusicResolver.search()` iteraba `getInitialPage().getItems()`
  sin llamar antes `extractor.fetchPage()`. `initialData` solo se rellena en
  `onFetchPage()` (invocado por `fetchPage()`); sin esa llamada quedaba `null`.
  Por eso además el descargador jamás logueaba: la petición no se lanzaba.
- Fix aplicado (2026-08-09): llamar `extractor.fetchPage()` antes de iterar
  `getInitialPage()` (patrón obligatorio del API). Verificado con curl que
  music.youtube.com y `youtubei/v1/search` devuelve la estructura esperada
  (200, `musicShelfRenderer`).
- Los otros dos métodos (`audioUrlFor`, `related`) usan `StreamInfo.getInfo()`,
  que ya llama a `fetchPage()` internamente → no tenían el bug.

## Estado actual (2026-08-09)

- ✅ Caché local, ducking, reproducción segundo plano, fallback offline.
- ✅ Búsqueda NewPipe con `fetchPage()` corregido (NPE resuelto en
  `NewPipeMusicResolver.search`).
- ✅ Match local **difuso** (`FuzzyMatch`): typos y palabras parciales encuentran
  la canción cacheada parecida → si no hay nada parecido, NewPipe; `mostPlayed()`
  solo como degradación offline. Resolutores NewPipe + Piped compilando.
- 🔲 Probar end-to-end en emulador (descarga real y reproducción de NewPipe),
  probar `audioUrlFor`, encadenado real y evicción en caché.
- 🔲 Actualizar ADRs (NewPipe como motor, Piped fallback) y README.
- 🔲 Commit de los cambios `music/` (working tree aún sin commit).

## Tests (directorios en `app/src/test/java/com/jarbis/alexa/`)

Módulo de música (carpeta `music/`):
- `OkHttpNewPipeDownloaderTest.kt` — GET, cabeceras hacia el servidor,
  seguimiento de redirects (MockWebServer). No cubre el bug `fetchPage()`.
- `MusicControllerTest.kt` — `resolve()` con todos los orígenes caídos
  devuelve `null` sin lanzar; match local usa la caché sin consultar
  orígenes (también con búsqueda **no exacta** como "rapers avion"); sin local
  sí consulta el resolver (NewPipe); con todo caído degrada a `mostPlayed()`.
  Caché sembrada en disco (sin red en Robolectric).
- `CacheStoreTest.kt` — índice JSON persiste; `totalBytes` solo ficheros
  existentes; `recordPlayed`; evicción por `playCount` y desempate por
  `lastPlayed`; canción protegida en reproducción; `bestLocalMatch` insensible
  a acentos **y difuso** (typo "rapers"→Rappers, prefijo "rap", orden por
  semejanza, sin falso positivo con títulos distintos, sin match → `null`,
  query vacío → `null`); `mostPlayed`.
- `PipedClientTest.kt` — búsqueda primer stream, mejor bitrate, extracción de
  id (watch/video/channel), failover entre instancias, related, todas caídas
  sin lanzar.

Otros tests relacionados con Música:
- `commands/impl/MusicQueryParserTest.kt` — extrae la búsqueda del comando
  para los idiomas es/en (preposición funciona para el dispatcher).

Comando de tests unitarios: `./gradlew :app:testDebugUnitTest`
(Robolectric incluido; se ejecuta sin emulador).

Instrumentados (emulador, requiere sistema idle): `./gradlew :app:connectedDebugAndroidTest`

## Verificación típica en emulador

- Emulador `Medium_Phone_API_35` (1080×2400). Antes de interactuar:
  `adb shell input keyevent KEYCODE_WAKEUP` (la pantalla duerme y rompe
  `uiautomator dump`).
- Secuencia: force-stop → `logcat -c` → `am start` → tap "Arrancar" → tap
  "Probar: musica" → esperar ~40s → mirar `logcat -s Jarbis` y respuesta TTS.

## Puntos de entrada

- `MusicBridge.state(...).resolve(query)` → `MusicTrack?` — lo usa la VM.
- `MusicPlayerService.intentFor(context, track)` para reproducir.
- `MusicPlayerService.stop(context)` para detener.