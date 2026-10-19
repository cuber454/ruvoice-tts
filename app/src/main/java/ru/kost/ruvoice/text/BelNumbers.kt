// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

/**
 * Числа в реальной жизни: даты, время, единицы, валюты.
 *
 * Зачем отдельно от [BelNormalizer]: там числа просто читаются словами, а «12.05.2026», «21:30»
 * и «3 км» так читать нельзя — получается «дванаццаць пяць дзве тысячы…». Здесь они раскрываются
 * по-белорусски: «дванаццатага мая дзве тысячы дваццаць шостага года», «дзевятая гадзіна трыццаць
 * хвілін», «тры кіламетры».
 *
 * Порядок важен: сначала даты и время (у них внутри цифры со знаками), потом единицы, и только
 * потом BelNormalizer читает оставшиеся числа как количественные.
 */
object BelNumbers {

    private val MONTHS = arrayOf(
        "студзеня", "лютага", "сакавіка", "красавіка", "мая", "чэрвеня",
        "ліпеня", "жніўня", "верасня", "кастрычніка", "лістапада", "снежня")

    /** Женский род: гадзіна, хвіліна, тысяча. */
    private val FEM = arrayOf(
        "нуль", "адна", "дзве", "тры", "чатыры", "пяць", "шэсць", "сем", "восем", "дзевяць",
        "дзесяць", "адзінаццаць", "дванаццаць", "трынаццаць", "чатырнаццаць", "пятнаццаць",
        "шаснаццаць", "сямнаццаць", "васямнаццаць", "дзевятнаццаць")

    private val TENS = arrayOf(
        "", "", "дваццаць", "трыццаць", "сорак", "пяцьдзясят", "шэсцьдзясят", "семдзесят",
        "восемдзесят", "дзевяноста")

    /** Белорусское склонение после числа: 1 — адзін, 2–4 — два, 5 — пяць. */
    fun plural(n: Long, one: String, few: String, many: String): String {
        val n100 = n % 100
        val n10 = n % 10
        return when {
            n100 in 11..14 -> many
            n10 == 1L -> one
            n10 in 2..4 -> few
            else -> many
        }
    }

    /** Женское количественное: 2 → «дзве», 21 → «дваццаць адна». */
    fun femCardinal(n: Long): String {
        if (n < 20) return FEM[n.toInt()]
        val t = TENS[(n / 10 % 10).toInt()]
        val u = n % 10
        return if (u == 0L) t else "$t ${FEM[u.toInt()]}"
    }

    // ---- даты ----

    /** 12.05.2026 → «дванаццатага мая дзве тысячы дваццаць шостага года»; 3.09 → «трэцяга верасня».
     * Без года — только «дд.мм» через точку: «1.5 кг», «1/2 шклянкі», «24/7» — не даты, их читают числа. */
    fun dates(text: String): String {
        fun date(m: MatchResult, dRaw: String, moRaw: String, yearRaw: String): String {
            val d = dRaw.toInt(); val mo = moRaw.toInt()
            if (d !in 1..31 || mo !in 1..12) return m.value
            val sb = StringBuilder(BelNormalizer.ordinal(d.toLong(), BelNormalizer.Form.GEN))
            sb.append(' ').append(MONTHS[mo - 1])
            if (yearRaw.isNotEmpty()) {
                val y0 = yearRaw.toLong()
                // двузначный год: «99» — 1999, «20» — 2020
                val y = if (yearRaw.length <= 2) (if (y0 > 30) 1900 else 2000) + y0 else y0
                sb.append(' ').append(BelNormalizer.ordinal(y, BelNormalizer.Form.GEN)).append(" года")
            }
            return sb.toString()
        }
        val s = DATE.replace(text) { m -> date(m, m.groupValues[1], m.groupValues[3], m.groupValues[4]) }
        return DAY_MONTH.replace(s) { m -> date(m, m.groupValues[1], m.groupValues[2], "") }
    }

    // ---- время ----

    /** 21:30 → «дваццаць адна гадзіна трыццаць хвілін» (часы словами, потом минуты; 9:00 → «дзевяць гадзін роўна»). */
    fun times(text: String): String = TIME.replace(text) { m ->
        val h = m.groupValues[1].toIntOrNull() ?: return@replace m.value
        val mi = m.groupValues[2].toIntOrNull() ?: return@replace m.value
        if (h !in 0..23 || mi !in 0..59) return@replace m.value
        val hb = StringBuilder(femCardinal(h.toLong()))
        hb.append(' ').append(plural(h.toLong(), "гадзіна", "гадзіны", "гадзін"))
        if (mi > 0) {
            hb.append(' ').append(femCardinal(mi.toLong()))
            hb.append(' ').append(plural(mi.toLong(), "хвіліна", "хвіліны", "хвілін"))
        } else {
            hb.append(' ').append("роўна")
        }
        hb.toString()
    }

    // ---- единицы и валюты ----

    /** Единица по точному токену: ключ — то, что стоит сразу за числом (без точки). */
    private val UNITS: Map<String, Triple<String, String, String>> = mapOf(
        "км/г" to Triple("кіламетр у гадзіну", "кіламетры ў гадзіну", "кіламетраў у гадзіну"),
        "км" to Triple("кіламетр", "кіламетры", "кіламетраў"),
        "см" to Triple("сантыметр", "сантыметры", "сантыметраў"),
        "мм" to Triple("міліметр", "міліметры", "міліметраў"),
        "кг" to Triple("кілаграм", "кілаграмы", "кілаграмаў"),
        "мл" to Triple("мілілітр", "мілілітры", "мілілітраў"),
        "мг" to Triple("міліграм", "міліграмы", "міліграмаў"),
        "га" to Triple("гектар", "гектары", "гектараў"),
        "м2" to Triple("квадратны метр", "квадратныя метры", "квадратных метраў"),
        "м²" to Triple("квадратны метр", "квадратныя метры", "квадратных метраў"),
        "м" to Triple("метр", "метры", "метраў"),
        "л" to Triple("літр", "літры", "літраў"),
        "г" to Triple("грам", "грамы", "грамаў"),
        "т" to Triple("тона", "тоны", "тон"),
        "шт" to Triple("штука", "штукі", "штук"),
        // «с» кириллицей: BelNormalizer сводит к ней «°C» до латиницы. Строчными: регистр опущен до единиц,
        // а заглавной «Ц» в символах модели нет
        "°с" to Triple("градус цэльсія", "градусы цэльсія", "градусаў цэльсія"),
        "%" to Triple("працэнт", "працэнты", "працэнтаў"),
        "руб" to Triple("рубель", "рублі", "рублёў"),
        "кап" to Triple("капейка", "капейкі", "капеек"),
        "тыс" to Triple("тысяча", "тысячы", "тысяч"),
        "млн" to Triple("мільён", "мільёны", "мільёнаў"),
        "млрд" to Triple("мільярд", "мільярды", "мільярдаў"),
        "₽" to Triple("рубель", "рублі", "рублёў"),
        "$" to Triple("долар", "долары", "долараў"),
        "€" to Triple("еўра", "еўра", "еўра"),
    )
    /** Единицы женского рода: счётное слово согласуется с ними, а не наоборот. */
    private val FEM_UNITS = setOf("т", "шт", "кап", "тыс")

    /** «3 км» → «тры кіламетры», «5 %» → «пяць працэнтаў», «2 руб.» → «два рублі», «2 т» → «дзве тоны». */
    fun units(text: String): String = NUMBER_UNIT.replace(text) { m ->
        val raw = m.groupValues[1].replace("\u00A0", "").replace(" ", "")
        val n = raw.toLongOrNull() ?: return@replace m.value
        val rest = m.groupValues[2].lowercase()
        val u = UNITS[rest] ?: return@replace m.value
        // единицы женского рода: «дзве тоны», «дзве капейкі», «дзве штукі», «дзве тысячы», а не «два»
        val num = if (rest in FEM_UNITS) BelNormalizer.cardinalFem(n) else BelNormalizer.cardinal(n)
        "$num ${plural(n, u.first, u.second, u.third)}"
    }

    // ---- сокращения ----

    /** «1999 г.» → «1999 год», «90-я гг.» → «90-я гады», «5 тыс.» уже разобрано единицами.
     * «5 г.» — граммы (units): год — только 3–4 цифры ([YEAR_G]). */
    fun abbreviations(text: String): String {
        var s = text
        s = YEAR_G.replace(s) { m -> m.groupValues[1] + " год" }
        s = GG.replace(s, "гады")
        return s
    }

    /** Сокращение без числа: «руб.» после «5 тыс.» или «г.» перед названием города. Только отдельным словом:
     * «капуста», «рубель» и уже развёрнутое единицами «два рублі» не трогаем. */
    fun standalone(text: String): String {
        var s = text
        s = s.replace(Regex("""(?<!\p{L})г\.(?=\s)"""), "горад ")
        s = s.replace(Regex("""(?<![\p{L}\d])руб\.?(?!\p{L})"""), "рублёў")
        s = s.replace(Regex("""(?<![\p{L}\d])кап\.?(?!\p{L})"""), "капеек")
        s = s.replace(Regex("""(?<![\p{L}\d])тыс\.(?!\p{L})"""), "тысяч")
        s = s.replace(Regex("""(?<![\p{L}\d])млрд(?!\p{L})"""), "мільярдаў")
        s = s.replace(Regex("""(?<![\p{L}\d])млн(?!\p{L})"""), "мільёнаў")
        s = s.replace(Regex("""(?<![\p{L}\d])шт\.(?!\p{L})"""), "штук")
        return s
    }

    /** С годом: «12.05.2026», «12/05/26»; не после цифры и знака — иначе кусок большого числа. */
    private val DATE = Regex("""(?<![\d.,])(\d{1,2})([./])(\d{1,2})\2(\d{4}|\d{2})(?!\d)""")
    /** Без года: «3.09» — месяц двумя цифрами, не дробь «1.5» и не кусок «1.10.2». */
    private val DAY_MONTH = Regex("""(?<![\d.,])(\d{1,2})\.(\d{2})(?!\d|[.,]\d)""")
    private val TIME = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)""")
    private val YEAR_G = Regex("""(?<!\d)(\d{3,4})\s*г\.""")
    private val GG = Regex("""(?<!\p{L})гг\.?""", RegexOption.IGNORE_CASE)
    private val NUMBER_UNIT = Regex(
        // длинные варианты раньше коротких: иначе «5 тыс.» разобралось бы как «5 т» (тоны).
        // Справа не буква: иначе «2026 год» — это «2026 г» (граммы) и «од», а «3 тамы» — «3 т» (тоны) и «амы».
        """(?<![\d.,])(\d{1,9}(?:[ \u00A0]\d{3})*)\s*(км/г|тыс|млрд|млн|руб|кап|км|см|мм|кг|мл|мг|м2|м²|га|шт|°с|%|₽|\$|€|м|л|г|т)\.?(?![\p{L}])""",
        RegexOption.IGNORE_CASE)
}
