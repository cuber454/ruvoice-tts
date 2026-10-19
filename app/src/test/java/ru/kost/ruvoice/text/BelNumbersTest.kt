// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.kost.ruvoice.TestData

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
        assertEquals("сёння дзве тысячы дваццаць шосты год, тры тамы", prep("Сёння 2026 год, 3 тамы"))
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

    /** Реальные символы пака, а не урезанный набор: тесты видят то же, что модель. */
    private val cis = TestData.cisAllowed
    private fun prep(t: String) = BelNormalizer.prepare(t, cis)

    @Test
    fun standaloneOnlyWholeWords() {
        // второй проход не трогает развёрнутое единицами и слова, которые начинаются с «руб», «кап»
        // точка сокращения в конце текста — конец предложения (abbrevDotEndsSentence)
        assertEquals("два рублі.", prep("2 руб."))
        assertEquals("капуста", prep("капуста"))
        assertEquals("беларускі рубель", prep("беларускі рубель"))
        assertEquals("пяць тысяч рублёў.", prep("5 тыс. руб."))
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
        assertEquals("тысяча дзевяцьсот дзевяноста дзявяты год.", prep("1999 г."))
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
        // «дд.мм» без единицы — по-прежнему дата; «г.» за ней — горад, не граммы
        assertEquals("сустрэча дванаццатага мая", prep("Сустрэча 12.05"))
        assertEquals("пятнаццатага чэрвеня горад гомель", prep("15.06 г. Гомель"))
        assertEquals("сустрэча пятнаццатага чэрвеня горад мінск", prep("Сустрэча 15.06 г. Мінск"))
        // не дроби: версии и адреса — числа по отдельности, как было
        assertEquals("адзін.дзесяць.два", prep("1.10.2"))
    }

    @Test
    fun gramsOrYear() {
        assertEquals("пяцьсот грамаў мукі", prep("500 г. мукі"))
        assertEquals("тысяча дзевяцьсот дзевяноста восьмы год.", prep("1998 г."))
        // с предлогом — год и в падеже предлога (не проверено носителем)
        assertEquals("у тысяча восемсот дванаццатым годзе.", prep("у 1812 г."))
        assertEquals("з тысяча дзевяцьсот дзевяностага года.", prep("з 1990 г."))
        // три цифры — год только после «у/ў»; «з», «па» — граммы
        assertEquals("у дзевяцьсот восемдзесят восьмым годзе.", prep("у 988 г."))
        assertEquals("з пяцьсот грамаў.", prep("з 500 г."))
        assertEquals("па пяцьсот грамаў мукі", prep("па 500 г. мукі"))
        assertEquals("па сто грамаў прадукту", prep("па 100 г. прадукту"))
        assertEquals("ад сто грамаў да дзвесце грамаў.", prep("ад 100 г. да 200 г."))
        assertEquals("тры тысячы грамаў.", prep("3000 г."))
    }

    @Test
    fun yearNeedsWholeNumber() {
        // «1 000 год» — не год «000», «12345 год» — не «1» + год «2345»
        assertEquals("адна тысяча год", prep("1 000 год"))
        assertEquals("дванаццаць тысяч трыста сорак пяць год", prep("12345 год"))
    }

    @Test
    fun dateWithG() {
        // «г.» после полной даты — уже «года», не «года горад» и не «годаг.»
        assertEquals("дванаццатага мая дзве тысячы дваццаць шостага года было", prep("12.05.2026 г. было"))
        assertEquals("дванаццатага мая дзве тысячы дваццаць шостага года.", prep("12.05.2026г."))
        assertEquals("нарадзіўся пятага сакавіка тысяча дзевяцьсот дзевяностага года.", prep("Нарадзіўся 5.03.1990 г."))
        // месяц словом: день и год порядковыми в родительном
        assertEquals("трэцяга мая дзве тысячы дваццаць шостага года.", prep("3 мая 2026 г."))
        assertEquals("трэцяга мая дзве тысячы дваццаць шостага года было", prep("3 мая 2026 года было"))
        assertEquals("з пятага мая", prep("з 5 мая"))
    }

    @Test
    fun phonesByGroups() {
        // группами как написано, с паузой; не миллионы
        assertEquals("восем, нуль дваццаць дзевяць, сто дваццаць тры, сорак пяць, шэсцьдзясят сем", prep("8 029 123-45-67"))
        assertEquals("тэл. восем, нуль сямнаццаць, дзвесце дваццаць два, трыццаць тры, сорак чатыры", prep("тэл. 8 017 222-33-44"))
        assertEquals("плюс сем, дзевяцьсот дзевяноста дзевяць, сто дваццаць тры, сорак пяць, шэсцьдзясят сем", prep("+7 999 123-45-67"))
        assertEquals("восем, нуль дваццаць дзевяць, сто дваццаць тры, сорак пяць, шэсцьдзясят сем", prep("8 (029) 123-45-67"))
        assertEquals("восем, нуль дваццаць дзевяць, сто дваццаць тры, сорак пяць, шэсцьдзясят сем", prep("8-029-123-45-67"))
        assertEquals("дзвесце дваццаць два, трыццаць тры, сорак чатыры", prep("222-33-44"))
        // диапазоны и дефисы — как были
        assertEquals("дзесяць тысяч-пятнаццаць тысяч", prep("10 000-15 000"))
        assertEquals("дзесяць-пятнаццаць хвілін", prep("10-15 хвілін"))
        assertEquals("тэл. восем-дваццаць дзевяць-сто дваццаць тры", prep("тэл. 8-029-123"))
    }

    @Test
    fun signBeforeNumber() {
        // «+» перед числом — «плюс», а не ударение на «адзін»; минус не теряется
        assertEquals("плюс адзін градус цэльсія", prep("+1 °C"))
        assertEquals("тэмпература плюс адзінаццаць", prep("тэмпература +11"))
        assertEquals("плюс адна тысяча", prep("+1 000"))
        assertEquals("мінус дваццаць градусаў цэльсія", prep("-20°С"))
        assertEquals("мінус пяць градусаў цэльсія", prep("−5 °C"))
        assertEquals("ад мінус пяць да плюс тры", prep("ад -5 да +3"))
        // дефис после буквы и цифры — не минус
        assertEquals("ту-сто пяцьдзясят чатыры", prep("Ту-154"))
        assertEquals("тысяча дзевяцьсот дзевяностыя", prep("1990-я"))
        assertEquals("д+якуй", prep("д+якуй"))
    }

    @Test
    fun ordinalSuffixes() {
        // форма по суффиксу; не проверено носителем
        assertEquals("першага верасня", prep("1-га верасня"))
        assertEquals("пяты клас", prep("5-ы клас"))
        assertEquals("другі", prep("2-гі"))
        assertEquals("трэці", prep("3-ці"))
        assertEquals("у пятым класе", prep("у 5-м класе"))
        assertEquals("у тысяча дзевяцьсот дзевяностыя гады.", prep("у 1990-я гг."))
        // «-я» не у десятилетия — род не знаем, не трогаем
        assertEquals("пяць-я кніга", prep("5-я кніга"))
    }

    @Test
    fun romanCentury() {
        // падеж по существительному; средний род не проверен носителем
        assertEquals("дваццатае стагоддзе", prep("XX стагоддзе"))
        assertEquals("дваццатага стагоддзя", prep("XX стагоддзя"))
        assertEquals("у дваццатым стагоддзі", prep("у XX стагоддзі"))
        assertEquals("дваццаць другое стагоддзе", prep("XXII стагоддзе"))
        assertEquals("дзевятнаццатае стагоддзе", prep("ХІХ стагоддзе"))   // кириллицей
        assertEquals("чатырнаццатага века", prep("XIV века"))
        // союз «І» и прочая латиница — как были
        assertEquals("і веку не хапіла", prep("І веку не хапіла"))
        assertEquals("эф-бэ-і", prep("FBI"))
    }

    @Test
    fun nonAsciiDigits() {
        // арабо-индийские и полноширинные: на Android \d их ловит, digits() падал на индексе
        assertEquals("тры кіламетры", prep("٣ км"))
        assertEquals("дванаццаць кіламетраў", prep("１２ км"))
        assertEquals("адзін два", BelNormalizer.digits("١٢"))
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
        // ponytail: дроби через слэш не читаются словами, чинить с носителем; здесь закреплено как есть, а не как верно
        assertEquals("адзін два шклянкі", prep("1/2 шклянкі"))
        assertEquals("дванаццатага мая дзве тысячы дваццаць шостага года", BelNumbers.dates("12/05/2026"))
        assertEquals("першага студзеня тысяча дзевяцьсот дзевяноста дзявятага года", BelNumbers.dates("01.01.99"))
    }

    /** Путь сервиса: Splitter, потом слой. */
    private fun path(t: String) = Splitter.sentences(t).map { prep(it) }

    @Test
    fun abbrevDotEndsSentence() {
        // Splitter после «г.», «руб.», «л.», «гг.» не режет, разворот точку съедал — предложения склеивались
        assertEquals(listOf("падзея адбылася ў дзве тысячы дваццаць чацвёртым годзе. цяпер там музей."),
            path("Падзея адбылася ў 2024 г. Цяпер там музей."))
        assertEquals(listOf("кошт — сем коска пяць рубля. тэлефон ёсць."), path("Кошт — 7,5 руб. Тэлефон ёсць."))
        assertEquals(listOf("вада — пяць літраў. мука — сто грамаў. цукар — дзве штукі."), path("Вада — 5 л. Мука — 100 г. Цукар — 2 шт."))
        assertEquals(listOf("у адна тысяча дзевяцьсот сорак адзін—адна тысяча дзевяцьсот сорак пяць гады. горад адноўлены."),
            path("У 1941–1945 гг. Горад адноўлены."))
        assertEquals("кошт пяць тысяч рублёў. тэлефон ёсць.", prep("Кошт 5 тыс. руб. Тэлефон ёсць."))
        assertEquals("вага пяць кілаграмаў.", prep("Вага 5kg."))
        assertEquals("шэсцьдзясят кіламетраў у гадзіну. далей", prep("60 км/г. Далей"))
        // перед строчной — сокращение внутри фразы; «г.» за «дд.мм» — горад
        assertEquals("пяць грамаў солі", prep("5 г. солі"))
        assertEquals("сустрэча пятнаццатага чэрвеня горад мінск.", prep("Сустрэча 15.06 г. Мінск."))
    }

    @Test
    fun latinGluedToDigits() {
        // одна буква при цифрах — именем, через пробел, не единица: «5G» не «пяць грамаў»
        assertEquals("эл-тэ-е чатыры гэ", prep("LTE 4G"))
        assertEquals("пяць гэ", prep("5G"))
        assertEquals("тры дэ-друк", prep("3D-друк"))
        assertEquals("а чатыры", prep("A4"))
        assertEquals("эм-пэ тры", prep("MP3"))
        assertEquals("галаксі эс дваццаць чатыры", prep("Galaxy S24"))
        // слово латиницей при числе — как было: «5kg» — единица
        assertEquals("пяць кілаграмаў", prep("5kg"))
    }

    @Test
    fun wordAbbreviations() {
        // «вуліцы» после «на/па», «і іншае» — не проверено носителем
        assertEquals("яблыкі, грушы і гэтак далей.", prep("Яблыкі, грушы і г. д."))
        assertEquals("яблыкі і гэтак далей. потым", prep("Яблыкі і г.д. Потым"))
        assertEquals("гэта, гэта значыць, памылка.", prep("Гэта, г. зн., памылка."))
        assertEquals("грушы і іншае. потым", prep("Грушы і інш. Потым"))
        assertEquals("грушы, інш. потым", prep("Грушы, інш. Потым"))   // без «і» не разворачиваем — и точка одна
        assertEquals("напрыклад, мінск.", prep("Напр., Мінск."))
        assertEquals("музей на вуліцы леніна, пяць.", prep("Музей на вул. Леніна, 5."))
        assertEquals("вуліца якуба коласа.", prep("Вул. Якуба Коласа."))
        // «на вул.» Splitter не режет
        assertEquals(listOf("музей на вуліцы леніна, пяць адкрыўся."), path("Музей на вул. Леніна, 5 адкрыўся."))
    }
}
