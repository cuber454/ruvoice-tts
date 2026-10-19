// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

/**
 * Белорусская подготовка текста перед моделью.
 *
 * Зачем свой, а не [Normalizer]: русский нормализатор раскрывает русские сокращения, склоняет
 * по таблицам AOT и переводит латиницу в русские буквы — для белорусского это порча: «и», «щ», «ъ»
 * в таблице cis есть, и модель прочла бы русские формы. Здесь только то, без чего модель не читает:
 *  - числа словами (в таблице символов модели цифр нет — они выпадали из речи);
 *  - «!» → «.» (восклицательный знак стоит в первых трёх служебных символах, Silero их вырезает,
 *    а токен «!» при обучении не использовался; интонацию восклицания делаем питчем отдельно);
 *  - апостроф убираем (белорусское «аб'ява»: в символах cis-пака он служебный и до модели не доходит);
 *  - скобки → запятая по правилу pause_parens, кавычки — без паузы (как в русском RuVoice);
 *  - всё, чего в таблице символов нет, выкидываем.
 */
object BelNormalizer {

    private val VOWELS = "аеёіоуыэюя"

    private val UNITS = arrayOf(
        "нуль", "адзін", "два", "тры", "чатыры", "пяць", "шэсць", "сем", "восем", "дзевяць",
        "дзесяць", "адзінаццаць", "дванаццаць", "трынаццаць", "чатырнаццаць", "пятнаццаць",
        "шаснаццаць", "сямнаццаць", "васямнаццаць", "дзевятнаццаць")
    private val TENS = arrayOf(
        "", "", "дваццаць", "трыццаць", "сорак", "пяцьдзясят", "шэсцьдзясят", "семдзесят",
        "восемдзесят", "дзевяноста")
    private val HUNDREDS = arrayOf(
        "", "сто", "дзвесце", "трыста", "чатырыста", "пяцьсот", "шэсцьсот", "семсот",
        "восемсот", "дзевяцьсот")
    /** Тысячи женского рода: «адна тысяча», «дзве тысячы». */
    private val THOUSAND_F = arrayOf(
        "нуль", "адна", "дзве", "тры", "чатыры", "пяць", "шэсць", "сем", "восем", "дзевяць",
        "дзесяць", "адзінаццаць", "дванаццаць", "трынаццаць", "чатырнаццаць", "пятнаццаць",
        "шаснаццаць", "сямнаццаць", "васямнаццаць", "дзевятнаццаць")

    /** Порядковые (мужской род): именительный, родительный, предложный. */
    private val ORD_NOM = arrayOf(
        "нулявы", "першы", "другі", "трэці", "чацвёрты", "пяты", "шосты", "сёмы", "восьмы",
        "дзявяты", "дзясяты", "адзінаццаты", "дванаццаты", "трынаццаты", "чатырнаццаты",
        "пятнаццаты", "шаснаццаты", "сямнаццаты", "васямнаццаты", "дзевятнаццаты")
    private val ORD_GEN = arrayOf(
        "нулявога", "першага", "другога", "трэцяга", "чацвёртага", "пятага", "шостага", "сёмага",
        "восьмага", "дзявятага", "дзясятага", "адзінаццатага", "дванаццатага", "трынаццатага",
        "чатырнаццатага", "пятнаццатага", "шаснаццатага", "сямнаццатага", "васямнаццатага",
        "дзевятнаццатага")
    private val ORD_LOC = arrayOf(
        "нулявым", "першым", "другім", "трэцім", "чацвёртым", "пятым", "шостым", "сёмым",
        "восьмым", "дзявятым", "дзясятым", "адзінаццатым", "дванаццатым", "трынаццатым",
        "чатырнаццатым", "пятнаццатым", "шаснаццатым", "сямнаццатым", "васямнаццатым",
        "дзевятнаццатым")

    private val TENS_ORD = mapOf(
        20 to Triple("дваццаты", "дваццатага", "дваццатым"),
        30 to Triple("трыццаты", "трыццатага", "трыццатым"),
        40 to Triple("саракавы", "саракавога", "саракавым"),
        50 to Triple("пяцідзясяты", "пяцідзясятага", "пяцідзясятым"),
        60 to Triple("шасцідзясяты", "шасцідзясятага", "шасцідзясятым"),
        70 to Triple("сямідзясяты", "сямідзясятага", "сямідзясятым"),
        80 to Triple("васьмідзясяты", "васьмідзясятага", "васьмідзясятым"),
        90 to Triple("дзевяносты", "дзевяностага", "дзевяностым"))

    private val HUNDREDS_ORD = mapOf(
        100 to Triple("соты", "сотага", "сотым"),
        200 to Triple("двухсоты", "двухсотага", "двухсотым"),
        300 to Triple("трохсоты", "трохсотага", "трохсотым"),
        400 to Triple("чатырохсоты", "чатырохсотага", "чатырохсотым"),
        500 to Triple("пяцісоты", "пяцісотага", "пяцісотым"),
        600 to Triple("шасцісоты", "шасцісотага", "шасцісотым"),
        700 to Triple("сямісоты", "сямісотага", "сямісотым"),
        800 to Triple("васьмісоты", "васьмісотага", "васьмісотым"),
        900 to Triple("дзевяцісоты", "дзевяцісотага", "дзевяцісотым"))

    /** Падеж последнего слова у порядкового числительного в году. */
    enum class Form { NOM, GEN, LOC }

    /** 0..999 в мужском роде. */
    private fun under1000(n: Int, fem: Boolean = false): String {
        val sb = StringBuilder()
        val h = n / 100
        if (h > 0) sb.append(HUNDREDS[h])
        val r = n % 100
        if (r > 0) {
            if (sb.isNotEmpty()) sb.append(' ')
            if (r < 20) sb.append(if (fem) THOUSAND_F[r] else UNITS[r])
            else {
                sb.append(TENS[r / 10])
                val u = r % 10
                if (u > 0) { sb.append(' '); sb.append(if (fem) THOUSAND_F[u] else UNITS[u]) }
            }
        }
        return sb.toString()
    }

    /** Количественное: 1234 → «адна тысяча дзвесце трыццаць чатыры». */
    fun cardinal(n: Long): String = count(n, fem = false)

    /** То же для единиц женского рода («тона», «капейка», «штука», «тысяча»): 2 → «дзве», а не «два». */
    fun cardinalFem(n: Long): String = count(n, fem = true)

    /** Слово «тысяча» по числу: 1 → тысяча, 2–4 → тысячы, 5+ → тысяч. */
    private fun thousandWord(n: Int) = when {
        n % 10 == 1 && n % 100 != 11 -> "тысяча"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "тысячы"
        else -> "тысяч"
    }

    /** Число по цифрам: «80291234567» → «восем нуль два…». */
    fun digits(raw: String) = raw.map { UNITS[it.digitToInt()] }.joinToString(" ")

    /** Группа цифр как написана: нули в начале — словом каждый («029» — «нуль дваццаць дзевяць», «05» в дроби). */
    fun group(raw: String): String {
        val rest = raw.trimStart('0')
        val tail = rest.takeIf { it.isNotEmpty() }?.let { r -> r.toLongOrNull()?.let { cardinal(it) } ?: digits(r) }
        return (List(raw.length - rest.length) { UNITS[0] } + listOfNotNull(tail)).joinToString(" ")
    }

    /** С миллиарда — по цифрам: разрядов дальше нет (телефон, номер карты), а under1000 на них падал. */
    private const val BIG = 1_000_000_000L

    private fun count(n: Long, fem: Boolean): String {
        if (n < 0) return "мінус " + count(-n, fem)
        if (n == 0L) return UNITS[0]
        if (n >= BIG) return digits(n.toString())
        val parts = ArrayList<String>()
        val millions = (n / 1_000_000).toInt()
        val thousands = ((n / 1000) % 1000).toInt()
        val rest = (n % 1000).toInt()
        if (millions > 0) parts.add(under1000(millions) + " " + BelNumbers.plural(millions.toLong(), "мільён", "мільёны", "мільёнаў"))
        if (thousands > 0) parts.add(under1000(thousands, fem = true) + " " + thousandWord(thousands))
        if (rest > 0) parts.add(under1000(rest, fem = fem))
        return parts.joinToString(" ")
    }

    /** Порядковое по последнему компоненту: 2026 → «дзве тысячы дваццаць шосты», 1998 → «тысяча дзевяцьсот
     * дзевяноста восьмы» (в годах без «адна»), 2000 → «двухтысячны». */
    fun ordinal(n: Long, form: Form = Form.NOM): String {
        if (n == 0L) return pick(ORD_NOM[0], ORD_GEN[0], ORD_LOC[0], form)
        // порядковые от миллиона в тексте не встречаются (годы — до четырёх цифр): количественным, без падения
        if (n >= 1_000_000) return cardinal(n)
        val head = StringBuilder()
        fun add(s: String) { if (s.isNotEmpty()) { if (head.isNotEmpty()) head.append(' '); head.append(s) } }
        val thousands = (n / 1000).toInt()
        val rest = (n % 1000).toInt()
        if (rest == 0) {
            // круглые тысячи — одним словом: 1000 → «тысячны», 2000 → «двухтысячны» (основа как у «двухсоты»)
            // ponytail: от 10 тысяч основа не выводится, оставлено «дзесяць тысячны»; форма не проверена носителем
            val stem = when (thousands) {
                1 -> ""
                in 2..9 -> HUNDREDS_ORD.getValue(thousands * 100).first.removeSuffix("соты")
                else -> under1000(thousands, fem = true) + " "
            }
            return stem + pick("тысячны", "тысячнага", "тысячным", form)
        }
        if (thousands == 1) add("тысяча")
        else if (thousands > 0) add(under1000(thousands, fem = true) + " " + thousandWord(thousands))
        val h = rest / 100
        val r = rest % 100
        // круглые сотни порядковым целиком: 1900 → «тысяча дзевяцісоты», а не «…дзевяцьсот дзевяцісоты»
        if (h > 0 && r > 0) add(HUNDREDS[h])
        val tail = buildString {
            when {
                r == 0 -> append(pickOrd(h * 100, form))
                r < 20 -> append(pick(ORD_NOM[r], ORD_GEN[r], ORD_LOC[r], form))
                else -> {
                    // порядковым становится только последнее слово: 26 → «дваццаць шосты»,
                    // а не «дваццаты шосты»; десятки целиком (20, 30…) берём из ORD-таблицы
                    val u = r % 10
                    if (u == 0) {
                        val t = TENS_ORD[r]!!
                        append(pick(t.first, t.second, t.third, form))
                    } else {
                        append(TENS[r / 10])
                        append(' ')
                        append(pick(ORD_NOM[u], ORD_GEN[u], ORD_LOC[u], form))
                    }
                }
            }
        }
        // у составного числительного порядковым становится только последнее слово
        return (head.toString() + " " + tail).trim()
    }

    private fun pickOrd(v: Int, form: Form): String =
        HUNDREDS_ORD[v]?.let { pick(it.first, it.second, it.third, form) } ?: ""

    private fun pick(nom: String, gen: String, loc: String, form: Form) = when (form) {
        Form.NOM -> nom
        Form.GEN -> gen
        Form.LOC -> loc
    }

    // «гадоў», «гадах» — после количественного («100 гадоў»), год порядковым только в единственном числе
    // слева не цифра и не разряд: «1 000 год», «12345 год» — не год «000» и «2345»
    private val YEAR_RE = Regex("(?<![\\d.,])(?<!\\d[ \\u00A0\\u202F])(\\d{3,4})\\s*(год|года|годзе)(?![а-яёіў])", RegexOption.IGNORE_CASE)
    /** Разряды тысяч через пробел — только группами по три («1 000 000») и без соседних групп: телефон
     * «29 123 45 67», номер карты «4276 1234…» и «1 2 3» — отдельные числа. */
    private val DIGIT_GROUP_RE = Regex("(?<!\\d[ \\u00A0\\u202F]?)\\d{1,3}(?:[ \\u00A0\\u202F]\\d{3})+(?![ \\u00A0\\u202F]?\\d)|\\d+")

    /** Десятичная дробь: целая часть (можно разрядами, как [DIGIT_GROUP_RE]), точка или запятая, цифры; не кусок
     * «1.10.2» или IP-адреса. */
    private val DECIMAL_RE = Regex("""(?<![\d.,]|\d[ \x{A0}\x{202F}])(\d{1,3}(?:[ \x{A0}\x{202F}]\d{3})+|\d+)[.,](\d+)(?![.,]?\d)""")

    /** Число словами: до 999 999 999, длиннее — по цифрам; с годом — порядковым и в нужном падеже; дробь — [BelNumbers.decimal]. */
    fun numberWords(text: String): String {
        var s = YEAR_RE.replace(text) { m ->
            val n = m.groupValues[1].toLongOrNull() ?: return@replace m.value
            val word = m.groupValues[2].lowercase()
            val form = when (word) {
                "годзе" -> Form.LOC
                "года" -> Form.GEN
                else -> Form.NOM
            }
            ordinal(n, form) + " " + word
        }
        // дробь без единицы: «3,14», «1.5»; «12.05» сюда не доходит — его уже прочли датой
        s = DECIMAL_RE.replace(s) { m -> BelNumbers.decimal(m.groupValues[1].filter { it.isDigit() }, m.groupValues[2]) }
        s = DIGIT_GROUP_RE.replace(s) { m ->
            val raw = m.value.filter { it.isDigit() }
            raw.toLongOrNull()?.let { cardinal(it) } ?: digits(raw)
        }
        return s
    }

    /** Телефон с дефисами: «8 029 123-45-67», «+375 (29) 123-45-67», «222-33-44» — группами как написано, с паузой
     * между ними. Без хвоста «-xx-xx» не телефон: «10 000-15 000», «10-15», «1990-2000». */
    private val PHONE_RE = Regex("""(?<![\d\p{L}])(?:\d{1,5}[ \x{A0}-]|\(\d{1,5}\)[ \x{A0}-]?){0,3}\d{2,3}-\d{2}-\d{2}(?![\d\p{L}]|-\d)""")
    private val DIGITS_RE = Regex("\\d+")
    private fun phones(text: String) = PHONE_RE.replace(text) { m -> DIGITS_RE.findAll(m.value).joinToString(", ") { group(it.value) } }

    /** Гласная со знаком ударения над ней (U+0301 пишет «Книга с ударениями», гравис U+0300 — словари): «+» перед ней. */
    private val COMBINING_ACUTE_RE = Regex("([аеёіоуыэюяАЕЁІОУЫЭЮЯ])[\\u0301\\u0300]")
    /** Мягкий перенос книг и невидимые соединители: выкидываем, иначе фильтр рвал слово пробелом. */
    private val ZERO_WIDTH_RE = Regex("[\\u00AD\\u200B-\\u200D\\u2060\\uFEFF]")
    /** «+» не перед гласной — не ударение: «C++» — «плюс». */
    private val PLUS_NOT_STRESS_RE = Regex("\\+(?![аеёіоуыэюя])")
    /** Знак перед числом — до чисел: иначе «+1» становится «+адзін» (ударение), а «-20» теряет минус. Минус — не после
     * буквы и цифры: «10-15», «8-029», «1990-я», «Ту-154» не трогаем. */
    private val PLUS_NUMBER_RE = Regex("\\+(?=\\s*\\d)")
    private val MINUS_NUMBER_RE = Regex("(?<![\\p{L}\\d])[-−](?=\\d)")
    /** Цифры Nd не из ASCII (арабо-индийские, полноширинные): \d в ICU их ловит, а таблицы чисел — нет. */
    private val OTHER_DIGIT_RE = Regex("(?![0-9])\\p{Nd}")
    /** Апостроф во всех написаниях. В символах cis-пака он есть, но служебный (первые три, Silero их вырезает): до
     * модели не доходит, «аб'ява» читается «абява». Тем же классом его снимают поправки пака (Pack.corrections). */
    val APOSTROPHE = Regex("[’'`´ʼ‘]")
    /** Градусы Цельсия латинской или кириллической «C» — к одной записи «°с» (кириллица). */
    private val CELSIUS_RE = Regex("°\\s*[CcСс](?!\\p{L})")
    private val COMMA_RUN_RE = Regex(",(?:\\s*,)+")
    // и после тире: «— «Так», —» и «— (ціха) Так» не дают «—, так»
    private val COMMA_EDGE_RE = Regex("^[\\s,]+|(?<=[:;.?…—])\\s*,|\\s*,(?=\\s*(?:[.?…;:]|$))")
    private val SPACE_BEFORE_PUNCT_RE = Regex("\\s+(?=[.,?…:;])")
    private val WS_RE = Regex("\\s+")
    /** Точка сокращения, закрывшая предложение: единица или год за числом («2024 г. Цяпер», «7,5 руб.» в конце текста),
     * «і г. д.», «і інш.». Splitter после них не режет, а разворот точку съедает — ставим за ней вторую, отдельным
     * знаком: она переживает разворот. «г.» за «дд.мм» — горад («15.06 г. Мінск»), регистр — до lowercase. */
    private val ABBR_END_RE = Regex("""(?:(?<=\d[ \x{A0}]?|\d-я[ \x{A0}]|(?:тыс|млн|млрд)\.?[ \x{A0}])""" +
        """(?:км/г|тыс|млрд|млн|руб|кап|км|см|мм|кг|мл|мг|га|шт|гг|(?<!\.\d\d[ \x{A0}]?)г|м|л|т)""" +
        // только там, где [WORD_ABBR] их развернёт: иначе точка осталась бы двойной
        """|(?<=(?<!\p{L})і\s{1,3}г\.\s{0,3})д|(?<=(?<!\p{L})і\s{1,3})інш)\.(?=\s+\p{Lu}|\s*$)""")
    /** Сокращения словами; «вуліцы» после «на/па» (месны), «і іншае» — не проверено носителем. */
    private val WORD_ABBR = listOf(
        Regex("""(?<!\p{L})і\s+г\.\s*д\.""") to "і гэтак далей",
        Regex("""(?<!\p{L})г\.\s*зн\.""") to "гэта значыць",
        Regex("""(?<!\p{L})і\s+інш\.""") to "і іншае",
        Regex("""(?<!\p{L})напр\.""") to "напрыклад",
        Regex("""(?<!\p{L})(на|па)\s+вул\.""") to "$1 вуліцы",
        Regex("""(?<!\p{L})вул\.""") to "вуліца")
    /** Смесь «?!» (Normalizer.punctuation сводит к ней «!?», «?!?») — вопрос, как у русского cis: «!» туда не доходит. */
    private val QUEST_EXCL_RE = Regex("\\?!|!\\?")
    /** Скобки — пауза запятой (pause_parens, как у русского); кавычки — без паузы, их выкидывает фильтр. */
    private val PARENS_RE = Regex("[()\\[\\]]")

    /** Итоговая подготовка: латиница, числа, знаки, фильтр по таблице символов модели. */
    fun prepare(text: String, allowed: String, rules: Rules = Rules()): String {
        var s = COMBINING_ACUTE_RE.replace(text) { "+" + it.groupValues[1] }.replace(ZERO_WIDTH_RE, "")
        s = OTHER_DIGIT_RE.replace(s) { Character.digit(it.value.codePointAt(0), 10).toString() }
        // «°C» латиницей — до Latin, иначе она делает из «C» «к» и единица не находится
        s = CELSIUS_RE.replace(s, "°с")
        s = BelNumbers.roman(s)                       // «XX стагоддзе» — до Latin, иначе «экс-экс»
        // латиницы в таблице символов модели нет вовсе — «FilmBell» раньше просто выпадало
        // точка сокращения в конце предложения — после латиницы («5kg.» — уже «5 кг.»), но до lowercase
        s = ABBR_END_RE.replace(Latin.translate(s), "$0 .").lowercase()
        s = s.replace(APOSTROPHE, "")
        for ((re, rep) in WORD_ABBR) s = re.replace(s, rep)    // до «г.» — «горад» и до года
        s = MINUS_NUMBER_RE.replace(PLUS_NUMBER_RE.replace(s, " плюс "), " мінус ")
        s = phones(s)
        // даты и время — до единиц: у них внутри цифры со знаками (12.05.2026, 21:30)
        s = BelNumbers.dates(s)
        s = BelNumbers.times(s)
        s = BelNumbers.ordinals(s)                    // «1-га верасня», «5-ы клас», «1990-я»
        s = BelNumbers.abbreviations(s)               // «1999 г.» → «1999 год»
        s = BelNumbers.units(s)                       // «3 км» → «тры кіламетры»
        s = BelNumbers.standalone(s)                  // «руб.» без числа
        s = numberWords(s)
        s = QUEST_EXCL_RE.replace(s, "?").replace('!', '.')   // «!» модель не получает (служебный символ)
        if (rules.on("pause_parens")) s = PARENS_RE.replace(s, ", ")
        // тире — то, что знает модель: у cis-пака только «—», а Splitter делает из « - » «–»; на «—» и пауза тире
        s = s.replace('–', '—')
        s = PLUS_NOT_STRESS_RE.replace(s, " плюс ")
        val sb = StringBuilder(s.length)
        for (c in s) {
            when {
                c in allowed -> sb.append(c)
                // надстрочный знак не над гласной — без следа, пробелом он рвал бы слово
                Character.getType(c) == Character.NON_SPACING_MARK.toInt() -> {}
                else -> sb.append(' ')
            }
        }
        // скобки дали запятые: подряд — одна, в начале и рядом с точкой — не нужна
        val out = COMMA_RUN_RE.replace(sb.toString().replace(WS_RE, " "), ",")
        return SPACE_BEFORE_PUNCT_RE.replace(COMMA_EDGE_RE.replace(out, ""), "").replace(WS_RE, " ").trim()
    }
}
