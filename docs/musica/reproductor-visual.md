# Reproductor visual con carátula y controles accesibles

**Estado:** diseño aprobado; pendiente de implementación y validación.  
**Fecha:** 2026-10-24.

## Objetivo

Hacer el reproductor más amigable: carátula visible, identificación de la canción y controles reconocibles propios de Android. El usuario confirmó que «seguir» significa reanudar desde el punto donde se pausó.

## Presentación acordada

- Carátula grande y centrada, con título y artista debajo; si el artista no está disponible se omite ese dato.
- Imagen musical de sustitución si no hay portada o falla su carga.
- Barra de progreso, tiempo transcurrido y duración.
- Botones de iconos Material con etiquetas accesibles: anterior, reproducir, pausar/seguir, siguiente y parar.
- Reproducir, pausar y seguir comparten un botón principal cuyo icono y etiqueta cambian con el estado; parar es un control separado.
- Los controles deben poder usarse sin recortes en una pantalla estrecha o con texto ampliado; el título largo no debe desplazar los botones fuera de pantalla.
- Se conservan Buscar, Mi música y Configuración y las reglas de cola aprobadas: lista visible para música local, relacionadas para selección remota.

## Contrato del transporte

| Estado o comando | Resultado esperado |
|---|---|
| Sin canción seleccionada | Imagen de sustitución e invitación a elegir música; transporte sin operaciones inválidas. |
| Reproducir tras seleccionar o parar | Inicia la canción actual desde su posición inicial. |
| Pausar | Detiene el avance conservando posición, canción y cola. |
| Seguir | Continúa desde la posición pausada. |
| Parar | Detiene, vuelve al inicio y conserva canción y cola; no encadena otra canción. |
| Anterior / siguiente | Navega por la cola o historial disponibles; el botón refleja si la operación está disponible. |
| Cambios desde Android | La interfaz actualiza carátula, título, progreso y controles a partir de la sesión. |

La parada manual se especifica en el [ADR-0008](../adr/0008-transporte-manual-conserva-cola.md). El comportamiento hablado previo no se modifica por este documento.

## Carátulas y metadatos

Contrato arquitectónico: [ADR-0007](../adr/0007-caratulas-metadatos-compartidos-offline.md).

- **Internet:** obtener la carátula de la canción elegida y conservarla para la descarga.
- **Carpeta:** leer los metadatos y la carátula incrustada si el archivo los ofrece, mediante su URI de documento.
- **Descargadas:** utilizar los metadatos e imagen disponibles localmente, sin necesitar red.
- **Sesión:** propagar los mismos datos a Media3 para su presentación en la aplicación, la notificación y el bloqueo, según las capacidades de Android.
- **Degradación:** seguir reproduciendo si la imagen falta, está dañada o no se puede leer. No inferir un artista inventado ni asociar la portada de otra canción.

## Mapa de implementación

Las rutas siguientes son relativas a `app/src/main/java/com/jarbis/alexa/` y se localizaron con CBM. Describen trabajo pendiente, no funciones ya implementadas.

| Área | Puntos de integración |
|---|---|
| Diseño y accesibilidad | `ui/MusicScreen.kt`: `NowPlaying` y controles. |
| Estado y transporte | `MusicViewModel.kt`: `MusicUiState`, observación de metadatos y operaciones manuales. |
| Datos de la canción | `music/MusicResolver.kt`, `music/MusicController.kt`: ampliar metadatos opcionales y conservarlos durante la resolución. |
| Orígenes remotos | `music/NewPipeMusicResolver.kt`, `music/PipedMusicResolver.kt`, `music/PipedClient.kt`: recoger metadatos disponibles. |
| Biblioteca y persistencia | `music/LocalMusicLibrary.kt`, `music/CacheManager.kt`, `music/CacheStore.kt`: lectura local, compatibilidad del índice y conservación/limpieza de imágenes. |
| Sistema Android | `music/MusicPlayerService.kt`: publicar metadatos y preservar la cola al parar manualmente. |

El código actual tiene una tarjeta de transporte textual; `MusicTrack` contiene identificador, título y URI de audio. Por ello, añadir una imagen a la pantalla no basta: hay que llevar los metadatos por todo el flujo. La biblioteca, la reproducción compartida y las pruebas existentes son la base de este cambio.

## Orden de implementación

1. Ampliar metadatos opcionales con lectura compatible del índice existente.
2. Incorporar extracción local y remota y conservación offline de imágenes.
3. Propagar metadatos a la sesión y completar la semántica de pausa, seguir y parar manualmente.
4. Renovar la tarjeta visual y sus controles accesibles.
5. Añadir las comprobaciones relevantes y ejecutar la validación en Android Studio.

## Criterios de aceptación

- [ ] Se muestra la portada correspondiente a la canción actual, junto con título y artista cuando están disponibles.
- [ ] Un archivo local con carátula incrustada la muestra sin copiar ni modificar el audio original.
- [ ] Una canción descargada con carátula conserva su imagen tras cerrar y abrir la aplicación en modo avión.
- [ ] Un índice antiguo o una canción sin portada siguen siendo reproducibles y muestran la imagen de sustitución.
- [ ] Una imagen fallida o de carga tardía no bloquea los controles ni sustituye la portada de la canción siguiente.
- [ ] Pausar y seguir conservan la posición, con tolerancia al tiempo de procesamiento; el audio continúa desde ese punto.
- [ ] Parar deja la canción al inicio, sin reproducción ni avance espontáneo; reproducir después inicia esa misma canción.
- [ ] Parar durante la resolución de una relacionada no permite que esa solicitud vuelva a arrancar música.
- [ ] Anterior y siguiente respetan la cola local o el historial/remoto existentes.
- [ ] La pantalla refleja pausa y cambios de canción hechos desde los controles de bloqueo.
- [ ] Carátula y metadatos se publican en la sesión para notificación y bloqueo.
- [ ] Los botones tienen nombres accesibles y objetivos táctiles de al menos 48 dp; la carátula no duplica innecesariamente el título para lectores de pantalla.
- [ ] Se comprueban pantalla estrecha, título largo y texto ampliado sin ocultar los controles esenciales.

## Validación prevista

Usar el AVD de Android Studio **`Jarbis_API_35`**. Preparar archivos sintéticos con y sin imagen incrustada y una descarga real con carátula; comprobar reproducción local, sin conexión y controles nativos de bloqueo. Las pruebas controladas deben cubrir metadatos y cambios de estado sin depender de servicios externos; la descarga real se verifica por separado.

```sh
python3 scripts/verify-music.py --connected
```

Los resultados anteriores registrados en [el contexto de Música](CONTEXT.md) corresponden al reproductor previo. No validan esta mejora visual ni sus criterios todavía pendientes.
