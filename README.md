# Jarbis

Asistente de voz Android 100% offline en el reconocimiento (Vosk). Escucha continua, se despierta con una **palabra de activación** configurable y ejecuta **comandos** hablados a través de **módulos** (Tiempo, Música).

## Requisitos

- Android SDK (emulador `Medium_Phone_API_35` ya usado en este proyecto).
- **Usa siempre `./gradlew`** (wrapper Gradle 8.13). NO uses el gradle del sistema (9.6.1) ni el entorno con AGP más nuevo.

## Emulador

Si no está corriendo:

```
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_35
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

Abre la app en el emulador, pulsa **Arrancar** y concede permisos de micrófono y notificaciones.

### Probar el flujo de voz

- Con voz: di la **palabra de activación** (por defecto `alexa`) y a continuación el comando, p.ej. *"alexa pon una canción de queen"*.
- Sin voz (UI): en la pantalla hay botones para simular un comando (despacho del pipeline sin micrófono).

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

## Música (fase 2)

El **Módulo Música** reproduce en segundo plano *in-app*:

- **Piped API** (búsqueda/stream/next) con fallover de instancias públicas.
- **Media3/ExoPlayer** en un `MediaSessionService` (`foregroundServiceType="mediaPlayback"`).
- **Caché local** de 1 GB en `externalFilesDir("music_cache")` con índice JSON; evicción por menor `playCount` (nunca la canción en reproducción).
- **Ducking**: al despertar el asistente la música baja (`AUDIOFOCUS_TRANSIENT_MAY_DUCK`) sin pausar la escucha continua.
- Offline: fallback a la mejor canción cacheada.

Nota: la reproducción real depende de que una instancia pública de Piped responda. Si falla la red, se usa la caché local (vacía hasta el primer uso con red).

## Documentación del dominio

- **Glosario del producto**: `CONTEXT.md` (palabras y relaciones del dominio Jarbis).
- **Decisiones (ADRs)**: `docs/adr/0001-*.md` … `0004-*.md`.
  - `0004` (aceptado) describe la arquitectura de Música fase 2; `0003` está `superseded`.

## Modelos Vosk

Los modelos (`vosk-model-small-es-0.42.zip`, `vosk-model-small-en-us-0.15.zip`) están en `app/src/main/assets/`. Para descargarlos/reinstalarlos se provee el script `scripts/download-models.sh`.