# ADR 0006: Gestión del Ciclo de Vida del Motor y Corrutinas

## Contexto
El motor de reconocimiento (`AssistantEngine`) utiliza dos ámbitos de corrutinas (`CoroutineScope`): `lifetimeScope` para tareas permanentes (como observar cambios en la configuración) y `runScope` para el bucle de ejecución activo (escucha de micrófono y procesamiento).

Se detectó que al realizar un `restart()`, el `runScope` actual se cancelaba, pero no se provisionaba uno nuevo, dejando el motor en un estado donde los procesos dependientes de `runScope` (como la escucha de comandos) ya no podían ejecutarse.

## Decisión
1. **Re-inicialización de Scope**: El método `restart()` debe asegurar la creación de un nuevo `CoroutineScope` con un `SupervisorJob` para que el motor pueda recuperar su operatividad tras una interrupción o cambio de configuración (como cambio de idioma).
2. **Vínculo al ApplicationContext**: Para evitar fugas de memoria (*memory leaks*), `AssistantEngine` debe operar estrictamente con el `applicationContext`.
3. **Gestión de la Fase**: El ciclo de vida de la fase (`Phase`) debe ser consistente con el estado del `runScope`.

## Consecuencias
- El método `start()` ahora es idempotente y seguro si se llama repetidamente.
- El método `restart()` es robusto frente a la cancelación de jobs previos.
- Se garantiza que los procesos de escucha no mueren permanentemente tras un cambio de idioma o palabra de activación.
