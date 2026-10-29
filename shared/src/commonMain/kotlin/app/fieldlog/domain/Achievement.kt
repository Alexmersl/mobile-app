package app.fieldlog.domain

import kotlin.jvm.JvmInline

/** «Собрано» — гриб и ягода, «увидено» — растение, «встречено» — животное. */
enum class AchievementType {
    COLLECTED,
    SEEN,
    ENCOUNTERED,
}

/** Стабильный id ачивки: целиком определяется видом, не зависит от находки и времени. */
@JvmInline
value class AchievementId(val value: String) {
    companion object {
        fun of(species: SpeciesRef): AchievementId = when (species) {
            is SpeciesRef.Dictionary -> AchievementId("achievement:dictionary:${species.id}")
            is SpeciesRef.Custom -> AchievementId("achievement:custom:${species.id}")
        }
    }
}

class Achievement(
    val id: AchievementId,
    val species: SpeciesRef,
    val type: AchievementType,
    val unlockedAt: Timestamp,
    /** Находка, первой открывшая ачивку. После удаления находки ссылка остаётся как есть. */
    val firstFindingId: FindingId,
) {
    override fun equals(other: Any?): Boolean = other is Achievement && id == other.id

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String = "Achievement(${id.value}, $type)"
}

class SaveResult(
    val log: FieldLog,
    /** Ачивка, открытая именно этим сохранением; `null`, если открывать нечего. */
    val unlocked: Achievement?,
)

/**
 * Находки и ачивки одного пользователя (чистое состояние, без хранилища).
 * Пины — это находки; удаление находки убирает пин, но не ачивку.
 */
class FieldLog private constructor(
    val userId: UserId,
    findings: List<Finding>,
    achievements: Map<AchievementId, Achievement>,
) {
    private val findingList: List<Finding> = findings.toList()
    private val achievementMap: Map<AchievementId, Achievement> = achievements.toMap()

    /** Пины на карте. */
    val pins: List<Finding> get() = findingList

    val achievements: List<Achievement> get() = achievementMap.values.toList()

    fun hasAchievement(species: SpeciesRef): Boolean = AchievementId.of(species) in achievementMap

    /**
     * Сохраняет находку: ставит пин и, если вид встречен впервые, открывает ачивку.
     * Повтор вида — новый пин без новой ачивки. Место ачивку не даёт.
     */
    fun save(finding: Finding): SaveResult {
        require(finding.userId == userId) { "Находка другого пользователя" }
        require(findingList.none { it.id == finding.id }) { "Находка ${finding.id.value} уже сохранена" }

        val species = finding.species
        val type = finding.category.achievementType
        val unlocked = if (species != null && type != null && !hasAchievement(species)) {
            Achievement(AchievementId.of(species), species, type, finding.foundAt, finding.id)
        } else {
            null
        }
        val newAchievements = if (unlocked != null) achievementMap + (unlocked.id to unlocked) else achievementMap
        return SaveResult(FieldLog(userId, findingList + finding, newAchievements), unlocked)
    }

    /** Снимает пин. Ачивки не затрагиваются. Неизвестный id ничего не меняет. */
    fun delete(id: FindingId): FieldLog =
        FieldLog(userId, findingList.filterNot { it.id == id }, achievementMap)

    companion object {
        fun empty(userId: UserId): FieldLog = FieldLog(userId, emptyList(), emptyMap())

        /** Собирает состояние из сохранённых данных (для слоя `data`). */
        fun restore(userId: UserId, findings: List<Finding>, achievements: List<Achievement>): FieldLog {
            require(findings.all { it.userId == userId }) { "Находка другого пользователя" }
            return FieldLog(userId, findings, achievements.associateBy { it.id })
        }
    }
}
