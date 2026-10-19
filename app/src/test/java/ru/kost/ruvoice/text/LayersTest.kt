// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kost.ruvoice.TestData

/** Таблица языков: слой берётся по коду из pack.json, русский — умолчание. */
class LayersTest {

    @Test fun tableByCode() {
        assertSame(RuLayer, Layers.of("ru"))
        assertSame(BeLayer, Layers.of("be"))
        assertSame(BeLayer, Layers.of("BE"))          // регистр кода пака не важен
        assertSame(RuLayer, Layers.of(null))           // пак без lang — русский
        assertSame(RuLayer, Layers.of("uk"))           // язык, которого в таблице пока нет
        assertSame(RuLayer, Layers.RU)
        assertTrue(Layers.has("be")); assertTrue(!Layers.has("uk"))
    }

    @Test fun localesForTtsApi() {
        // ISO-3 страны — из Locale: у Беларуси BLR (BEL — Бельгия)
        assertEquals(listOf("rus", "RUS"), RuLayer.locale.let { listOf(it.isO3Language, it.isO3Country) })
        assertEquals(listOf("bel", "BLR"), BeLayer.locale.let { listOf(it.isO3Language, it.isO3Country) })
        assertSame(BeLayer, Layers.ofIso3("bel"))
        assertSame(RuLayer, Layers.ofIso3("rus"))
        assertEquals(null, Layers.ofIso3("ukr"))
        assertEquals(null, Layers.ofIso3(null))
    }

    @Test fun belPrepare() {
        // числа словами, латиница транслитом, фильтр по символам модели
        assertEquals("дзве тысячы дваццаць шэсць", BeLayer.prepare("2026", "абвгдеёжзійклмнопрстуўфхцчшыьэюя+,. ", Rules()))
        // «!» в модель не идёт (служебный символ), апострофа в таблице нет
        assertEquals("прывет.", BeLayer.prepare("Прыв'ет!", "абвгдеёжзійклмнопрстуўфхцчшыьэюя+,. ", Rules()))
    }

    @Test fun belQuestions() {
        val d = TestData.data()
        val lists = BeLayer.questions(d)
        assertTrue("хто" in lists.whForms)
        assertEquals("wh_q", SentenceType.classify("Дзе ты быў?", lists))
        assertEquals("general_q", SentenceType.classify("Ты прыйдзеш заўтра?", lists))
        assertEquals("exclam", SentenceType.classify("Паспеем да цягніка!", lists))
        assertEquals("st", SentenceType.classify("Сёння 2026 год.", lists))
        // правило выключено — тип не считается (так же, как у русского)
        assertEquals("st", SentenceType.classify("Дзе ты быў?", lists, Rules(setOf("intonation"))))
    }

    @Test fun ruQuestionsAreItsOwn() {
        val d = TestData.data()
        assertEquals("wh_q", SentenceType.classify("Где ты был?", RuLayer.questions(d)))
        assertTrue("где" in RuLayer.questions(d).whForms)
        assertTrue("где" !in BeLayer.questions(d).whForms)
    }
}
