package app.fieldlog.domain

/**
 * Метка итерации 1 (docs/plan.md): находка **без вида**. Существующая [Finding] требует вид для
 * гриба, ягоды, растения и животного и остаётся как есть для итерации 2; к экранам она не
 * подключена. [Pin] — отдельный тип с теми же правилами, кроме вида: его нет вообще.
 *
 * Id и значения-обёртки общие с [Finding] ([FindingId], [UserId], [Coordinates], [PhotoRef],
 * [Timestamp]), чтобы в итерации 2 метка осталась той же записью с тем же id.
 */
class PinDraft(
    val id: FindingId,
    val userId: UserId,
    val category: FindingCategory,
    val coordinates: Coordinates,
    val foundAt: Timestamp,
    val photo: PhotoRef? = null,
    val note: String = "",
)

enum class PinError {
    /** Для гриба, ягоды, растения и животного фото обязательно. */
    PHOTO_REQUIRED,
}

sealed interface CreatePinResult {
    class Created(val pin: Pin) : CreatePinResult
    class Invalid(val errors: List<PinError>) : CreatePinResult
}

/**
 * Сохранённая метка. Категория, координаты и фото после создания не меняются: у класса нет
 * ни `copy`, ни способа их заменить. Заметку можно только дописать ([appendNote]).
 * Удаление ([markDeleted]) снимает пин с карты ([isVisible] == false); запись остаётся,
 * чтобы в итерации 2 очередь синхронизации узнала об удалении.
 */
class Pin private constructor(
    val id: FindingId,
    val userId: UserId,
    val category: FindingCategory,
    val coordinates: Coordinates,
    val foundAt: Timestamp,
    val photo: PhotoRef?,
    val note: String,
    val isDeleted: Boolean,
) {
    /** Пин виден на карте, пока его не удалили. */
    val isVisible: Boolean get() = !isDeleted

    /** Новая метка с дописанной заметкой. Пустой текст ничего не меняет. */
    fun appendNote(addition: String): Pin {
        val text = addition.trim()
        if (text.isEmpty()) return this
        val newNote = if (note.isBlank()) text else note + "\n" + text
        return Pin(id, userId, category, coordinates, foundAt, photo, newNote, isDeleted)
    }

    /** Мягко удалённая метка: пин снят, данные целы. */
    fun markDeleted(): Pin =
        if (isDeleted) this else Pin(id, userId, category, coordinates, foundAt, photo, note, isDeleted = true)

    override fun equals(other: Any?): Boolean =
        other is Pin &&
            id == other.id &&
            userId == other.userId &&
            category == other.category &&
            coordinates == other.coordinates &&
            foundAt == other.foundAt &&
            photo == other.photo &&
            note == other.note &&
            isDeleted == other.isDeleted

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Pin(id=${id.value}, category=$category, deleted=$isDeleted)"

    companion object {
        /** Создаёт метку или возвращает все найденные нарушения правил. */
        fun create(draft: PinDraft): CreatePinResult {
            val errors = validate(draft.category, draft.photo)
            if (errors.isNotEmpty()) return CreatePinResult.Invalid(errors)
            return CreatePinResult.Created(
                Pin(
                    id = draft.id,
                    userId = draft.userId,
                    category = draft.category,
                    coordinates = draft.coordinates,
                    foundAt = draft.foundAt,
                    photo = draft.photo,
                    note = draft.note,
                    isDeleted = false,
                ),
            )
        }

        /**
         * Восстанавливает метку из хранилища. Правила те же: запись, нарушающая их, — повреждённые
         * данные, а не «метка с особенностями».
         */
        fun restore(draft: PinDraft, isDeleted: Boolean): Pin {
            val errors = validate(draft.category, draft.photo)
            check(errors.isEmpty()) { "Метка ${draft.id.value} в хранилище нарушает правила: $errors" }
            return Pin(
                id = draft.id,
                userId = draft.userId,
                category = draft.category,
                coordinates = draft.coordinates,
                foundAt = draft.foundAt,
                photo = draft.photo,
                note = draft.note,
                isDeleted = isDeleted,
            )
        }

        private fun validate(category: FindingCategory, photo: PhotoRef?): List<PinError> = buildList {
            if (category.requiresPhoto && photo == null) add(PinError.PHOTO_REQUIRED)
        }
    }
}

/**
 * Локальный владелец меток итерации 1 (аккаунта ещё нет). Постоянное значение: при появлении
 * входа метки этого владельца перепривязываются к аккаунту одним запросом.
 */
val LOCAL_OWNER: UserId = UserId("local")
