package app.fieldlog.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.fail

class FindingTest {
    private val photoCategories = listOf(
        FindingCategory.MUSHROOM,
        FindingCategory.BERRY,
        FindingCategory.PLANT,
        FindingCategory.ANIMAL,
    )

    @Test
    fun photoIsRequiredForMushroomBerryPlantAndAnimal() {
        for (category in photoCategories) {
            val result = Finding.create(draft(category = category, photo = null))
            val invalid = assertIs<CreateFindingResult.Invalid>(result, "Без фото ($category) находка не сохраняется")
            assertEquals(listOf(FindingError.PHOTO_REQUIRED), invalid.errors, "Категория $category")
        }
    }

    @Test
    fun placeIsSavedWithoutPhoto() {
        val result = Finding.create(draft(category = FindingCategory.PLACE))
        val created = assertIs<CreateFindingResult.Created>(result)
        assertNull(created.finding.photo)
        assertNull(created.finding.species)
    }

    @Test
    fun speciesIsRequiredForFourCategoriesAndForbiddenForPlace() {
        for (category in photoCategories) {
            val invalid = assertIs<CreateFindingResult.Invalid>(Finding.create(draft(category = category, species = null)))
            assertEquals(listOf(FindingError.SPECIES_REQUIRED), invalid.errors)
        }
        val place = Finding.create(draft(category = FindingCategory.PLACE, species = SpeciesRef.Custom("x")))
        assertEquals(
            listOf(FindingError.SPECIES_NOT_ALLOWED_FOR_PLACE),
            assertIs<CreateFindingResult.Invalid>(place).errors,
        )
    }

    @Test
    fun noteCanBeAppendedButSpeciesAndPhotoStay() {
        val original = when (val r = Finding.create(draft(note = "у реки"))) {
            is CreateFindingResult.Created -> r.finding
            is CreateFindingResult.Invalid -> fail("${r.errors}")
        }

        val updated = original.appendNote("  ещё три рядом  ")

        assertEquals("у реки\nещё три рядом", updated.note)
        assertEquals(original.id, updated.id)
        assertEquals(original.species, updated.species)
        assertEquals(original.photo, updated.photo)
        assertEquals(original.coordinates, updated.coordinates)
        assertEquals("у реки", original.note, "Исходная находка не меняется")
        assertSame(original, original.appendNote("   "), "Пустое дописывание ничего не меняет")
    }

    @Test
    fun appendingToEmptyNoteStartsTheNote() {
        assertEquals("первая запись", finding().appendNote("первая запись").note)
    }
}
