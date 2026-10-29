package app.fieldlog.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AchievementTest {
    private val empty = FieldLog.empty(testUser)

    @Test
    fun firstCatchOfSpeciesUnlocksAchievementAndRepeatDoesNot() {
        val first = empty.save(finding(id = "f-1"))
        val unlocked = assertNotNull(first.unlocked)
        assertEquals(AchievementId.of(SpeciesRef.Dictionary("boletus")), unlocked.id)

        val repeat = first.log.save(finding(id = "f-2", foundAt = 2_000L))

        assertNull(repeat.unlocked, "Повтор ачивку не выдаёт")
        assertEquals(2, repeat.log.pins.size, "Повтор ставит новый пин")
        assertEquals(1, repeat.log.achievements.size)
        assertEquals(1_000L, repeat.log.achievements.single().unlockedAt.epochMillis)
    }

    @Test
    fun customSpeciesUnlocksItsOwnAchievement() {
        val custom = SpeciesRef.Custom("my-mushroom")
        val result = empty.save(finding(species = custom))

        assertEquals(custom, assertNotNull(result.unlocked).species)
    }

    @Test
    fun achievementIdIsDeterministicPerSpeciesAndDiffersBetweenSpecies() {
        val boletusRef = SpeciesRef.Dictionary("boletus")
        assertEquals(AchievementId.of(boletusRef), AchievementId.of(SpeciesRef.Dictionary("boletus")))
        assertNotEquals(AchievementId.of(boletusRef), AchievementId.of(SpeciesRef.Dictionary("amanita")))
        assertNotEquals(
            AchievementId.of(SpeciesRef.Dictionary("x")),
            AchievementId.of(SpeciesRef.Custom("x")),
            "Свой вид и вид словаря с одинаковым id — разные ачивки",
        )
    }

    @Test
    fun deletingFindingRemovesPinButKeepsAchievement() {
        val saved = empty.save(finding(id = "f-1")).log

        val afterDelete = saved.delete(FindingId("f-1"))

        assertTrue(afterDelete.pins.isEmpty(), "Пин снят")
        assertEquals(1, afterDelete.achievements.size, "Ачивка осталась")
        assertTrue(afterDelete.hasAchievement(SpeciesRef.Dictionary("boletus")))

        val recatch = afterDelete.save(finding(id = "f-2"))
        assertNull(recatch.unlocked, "После удаления и новой поимки ачивка не выдаётся повторно")
    }

    @Test
    fun placeNeverUnlocksAchievement() {
        val result = empty.save(finding(category = FindingCategory.PLACE))

        assertNull(result.unlocked)
        assertTrue(result.log.achievements.isEmpty())
        assertEquals(1, result.log.pins.size, "Место при этом сохраняется как пин")
    }

    @Test
    fun achievementTypeFollowsCategory() {
        assertEquals(AchievementType.COLLECTED, FindingCategory.MUSHROOM.achievementType)
        assertEquals(AchievementType.COLLECTED, FindingCategory.BERRY.achievementType)
        assertEquals(AchievementType.SEEN, FindingCategory.PLANT.achievementType)
        assertEquals(AchievementType.ENCOUNTERED, FindingCategory.ANIMAL.achievementType)
        assertNull(FindingCategory.PLACE.achievementType)

        val seen = empty.save(finding(category = FindingCategory.PLANT, species = SpeciesRef.Dictionary("fern")))
        assertEquals(AchievementType.SEEN, assertNotNull(seen.unlocked).type)
    }
}
