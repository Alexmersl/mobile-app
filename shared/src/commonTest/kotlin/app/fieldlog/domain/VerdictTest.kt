package app.fieldlog.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VerdictTest {
    private fun statusOf(verdict: Verdict): SpeciesStatus? = (verdict as? Verdict.WithStatus)?.status

    @Test
    fun statusComesFromBuiltInCardOfConfirmedSpecies() {
        assertEquals(
            SpeciesStatus.EDIBLE,
            statusOf(Verdict.of(SpeciesRef.Dictionary("boletus"), testDictionary)),
        )
        assertEquals(
            SpeciesStatus.POISONOUS,
            statusOf(Verdict.of(SpeciesRef.Dictionary("amanita"), testDictionary)),
        )
    }

    @Test
    fun speciesOutsideDictionaryHasNoStatus() {
        assertIs<Verdict.NoStatus>(Verdict.of(SpeciesRef.Dictionary("not-in-dictionary"), testDictionary))
    }

    @Test
    fun customSpeciesHasNoStatusEvenWithIdOfDictionaryCard() {
        assertIs<Verdict.NoStatus>(Verdict.of(SpeciesRef.Custom("boletus"), testDictionary))
    }

    @Test
    fun noConfirmedSpeciesHasNoStatus() {
        assertIs<Verdict.NoStatus>(Verdict.of(null, testDictionary))
    }

    @Test
    fun notSureRecognitionHasNoStatus() {
        val verdict = Verdict.of(SpeciesRef.Dictionary("boletus"), testDictionary, RecognitionResult.NotSure)
        assertIs<Verdict.NoStatus>(verdict)
    }

    @Test
    fun suggestedRecognitionDoesNotChangeStatusFromCard() {
        val verdict = Verdict.of(
            SpeciesRef.Dictionary("amanita"),
            testDictionary,
            RecognitionResult.Suggested("boletus"),
        )
        assertEquals(SpeciesStatus.POISONOUS, statusOf(verdict), "Статус — у вида, который подтвердил человек")
    }

    @Test
    fun conditionallyEdibleIsShownAsUnknownStatusNotAsMissing() {
        val verdict = Verdict.of(SpeciesRef.Dictionary("morel"), testDictionary)

        assertEquals(SpeciesStatus.UNKNOWN, statusOf(verdict))
        assertEquals(Disclaimers.WITH_STATUS, verdict.disclaimer)
    }

    @Test
    fun animalHasNoStatus() {
        assertIs<Verdict.NoStatus>(Verdict.of(SpeciesRef.Dictionary("moose"), testDictionary))
    }

    @Test
    fun animalAndPlaceCardsCannotCarryStatus() {
        val animal = runCatching {
            SpeciesCard("wolf", "Волк", "Canis lupus", FindingCategory.ANIMAL, SpeciesStatus.EDIBLE)
        }
        assertIs<IllegalArgumentException>(animal.exceptionOrNull())
        val place = runCatching { SpeciesCard("p", "Поляна", "-", FindingCategory.PLACE, null) }
        assertIs<IllegalArgumentException>(place.exceptionOrNull())
    }

    @Test
    fun modelAnswerBecomesOneDictionarySpeciesOrNotSure() {
        assertEquals(
            RecognitionResult.Suggested("boletus"),
            RecognitionResult.from(ModelAnswer.Species(listOf("boletus")), testDictionary),
        )
        assertEquals(RecognitionResult.NotSure, RecognitionResult.from(ModelAnswer.NotSure, testDictionary))
        assertEquals(RecognitionResult.NotSure, RecognitionResult.from(ModelAnswer.Species(emptyList()), testDictionary))
        assertEquals(
            RecognitionResult.NotSure,
            RecognitionResult.from(ModelAnswer.Species(listOf("boletus", "amanita")), testDictionary),
            "Несколько кандидатов — «не уверен»",
        )
        assertEquals(
            RecognitionResult.NotSure,
            RecognitionResult.from(ModelAnswer.Species(listOf("unknown-id")), testDictionary),
            "Вид вне словаря — «не уверен»",
        )
    }

    @Test
    fun disclaimersMatchPlanVerbatim() {
        assertEquals(
            "Вид может быть определён ошибочно. Похожие виды отличаются. " +
                "По приложению нельзя решать, можно ли это есть.",
            Disclaimers.WITH_STATUS,
        )
        assertEquals(
            "Надёжно определить не получилось. По приложению нельзя решать, можно ли это есть.",
            Disclaimers.WITHOUT_VERDICT,
        )
    }

    @Test
    fun verdictCarriesMatchingDisclaimer() {
        assertEquals(Disclaimers.WITH_STATUS, Verdict.WithStatus(SpeciesStatus.EDIBLE).disclaimer)
        assertEquals(Disclaimers.WITHOUT_VERDICT, Verdict.NoStatus.disclaimer)
    }
}
