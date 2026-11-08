package app.fieldlog.data

import app.fieldlog.domain.FindingId
import app.fieldlog.domain.Pin
import app.fieldlog.domain.UserId
import kotlinx.coroutines.flow.Flow

enum class SavePinResult {
    SAVED,

    /** Метка с таким id уже есть. Она не перезаписывается: фото и категория не меняются. */
    ALREADY_EXISTS,
}

/**
 * Локальное хранилище меток итерации 1. Работает без сети. Правила «можно / нельзя» живут в
 * `domain` ([Pin]); репозиторий их не принимает, а только хранит уже проверенные метки.
 *
 * Нет методов, которые меняют фото, категорию, координаты или время сохранённой метки:
 * менять можно только заметку (дописать) и признак удаления.
 */
interface PinRepository {
    /** Сохраняет новую метку. Существующий id не перезаписывает. */
    suspend fun save(pin: Pin): SavePinResult

    /** Видимые метки владельца, новые сверху. Список обновляется при любом изменении. */
    fun observePins(owner: UserId): Flow<List<Pin>>

    /** Видимые метки владельца, новые сверху. */
    suspend fun getPins(owner: UserId): List<Pin>

    /** Метка по id; `null`, если её нет или она удалена. */
    suspend fun getPin(id: FindingId): Pin?

    /** Дописывает заметку и возвращает метку; `null`, если метки нет или она удалена. */
    suspend fun appendNote(id: FindingId, addition: String): Pin?

    /** Мягко удаляет метку: пин снят, строка остаётся. `false`, если метки нет или она уже удалена. */
    suspend fun delete(id: FindingId): Boolean
}
