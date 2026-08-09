# Add project specific ProGuard rules here.
-keep class org.vosk.** { *; }

# Reglas de NewPipeExtractor (solo aplican si se activa minify).
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter { *; }
-dontwarn org.mozilla.javascript.tools.**
-keep class org.schabi.newpipe.extractor.timeago.patterns.** { *; }