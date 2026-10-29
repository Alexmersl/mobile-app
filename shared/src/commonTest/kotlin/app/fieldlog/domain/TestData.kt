package app.fieldlog.domain

import kotlin.test.fail

/** Тестовые карточки. Это не данные словаря (задача 4), а фикстуры для проверки правил. */
internal val testUser = UserId("user-1")

internal val boletus = SpeciesCard("boletus", "Белый гриб", "Boletus edulis", FindingCategory.MUSHROOM, SpeciesStatus.EDIBLE)
internal val amanita = SpeciesCard("amanita", "Мухомор красный", "Amanita muscaria", FindingCategory.MUSHROOM, SpeciesStatus.POISONOUS)

/** Условно съедобный гриб записан в словаре как «неизвестно». */
internal val morel = SpeciesCard("morel", "Сморчок", "Morchella esculenta", FindingCategory.MUSHROOM, SpeciesStatus.UNKNOWN)
internal val blueberry = SpeciesCard("blueberry", "Черника", "Vaccinium myrtillus", FindingCategory.BERRY, SpeciesStatus.EDIBLE)
internal val fern = SpeciesCard("fern", "Папоротник орляк", "Pteridium aquilinum", FindingCategory.PLANT, SpeciesStatus.UNKNOWN)
internal val moose = SpeciesCard("moose", "Лось", "Alces alces", FindingCategory.ANIMAL, null)

internal val testDictionary: SpeciesDictionary = listOf(boletus, amanita, morel, blueberry, fern, moose)
    .associateBy { it.id }
    .let { cards -> SpeciesDictionary { id -> cards[id] } }

internal fun draft(
    id: String = "f-1",
    category: FindingCategory = FindingCategory.MUSHROOM,
    species: SpeciesRef? = if (category.hasSpecies) SpeciesRef.Dictionary("boletus") else null,
    photo: PhotoRef? = if (category.requiresPhoto) PhotoRef("file:///photo.jpg") else null,
    note: String = "",
    foundAt: Long = 1_000L,
    user: UserId = testUser,
) = FindingDraft(
    id = FindingId(id),
    userId = user,
    category = category,
    species = species,
    coordinates = Coordinates(55.75, 37.62),
    foundAt = Timestamp(foundAt),
    photo = photo,
    note = note,
)

internal fun finding(
    id: String = "f-1",
    category: FindingCategory = FindingCategory.MUSHROOM,
    species: SpeciesRef? = if (category.hasSpecies) SpeciesRef.Dictionary("boletus") else null,
    foundAt: Long = 1_000L,
): Finding = when (val result = Finding.create(draft(id = id, category = category, species = species, foundAt = foundAt))) {
    is CreateFindingResult.Created -> result.finding
    is CreateFindingResult.Invalid -> fail("Ожидалась валидная находка, ошибки: ${result.errors}")
}
