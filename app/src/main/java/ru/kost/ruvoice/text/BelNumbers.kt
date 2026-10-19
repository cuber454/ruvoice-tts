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
        var s = DATE.replace(text) { m -> date(m, m.groupValues[1], m.groupValues[3], m.groupValues[4]) }
        s = DAY_MONTH.replace(s) { m -> date(m, m.groupValues[1], m.groupValues[2], "") }
        // месяц словом: «3 мая 2026 г.» → «трэцяга мая … шостага года», как у цифровой даты
        return DAY_MONTH_WORD.replace(s) { m ->
            date(m, m.groupValues[1], (MONTHS.indexOf(m.groupValues[2].lowercase()) + 1).toString(), m.groupValues[3])
        }
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
    /** Единица после десятичной дроби — родительный единственного, как после дробных числительных: «1,5 кг» —
     * «… кілаграма». Формы не проверены носителем. */
    private val UNITS_FRAC = mapOf(
        "км/г" to "кіламетра ў гадзіну", "км" to "кіламетра", "см" to "сантыметра", "мм" to "міліметра", "кг" to "кілаграма",
        "мл" to "мілілітра", "мг" to "міліграма", "га" to "гектара", "м2" to "квадратнага метра", "м²" to "квадратнага метра",
        "м" to "метра", "л" to "літра", "г" to "грама", "т" to "тоны", "шт" to "штукі", "°с" to "градуса цэльсія",
        "%" to "працэнта", "руб" to "рубля", "кап" to "капейкі", "тыс" to "тысячы", "млн" to "мільёна", "млрд" to "мільярда",
        "₽" to "рубля", "$" to "долара", "€" to "еўра")

    /** «3 км» → «тры кіламетры», «5 %» → «пяць працэнтаў», «2 руб.» → «два рублі», «2 т» → «дзве тоны»;
     * «1,5 кг» → «адзін коска пяць кілаграма» ([decimal]). */
    fun units(text: String): String = NUMBER_UNIT.replace(text) { m ->
        val raw = m.groupValues[1].replace("\u00A0", "").replace(" ", "")
        val n = raw.toLongOrNull() ?: return@replace m.value
        val rest = m.groupValues[3].lowercase()
        val u = UNITS[rest] ?: return@replace m.value
        if (m.groupValues[2].isNotEmpty()) return@replace "${decimal(raw, m.groupValues[2])} ${UNITS_FRAC[rest] ?: u.third}"
        // единицы женского рода: «дзве тоны», «дзве капейкі», «дзве штукі», «дзве тысячы», а не «два»
        val num = if (rest in FEM_UNITS) BelNormalizer.cardinalFem(n) else BelNormalizer.cardinal(n)
        "$num ${plural(n, u.first, u.second, u.third)}"
    }

    /** Десятичная дробь: «1,05» → «адзін коска нуль пяць». Нули в начале дробной части — словом каждый, иначе
     * «1,05» звучало бы как «1,5». ponytail: разговорное «коска», а не «адна цэлая пяць сотых»; не проверено носителем. */
    fun decimal(intRaw: String, fracRaw: String): String =
        (intRaw.toLongOrNull()?.let { BelNormalizer.cardinal(it) } ?: BelNormalizer.digits(intRaw)) + " коска " + BelNormalizer.group(fracRaw)

    // ---- порядковые ----

    /** «1-га верасня» → «першага верасня», «5-ы клас» → «пяты клас», «у 5-м класе» → «у пятым класе», «1990-я» →
     * «тысяча дзевяцьсот дзевяностыя». Форма — по концу суффикса: «-га» родны (м./н.), «-ы/-і» (-гі, -ці, -шы) назоўны
     * м., «-м» месны/творны м., «-я» — только у четырёхзначного на 0 (десятилетие, мн.); прочее («5-я» — «пятая»?) не
     * трогаем. Не проверено носителем. */
    fun ordinals(text: String): String = ORD_SUFFIX.replace(text) { m ->
        val n = m.groupValues[1].toLongOrNull() ?: return@replace m.value
        val sfx = m.groupValues[2].lowercase()
        when {
            sfx.endsWith("га") -> BelNormalizer.ordinal(n, BelNormalizer.Form.GEN)
            sfx.endsWith("м") -> BelNormalizer.ordinal(n, BelNormalizer.Form.LOC)
            sfx.endsWith("ы") || sfx.endsWith("і") -> BelNormalizer.ordinal(n)
            sfx == "я" && m.groupValues[1].length == 4 && n % 10 == 0L -> BelNormalizer.ordinal(n) + "я"
            else -> m.value
        }
    }

    /** Римское число I–XXX перед «стагоддзе», «век» — порядковым в падеже существительного: «XX стагоддзе» →
     * «дваццатае стагоддзе», «XX стагоддзя» → «дваццатага», «у XX стагоддзі» → «у дваццатым». Кириллические «Х», «І»
     * тоже: так часто набирают. Остальную латиницу не трогаем. Средний род — не проверено носителем. */
    fun roman(text: String): String = ROMAN.replace(text) { m ->
        // кириллическое «І» одно — союз: «І веку не хапіла»
        if (m.groupValues[1] == "І") return@replace m.value
        var n = 0; var prev = 0
        for (c in m.groupValues[1].reversed()) {
            val v = when (c) { 'I', 'І' -> 1; 'V' -> 5; else -> 10 }
            if (v < prev) n -= v else { n += v; prev = v }
        }
        val noun = m.groupValues[2]
        val word = when (noun.lowercase()) {
            "стагоддзе" -> BelNormalizer.ordinal(n.toLong()).let { o ->
                val last = o.substringAfterLast(' ')
                o.dropLast(last.length) + (NEUTER[last] ?: (last.removeSuffix("ы") + "ае"))
            }
            "век" -> BelNormalizer.ordinal(n.toLong())
            "стагоддзя", "века" -> BelNormalizer.ordinal(n.toLong(), BelNormalizer.Form.GEN)
            else -> BelNormalizer.ordinal(n.toLong(), BelNormalizer.Form.LOC)   // стагоддзі, стагоддзем, веку, веце, векам
        }
        "$word $noun"
    }
    /** Средний род на «-і» нельзя вывести из мужского заменой окончания. */
    private val NEUTER = mapOf("другі" to "другое", "трэці" to "трэцяе")

    // ---- сокращения ----

    /** «1999 г.» → «1999 год», «у 1812 г.» → «у 1812 годзе», «90-я гг.» → «90-я гады», «5 тыс.» уже разобрано единицами.
     * «5 г.», «500 г. мукі» — граммы (units): год — 1000–2199 или три цифры после «у/ў» ([YEAR_G]). */
    fun abbreviations(text: String): String {
        var s = text
        s = YEAR_G.replace(s) { m ->
            val prep = m.groupValues[1]
            val raw = m.groupValues[2]
            val p = prep.trim().lowercase()
            // год — 1000–2199 или три цифры после «у/ў» («у 988 г.»); «з 500 г.», «па 500 г. мукі» — граммы
            if (if (raw.length < 4) p != "у" && p != "ў" else raw.toInt() !in 1000..2199) return@replace m.value
            // падеж по предлогу: у/ў — месны, з/да/ад — родны, па — вінавальны; не проверено носителем
            val word = when (p) { "у", "ў" -> "годзе"; "з", "да", "ад" -> "года"; else -> "год" }
            "$prep$raw $word"
        }
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

    /** С годом: «12.05.2026», «12/05/26»; не после цифры и знака — иначе кусок большого числа. Хвост «г.» съедаем:
     * «года» дата уже говорит, иначе «…года горад». */
    private val DATE = Regex("""(?<![\d.,])(\d{1,2})([./])(\d{1,2})\2(\d{4}|\d{2})(?!\d)(?:\s*г\.)?""")
    /** «3 мая», «3 мая 2026 г.», «3 мая 2026 года»: год съедаем вместе с «г.»/«года» — date() скажет «года» сам. */
    private val DAY_MONTH_WORD = Regex("""(?<![\d.,])(\d{1,2})\s+(${MONTHS.joinToString("|")})(?!\p{L})(?:\s+(\d{4})(?!\d)(?:\s*г\.|\s+года(?!\p{L}))?)?""",
        RegexOption.IGNORE_CASE)
    /** Число с суффиксом порядкового ([ordinals]); не кусок «8-029» и не «1.5-га». */
    private val ORD_SUFFIX = Regex("""(?<![\d.,-])(\d{1,4})-(га|ы|і|гі|ці|шы|ты|мы|м|ым|ім|я)(?!\p{L})""", RegexOption.IGNORE_CASE)
    /** I–XXX латиницей или кириллицей ([roman]): непустое, не часть слова. */
    private val ROMAN = Regex("""(?<![\p{L}\d])(?=[IVXІХ])([XХ]{0,3}(?:[IІ][XХ]|[IІ]V|V?[IІ]{0,3}))\s+([Сс]тагоддз(?:е|я|і|ем)|[Вв](?:ек|ека|еку|еце|екам))(?!\p{L})""")
    // длинные варианты раньше коротких: иначе «5 тыс.» разобралось бы как «5 т» (тоны).
    // Справа не буква: иначе «2026 год» — это «2026 г» (граммы) и «од», а «3 тамы» — «3 т» (тоны) и «амы».
    private const val UNIT = """(км/г|тыс|млрд|млн|руб|кап|км|см|мм|кг|мл|мг|м2|м²|га|шт|°с|%|₽|\$|€|м|л|г|т)\.?(?![\p{L}])"""
    /** Без года: «3.09» — месяц двумя цифрами, не дробь «1.5», не кусок «1.10.2» и не число с единицей «1.05 кг».
     * «г.» после «дд.мм» — горад, а не граммы: «15.06 г. Гомель». */
    private val DAY_MONTH = Regex("""(?<![\d.,])(\d{1,2})\.(\d{2})(?!\d|[.,]\d)(?!\s*(?!г\.)$UNIT)""", RegexOption.IGNORE_CASE)
    private val TIME = Regex("""(?<!\d)(\d{1,2}):(\d{2})(?!\d)""")
    private val YEAR_G = Regex("""(?:(?<!\p{L})((?:у|ў|з|да|па|ад)\s+))?(?<!\d)(\d{3,4})\s*г\.""", RegexOption.IGNORE_CASE)
    private val GG = Regex("""(?<!\p{L})гг\.?""", RegexOption.IGNORE_CASE)
    /** Число, дробная часть через точку или запятую ([decimal]), единица [UNIT]. */
    private val NUMBER_UNIT = Regex(
        """(?<![\d.,])(\d{1,9}(?:[ \u00A0]\d{3})*)(?:[.,](\d+))?\s*$UNIT""",
        RegexOption.IGNORE_CASE)
}
