package app.fieldlog

import app.fieldlog.presentation.APP_TITLE
import kotlin.test.Test
import kotlin.test.assertEquals

/** Проверка каркаса: чтобы job commonTest в CI не был пустым. Логики домена здесь нет. */
class ScaffoldTest {
    @Test
    fun appTitleIsFieldLog() {
        assertEquals("FieldLog", APP_TITLE)
    }
}
