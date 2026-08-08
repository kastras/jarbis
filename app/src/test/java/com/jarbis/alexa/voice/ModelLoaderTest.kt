package com.jarbis.alexa.voice

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ModelLoaderTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `quita el prefijo del nombre del modelo`() {
        assertEquals(
            "graph/Gr.fst",
            ModelLoader.flattenPrefix("vosk-model-small-es-0.42/graph/Gr.fst", "vosk-model-small-es-0.42"),
        )
    }

    @Test
    fun `conserva rutas anidadas sin prefijo`() {
        assertEquals(
            "conf/model.conf",
            ModelLoader.flattenPrefix("conf/model.conf", "vosk-model-small-es-0.42"),
        )
    }

    @Test
    fun `archivo en la raiz se mantiene`() {
        assertEquals("README", ModelLoader.flattenPrefix("README", "x"))
    }

    @Test
    fun `copia un zip grande sin truncar los bytes finales`() {
        val payload = buildZip(listOf("conf/model.conf", "am/final.mdl", "graph/Gr.fst"), payloadSize = 20_000)
        val target = File(temp.newFolder(), "_bundle.zip")

        ModelLoader.copyToFile(payload.inputStream(), target)

        assertEquals(payload.size.toLong(), target.length())
        assertArrayEquals(payload, target.readBytes())
        ZipFile(target).use { zip ->
            val entry = zip.getEntry("graph/Gr.fst")
            assertNotNull(entry)
            assertEquals(20_000L, entry.size)
        }
    }

    @Test
    fun `valida un modelo extraido completo`() {
        val dir = temp.newFolder()
        writeModelFile(dir, "conf/model.conf")
        writeModelFile(dir, "am/final.mdl")
        writeModelFile(dir, "graph/Gr.fst")

        assertTrue(ModelLoader.isValid(dir))
    }

    @Test
    fun `rechaza un modelo con fichero clave ausente`() {
        val dir = temp.newFolder()
        writeModelFile(dir, "conf/model.conf")
        writeModelFile(dir, "am/final.mdl")

        assertFalse(ModelLoader.isValid(dir))
    }

    @Test
    fun `rechaza un modelo con fichero clave vacio`() {
        val dir = temp.newFolder()
        writeModelFile(dir, "conf/model.conf", size = 0)
        writeModelFile(dir, "am/final.mdl")
        writeModelFile(dir, "graph/Gr.fst")

        assertFalse(ModelLoader.isValid(dir))
    }

    private fun buildZip(entryNames: List<String>, payloadSize: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            entryNames.forEachIndexed { index, name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(ByteArray(payloadSize) { index.toByte() })
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun writeModelFile(dir: File, relative: String, size: Int = 4_096) {
        val target = File(dir, relative)
        target.parentFile?.mkdirs()
        target.writeBytes(ByteArray(size))
    }
}