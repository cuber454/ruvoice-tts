// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Test

/** Даты, время, единицы и сокращения по-белорусски. */
class BelNumbersTest {

    @Test
    fun datesFull() {
        assertEquals("дванаццатага мая дзве тысячы дваццаць шостага года",
            BelNumbers.dates("12.05.2026"))
    }

    @Test
    fun datesWithoutYear() {
        assertEquals("трэцяга верасня", BelNumbers.dates("3.09"))
    }

    @Test
    fun datesTwoDigitYear() {
        assertEquals("першага студзеня дзве тысячы дваццатага года",
            BelNumbers.dates("01.01.20"))
    }

    @Test
    fun timesFull() {
        assertEquals("дваццаць адна гадзіна трыццаць хвілін", BelNumbers.times("21:30"))
    }

    @Test
    fun timesRoundHour() {
        assertEquals("дзевяць гадзін роўна", BelNumbers.times("9:00"))
    }

    @Test
    fun unitsBase() {
        assertEquals("тры кіламетры", BelNumbers.units("3 км"))
        assertEquals("адзін кіламетр", BelNumbers.units("1 км"))
        assertEquals("пяць кіламетраў", BelNumbers.units("5 км"))
    }

    @Test
    fun unitsPercentAndMoney() {
        assertEquals("пяць працэнтаў", BelNumbers.units("5 %"))
        assertEquals("два рублі", BelNumbers.units("2 руб."))
        assertEquals("трыста долараў", BelNumbers.units("300 $"))
    }

    @Test
    fun thousandsWithSeparator() {
        assertEquals("дзве тысячы кіламетраў", BelNumbers.units("2 000 км"))
    }

    @Test
    fun yearAbbreviation() {
        assertEquals("1999 год", BelNumbers.abbreviations("1999 г."))
    }

    @Test
    fun standaloneMoney() {
        assertEquals("пяць тысяч рублёў", BelNumbers.standalone(BelNumbers.units("5 тыс. руб.")))
    }

    @Test
    fun unitOnlyAsSeparateToken() {
        // «г» и «т» — это граммы и тоны, но только отдельным знаком за числом: иначе «2026 год» становился
        // «2026 г(раммаў) + од», а «3 тамы» — «3 т(оны) + амы» (нашлось на живом паке 19.10.2026)
        assertEquals("2026 год", BelNumbers.units("2026 год"))
        assertEquals("3 тамы", BelNumbers.units("3 тамы"))
        assertEquals("пяць грамаў", BelNumbers.units("5 г."))
        assertEquals("дзве тоны", BelNumbers.units("2 т"))
        assertEquals("дзве штукі", BelNumbers.units("2 шт."))
        assertEquals("дзве капейкі", BelNumbers.units("2 кап."))
        assertEquals("дзве тысячы", BelNumbers.units("2 тыс."))
        assertEquals("на годзе жыцця", BelNumbers.units("на годзе жыцця"))
    }

    @Test
    fun yearReadsAsOrdinal() {
        // год — порядковым: «дзве тысячы дваццаць шосты год», а не «…шосты грамаўод»
        assertEquals("сёння дзве тысячы дваццаць шосты год, тры тамы", BelNormalizer.prepare("Сёння 2026 год, 3 тамы", "абвгдеёжзійклмнопрстуўфхцчшыьэюя,."))
    }

    @Test
    fun ordinalOnlyLastWord() {
        assertEquals("дзве тысячы дваццаць шосты", BelNormalizer.ordinal(2026))
        assertEquals("сто дваццаць шосты", BelNormalizer.ordinal(126))
        assertEquals("дваццаты", BelNormalizer.ordinal(20))
    }

    @Test
    fun pluralRules() {
        assertEquals("адзін", BelNormalizer.cardinal(1))
        assertEquals("два", BelNormalizer.cardinal(2))
        assertEquals("адзінаццаць", BelNormalizer.cardinal(11))
    }

    private val cis = "абвгдеёжзійклмнопрстуўфхцчшыьэюя+-,.:;?—… "
    private fun prep(t: String) = BelNormalizer.prepare(t, cis)

    @Test
    fun standaloneOnlyWholeWords() {
        // второй проход не трогает развёрнутое единицами и слова, которые начинаются с «руб», «кап»
        assertEquals("два рублі", prep("2 руб."))
        assertEquals("капуста", prep("капуста"))
        assertEquals("беларускі рубель", prep("беларускі рубель"))
        assertEquals("пяць тысяч рублёў", prep("5 тыс. руб."))
        assertEquals("цана ў рублёў", prep("цана ў руб."))
    }

    @Test
    fun bigNumbersAndDigitGroups() {
        // с миллиарда — по цифрам, без падения запроса
        assertEquals("восем нуль два дзевяць адзін два тры чатыры пяць шэсць сем", prep("80291234567"))
        assertEquals("адзін пяць нуль нуль нуль нуль нуль нуль нуль нуль", BelNormalizer.cardinal(1_500_000_000))
        // через пробел склеиваются только группы по три
        assertEquals("плюс трыста семдзесят пяць дваццаць дзевяць сто дваццаць тры сорак пяць шэсцьдзясят сем", prep("+375 29 123 45 67"))
        assertEquals("адзін два тры", prep("1 2 3"))
        assertEquals("чатыры тысячы дзвесце семдзесят шэсць адна тысяча дзвесце трыццаць чатыры", prep("4276 1234"))
        assertEquals("адзін мільён", prep("1 000 000"))
        assertEquals("тры мільёны дзвесце тысяч", prep("3 200 000"))
        assertEquals("пяць мільёнаў", BelNormalizer.cardinal(5_000_000))
    }

    @Test
    fun ordinalRoundThousandsAndHundreds() {
        assertEquals("тысячны", BelNormalizer.ordinal(1000))
        assertEquals("двухтысячны", BelNormalizer.ordinal(2000))
        assertEquals("двухтысячнага", BelNormalizer.ordinal(2000, BelNormalizer.Form.GEN))
        assertEquals("тысяча дзевяцісоты", BelNormalizer.ordinal(1900))
        assertEquals("дзве тысячы соты", BelNormalizer.ordinal(2100))
        assertEquals("тысяча дзевяцьсот дзевяноста восьмы", BelNormalizer.ordinal(1998))
        assertEquals("тысяча дзевяцьсот дзевяноста восьмым", BelNormalizer.ordinal(1998, BelNormalizer.Form.LOC))
        assertEquals("у двухтысячным годзе", prep("у 2000 годзе"))
        assertEquals("тысяча дзевяцьсот дзевяноста восьмага года", prep("1998 года"))
    }

    @Test
    fun yearsPluralIsCardinal() {
        // «гадоў», «гадах» — после количественного
        assertEquals("сто гадоў", prep("100 гадоў"))
        assertEquals("у трохсотым годзе", prep("у 300 годзе"))
    }

    @Test
    fun gramsAreNotYear() {
        assertEquals("пяць грамаў солі", prep("5 г. солі"))
        assertEquals("тысяча дзевяцьсот дзевяноста дзявяты год", prep("1999 г."))
    }

    @Test
    fun decimals() {
        // «коска» и родительный единицы после дроби — не проверено носителем
        assertEquals("адзін коска пяць кілаграма", prep("1.5 кг"))
        assertEquals("адзін коска пяць кілаграма", prep("1,5 кг"))
        // число с единицей — не дата «першага мая»; нуль в дробной части читается
        assertEquals("1.05 кг", BelNumbers.dates("1.05 кг"))
        assertEquals("адзін коска нуль пяць кілаграма", prep("1.05 кг"))
        assertEquals("два коска семдзесят пяць працэнта", prep("2,75 %"))
        assertEquals("тры коска чатырнаццаць", prep("3,14"))
        assertEquals("адна тысяча коска пяць", prep("1 000,5"))
        // «дд.мм» без единицы — по-прежнему дата
        assertEquals("сустрэча дванаццатага мая", prep("Сустрэча 12.05"))
        // не дроби: версии и адреса — числа по отдельности, как было
        assertEquals("адзін.дзесяць.два", prep("1.10.2"))
    }

    @Test
    fun gramsOrYear() {
        assertEquals("пяцьсот грамаў мукі", prep("500 г. мукі"))
        assertEquals("тысяча дзевяцьсот дзевяноста восьмы год", prep("1998 г."))
        // с предлогом — год и в падеже предлога (не проверено носителем)
        assertEquals("у тысяча восемсот дванаццатым годзе", prep("у 1812 г."))
        assertEquals("з дзевяцьсот восемдзесят восьмага года", prep("з 988 г."))
        assertEquals("тры тысячы грамаў", prep("3000 г."))
    }

    @Test
    fun celsiusLowercase() {
        assertEquals("дваццаць градусаў цэльсія", prep("20 °C"))      // латинская «C»
        assertEquals("пяць градусаў цэльсія", prep("5°С"))           // кириллическая «С»
    }

    @Test
    fun fractionsAreNotDates() {
        assertEquals("1.5 кг", BelNumbers.dates("1.5 кг"))
        assertEquals("1/2 шклянкі", BelNumbers.dates("1/2 шклянкі"))
        assertEquals("24/7", BelNumbers.dates("24/7"))
        assertEquals("1.10.2", BelNumbers.dates("1.10.2"))
        // дробь словами не читаем: числа по отдельности
        assertEquals("адзін два шклянкі", prep("1/2 шклянкі"))
        assertEquals("дванаццатага мая дзве тысячы дваццаць шостага года", BelNumbers.dates("12/05/2026"))
        assertEquals("першага студзеня тысяча дзевяцьсот дзевяноста дзявятага года", BelNumbers.dates("01.01.99"))
    }
}
