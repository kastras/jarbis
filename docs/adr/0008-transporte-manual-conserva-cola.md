# ADR-0008 — Controles manuales sobre la sesión compartida, conservando la cola

**Estado:** aceptado como diseño; implementación pendiente.  
**Fecha:** 2026-10-24.

El nuevo reproductor distingue pausar, seguir y parar. Se decide ejecutar sus controles sobre la sesión multimedia existente y conservar en ella la canción y la cola al parar manualmente, en lugar de reconstruirlas desde una copia de estado en la interfaz.

**Pausar** conserva la posición; **seguir** reanuda desde esa posición. **Parar** detiene la reproducción, vuelve al inicio de la canción actual y conserva la lista seleccionada. Al reproducir después de parar, se empieza esa canción desde el inicio. Anterior y siguiente mantienen la navegación por la cola y el historial existentes.

## Alternativas consideradas

- **Parar eliminando la cola o cerrando el servicio:** libera el estado, pero impide volver a reproducir la canción y exige guardar o reconstruir una cola aparte.
- **Pausar para todas las operaciones:** conserva el estado, pero no cumple la diferencia acordada entre pausar y parar al inicio.
- **Transporte compartido con parada manual al inicio (elegido):** mantiene una única fuente de estado y permite mostrar los controles correctos después de cada operación.

## Consecuencias

- La pantalla reflejará el estado confirmado de la sesión, también cuando el cambio venga de los controles multimedia de Android.
- Parar manualmente no se considerará final natural de canción y no disparará relacionadas ni avance de cola. Una resolución de siguiente pendiente no podrá reiniciar la reproducción después de parar.
- Se debe revisar el tratamiento de pausa, parada y solicitudes asíncronas al implementar, además del dibujo de los botones.
- El alcance de esta decisión es el transporte manual. El comando hablado «para» mantiene el contrato documentado en el ADR-0005; equipararlo a esta parada requeriría una decisión explícita adicional.

Estados y criterios verificables: [Reproductor visual](../musica/reproductor-visual.md).
