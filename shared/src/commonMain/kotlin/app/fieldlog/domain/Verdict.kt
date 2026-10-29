package app.fieldlog.domain

/** Тексты дисклеймеров. Дословно из `docs/plan.md`, раздел «Вердикт». */
object Disclaimers {
    /** Рядом с показанным статусом. */
    const val WITH_STATUS: String =
        "Вид может быть определён ошибочно. Похожие виды отличаются. " +
            "По приложению нельзя решать, можно ли это есть."

    /** Когда вердикта нет. */
    const val WITHOUT_VERDICT: String =
        "Надёжно определить не получилось. По приложению нельзя решать, можно ли это есть."
}

/** Ответ модели: список id видов встроенного словаря либо «не уверен». Ничего другого модель не возвращает. */
sealed interface ModelAnswer {
    class Species(val speciesIds: List<String>) : ModelAnswer
    data object NotSure : ModelAnswer
}

/** Результат распознавания после разбора ответа: один вид словаря либо «не уверен». */
sealed interface RecognitionResult {
    data class Suggested(val speciesId: String) : RecognitionResult
    data object NotSure : RecognitionResult

    companion object {
        /**
         * «Не уверен» — это ответ «не уверен», пустой список, несколько кандидатов
         * и единственный кандидат, которого нет во встроенном словаре.
         */
        fun from(answer: ModelAnswer, dictionary: SpeciesDictionary): RecognitionResult = when (answer) {
            ModelAnswer.NotSure -> NotSure
            is ModelAnswer.Species -> {
                val id = answer.speciesIds.singleOrNull()
                if (id != null && dictionary.find(id) != null) Suggested(id) else NotSure
            }
        }
    }
}

/** Вердикт по находке: либо статус из карточки, либо «без статуса». */
sealed interface Verdict {
    val disclaimer: String

    class WithStatus(val status: SpeciesStatus) : Verdict {
        override val disclaimer: String get() = Disclaimers.WITH_STATUS
    }

    data object NoStatus : Verdict {
        override val disclaimer: String get() = Disclaimers.WITHOUT_VERDICT
    }

    companion object {
        /**
         * Статус берётся только из карточки встроенного словаря у вида, подтверждённого человеком.
         *
         * Без статуса остаются: свой вид, вид вне словаря, животное и место (у их карточек статуса нет),
         * а также находка с распознаванием «не уверен» ([recognition]; `null` — распознавания не было,
         * например вид выбран руками без сети).
         */
        fun of(
            confirmedSpecies: SpeciesRef?,
            dictionary: SpeciesDictionary,
            recognition: RecognitionResult? = null,
        ): Verdict {
            if (recognition == RecognitionResult.NotSure) return NoStatus
            val dictionarySpecies = confirmedSpecies as? SpeciesRef.Dictionary ?: return NoStatus
            val status = dictionary.find(dictionarySpecies.id)?.status ?: return NoStatus
            return WithStatus(status)
        }
    }
}
