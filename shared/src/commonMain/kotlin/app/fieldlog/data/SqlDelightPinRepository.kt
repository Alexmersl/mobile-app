package app.fieldlog.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.fieldlog.data.db.PinDatabase
import app.fieldlog.domain.Coordinates
import app.fieldlog.domain.FindingCategory
import app.fieldlog.domain.FindingId
import app.fieldlog.domain.Pin
import app.fieldlog.domain.PinDraft
import app.fieldlog.domain.CreatePinResult
import app.fieldlog.domain.PhotoRef
import app.fieldlog.domain.Timestamp
import app.fieldlog.domain.UserId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import app.fieldlog.data.db.Pin as PinRow

/**
 * [PinRepository] на SQLDelight. Драйвер создаёт платформа (Android — с `Context`, iOS — свой),
 * в общем коде только SQL и эта реализация. Запросы короткие и идут на [Dispatchers.Default]:
 * `Dispatchers.IO` из общего кода недоступен на iOS-целях.
 */
class SqlDelightPinRepository(
    database: PinDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : PinRepository {
    private val queries = database.pinQueries

    override suspend fun save(pin: Pin): SavePinResult = withContext(dispatcher) {
        queries.transactionWithResult {
            if (queries.selectAnyById(pin.id.value).executeAsOneOrNull() != null) {
                return@transactionWithResult SavePinResult.ALREADY_EXISTS
            }
            queries.insert(
                id = pin.id.value,
                owner_id = pin.userId.value,
                category = pin.category.toCode(),
                latitude = pin.coordinates.latitude,
                longitude = pin.coordinates.longitude,
                found_at = pin.foundAt.epochMillis,
                photo = pin.photo?.uri,
                note = pin.note,
                deleted = if (pin.isDeleted) 1L else 0L,
            )
            SavePinResult.SAVED
        }
    }

    override fun observePins(owner: UserId): Flow<List<Pin>> =
        queries.selectVisible(owner.value)
            .asFlow()
            .mapToList(dispatcher)
            .map { rows -> rows.map { it.toPin() } }

    override suspend fun getPins(owner: UserId): List<Pin> = withContext(dispatcher) {
        queries.selectVisible(owner.value).executeAsList().map { it.toPin() }
    }

    override suspend fun getPin(id: FindingId): Pin? = withContext(dispatcher) {
        queries.selectVisibleById(id.value).executeAsOneOrNull()?.toPin()
    }

    override suspend fun appendNote(id: FindingId, addition: String): Pin? = withContext(dispatcher) {
        queries.transactionWithResult {
            val current = queries.selectVisibleById(id.value).executeAsOneOrNull()?.toPin()
                ?: return@transactionWithResult null
            val updated = current.appendNote(addition)
            if (updated.note != current.note) queries.updateNote(updated.note, id.value)
            updated
        }
    }

    override suspend fun delete(id: FindingId): Boolean = withContext(dispatcher) {
        queries.transactionWithResult {
            queries.markDeleted(id.value)
            queries.changes().executeAsOne() > 0L
        }
    }

    /** Строка вместе с удалёнными: нужна тестам и очереди синхронизации (итерация 2). */
    internal fun rawRow(id: FindingId): PinRow? = queries.selectAnyById(id.value).executeAsOneOrNull()
}

private fun PinRow.toPin(): Pin = Pin.restore(
    PinDraft(
        id = FindingId(id),
        userId = UserId(owner_id),
        category = category.toCategory(),
        coordinates = Coordinates(latitude, longitude),
        foundAt = Timestamp(found_at),
        photo = photo?.let(::PhotoRef),
        note = note,
    ),
    isDeleted = deleted != 0L,
)

// Коды в базе — стабильный контракт хранилища: переименование enum в Kotlin данные не ломает.
private fun FindingCategory.toCode(): String = when (this) {
    FindingCategory.MUSHROOM -> "mushroom"
    FindingCategory.BERRY -> "berry"
    FindingCategory.PLANT -> "plant"
    FindingCategory.ANIMAL -> "animal"
    FindingCategory.PLACE -> "place"
}

private fun String.toCategory(): FindingCategory = when (this) {
    "mushroom" -> FindingCategory.MUSHROOM
    "berry" -> FindingCategory.BERRY
    "plant" -> FindingCategory.PLANT
    "animal" -> FindingCategory.ANIMAL
    "place" -> FindingCategory.PLACE
    else -> error("Неизвестная категория в хранилище: $this")
}
