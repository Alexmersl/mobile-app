package app.fieldlog.domain

/** Категория находки. Набор фиксирован планом. */
enum class FindingCategory {
    MUSHROOM,
    BERRY,
    PLANT,
    ANIMAL,
    PLACE,
    ;

    /** Фото обязательно для всех категорий, кроме места. */
    val requiresPhoto: Boolean get() = this != PLACE

    /** У места нет вида: оно не входит в словарь и не даёт ачивку. */
    val hasSpecies: Boolean get() = this != PLACE

    /** Тип ачивки категории; `null` у места: место ачивку не даёт. */
    val achievementType: AchievementType?
        get() = when (this) {
            MUSHROOM, BERRY -> AchievementType.COLLECTED
            PLANT -> AchievementType.SEEN
            ANIMAL -> AchievementType.ENCOUNTERED
            PLACE -> null
        }
}
