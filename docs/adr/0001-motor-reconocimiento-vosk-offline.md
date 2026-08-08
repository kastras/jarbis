# ADR-0001 — Motor de reconocimiento de voz 100% offline con Vosk

El asistente necesita escuchar de forma continua, detectar una **palabra de activación** configurable y reconocer **comandos**. Se decidió usar **Vosk** (open source, Apache-2.0) como único motor de reconocimiento, en lugar de Porcupine (Picovoice; las palabras custom requieren entrenarlas en la consola web y/o cuenta) o del SpeechRecognizer de Google (depende de red y no permite palabra custom).

Vosk permite construir en tiempo de ejecución gramáticas restringidas (p. ej. `["alexa","[unk]"]`) para el detector del activador, de modo que cambiar la **palabra de activación** en la configuración no requiere reentrenar ni recompilar: basta recrear la gramática. Se embarcan en assets los modelos `vosk-model-small-es-0.42` y `vosk-model-small-en-us-0.15` (unos 40 MB cada uno).

**Status**: accepted

**Consecuencia**: el reconocimiento funciona sin red, pero la precisión de los modelos "small" es limitada; si llegara a necesitarse más exactitud se subiría a un modelo `large` (más peso) antes que cambiar de motor.