package app.fieldlog.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/** Правила метки итерации 1 (без вида). Правила [Finding] проверяют свои тесты и не менялись. */
class PinTest {
    private val photoCategories = listOf(
        FindingCategory.MUSHROOM,
        FindingCategory.BERRY,
        FindingCategory.PLANT,
        FindingCategory.ANIMAL,
    )

    @Test
    fun photoIsRequiredForMushroomBerryPlantAndAnimal() {
        for (category in photoCategories) {
            val result = Pin.create(pinDraft(category = category, photo = null))
            val invalid = assertIs<CreatePinResult.Invalid>(result, "Без фото ($category) метка не сохраняется")
            assertEquals(listOf(PinError.PHOTO_REQUIRED), invalid.errors, "Категория $category")
        }
    }

    @Test
    fun pinWithPhotoIsCreatedWithoutAnySpecies() {
        for (category in photoCategories) {
            val created = assertIs<CreatePinResult.Created>(Pin.create(pinDraft(category = category)))
            assertEquals(category, created.pin.category)
            assertEquals(PhotoRef("file:///photo.jpg"), created.pin.photo)
        }
    }

    @Test
    fun placeIsSavedWithoutSpeciesAndPhoto() {
        val created = assertIs<CreatePinResult.Created>(Pin.create(pinDraft(category = FindingCategory.PLACE)))
        assertNull(created.pin.photo)
        assertTrue(created.pin.isVisible)
    }

    @Test
    fun noteCanBeAppendedButCategoryPhotoAndCoordinatesStay() {
        val original = pin(note = "у реки")

        val updated = original.appendNote("  ещё три рядом  ")

        assertEquals("у реки\nещё три рядом", updated.note)
        assertEquals(original.id, updated.id)
        assertEquals(original.category, updated.category)
        assertEquals(original.photo, updated.photo)
        assertEquals(original.coordinates, updated.coordinates)
        assertEquals(original.foundAt, updated.foundAt)
        assertEquals("у реки", original.note, "Исходная метка не меняется")
    }

    @Test
    fun noteIsNotPrefixedWhenEmpty() {
        assertEquals("первая", pin(note = "").appendNote("первая").note)
    }

    @Test
    fun blankAdditionChangesNothing() {
        val original = pin(note = "у реки")
        assertSame(original, original.appendNote("   "))
    }

    @Test
    fun deletionRemovesThePinButKeepsTheData() {
        val original = pin(note = "у реки")

        val deleted = original.markDeleted()

        assertTrue(original.isVisible)
        assertFalse(deleted.isVisible)
        assertTrue(deleted.isDeleted)
        assertEquals(original.id, deleted.id)
        assertEquals(original.photo, deleted.photo)
        assertEquals(original.note, deleted.note)
        assertSame(deleted, deleted.markDeleted(), "Повторное удаление ничего не меняет")
    }

    @Test
    fun deletedPinStaysDeletedAfterAppendNote() {
        assertTrue(pin().markDeleted().appendNote("поздно").isDeleted)
    }

    @Test
    fun restoreKeepsDeletedFlagAndRejectsCorruptRecords() {
        val restored = Pin.restore(pinDraft(), isDeleted = true)
        assertTrue(restored.isDeleted)

        assertFailsWith<IllegalStateException> {
            Pin.restore(pinDraft(category = FindingCategory.MUSHROOM, photo = null), isDeleted = false)
        }
    }

    @Test
    fun findingRulesAreUntouchedByPins() {
        // Finding по-прежнему требует вид для четырёх категорий: метка без вида — отдельный тип.
        val result = Finding.create(draft(category = FindingCategory.MUSHROOM, species = null))
        assertEquals(
            listOf(FindingError.SPECIES_REQUIRED),
            assertIs<CreateFindingResult.Invalid>(result).errors,
        )
    }
}

internal fun pinDraft(
    id: String = "p-1",
    category: FindingCategory = FindingCategory.MUSHROOM,
    photo: PhotoRef? = if (category.requiresPhoto) PhotoRef("file:///photo.jpg") else null,
    note: String = "",
    foundAt: Long = 1_000L,
    owner: UserId = LOCAL_OWNER,
    coordinates: Coordinates = Coordinates(55.75, 37.62),
) = PinDraft(
    id = FindingId(id),
    userId = owner,
    category = category,
    coordinates = coordinates,
    foundAt = Timestamp(foundAt),
    photo = photo,
    note = note,
)

internal fun pin(
    id: String = "p-1",
    category: FindingCategory = FindingCategory.MUSHROOM,
    photo: PhotoRef? = if (category.requiresPhoto) PhotoRef("file:///photo.jpg") else null,
    note: String = "",
    foundAt: Long = 1_000L,
    owner: UserId = LOCAL_OWNER,
    coordinates: Coordinates = Coordinates(55.75, 37.62),
): Pin = when (val result = Pin.create(pinDraft(id, category, photo, note, foundAt, owner, coordinates))) {
    is CreatePinResult.Created -> result.pin
    is CreatePinResult.Invalid -> fail("Ожидалась валидная метка, ошибки: ${result.errors}")
}
