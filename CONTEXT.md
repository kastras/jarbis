# Jarbis

Aplicación Android de asistente de voz que escucha de forma continua, se despierta al oír una palabra de activación configurable y ejecuta comandos hablados por voz (100% offline en el reconocimiento).

## Language

**Asistente**:
La aplicación completa: persona habla → **palabra de activación** → **comando** → **módulo** ejecuta → **respuesta** hablada.
_Avoid_: skill, bot, voice assistant in product copy

**Palabra de activación**:
Palabra que, al ser detectada por el asistente, pasa de escuchar a otra a activarse y recoger un comando.
_Avoid_: hotword, keyword, gatillo

**Comando**:
Frase hablada que la persona pronuncia justo después de la **palabra de activación** y que el asistente intenta despachar a un **módulo**.
_Avoid_: acción, intent

**Módulo de comando**:
Unidad funcional pluggable que reconoce un tipo de orden (reconocimiento de frase + ejecución) y produce una **respuesta**.
_Avoid_: plugin extraño; usar "módulo"
_Avoid_: skill

**Palabra de comando** / **keywords**:
Las frases por las que el asistente decide a qué **módulo** va un **comando**; se definen por **idioma**.

**Tiempo**:
**Módulo de comando** que responde con el estado del tiempo en una **ciudad** (temperatura y condición).

**Música**:
**Módulo de comando** que reproduce en segundo plano la **búsqueda** pedida al activar, guarda una copia local de las canciones (con un **límite de disco**), encadena canciones parecidas al terminar cada una y responde con una **respuesta** de confirmación a **comandos de control** de lo que suena (siguiente, parar, subir/bajar el volumen).

**Respuesta**:
El texto que el asistente pronuncia (TTS) o abre en pantalla tras ejecutar un **módulo**.

**Comando de control**:
**Comando** que en lugar de pedir algo nuevo ordena cambiar lo que suena: siguiente canción, parar, o subir/bajar el volumen. Lo despacha el **Módulo Música** (p.ej. "siguiente canción", "para", "sube el volumen") y devuelve una **respuesta** de confirmación.

**Idioma**:
Variedad de idioma (es/en) en la que se escucha y responde al asistente; se elige por el usuario y afecta al reconocimiento y a la voz.

## Relationships

- Un **Asistente** acoge a un sendo de módulos activos (0..n), cada prueba con su **idioma**.
- Un **comando** se despacho como máximo a un **módulo** (fallback → UnknownCommand cuando no hablete ningún módulo).
- Un **módulo** tiene una o más **keywords** por **idioma** y devuelve una **respuesta**.
- La **palabra de activación** es única por config; los **módulos** son independientes de ella.

## Example dialogue

> **Dev:** "Si el usuario dice 'alexa pon una canción de queen', ¿la *canción* es un comando o parte de la búsqueda?"
> **Domain expert:** "Es el *comando* entero: se despacha al **Módulo Música** y la búsqueda ('queen') es el argumento de esa activación."
> **Dev:** "¿Tiempo y Música pueden estar desactivados por separado?"
> **Domain expert:** "Sí, cada **módulo** se activa/desactiva independientemente en configuración."

## Flagged ambiguities

- "alexa" es solo el valor por defecto de la **palabra de activación**; el término correcto es **palabra de activación** (configurable sin reentrenar).