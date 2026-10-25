# Contexto del Módulo Música

> Referencia para trabajar en el **Módulo Música** sin re-explorar el código.
> Última actualización: 2026-10-24. Glosario del dominio: `CONTEXT.md` (raíz).

## Diseño aprobado pendiente: reproductor visual

La mejora de carátula, título/artista y controles con iconos está definida en [reproductor-visual.md](reproductor-visual.md), con criterios de aceptación aún pendientes de ejecutar. El [ADR-0007](../adr/0007-caratulas-metadatos-compartidos-offline.md) registra los metadatos compartidos y carátulas offline; el [ADR-0008](../adr/0008-transporte-manual-conserva-cola.md), la parada manual al inicio conservando la cola. «Seguir» significa reanudar desde la posición pausada. Los resultados de pruebas de las secciones siguientes corresponden al reproductor anterior a esta mejora.

## Experiencia musical principal (2026-10-24)

### Reproducción durante la descarga

Ver [ADR-0009](../adr/0009-reproduccion-durante-descarga.md). La resolución remota entrega una pista reproducible sin esperar el archivo completo. `ProgressiveMusicDataSource` permite que Media3 lea los bytes disponibles de un `GrowingDownload` compartido: espera al alcanzar el extremo descargado y permite escuchar tras un búfer inicial (objetivo de 1 s de audio en el servicio). El archivo se publica en Descargadas únicamente al finalizar correctamente. Pausar conserva la descarga; cambiar de canción, parar o cerrar el servicio cancela las descargas incompletas. Los saltos a una posición aún no descargada esperan los bytes necesarios. Las descargas completas se reproducen desde su URI local, incluida la URI de una carpeta elegida.

- `MusicController.resolveSelection`, `resolve` y `nextLike` devuelven una URL remota; no inician ni duplican la transferencia de audio. La fuente Media3 la abre al preparar la canción, con su ID como clave estable. Al volver al historial consulta primero el archivo completo.
- `CacheManager` comparte la transferencia entre lectores y saltos, aplica el límite configurable (4 GiB por defecto, 256 MiB mínimo) y publica los archivos completos en la carpeta de descargas elegida mediante el selector de documentos; el índice permanece privado. Si se cambia la ubicación, copia las descargas y carátulas antes de actualizar el índice y conserva la ubicación anterior si falla. Limpia temporales abandonados al crear el gestor del proceso.
- Las carátulas no bloquean el audio. Durante la reproducción remota la pantalla usa imágenes acotadas en caché temporal (hasta cuatro JPEG); al completar el audio se guarda la carátula offline y se actualizan los metadatos de la sesión.
- Red real, AVD `Jarbis_API_35`: «Vino Tinto» empezó a sonar a los **10,4 s** desde la selección (8 s de extracción), antes de completar su descarga (~109 s). El segundo resultado de la búsqueda, «Yo y mi manager», de Kiko Veneno, empezó a los **5,2 s**. Ambos se completaron mientras estaban pausados. Son medidas de esa ejecución, no una garantía de tiempo de inicio.
- Verificación final: `python3 scripts/verify-music.py --connected` → **118 pruebas unitarias, 13 instrumentadas ejecutadas sin fallos y 1 prueba de internet omitida por defecto; APK y lint correctos**. La prueba opt-in de internet se ejecutó por separado con 1 prueba y 0 fallos. El servidor lento controlado verifica una única petición de audio, reproducción antes del archivo completo, guardado en pausa, reproducción offline y cancelación al parar/cambiar de canción.

Las referencias a la espera de descarga completa en la validación histórica de abajo describen el comportamiento anterior.

- `ui/MusicScreen.kt` es la pantalla inicial con reproductor, Buscar, Mi música y Configuración. La pestaña Configuración reúne palabra de activación, idioma, ciudad, módulos, cuota de caché y carpeta de descargas. La escucha se activa explícitamente y solo entonces se pide permiso de micrófono.
- `MusicViewModel.kt` conecta con `MediaController`, observa el estado real y controla transporte/progreso. Búsqueda manual: `MusicController.searchResults` recorre NewPipe/Piped; `resolveSelection` prepara exactamente el ID elegido, sin búsqueda difusa ni sustitución offline. Se conserva `resolve` para la voz.
- `MicInput` conecta `AcousticEchoCanceler` a la sesión de `AudioRecord` mientras la escucha está activa. Informa en el registro si el efecto está activo, no existe o no pudo activarse; en los dos últimos casos continúa escuchando sin atenuar la música.
- `LocalMusicLibrary.kt` enumera recursivamente la carpeta de música elegida y combina esa fuente con las descargas. `SettingsStore.musicFolderUri` y `downloadFolderUri` son independientes. La carpeta de música solo requiere permiso persistente de lectura; la carpeta de descargas requiere lectura/escritura. Si se revoca el acceso a descargas, se avisa y no se inician nuevas transferencias hasta volver a elegir una ubicación.
- Mi música presenta filtros Todas/Carpeta/Descargadas. Al tocar una canción se fija como cola la lista visible ordenada por título. En Descargadas cada canción puede borrarse individualmente; la que está sonando se marca para borrar al parar o cambiar de canción. La carpeta de música propia no se copia ni queda sometida a la cuota.
- `MusicPlayerService` comparte una sesión Media3 entre pantalla, voz y sistema. `ForwardingSimpleBasePlayer` publica Siguiente incluso antes de resolver una relacionada. La cola remota conserva historial; la local termina al acabar su lista. La notificación MediaStyle y los controles de bloqueo los genera Media3.
- Se eliminó el `onBind` que devolvía null (impedía conectar controladores). Las URI se transmiten completas en los intents de voz; no se convierte una URI de documento en una ruta de fichero.
- Descargas serializadas y acceso al índice sincronizado para compartir caché entre UI/voz/servicio. La evicción protege tanto el tema actual como el recién descargado.
- Verificación: `python3 scripts/verify-music.py --connected` compila, ejecuta unitarios, lint y pruebas en AVD de Android Studio. `MusicSessionTest` verifica transporte real por sesión con WAV sintético; `MainActivityTest` comprueba apertura sin micrófono, filtros y Configuración.

### Validación en Android Studio API 35

- AVD dedicado `Jarbis_API_35`, imagen Google Play arm64 API 35, partición de datos de 8 GB. El AVD compartido con Compresor no tenía espacio suficiente para instalar la APK; sus datos se conservaron.
- Resultado: **112 pruebas unitarias, 6 instrumentadas y lint sin errores**. Se cubren fin de cola local sin encadenado remoto, navegación/historial, pausa, desplazamiento y activación explícita de voz con cambio de palabra.
- QA sobre UI real: selector Android → carpeta con dos WAV sintéticos → lectura mediante URI de documento, cola en orden visible y controles nativos en bloqueo. Se verificaron pausa y siguiente desde esa tarjeta.
- Red real: búsqueda de Queen, selección de «Bohemian Rhapsody», descarga completa y reproducción. Después apareció en Descargadas y se reprodujo en modo avión; el permiso de carpeta sobrevivió a un cierre forzado y nueva apertura.
- La descarga completa precede a la reproducción remota y puede tardar dependiendo del origen. El encadenado remoto se verifica de forma determinista con resolutor controlado en las pruebas de sesión.
- Al probar la palabra de activación se detectó una carrera nativa de Vosk: `reset/close` simultáneos con `acceptWaveForm` abortaban el proceso. El acceso al reconocedor queda sincronizado y el micrófono deja de entregar fragmentos antes del cierre. El cambio previo en `AssistantEngine.restart` se conserva.

Las secciones siguientes documentan la resolución por comandos de voz y el historial de la infraestructura.

## Qué es

El **Módulo Música** ejecuta el **comando** del tipo *"pon X"*: resuelve la
búsqueda a una pista reproducible, la descarga a una **caché local** con
**cuota** configurable y la reproduce en segundo plano, encadenando canciones
"parecidas" al terminar cada una. La reproducción no interfiere con la escucha
continua del asistente (Vosk), solo baja el volumen mientras el asistente
escucha el comando (duck). Además acepta **comandos de control** de la
reproducción con confirmación hablada: siguiente, parar y volumen.

## Comandos de música (es/en)

- Búsqueda: `pon`, `toca`, `ponme`, `reproduce`, `reproducir`, `busca`,
  `buscar`, `buscame`, `dame`, `suena`, `escucha`, `quiero escuchar`,
  `dale play`, `pasame`, `echame`, `saca` (en: `play`, `play me`, `search`,
  `find`, `listen to`). Extrae la consulta descartando el verbo + muletillas
  (`MusicQueryParser.extract`).
- Control siguiente: `siguiente`, `pasa canción`, `otra canción` → `nextSong()`.
- Control parar: `para`, `para la música`, `parar` → `stopMusic()`.
- Control volumen: `sube el volumen`, `baja el volumen` → `volumeUp/Down()`.
- Cada control devuelve una **confirmación** hablada ("Siguiente canción.",
  "Música parada.", "Subo el volumen."), o "No hay música sonando."
- `MusicControl.command()` decide si la frase es control ANTES de tratarla
  como nueva búsqueda; "para mañana" NO es parar (solo "para" a secas o
  "para la música").

## Arquitectura (archivos en `app/src/main/java/com/jarbis/alexa/music/`)

| Archivo | Responsabilidad |
|---|---|
| `MusicController.kt` | Orquesta "pon X": caché local primero → orígenes. Devuelve `MusicTrack`. Nunca lanza. |
| `MusicResolver.kt` | Contrato de orgén (`search`, `audioUrlFor`, `related`) + `MusicResolvers.all()` = [NewPipe, Piped]. |
| `NewPipeMusicResolver.kt` | Origen primario: NewPipeExtractor (sin servidor de terceros). |
| `PipedMusicResolver.kt` | Origen de respaldo sobre `PipedClient`. |
| `OkHttpNewPipeDownloader.kt` | implementa `Downloader` de NewPipeExtractor sobre OkHttp. |
| `PipedClient.kt` | API pública de Piped con failover entre instancias. |
| `CacheManager.kt` | Comparte descargas progresivas, cuota configurable, almacenamiento elegido por SAF, migración transaccional y revisiones de biblioteca. Conserva `obtain` para descargar por completo explícitamente. |
| `GrowingDownload.kt` | Una transferencia HTTP secuencial, archivo creciente y lectores en espera; valida final y cancelación. |
| `ProgressiveMusicDataSource.kt` | Fuente Media3: usa el archivo completo si existe; en remoto lee la descarga compartida, sin otro HTTP. |
| `CacheStore.kt` | Índice JSON (kotlinx), cuota/evicción por `playCount`. |
| `MusicPlayerService.kt` | `MediaSessionService` con ExoPlayer; encadena "parecidas" al terminar. |
| `MusicBridge.kt` | Estado compartido entre motor y servicio (`MusicBridgeState`). |
| `PlaybackController.kt` | Ducking `AUDIOFOCUS_TRANSIENT_MAY_DUCK` mientras el asistente escucha el comando (se deshace al capturar la orden). |

## Flujo de resolución

```
"probar: pon una de queen"
 └─ MusicController.resolve("queen")
     ├─ 1. caché local: CacheStore.bestLocalMatch("queen") estricto →
     │      solo si el título coincide de verdad (sin red)
     └─ 2. si no hay match → recorre resolvers en orden:
           NewPipeMusicResolver.search → audioUrlFor → CacheManager.remoteTrack (sin descargar)
          └─ si falla → PipedMusicResolver (fallback)
     ├─ 3. si todos los orígenes fallan (offline) → mostPlayed() como degradación
      └─ 4. si no hay nada → null → el asistente responde "No pude encontrar música."
      └─ reproducción: Media3 → ProgressiveMusicDataSource → GrowingDownload
           ├─ búfer inicial → empieza a sonar mientras continúa la transferencia
           └─ EOF válido → renombrado + índice → Descargadas
```

- `nextLike(videoId)` (encadenar al terminar) recorre `related()` de cada
  origen y prepara la primera parecida con dirección de audio; sin red cae a la mejor
  canción cacheada que no sea la actual.
- El servicio (`MusicPlayerService` → `MusicBridge.nextResolver`) pide la
  siguiente al terminar cada tema.

## Resolución remota: por qué hay dos orígenes

- **NewPipeExtractor** (primario): extrae directamente de YouTube sin servidor
  de terceros. Mantiene la librería `org.schabi.newpipe.extractor` (JitPack,
  actualmente `v0.26.4`); hay que actualizarla cuando YouTube cambie su API.
- **Piped** (fallback): API pública de Piped. **Hoy todas las instancias
  públicas están caídas o devuelven `items:[]`** (comprobado 2026-08-09); se
  mantiene solo como red de seguridad. Lista en `PipedClient.DEFAULT_INSTANCES`.

### Cómo falla cada origen
- Error de red/crash → el método devuelve `null`/`emptyList`, nunca lanza →
  `MusicController` sigue con el siguiente origen → respuesta amable TTS.
- No bloquear: los resolvers corren en `Dispatchers.IO` y son `suspend`.

## Cache local (local-first)

- Forma de la caché: índice `index.json` privado en `externalFilesDir("music_cache")`;
  los archivos descargados viven ahí por defecto o en la **carpeta de descargas** elegida
  mediante SAF. El temporal progresivo se conserva privado hasta completar el audio.
- Cuota: configurable en Ajustes, predeterminada en 4 GiB y mínimo de 256 MiB; cuenta
  audio y carátulas y evita la canción en
  reproducción; evictúa la de menor `playCount` (desempate `lastPlayed`).
- Al cambiar la carpeta, se copian audio y carátulas antes de cambiar el índice; ante
  un error se conserva la ubicación anterior. Si el permiso de destino deja de existir,
  se muestra un aviso y las nuevas descargas fallan hasta volver a seleccionar carpeta.
- Cada canción descargada se puede borrar desde Mi música; si está sonando, queda
  marcada y se elimina al parar o cambiar de canción.
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
- Gradle: `com.github.teamnewpipe:NewPipeExtractor:v0.26.4` (JitPack,
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

- `MusicBridge.state(...).resolve(query)` → `MusicTrack?` — lo usa el motor.
- `MusicBridge.state(...).isPlaying` — marca si hay algo en reproducción
  (para que el motor sepa si responden los controles).
- `MusicPlayerService.intentFor(context, track)` para reproducir.
- `MusicPlayerService.stop(context)` para detener.
- `MusicPlayerService.next(context)` para saltar a la siguiente "parecida".
