package app.fieldlog.data

import app.cash.sqldelight.db.SqlDriver

/** Новая база в памяти (одна на вызов). Реальный SQLite платформы: JVM-sqlite или нативный. */
internal expect fun createInMemoryDriver(): SqlDriver

/**
 * Файловая база для проверки «перезапуска»: [open] можно вызвать снова после `driver.close()`,
 * и откроется тот же файл. Схема создаётся при первом открытии, как в приложении.
 */
internal interface TestDatabaseFile {
    fun open(): SqlDriver

    /** Стирает файл (и журнал) базы. */
    fun delete()
}

internal expect fun createTestDatabaseFile(): TestDatabaseFile
