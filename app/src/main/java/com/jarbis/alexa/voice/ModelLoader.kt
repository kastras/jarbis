package com.jarbis.alexa.voice

import android.content.Context
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Descomprime un modelo Vosk (.zip en assets) a la primera ejecución y
 * devuelve la ruta del directorio del modelo para Model(path).
 * Los zips oficiales traen un prefijo de primer nivel (el nombre del modelo);
 * se elimina para que el destino contenga am/, graph/ y conf/ directamente.
 *
 * El zip viaja SIN comprimir dentro del APK (androidResources.noCompress):
 * `assets.open` devuelve exactos los bytes del fichero, de modo que la copia
 * a `_bundle.zip` es fiel (con el asset comprimido, el inflado largo acababa
 * en `zip END header not found`). La extracción usa `ZipFile`, que lee el
 * directorio central: los zips de Vosk marcan los tamaños en el directorio
 * central (data descriptors), que es lo que `ZipInputStream` (cabeceras
 * locales) no respeta y por lo que algunos ficheros pequeños salían a 0 bytes.
 */
object ModelLoader {

    fun ensure(context: Context, assetZip: String): String {
        val modelName = modelNameOf(assetZip)
        val dir = File(context.filesDir, "models/$modelName")
        if (isValid(dir)) return dir.absolutePath

        dir.deleteRecursively()
        dir.mkdirs()

        val archive = File(dir, "_bundle.zip")
        try {
            copyToFile(context.assets.open(assetZip), archive)

            ZipFile(archive).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory || entry.name.contains("..")) continue
                    val relative = flattenPrefix(entry.name, modelName)
                    if (relative.isBlank()) continue
                    val target = File(dir, relative)
                    target.parentFile?.mkdirs()
                    copyToFile(zip.getInputStream(entry), target)
                }
            }
        } finally {
            archive.delete()
        }

        check(isValid(dir)) { "Modelo extraído incompleto desde $assetZip" }
        return dir.absolutePath
    }

    /** Copia un origen a un fichero cerrando ambos flujos (flush final garantizado). */
    internal fun copyToFile(source: InputStream, target: File) {
        source.use { input ->
            target.outputStream().buffered().use { output -> input.copyTo(output) }
        }
    }

    private fun modelNameOf(assetZip: String): String = assetZip.removeSuffix(".zip")

    /** Elimina el primer segmento del path si es el nombre del modelo. */
    internal fun flattenPrefix(name: String, modelName: String): String {
        val idx = name.indexOf('/')
        if (idx < 0) return name
        return if (name.substring(0, idx) == modelName) name.substring(idx + 1) else name
    }

    internal fun isValid(dir: File): Boolean =
        dir.exists() &&
            File(dir, "conf/model.conf").let { it.isFile && it.length() > 0 } &&
            File(dir, "am/final.mdl").let { it.isFile && it.length() > 0 } &&
            File(dir, "graph/Gr.fst").let { it.isFile && it.length() > 0 }
}