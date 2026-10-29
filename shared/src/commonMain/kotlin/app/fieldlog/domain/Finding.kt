package app.fieldlog.domain

import kotlin.jvm.JvmInline

@JvmInline
value class FindingId(val value: String) {
    init {
        require(value.isNotBlank()) { "Id находки не может быть пустым" }
    }
}

@JvmInline
value class UserId(val value: String) {
    init {
        require(value.isNotBlank()) { "Id пользователя не может быть пустым" }
    }
}

/** Момент времени в миллисекундах от эпохи Unix (без внешних библиотек времени). */
@JvmInline
value class Timestamp(val epochMillis: Long)

/** Ссылка на фото (путь или URI). Одно фото на находку. */
@JvmInline
value class PhotoRef(val uri: String) {
    init {
        require(uri.isNotBlank()) { "Ссылка на фото не может быть пустой" }
    }
}

class Coordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "Широта вне диапазона: $latitude" }
        require(longitude in -180.0..180.0) { "Долгота вне диапазона: $longitude" }
    }

    override fun equals(other: Any?): Boolean =
        other is Coordinates && latitude == other.latitude && longitude == other.longitude

    override fun hashCode(): Int = 31 * latitude.hashCode() + longitude.hashCode()

    override fun toString(): String = "Coordinates($latitude, $longitude)"
}

/** Что пользователь собирается сохранить. Проверяется в [Finding.create]. */
class FindingDraft(
    val id: FindingId,
    val userId: UserId,
    val category: FindingCategory,
    val species: SpeciesRef?,
    val coordinates: Coordinates,
    val foundAt: Timestamp,
    val photo: PhotoRef? = null,
    val note: String = "",
)

enum class FindingError {
    /** Для гриба, ягоды, растения и животного фото обязательно. */
    PHOTO_REQUIRED,

    /** Для гриба, ягоды, растения и животного вид должен быть подтверждён. */
    SPECIES_REQUIRED,

    /** У места вида нет. */
    SPECIES_NOT_ALLOWED_FOR_PLACE,
}

sealed interface CreateFindingResult {
    class Created(val finding: Finding) : CreateFindingResult
    class Invalid(val errors: List<FindingError>) : CreateFindingResult
}

/**
 * Сохранённая находка. Вид и фото после создания не меняются: у класса нет ни `copy`,
 * ни способа их заменить. Заметку можно только дописать ([appendNote]).
 */
class Finding private constructor(
    val id: FindingId,
    val userId: UserId,
    val category: FindingCategory,
    val species: SpeciesRef?,
    val coordinates: Coordinates,
    val foundAt: Timestamp,
    val photo: PhotoRef?,
    val note: String,
) {
    /** Новая находка с дописанной заметкой. Пустой текст ничего не меняет. */
    fun appendNote(addition: String): Finding {
        val text = addition.trim()
        if (text.isEmpty()) return this
        val newNote = if (note.isBlank()) text else note + "\n" + text
        return Finding(id, userId, category, species, coordinates, foundAt, photo, newNote)
    }

    override fun equals(other: Any?): Boolean =
        other is Finding &&
            id == other.id &&
            userId == other.userId &&
            category == other.category &&
            species == other.species &&
            coordinates == other.coordinates &&
            foundAt == other.foundAt &&
            photo == other.photo &&
            note == other.note

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Finding(id=${id.value}, category=$category, species=$species)"

    companion object {
        /** Создаёт находку или возвращает все найденные нарушения правил. */
        fun create(draft: FindingDraft): CreateFindingResult {
            val errors = buildList {
                if (draft.category.requiresPhoto && draft.photo == null) add(FindingError.PHOTO_REQUIRED)
                if (draft.category.hasSpecies && draft.species == null) add(FindingError.SPECIES_REQUIRED)
                if (!draft.category.hasSpecies && draft.species != null) {
                    add(FindingError.SPECIES_NOT_ALLOWED_FOR_PLACE)
                }
            }
            if (errors.isNotEmpty()) return CreateFindingResult.Invalid(errors)
            return CreateFindingResult.Created(
                Finding(
                    id = draft.id,
                    userId = draft.userId,
                    category = draft.category,
                    species = draft.species,
                    coordinates = draft.coordinates,
                    foundAt = draft.foundAt,
                    photo = draft.photo,
                    note = draft.note,
                ),
            )
        }
    }
}
