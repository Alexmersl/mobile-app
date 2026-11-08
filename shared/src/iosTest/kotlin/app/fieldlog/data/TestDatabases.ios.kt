@file:OptIn(ExperimentalForeignApi::class)

package app.fieldlog.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.fieldlog.data.db.PinDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID

internal actual fun createInMemoryDriver(): SqlDriver = NativeSqliteDriver(
    schema = PinDatabase.Schema,
    name = "memory-${NSUUID().UUIDString}",
    onConfiguration = { it.copy(inMemory = true) },
)

internal actual fun createTestDatabaseFile(): TestDatabaseFile = NativeTestDatabaseFile()

private class NativeTestDatabaseFile : TestDatabaseFile {
    private val directory = NSTemporaryDirectory().trimEnd('/') + "/fieldlog-test-" + NSUUID().UUIDString

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    }

    override fun open(): SqlDriver = NativeSqliteDriver(
        schema = PinDatabase.Schema,
        name = PIN_DATABASE_FILE,
        onConfiguration = { it.copy(extendedConfig = it.extendedConfig.copy(basePath = directory)) },
    )

    override fun delete() {
        NSFileManager.defaultManager.removeItemAtPath(directory, error = null)
    }
}
