package app.fieldlog.presentation

import kotlin.test.Test
import kotlin.test.assertEquals

class AppTabTest {
    @Test
    fun tabOrderIsMapDictionaryAchievements() {
        assertEquals(
            listOf(AppTab.Map, AppTab.Dictionary, AppTab.Achievements),
            AppTab.entries.toList(),
        )
        assertEquals(listOf("Карта", "Словарь", "Ачивки"), AppTab.entries.map { it.title })
    }

    @Test
    fun defaultTabIsMap() {
        assertEquals(AppTab.Map, AppTab.Default)
        assertEquals(0, AppTab.Default.ordinal)
    }

    @Test
    fun placeholdersAreSoonTexts() {
        assertEquals("Карта скоро", AppTab.Map.placeholder)
        assertEquals("Скоро", AppTab.Dictionary.placeholder)
        assertEquals("Скоро", AppTab.Achievements.placeholder)
    }

    @Test
    fun fromIndexRestoresEveryTab() {
        AppTab.entries.forEach { assertEquals(it, AppTab.fromIndex(it.ordinal)) }
    }

    @Test
    fun fromIndexFallsBackToDefaultWhenOutOfRange() {
        assertEquals(AppTab.Default, AppTab.fromIndex(-1))
        assertEquals(AppTab.Default, AppTab.fromIndex(AppTab.entries.size))
    }
}
