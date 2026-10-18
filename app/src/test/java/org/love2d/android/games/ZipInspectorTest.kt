package org.love2d.android.games

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipInspectorTest {

    @Test
    fun plainZipWithMainLua() {
        val file = tempFile(zip("main.lua", "assets/a.txt"))

        val inspection = ZipInspector.inspect(file)

        assertEquals(0L, inspection?.dataStart)
        assertTrue(inspection?.hasMainLua == true)
    }

    @Test
    fun zipWithoutMainLuaAtRoot() {
        val file = tempFile(zip("folder/main.lua", "assets/a.txt"))

        val inspection = ZipInspector.inspect(file)

        assertEquals(0L, inspection?.dataStart)
        assertFalse(inspection?.hasMainLua == true)
    }

    @Test
    fun polyglotExecutableWithAppendedZip() {
        val prefix = ByteArray(34_816).also {
            it[0] = 'M'.code.toByte()
            it[1] = 'Z'.code.toByte()
        }
        val file = tempFile(prefix + zip("main.lua"))

        val inspection = ZipInspector.inspect(file)

        assertEquals(prefix.size.toLong(), inspection?.dataStart)
        assertTrue(inspection?.hasMainLua == true)
    }

    @Test
    fun zipWithComment() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.setComment("love-game")
            zip.putNextEntry(ZipEntry("main.lua"))
            zip.write("print('hi')".toByteArray())
            zip.closeEntry()
        }
        val file = tempFile(output.toByteArray())

        val inspection = ZipInspector.inspect(file)

        assertEquals(0L, inspection?.dataStart)
        assertTrue(inspection?.hasMainLua == true)
    }

    @Test
    fun nonZipFileIsRejected() {
        val file = tempFile("not a zip archive".toByteArray())

        assertNull(ZipInspector.inspect(file))
    }

    private fun zip(vararg entries: String): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for (entry in entries) {
                zip.putNextEntry(ZipEntry(entry))
                zip.write("-- $entry".toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("zip-inspector-", ".tmp").apply {
            writeBytes(bytes)
            deleteOnExit()
        }
}
