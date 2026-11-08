package app.fieldlog.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import app.fieldlog.data.db.PinDatabase
import java.io.File
import java.util.Properties

internal actual fun createInMemoryDriver(): SqlDriver =
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), PinDatabase.Schema)

internal actual fun createTestDatabaseFile(): TestDatabaseFile = JvmTestDatabaseFile()

private class JvmTestDatabaseFile : TestDatabaseFile {
    private val directory = File.createTempFile("fieldlog-test", "").apply {
        delete()
        mkdirs()
    }
    private val file = File(directory, PIN_DATABASE_FILE)

    override fun open(): SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}", Properties(), PinDatabase.Schema)

    override fun delete() {
        directory.deleteRecursively()
    }
}
