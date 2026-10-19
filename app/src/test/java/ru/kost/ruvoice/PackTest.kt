package ru.kost.ruvoice

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PackTest {
    private val packJson = JSONObject(File(TestData.root(), "app/src/test/resources/golden_pack.json").readText()).getJSONObject("pack")
    private val filesDir = Files.createTempDirectory("ruvoice").toFile()

    private fun zip(json: String?, vararg models: String, extras: Map<String, ByteArray> = emptyMap()): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { z ->
            if (json != null) { z.putNextEntry(ZipEntry("pack.json")); z.write(json.toByteArray()); z.closeEntry() }
            for (m in models) { z.putNextEntry(ZipEntry(m)); z.write(byteArrayOf(1)); z.closeEntry() }
            for ((n, b) in extras) { z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
        }
        return bytes.toByteArray()
    }
    private val full get() = zip(packJson.toString(), "tts_mel.ptl", "backbone.pte", "head.ptl")

    @Test fun parsesPackJson() {
        val p = Pack(packJson.toString(), File("."))
        assertEquals("cis_ru", p.id); assertEquals("MIT", p.license); assertFalse(p.types)
        assertEquals(29, p.speakers.size); assertTrue("ru_alexandr" in p.speakers)
        assertFalse('!' in p.sym.allowed)
    }

    @Test fun rejectsOtherFormat() {
        val e = assertThrows(IllegalArgumentException::class.java) { Pack(packJson.put("format", 1).toString(), File(".")) }
        assertTrue(e.message!!, e.message!!.contains("другой версии"))
    }

    @Test fun langDefaultsToRuAndComesFromJson() {
        // пак без поля lang читается русским — старые паки не ломаются
        assertEquals("ru", Pack(packJson.toString(), File(".")).lang)
        assertEquals("be", Pack(JSONObject(packJson.toString()).put("lang", "be").toString(), File(".")).lang)
        // код языка в нижнем регистре: pack.json пишут люди и скрипты
        assertEquals("be", Pack(JSONObject(packJson.toString()).put("lang", "BE").toString(), File(".")).lang)
    }

    @Test fun accentorAndCorrectionsAreOptional() {
        val dir = Files.createTempDirectory("packlang").toFile()
        // пак без файлов языка: ни акцентора, ни поправок
        val plain = Pack(packJson.toString(), dir)
        assertFalse(plain.accentor)
        assertTrue(plain.corrections.isEmpty())
        assertEquals(0L, plain.size)   // размер считают только файлы модели
        // файлы языка появились — пак отдаёт акцентор и поправки ударений
        File(dir, Packs.ACCENTOR).writeText("x")
        File(dir, Packs.STRESS).writeText("дзякуй дзя+куй\n\n# комментарий\nсталы +сталы\nодно\n")
        val p = Pack(packJson.toString(), dir)
        assertTrue(p.accentor)
        assertEquals(mapOf("дзякуй" to "дзя+куй", "сталы" to "+сталы"), p.corrections)
    }

    @Test fun installCopiesLanguageFiles() {
        val extras = mapOf(Packs.ACCENTOR to byteArrayOf(1, 2), Packs.STRESS to "а +а".toByteArray())
        val p = Packs.install(ByteArrayInputStream(zip(packJson.toString(), "tts_mel.ptl", "backbone.pte", "head.ptl", extras = extras)), filesDir)
        assertTrue(File(p.dir, Packs.ACCENTOR).isFile)
        assertEquals(mapOf("а" to "+а"), p.corrections)
        Packs.delete(filesDir, p.id)
    }

    @Test fun installsListsDeletes() {
        File(filesDir, "packs/.tmp-old").mkdirs(); File(filesDir, "packs/.tmp-old/x").writeText("x")
        val p = Packs.install(ByteArrayInputStream(full), filesDir)
        assertEquals("cis_ru", p.id)
        assertFalse(File(filesDir, "packs/.tmp-old").exists()) // хвост убитого процесса убран
        assertEquals(listOf("cis_ru"), Packs.installed(filesDir).map { it.id })
        assertTrue(File(filesDir, "packs/cis_ru/head.ptl").isFile)
        assertEquals(3L, Packs.installed(filesDir).single().size)
        assertNotNull(Packs.find(filesDir, "cis_ru")); assertNull(Packs.find(filesDir, "nope"))
        Packs.delete(filesDir, "cis_ru")
        assertTrue(Packs.installed(filesDir).isEmpty()); assertFalse(File(filesDir, "packs/cis_ru").exists())
    }

    @Test fun rejectsZipWithoutModelFile() {
        val e = assertThrows(IllegalArgumentException::class.java) { Packs.install(ByteArrayInputStream(zip(packJson.toString(), "tts_mel.ptl", "backbone.pte")), filesDir) }
        assertEquals(Packs.NOT_A_PACK, e.message)
        assertTrue((File(filesDir, "packs").listFiles() ?: emptyArray()).none { it.name.startsWith(".tmp") })
        assertTrue(Packs.installed(filesDir).isEmpty())
    }

    @Test fun deleteRejectsBadId() {
        assertThrows(IllegalArgumentException::class.java) { Packs.delete(filesDir, "..") }
    }

    @Test fun rejectsZipWithoutJsonAndBrokenJson() {
        assertEquals(Packs.NOT_A_PACK, assertThrows(IllegalArgumentException::class.java) { Packs.install(ByteArrayInputStream(zip(null, "tts_mel.ptl", "backbone.pte", "head.ptl")), filesDir) }.message)
        assertEquals(Packs.NOT_A_PACK, assertThrows(IllegalArgumentException::class.java) { Packs.install(ByteArrayInputStream(zip("{oops", "tts_mel.ptl", "backbone.pte", "head.ptl")), filesDir) }.message)
    }

    @Test fun rejectsTruncatedZip() {
        val bytes = full
        val truncated = bytes.copyOfRange(0, bytes.size * 6 / 10)
        val e = assertThrows(IllegalArgumentException::class.java) { Packs.install(ByteArrayInputStream(truncated), filesDir) }
        assertEquals(Packs.NOT_A_PACK, e.message)
        assertTrue((File(filesDir, "packs").listFiles() ?: emptyArray()).none { it.name.startsWith(".tmp") })
    }

    @Test fun reinstallReplaces() {
        Packs.install(ByteArrayInputStream(full), filesDir)
        File(filesDir, "packs/cis_ru/stray.txt").writeText("x")
        Packs.install(ByteArrayInputStream(full), filesDir)
        assertFalse(File(filesDir, "packs/cis_ru/stray.txt").exists())
        assertEquals(1, Packs.installed(filesDir).size)
    }
}
