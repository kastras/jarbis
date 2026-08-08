# ADR-0003 — Módulo Música: abrir YouTube externamente en v1

El **Módulo Música** debe reproducir la **búsqueda** pedida por la persona ("pon una canción de X"). Para la primera versión se decidió **abrir la búsqueda en YouTube mediante `Intent.ACTION_VIEW`** (app de YouTube o navegador) en lugar de reproducir dentro de la app.

Motivos: sin API key, sin dependencias frágiles y listo para probar ya en el emulador. El reproductor in-app (ExoPlayer/Media3 + un proveedor de streams tipo Piped) queda como fase 2, y encaja sin cambios en el registro de módulos del ADR-0002.

**Status**: superseded by [ADR-0004](0004-musica-piped-media3-cache-local.md) (reproducción in-app en fase 2)

**Considered Options**:
- **In-app streaming (Media3 + Piped API)**: reproducción dentro de la app, pero depende de instancias públicas de terceros que pueden caer.
- **Intent externo (elegido)**: simple y estable para la v1; el usuario cambia de app para la reproducción.
- **YouTube Data API con API key**: reproducción controlada pero exige clave y cuentas.

**Consecuencia**: hasta la fase 2, la reproducción sucede fuera de la app; la interfaz `CommandModule` ya está lista para enchufar el reproductor interno sin reescribir el despacho de comandos.