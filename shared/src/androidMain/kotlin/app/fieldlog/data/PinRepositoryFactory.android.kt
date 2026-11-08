package app.fieldlog.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.fieldlog.data.db.PinDatabase

/**
 * Репозиторий меток в файле БД приложения. `Context` передаёт `androidApp` (Application),
 * общий код его не видит. Файл создаётся и мигрируется при первом обращении к базе.
 */
fun createPinRepository(context: Context): PinRepository {
    val driver = AndroidSqliteDriver(PinDatabase.Schema, context.applicationContext, PIN_DATABASE_FILE)
    return SqlDelightPinRepository(PinDatabase(driver))
}
