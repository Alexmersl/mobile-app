package app.fieldlog.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncEntryTest {
    @Test
    fun localFindingIsNotPurgedUntilServerConfirms() {
        val deleted = SyncEntry.pending(finding()).markDeleted()

        assertFalse(deleted.isPinVisible, "Пин снят сразу")
        assertFalse(deleted.mayPurgeLocalCopy, "Запись ещё не принята сервером")

        for (response in listOf(ServerResponse.REJECTED, ServerResponse.NO_RESPONSE)) {
            val afterFailure = deleted.onServerResponse(response)
            assertEquals(SyncState.PENDING, afterFailure.state, "Ответ $response приём не подтверждает")
            assertFalse(afterFailure.mayPurgeLocalCopy)
        }

        assertTrue(deleted.onServerResponse(ServerResponse.ACCEPTED).mayPurgeLocalCopy)
    }

    @Test
    fun acceptedButNotDeletedEntryIsNotPurged() {
        val accepted = SyncEntry.pending(finding()).onServerResponse(ServerResponse.ACCEPTED)

        assertEquals(SyncState.ACCEPTED, accepted.state)
        assertTrue(accepted.isPinVisible)
        assertFalse(accepted.mayPurgeLocalCopy)
    }

    @Test
    fun newLocalFindingWaitsForServer() {
        assertEquals(SyncState.PENDING, SyncEntry.pending(finding()).state)
    }
}
