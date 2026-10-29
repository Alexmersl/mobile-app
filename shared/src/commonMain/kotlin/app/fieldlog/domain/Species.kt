package app.fieldlog.domain

/** Ссылка на вид: вид встроенного словаря или свой вид пользователя. */
sealed interface SpeciesRef {
    val id: String

    data class Dictionary(override val id: String) : SpeciesRef {
        init {
            require(id.isNotBlank()) { "Id вида словаря не может быть пустым" }
        }
    }

    data class Custom(override val id: String) : SpeciesRef {
        init {
            require(id.isNotBlank()) { "Id своего вида не может быть пустым" }
        }
    }
}

/**
 * Статус вида из карточки словаря. Условно съедобные и спорные виды записываются как [UNKNOWN]:
 * третьего значения у типа нет.
 */
enum class SpeciesStatus {
    EDIBLE,
    POISONOUS,
    UNKNOWN,
}

/**
 * Карточка вида встроенного словаря: только то, что нужно вердикту.
 * Данные словаря живут не здесь (задача 4).
 *
 * [status] равен `null` у животных и мест: статуса у них нет.
 */
class SpeciesCard(
    val id: String,
    val nameRu: String,
    val nameLatin: String,
    val category: FindingCategory,
    val status: SpeciesStatus?,
) {
    init {
        require(id.isNotBlank()) { "Id карточки не может быть пустым" }
        require(category.hasSpecies) { "У места нет карточки вида" }
        require(status == null || category.allowsStatus) { "У категории $category статуса нет" }
    }

    override fun equals(other: Any?): Boolean =
        other is SpeciesCard &&
            id == other.id &&
            nameRu == other.nameRu &&
            nameLatin == other.nameLatin &&
            category == other.category &&
            status == other.status

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + nameRu.hashCode()
        result = 31 * result + nameLatin.hashCode()
        result = 31 * result + category.hashCode()
        result = 31 * result + (status?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "SpeciesCard(id=$id, category=$category, status=$status)"
}

/** Статус бывает только у гриба, ягоды и растения. */
private val FindingCategory.allowsStatus: Boolean
    get() = this == FindingCategory.MUSHROOM || this == FindingCategory.BERRY || this == FindingCategory.PLANT

/** Встроенный словарь как источник карточек. Реализации — в `data`, здесь только контракт. */
fun interface SpeciesDictionary {
    fun find(id: String): SpeciesCard?
}
