// SPDX-License-Identifier: CC-BY-NC-SA-4.0
// Белорусский текстовый слой для RuVoice TTS (см. LICENSE репозитория).
package ru.kost.ruvoice.text

import ru.kost.ruvoice.SileroData
import java.util.Locale

/** Слова, по которым [SentenceType] узнаёт вопрос: у каждого языка свои. У штатной русской модели они лежат
 * в данных (stress.bin: wh_forms, leading_fillers, tag_patterns), у второй языка — здесь, в его слое. */
interface QuestionLists {
    val whForms: Set<String>
    val leadingFillers: Set<String>
    val tagRe: Regex
}

/**
 * Язык текста: выбирается по полю `lang` пака, которым читается фраза, — а не по сборке приложения.
 * У каждого языка своя подготовка текста (числа, сокращения, латиница) и свои ударения: за ударения
 * отвечает [Stress], которому слой передаётся вместе с поправками пака.
 *
 * Новый язык CIS-модели добавляется ещё одной строкой в [Layers]: пока в таблице только `ru` и `be`.
 */
interface TextLayer {
    /** Код языка, как в `pack.json`: `ru`, `be`. */
    val code: String

    /** Язык и страна голосов в TTS API: ISO-3 из неё система спрашивает язык и выбирает голос. */
    val locale: Locale

    /** Текст запроса к модели: числа, сокращения, латиница, знаки — и фильтр по символам модели [allowed]. */
    fun prepare(text: String, allowed: String, rules: Rules): String

    /** Списки для классификации вопроса (см. [QuestionLists]). */
    fun questions(d: SileroData): QuestionLists

    /** Ударения языка: акцентор пака и его поправки поверх словаря пользователя. null — русский конвейер [Stress]
     * (таблицы, омографы, «ё»). */
    fun stress(models: StressModels, userDict: Map<String, String>, corrections: Map<String, String>): ((String) -> String)? = null
}

/** Русский слой — ровно то, что было до появления второго языка: [Normalizer] и данные stress.bin. */
object RuLayer : TextLayer {
    override val code = "ru"
    override val locale = Locale("ru", "RU")
    override fun prepare(text: String, allowed: String, rules: Rules): String = Normalizer.prepare(text, allowed, rules)
    override fun questions(d: SileroData): QuestionLists = d
}

/** Белорусский слой: [BelNormalizer] (числа словами, «!» → «.», апостроф, фильтр по символам пака). */
object BeLayer : TextLayer {
    override val code = "be"
    override val locale = Locale("be", "BY")
    override fun prepare(text: String, allowed: String, rules: Rules): String = BelNormalizer.prepare(text, allowed)
    override fun questions(d: SileroData): QuestionLists = BE_QUESTIONS
    override fun stress(models: StressModels, userDict: Map<String, String>, corrections: Map<String, String>) =
        BelStress(models, userDict, corrections)::apply
}

/** Списки белорусского слоя: те же ключи, что у русского в silero_be.json, — перенесены сюда, потому что
 * вторая языка нет в stress.bin, и данные модели на него не распространяются. */
private object BE_QUESTIONS : QuestionLists {
    override val whForms = setOf(
        "хто", "што", "дзе", "калі", "чаму", "навошта", "як", "які", "якая", "якое", "якія", "чый", "чыя",
        "чыё", "чыі", "колькі", "куды", "адкуль", "каторы", "чым", "каго", "чаго")
    override val leadingFillers = setOf(
        "ну", "вось", "а", "дык", "мо", "мабыць", "нават", "проста", "значыць", "так", "але", "ці")
    override val tagRe = Regex(listOf(
        "\\bня\\s+прауда\\b", "\\bня\\s+прауд[аы]\\b", "\\bці\\s+не\\s+так\\b", "\\bтак\\s+жа\\b")
        .joinToString("|") { "(?:$it)" }, RegexOption.IGNORE_CASE)
}

/** Таблица языков: язык берётся по коду пака, остальной код о языках не знает. */
object Layers {
    private val all = listOf(RuLayer, BeLayer)
    private val table: Map<String, TextLayer> = all.associateBy { it.code }

    /** Русский — умолчание: так читаются паки без поля `lang` и встроенная модель. */
    val RU: TextLayer get() = RuLayer

    fun of(code: String?): TextLayer = table[code?.lowercase()] ?: RuLayer

    /** Есть ли слой для кода пака: пак на языке, которого приложение не знает, не ставится. */
    fun has(code: String) = code in table

    /** Слой по языку из TTS API (ISO-3: «rus», «bel»); null — такого языка нет. */
    fun ofIso3(lang: String?): TextLayer? = all.firstOrNull { it.locale.isO3Language == lang }
}
