# Jarbis

Reproductor de música Android con búsqueda manual en internet, biblioteca local y asistente de voz opcional. El reconocimiento de voz es 100% offline (Vosk): escucha una **palabra de activación** configurable y ejecuta **comandos** mediante **módulos** (Tiempo, Música).

## Requisitos

- Android SDK con un AVD de Android Studio API 35 (`Jarbis_API_35` para las pruebas de Música).
- **Usa siempre `./gradlew`** (wrapper Gradle 8.13). NO uses el gradle del sistema (9.6.1) ni el entorno con AGP más nuevo.

## Emulador

Si no está corriendo:

```
~/Library/Android/sdk/emulator/emulator -avd Jarbis_API_35
```

Comprueba que la app está conectada:

```
adb devices
```

> Gotcha: justo después del boot el emulador puede estar ocupado y los tests instrumentados fallan por ANR que no es de la app. Espera a que el sistema esté idle y reintenta.

## Ejecutar la app

```
./gradlew :app:installDebug
```

Al abrir aparece el **reproductor**. Usa **Buscar** para elegir un resultado de internet o **Mi música → Elegir carpeta** para abrir tus canciones locales. Los filtros **Todas**, **Carpeta** y **Descargadas** separan las fuentes. La carpeta se reproduce directamente, sin copiar archivos.

La reproducción continúa en segundo plano, con pausa/reanudar, anterior, siguiente y progreso en los controles multimedia de Android y en la pantalla de bloqueo. Desde Mi música se sigue la lista visible; desde internet se encadenan canciones parecidas.

Las canciones de internet **empiezan a sonar mientras se descargan**, tras un búfer inicial. La escucha y el guardado comparten una transferencia de audio. Al completarse aparecen en **Descargadas**, disponibles sin conexión. Pausar permite terminar el guardado; parar o cambiar de canción cancela el archivo incompleto. Adelantar a una parte aún no recibida puede requerir esperar.

### Probar el flujo de voz

- En **Configuración**, cambia la **palabra de activación** y pulsa **Activar escucha por voz**. Solo entonces se solicita permiso del micrófono.
- Di la palabra (por defecto `alexa`) y a continuación el comando, p.ej. *"alexa pon una canción de queen"*.
- El uso manual del reproductor no necesita permiso del micrófono.

Ver los logs de la app en directo:

```
adb logcat -s Jarbis
```

## Tests

- **Unitarios** (rápidos, sin emulador):
  ```
  ./gradlew :app:testDebugUnitTest
  ```
- **Instrumentados** (en emulador; espera a que el sistema esté idle):
  ```
  ./gradlew :app:connectedDebugAndroidTest
  ```
  Reportes: `app/build/reports/androidTests/connected/debug/`.

Verificación conjunta con salida resumida (APK, unitarios, lint y pruebas instrumentadas):

```sh
python3 scripts/verify-music.py --connected
```

El AVD debe estar arrancado y desbloqueado. Las pruebas de sesión usan audio WAV sintético y verifican cola local, salto remoto, historial, pausa y desplazamiento sin depender de servicios externos. La búsqueda real por internet se valida por separado porque depende de los orígenes.

Para comprobar búsqueda, reproducción progresiva y guardado con internet real:

```sh
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.jarbis.alexa.RemoteMusicTest \
  -Pandroid.testInstrumentationRunnerArguments.remoteMusic=true \
  -Pandroid.testInstrumentationRunnerArguments.musicQuery=estopa --console=plain -q
```

Esta prueba queda omitida en la ejecución normal. Sus logs (`adb logcat -s RemoteMusicTest`) distinguen resolución de la dirección, inicio del audio y archivo completo. La suite normal verifica el arranque anticipado con un servidor lento local y una única petición de audio.

## Música

El **Módulo Música** reproduce en segundo plano *in-app*:

- **NewPipe** como origen principal y **Piped API** como respaldo.
- **Media3/ExoPlayer** en un `MediaSessionService` (`foregroundServiceType="mediaPlayback"`).
- **Caché local** de 1 GB en `externalFilesDir("music_cache")` con índice JSON; evicción por menor `playCount` (nunca la canción en reproducción).
- **Ducking**: al despertar el asistente la música baja (`AUDIOFOCUS_TRANSIENT_MAY_DUCK`) sin pausar la escucha continua.
- Offline: fallback a la mejor canción cacheada.

La búsqueda manual presenta resultados y reproduce exactamente la selección, sin sustituirla por otra canción cacheada si falla. Los comandos de voz conservan su resolución local-first y degradación offline. Las descargas están sujetas a la cuota de caché; la carpeta elegida no lo está.

## Documentación del dominio

- **Glosario del producto**: `CONTEXT.md` (palabras y relaciones del dominio Jarbis).
- **Decisiones (ADRs)**: [`docs/adr/`](docs/adr/).
  - `0004` (aceptado) describe la arquitectura de Música fase 2; `0003` está `superseded`.
- **Próxima mejora del reproductor** (diseño aprobado, implementación pendiente): [carátula y controles accesibles](docs/musica/reproductor-visual.md).
  - [ADR-0007](docs/adr/0007-caratulas-metadatos-compartidos-offline.md): metadatos compartidos y carátulas offline.
  - [ADR-0008](docs/adr/0008-transporte-manual-conserva-cola.md): pausa, seguir y parada manual conservando la cola.
- [ADR-0009](docs/adr/0009-reproduccion-durante-descarga.md): reproducción durante la descarga y publicación offline de archivos completos.

## Modelos Vosk

Los modelos (`vosk-model-small-es-0.42.zip`, `vosk-model-small-en-us-0.15.zip`) están en `app/src/main/assets/`. Para descargarlos/reinstalarlos se provee el script `scripts/download-models.sh`.
