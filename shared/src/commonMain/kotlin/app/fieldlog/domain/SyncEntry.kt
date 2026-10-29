package app.fieldlog.domain

enum class SyncState {
    /** Сервер приём ещё не подтвердил. */
    PENDING,

    /** Сервер подтвердил приём. */
    ACCEPTED,
}

/** Что пришло от сервера в ответ на отправку записи. */
enum class ServerResponse {
    ACCEPTED,
    REJECTED,

    /** Нет сети, таймаут, ошибка сервера: приём не подтверждён. */
    NO_RESPONSE,
}

/**
 * Состояние синхронизации одной находки (чистая логика, без хранилища и сети).
 *
 * Правило: локальная находка не удаляется, пока сервер не подтвердил приём.
 * Удаление пользователем ([markDeleted]) лишь убирает пин; запись физически можно стереть
 * только после подтверждения ([mayPurgeLocalCopy]).
 */
class SyncEntry private constructor(
    val finding: Finding,
    val state: SyncState,
    val deletedByUser: Boolean,
) {
    /** Пин виден на карте. */
    val isPinVisible: Boolean get() = !deletedByUser

    /** Можно ли стереть локальную копию: только после подтверждения сервером. */
    val mayPurgeLocalCopy: Boolean get() = state == SyncState.ACCEPTED && deletedByUser

    fun markDeleted(): SyncEntry = SyncEntry(finding, state, deletedByUser = true)

    fun onServerResponse(response: ServerResponse): SyncEntry = when (response) {
        ServerResponse.ACCEPTED -> SyncEntry(finding, SyncState.ACCEPTED, deletedByUser)
        ServerResponse.REJECTED, ServerResponse.NO_RESPONSE -> this
    }

    companion object {
        /** Новая локальная находка сразу ждёт отправки. */
        fun pending(finding: Finding): SyncEntry = SyncEntry(finding, SyncState.PENDING, deletedByUser = false)

        fun restore(finding: Finding, state: SyncState, deletedByUser: Boolean): SyncEntry =
            SyncEntry(finding, state, deletedByUser)
    }
}
