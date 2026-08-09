# ADR-0002 — Comandos como módulos pluggables

Los **comandos** deben ser ampliables sin tocar el núcleo del asistente. Se decidió un modelo de **módulos**: una interfaz `CommandModule` (identificador, `keywords` por **idioma** y un método `execute` suspendible) descubierta por un registro (`CommandRegistry`) que elige qué módulo recibe cada frase reconocida. Cada módulo se activa/desactiva por separado en la configuración (guardado en DataStore como conjunto de módulos deshabilitados). Si ningún módulo matchea, el asistente responde con `UnknownCommand` y sugerencias.

El matcheo es por **palabra completa normalizada** (minúsculas, sin acentos ni puntuación), eligiendo la **keyword más larga** cuando varias coinciden (ver ADR-0005); así "compone" no dispara "pon" y "tiempo para mañana" gana `tiempo` sobre `para`. Los dos primeros módulos son **Tiempo** (Open-Meteo, sin API key) y **Música** (ver ADR-0003/0004/0005).

**Status**: accepted

**Consecuencia**: añadir un tercer módulo es crear la clase y registrarla en `DefaultModules`; el pipeline de voz, la UI y la configuración no cambian.