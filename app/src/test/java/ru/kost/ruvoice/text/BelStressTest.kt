// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.kost.ruvoice.TestData

/** Язык текста ударений: белорусский конвейер включается слоем пака, русский остаётся по умолчанию. */
class BelStressTest {
    private val d = TestData.data()

    /** Акцентор-заглушка: одно слово — вероятности по его гласным (так отдаёт accentor.ptl). */
    private class Fake(private val probs: Array<FloatArray>) : StressModels {
        var asked: List<String> = emptyList()
        override fun accentor(words: List<String>): Pair<Array<FloatArray>, Array<FloatArray>> {
            asked = words
            return Pair(probs, emptyArray())
        }
        override fun homo(ids: List<LongArray>, starts: LongArray, ends: LongArray): FloatArray = FloatArray(0)
    }

    @Test fun accentorChoosesVowel() {
        // «дзякуй»: гласные «я» и «у» — акцентор выбирает вторую
        val stress = Stress(d, Fake(arrayOf(floatArrayOf(0.1f, 0.9f))), emptyMap(), Rules(), layer = BeLayer)
        assertEquals("дзяк+уй", stress.apply("дзякуй"))
    }

    @Test fun shortAndServiceWordsNotMarked() {
        val m = Fake(arrayOf(floatArrayOf(0.9f, 0.1f)))
        val stress = Stress(d, m, emptyMap(), Rules(), layer = BeLayer)
        // «з» — односложное, «намі» — два слога: акцентор спрашиваем только про второе
        assertEquals("з н+амі", stress.apply("з намі"))
        assertEquals(listOf("намі"), m.asked)
    }

    @Test fun userDictWinsOverAccentor() {
        val stress = Stress(d, Fake(arrayOf(floatArrayOf(0.1f, 0.9f))), mapOf("дзякуй" to "дз+якуй"), Rules(), layer = BeLayer)
        assertEquals("дз+якуй", stress.apply("дзякуй"))
    }

    @Test fun packCorrectionsApplied() {
        // поправка пака (stress.txt) — до акцентора
        val stress = Stress(d, Fake(arrayOf(floatArrayOf(0.1f, 0.9f))), emptyMap(), Rules(), layer = BeLayer,
            corrections = mapOf("дзякуй" to "дзя+куй"))
        assertEquals("дзя+куй", stress.apply("дзякуй"))
    }

    @Test fun userDictWinsOverPackCorrections() {
        val stress = Stress(d, Fake(arrayOf(floatArrayOf(0.1f, 0.9f))), mapOf("дзякуй" to "дз+якуй"), Rules(),
            layer = BeLayer, corrections = mapOf("дзякуй" to "дзя+куй"))
        assertEquals("дз+якуй", stress.apply("дзякуй"))
    }
}
