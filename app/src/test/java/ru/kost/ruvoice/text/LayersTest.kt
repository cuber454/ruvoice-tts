// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.kost.ruvoice.Pipeline
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

    private val cis = "абвгдеёжзійклмнопрстуўфхцчшыьэюя+-,.:;?—… "

    @Test fun belAccentMarksAndInvisibleChars() {
        // знак ударения над гласной («Книга с ударениями» пишет U+0301) — «+» перед ней, а не пробел посреди слова
        assertEquals("дз+якуй", BeLayer.prepare("дзя́куй", cis, Rules()))
        assertEquals("дз+якуй", BeLayer.prepare("дзя̀куй", cis, Rules()))
        // мягкий перенос, ZWSP/ZWJ/ZWNJ/WJ/BOM — без следа; знак над согласной — тоже
        assertEquals("перанос слова", BeLayer.prepare("пера­нос сло​ва‌‍⁠﻿", cis, Rules()))
        assertEquals("дзякуй", BeLayer.prepare("дз́якуй", cis, Rules()))
    }

    @Test fun belDashReachesModel() {
        // Splitter делает из « - » «–», в символах cis только «—»: на нём пауза тире (dashPauseMs)
        val d = TestData.data()
        val seg = Pipeline.plan("Мінск - сталіца", d, 0, 0, layer = BeLayer, allowed = cis).single().text
        assertEquals("мінск — сталіца", BeLayer.prepare(seg, cis, Rules()))
        assertEquals("мінск — сталіца", BeLayer.prepare("Мінск — сталіца", cis, Rules()))
        assertEquals("хто-небудзь", BeLayer.prepare("хто-небудзь", cis, Rules()))
        val pack = org.json.JSONObject(java.io.File(TestData.root(), "app/src/test/resources/golden_pack.json").readText()).getJSONObject("pack")
        assertTrue('—' in ru.kost.ruvoice.Symbols.fromJson(pack).symbolToId)
    }

    @Test fun belQuotesWithoutStrayCommas() {
        assertEquals("так, — сказаў ён.", BeLayer.prepare("\"Так\", — сказаў ён.", cis, Rules()))
        assertEquals("так. — сказаў ён.", BeLayer.prepare("«Так!» — сказаў ён.", cis, Rules()))
        assertEquals("ён сказаў: так.", BeLayer.prepare("Ён сказаў: «Так».", cis, Rules()))
        assertEquals("кніга, том два, тут", BeLayer.prepare("Кніга (том 2) тут", cis, Rules()))
    }

    @Test fun belQuestionWords() {
        val q = BeLayer.questions(TestData.data())
        // «і», «ў» внутри слова: «калі», «колькі», «чыі»
        assertEquals("wh_q", SentenceType.classify("Калі ты вернешся?", q))
        assertEquals("wh_q", SentenceType.classify("Колькі гэта каштуе?", q))
        assertEquals("wh_q", SentenceType.classify("Чыі гэта рэчы?", q))
        // «ё» нормализуется в «е» — и в списках тоже
        assertEquals("wh_q", SentenceType.classify("Чыё гэта паліто?", q))
        assertEquals("tag_q", SentenceType.classify("Ты прыйдзеш, ня праўда?", q))
        assertEquals("tag_q", SentenceType.classify("Добра атрымалася, ці не так?", q))
        // союз альтернативы — белорусский; «ці» в начале — частица общего вопроса
        assertEquals("alternative_q", SentenceType.classify("Вам чай ці каву?", q))
        assertEquals("alternative_q", SentenceType.classify("Пойдзем пешшу або паедзем?", q))
        assertEquals("general_q", SentenceType.classify("Ці прыйдзеш ты заўтра?", q))
        assertEquals("general_q", SentenceType.classify("Вам чай или каву?", q))
        // русский союз остаётся у русского
        assertEquals("alternative_q", SentenceType.classify("Чай или кофе?", RuLayer.questions(TestData.data())))
    }

    @Test fun belLetterNames() {
        val d = TestData.data()
        fun first(t: String) = Pipeline.plan(t, d, 0, 0, layer = BeLayer, allowed = cis).single().text
        assertEquals("+эль", first("л"))
        assertEquals("+у нескладовае", first("Ў"))
        assertEquals("+і нескладовае", first("й"))
        assertEquals("м+яккі знак", first("ь"))
        assertEquals("б+э", first("b"))
        assertEquals("прописная буква +эр", first("прописная буква Р."))
        // эхо ввода — тоже белорусским именем
        assertEquals("удаление, +эль, заглавная", Pipeline.plan("Удаление заглавная Л", d, 0, 0, rules = Rules().with("letter_echo_all", true), layer = BeLayer).single().text)
        // русский голос — как был
        assertEquals("+эл.", Pipeline.plan("л", d, 0, 0).single().text)
        assertEquals("удаление, +эл, заглавная", Pipeline.plan("Удаление заглавная Л", d, 0, 0, rules = Rules().with("letter_echo_all", true)).single().text)
    }

    @Test fun ruQuestionsAreItsOwn() {
        val d = TestData.data()
        assertEquals("wh_q", SentenceType.classify("Где ты был?", RuLayer.questions(d)))
        assertTrue("где" in RuLayer.questions(d).whForms)
        assertTrue("где" !in BeLayer.questions(d).whForms)
    }
}
