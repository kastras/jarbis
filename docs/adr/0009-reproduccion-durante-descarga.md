# ADR-0009 — Reproducir mientras se descarga y publicar solo archivos completos

**Estado:** aceptado e implementado.  
**Fecha:** 2026-10-24.

## Contexto

La selección manual y los comandos de voz esperan actualmente a descargar todo el audio. En pruebas reales con Estopa la espera supera los cien segundos por canción aunque ya hay bytes suficientes para escuchar. Media3 admite fuentes progresivas y sigue siendo el único reproductor compartido por pantalla, voz y controles del sistema.

## Decisión

- Separar la obtención de la dirección remota de la descarga completa. Resolver una selección devuelve su identidad, metadatos y dirección sin descargar el audio.
- Utilizar una fuente progresiva de Media3 respaldada por un archivo temporal creciente y una única transferencia HTTP de audio. El lector espera cuando necesita bytes que aún no han llegado; no inicia otra descarga por cada lectura o salto.
- La transferencia pertenece al servicio, no a la pantalla. Pausar permite completar el guardado. Cambiar de canción, parar o destruir el servicio cancela el trabajo incompleto y libera los lectores en espera.
- Conservar el índice y los archivos completos existentes. Publicar el temporal mediante renombrado solo al llegar al final válido de la respuesta; entonces actualizar el índice, los metadatos y Mi música. Limpiar parciales cancelados o fallidos, también al recuperar una sesión tras cierre del proceso.
- Contabilizar el temporal dentro del presupuesto de descarga y aplicar la cuota a los archivos completos, protegiendo el tema en uso. Las carátulas no retrasan el inicio del audio.
- Una búsqueda manual mantiene exactamente la selección. La voz y las relacionadas utilizan el mismo camino progresivo. Las canciones locales o ya descargadas no requieren red.

## Consecuencias

Se reduce el tiempo hasta escuchar, pero no se corrigen los errores al extraer la dirección del audio. Si la red es más lenta que la reproducción, Media3 vuelve al estado de búfer. Adelantar a una posición no recibida puede requerir esperar: no se abre una transferencia adicional. Una canción cancelada antes de completarse no aparece en Descargadas.

## Verificación

- Servidor controlado lento: reproducción real antes del fin de la transferencia y una sola petición de audio.
- Finalización: archivo completo en el índice y reproducción posterior sin red.
- Cancelación, error HTTP/respuesta truncada y cierre: sin entradas parciales ni lectores bloqueados.
- Controles de sesión y biblioteca local conservados en el emulador de Android Studio.
- Prueba opt-in con red real y Estopa, distinguiendo el tiempo de extracción del inicio de reproducción.

Implementación: `GrowingDownload`, `ProgressiveMusicDataSource`, `CacheManager` y `MusicPlayerService`. El servicio usa un objetivo de búfer inicial de 1 s; la extracción de la dirección y las condiciones de red siguen influyendo en la espera total. `ProgressiveDownloadTest` cubre lectores compartidos, saltos, errores y parciales. `MusicSessionTest` comprueba reproducción real antes de completar, pausa con guardado y reproducción offline, además de la cancelación desde los controles.

Prueba real en Android Studio API 35: «Vino Tinto» de Estopa sonó a los 10,4 s (8 s resolviendo la dirección) y acabó de guardarse aproximadamente a los 109 s. Un segundo resultado sonó a los 5,2 s. Estos tiempos son observaciones de una ejecución, no garantías.
