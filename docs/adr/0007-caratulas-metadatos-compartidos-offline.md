# ADR-0007 — Carátulas y metadatos compartidos, disponibles sin conexión

**Estado:** aceptado como diseño; implementación pendiente.  
**Fecha:** 2026-10-24.

El reproductor necesita mostrar carátula, título y artista tanto para música de internet como para archivos locales. La misma canción debe identificarse de forma coherente en la aplicación, la notificación y la pantalla de bloqueo, incluso cuando una descarga se escucha sin conexión.

Se decide transportar los metadatos opcionales junto con la identidad de la canción, desde la resolución o lectura local hasta la sesión Media3. La interfaz consumirá los metadatos de esa sesión compartida; no mantendrá una segunda identidad de canción independiente del servicio.

Las carátulas remotas de canciones descargadas se conservarán localmente asociadas a la descarga. Para archivos de la carpeta elegida se leerá la imagen incrustada, cuando exista, sin modificar el archivo original. La ausencia de artista o carátula y los errores de lectura o descarga de imágenes no impedirán reproducir el audio: se mostrará una imagen musical de sustitución cuando sea necesario.

## Alternativas consideradas

- **Cargar la carátula solo desde la pantalla:** simplifica el primer cambio visual, pero duplica la resolución de metadatos y puede mostrar otra imagen o ninguna en bloqueo.
- **Conservar únicamente una URL remota:** evita almacenamiento de imágenes, pero pierde la carátula de las descargas al escuchar sin conexión.
- **Metadatos compartidos y carátula local para descargas (elegido):** requiere ampliar modelos y persistencia, a cambio de coherencia entre controles y disponibilidad offline.

## Consecuencias

- La ampliación del índice debe seguir leyendo canciones antiguas sin estos campos; no se exigirá volver a descargar su audio.
- Las imágenes tendrán una política de tamaño y limpieza ligada a la caché, para no crear almacenamiento ilimitado ni imágenes huérfanas. El formato, los límites concretos y la disposición de archivos se definirán al implementar.
- La extracción y carga de imágenes se realizará fuera del hilo principal y evitará aplicar una imagen tardía a otra canción tras un salto.
- La lectura local debe respetar el permiso persistente y las URI de documento existentes.
- Esta decisión complementa el ADR-0004; no cambia los orígenes ni las reglas de elección o continuidad musical.

Diseño visual, alcance y aceptación: [Reproductor visual](../musica/reproductor-visual.md).
