package app.fieldlog.data

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.fieldlog.data.db.PinDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * Репозиторий меток в файле БД приложения (Application Support: не виден в «Файлах», попадает в
 * резервную копию устройства). Файл создаётся и мигрируется при первом обращении к базе.
 */
fun createPinRepository(): PinRepository {
    val driver = NativeSqliteDriver(
        schema = PinDatabase.Schema,
        name = PIN_DATABASE_FILE,
        onConfiguration = { configuration ->
            configuration.copy(extendedConfig = configuration.extendedConfig.copy(basePath = applicationSupportPath()))
        },
    )
    return SqlDelightPinRepository(PinDatabase(driver))
}

/** Папка Application Support приложения; создаётся, если её ещё нет. */
@OptIn(ExperimentalForeignApi::class)
internal fun applicationSupportPath(): String {
    val manager = NSFileManager.defaultManager
    val url: NSURL = manager.URLForDirectory(
        directory = NSApplicationSupportDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = true,
        error = null,
    ) ?: error("Нет папки Application Support")
    return requireNotNull(url.path) { "У папки Application Support нет пути" }
}
