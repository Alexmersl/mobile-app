package app.fieldlog.data

import app.cash.sqldelight.db.SqlDriver
import app.fieldlog.data.db.PinDatabase
import app.fieldlog.domain.Coordinates
import app.fieldlog.domain.FindingCategory
import app.fieldlog.domain.FindingId
import app.fieldlog.domain.LOCAL_OWNER
import app.fieldlog.domain.PhotoRef
import app.fieldlog.domain.Pin
import app.fieldlog.domain.UserId
import app.fieldlog.domain.pin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Репозиторий меток на реальном SQLite платформы (JVM-sqlite для androidHostTest, нативный
 * sqlite для iOS). Сети в слое данных нет: всё, что здесь проверяется, работает офлайн.
 */
class SqlDelightPinRepositoryTest {
    private val drivers = mutableListOf<SqlDriver>()
    private val files = mutableListOf<TestDatabaseFile>()

    @AfterTest
    fun tearDown() {
        drivers.forEach { runCatching { it.close() } }
        files.forEach { it.delete() }
    }

    private fun inMemory(): SqlDelightPinRepository = repositoryOn(createInMemoryDriver())

    private fun repositoryOn(driver: SqlDriver): SqlDelightPinRepository {
        drivers += driver
        return SqlDelightPinRepository(PinDatabase(driver))
    }

    @Test
    fun savedPinIsReadBackWithEveryField() = runTest {
        val repository = inMemory()
        val saved = pin(
            id = "p-1",
            category = FindingCategory.BERRY,
            photo = PhotoRef("file:///data/photo-1.jpg"),
            note = "много, у ручья",
            foundAt = 1_730_000_000_123L,
            coordinates = Coordinates(-33.8567844, 151.213108),
        )

        assertEquals(SavePinResult.SAVED, repository.save(saved))

        assertEquals(saved, repository.getPin(saved.id))
        assertEquals(listOf(saved), repository.getPins(LOCAL_OWNER))
    }

    @Test
    fun everyCategoryRoundTripsAndPlaceNeedsNoPhoto() = runTest {
        val repository = inMemory()
        val all = FindingCategory.entries.mapIndexed { index, category ->
            pin(id = "p-$index", category = category, foundAt = index.toLong())
        }
        all.forEach { assertEquals(SavePinResult.SAVED, repository.save(it)) }

        assertEquals(all.reversed(), repository.getPins(LOCAL_OWNER), "Новые сверху")
        assertNull(repository.getPin(FindingId("p-4"))!!.photo, "Место без фото")
    }

    @Test
    fun pinsOfAnotherOwnerAreNotListed() = runTest {
        val repository = inMemory()
        val other = UserId("account-1")
        repository.save(pin(id = "mine"))
        repository.save(pin(id = "theirs", owner = other))

        assertEquals(listOf(FindingId("mine")), repository.getPins(LOCAL_OWNER).map { it.id })
        assertEquals(listOf(FindingId("theirs")), repository.getPins(other).map { it.id })
    }

    @Test
    fun unknownPinIsNull() = runTest {
        assertNull(inMemory().getPin(FindingId("nope")))
    }

    @Test
    fun pinSurvivesRestart() = runTest {
        val file = createTestDatabaseFile().also { files += it }
        val firstRun = file.open()
        val saved = pin(id = "p-1", note = "у реки", foundAt = 42L)
        val removed = pin(id = "p-2", category = FindingCategory.PLACE, foundAt = 43L)
        SqlDelightPinRepository(PinDatabase(firstRun)).apply {
            save(saved)
            save(removed)
            appendNote(saved.id, "ещё три рядом")
            delete(removed.id)
        }
        firstRun.close() // «перезапуск»: процесс закрыл базу

        val secondRun = file.open().also { drivers += it }
        val repository = SqlDelightPinRepository(PinDatabase(secondRun))

        val restored = repository.getPins(LOCAL_OWNER)
        assertEquals(listOf(saved.appendNote("ещё три рядом")), restored)
        assertNull(repository.getPin(removed.id), "Удаление тоже пережило перезапуск")
        assertNotNull(repository.rawRow(removed.id), "Строка удалённой метки осталась")
    }

    @Test
    fun noteIsAppendedAndStored() = runTest {
        val repository = inMemory()
        val saved = pin(note = "у реки")
        repository.save(saved)

        val updated = repository.appendNote(saved.id, "  ещё три рядом ")

        assertEquals("у реки\nещё три рядом", updated!!.note)
        assertEquals("у реки\nещё три рядом", repository.getPin(saved.id)!!.note)

        repository.appendNote(saved.id, "и пятая")
        assertEquals("у реки\nещё три рядом\nи пятая", repository.getPin(saved.id)!!.note)
    }

    @Test
    fun blankNoteChangesNothingAndUnknownPinGivesNull() = runTest {
        val repository = inMemory()
        val saved = pin(note = "у реки")
        repository.save(saved)

        assertEquals(saved, repository.appendNote(saved.id, "   "))
        assertEquals(saved, repository.getPin(saved.id))
        assertNull(repository.appendNote(FindingId("nope"), "текст"))
    }

    @Test
    fun softDeletedPinLeavesTheListButTheRowStays() = runTest {
        val repository = inMemory()
        val keep = pin(id = "keep", foundAt = 1L)
        val gone = pin(id = "gone", note = "у реки", foundAt = 2L)
        repository.save(keep)
        repository.save(gone)

        assertTrue(repository.delete(gone.id))

        assertEquals(listOf(keep), repository.getPins(LOCAL_OWNER))
        assertNull(repository.getPin(gone.id))
        val row = assertNotNull(repository.rawRow(gone.id), "Запись остаётся для очереди синхронизации")
        assertEquals(1L, row.deleted)
        assertEquals(gone.photo!!.uri, row.photo, "Данные удалённой метки целы")
        assertEquals("у реки", row.note)
    }

    @Test
    fun deleteIsFalseForUnknownOrAlreadyDeletedPin() = runTest {
        val repository = inMemory()
        val saved = pin()
        repository.save(saved)

        assertFalse(repository.delete(FindingId("nope")))
        assertTrue(repository.delete(saved.id))
        assertFalse(repository.delete(saved.id))
    }

    @Test
    fun noteCannotBeAppendedToDeletedPin() = runTest {
        val repository = inMemory()
        val saved = pin(note = "у реки")
        repository.save(saved)
        repository.delete(saved.id)

        assertNull(repository.appendNote(saved.id, "поздно"))
        assertEquals("у реки", repository.rawRow(saved.id)!!.note)
    }

    @Test
    fun savingExistingIdDoesNotOverwritePhotoCategoryOrCoordinates() = runTest {
        val repository = inMemory()
        val original = pin(
            id = "p-1",
            category = FindingCategory.MUSHROOM,
            photo = PhotoRef("file:///original.jpg"),
            coordinates = Coordinates(10.0, 20.0),
        )
        repository.save(original)

        val result = repository.save(
            pin(
                id = "p-1",
                category = FindingCategory.PLANT,
                photo = PhotoRef("file:///other.jpg"),
                coordinates = Coordinates(11.0, 21.0),
            ),
        )

        assertEquals(SavePinResult.ALREADY_EXISTS, result)
        assertEquals(original, repository.getPin(original.id))
    }

    @Test
    fun appendNoteAndDeleteDoNotTouchPhotoCategoryOrCoordinates() = runTest {
        val repository = inMemory()
        val original = pin(
            category = FindingCategory.ANIMAL,
            photo = PhotoRef("file:///fox.jpg"),
            coordinates = Coordinates(10.5, 20.5),
            foundAt = 77L,
        )
        repository.save(original)

        repository.appendNote(original.id, "рыжая")
        repository.delete(original.id)

        val row = repository.rawRow(original.id)!!
        assertEquals("animal", row.category)
        assertEquals("file:///fox.jpg", row.photo)
        assertEquals(10.5, row.latitude)
        assertEquals(20.5, row.longitude)
        assertEquals(77L, row.found_at)
        assertEquals(LOCAL_OWNER.value, row.owner_id)
    }

    @Test
    fun savingAlreadyDeletedPinKeepsItOffTheList() = runTest {
        val repository = inMemory()
        val tombstone: Pin = pin(id = "t-1").markDeleted()

        assertEquals(SavePinResult.SAVED, repository.save(tombstone))

        assertTrue(repository.getPins(LOCAL_OWNER).isEmpty())
        assertEquals(1L, repository.rawRow(tombstone.id)!!.deleted)
    }

    @Test
    fun databaseRejectsPhotolessMushroom() = runTest {
        val database = PinDatabase(createInMemoryDriver().also { drivers += it })
        val failure = runCatching {
            database.pinQueries.insert("bad", "local", "mushroom", 1.0, 2.0, 3L, null, "", 0L)
        }
        assertTrue(failure.isFailure, "CHECK в схеме не пускает гриб без фото, даже мимо domain")
    }

    @Test
    fun observedListFollowsSaveNoteAndDelete() = runTest {
        val repository = inMemory()
        val saved = pin(id = "p-1")

        // Реальные потоки и потоки базы: виртуальное время runTest тут не годится.
        withContext(Dispatchers.Default) {
            val seen = async {
                withTimeout(10_000) { repository.observePins(LOCAL_OWNER).first { list -> list.any { it.id == saved.id } } }
            }
            repository.save(saved)
            assertEquals(listOf(saved), seen.await())

            val noted = async {
                withTimeout(10_000) { repository.observePins(LOCAL_OWNER).first { list -> list.singleOrNull()?.note == "дописано" } }
            }
            repository.appendNote(saved.id, "дописано")
            assertEquals("дописано", noted.await().single().note)

            val emptied = async {
                withTimeout(10_000) { repository.observePins(LOCAL_OWNER).first { it.isEmpty() } }
            }
            repository.delete(saved.id)
            assertTrue(emptied.await().isEmpty())
        }
    }
}
