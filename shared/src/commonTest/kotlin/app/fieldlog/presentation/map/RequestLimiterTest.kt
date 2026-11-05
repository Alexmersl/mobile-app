package app.fieldlog.presentation.map

import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Условие OpenFreeMap: не больше 4 одновременных запросов. Корутины идут на `Unconfined` и
 * сами управляют воротами, поэтому тест однопоточный и детерминированный.
 */
class RequestLimiterTest {
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private class Probe {
        var active = 0
        var peak = 0
        var started = 0
        val gate = CompletableDeferred<Unit>()

        suspend fun fetch(request: MapHttpRequest): MapHttpResponse {
            active++
            started++
            peak = max(peak, active)
            try {
                gate.await()
            } finally {
                active--
            }
            return MapHttpResponse(status = 200, body = ByteArray(0))
        }
    }

    private fun request(n: Int) = MapHttpRequest("https://tiles.openfreemap.org/planet/$n", emptyMap())

    @Test
    fun neverMoreThanFourRequestsAtOnce() {
        val probe = Probe()
        val limited = limitParallel(OfflineLimits.MAX_PARALLEL_REQUESTS, probe::fetch)

        val jobs = List(20) { n -> scope.launch { limited(request(n)) } }

        // Из 20 запросов идут ровно 4, остальные ждут.
        assertEquals(4, probe.active)
        assertEquals(4, probe.started)

        probe.gate.complete(Unit)

        assertTrue(jobs.all { it.isCompleted })
        assertEquals(20, probe.started)
        assertEquals(4, probe.peak)
        assertEquals(0, probe.active)
    }

    @Test
    fun waitingRequestsStartAsSoonAsPlacesFree() {
        val gates = List(6) { CompletableDeferred<Unit>() }
        var active = 0
        var peak = 0
        val limited = limitParallel(4) { request ->
            val gate = gates[request.url.substringAfterLast('/').toInt()]
            active++
            peak = max(peak, active)
            try {
                gate.await()
            } finally {
                active--
            }
            MapHttpResponse(200, ByteArray(0))
        }

        List(6) { n -> scope.launch { limited(request(n)) } }
        assertEquals(4, active)

        // Закончился один запрос — сразу стартует пятый, но не больше четырёх.
        gates[0].complete(Unit)
        assertEquals(4, active)
        gates[1].complete(Unit)
        assertEquals(4, active)
        gates[2].complete(Unit)
        // Остались запущенными 3, 4 и 5.
        assertEquals(3, active)
        assertEquals(4, peak)
    }

    @Test
    fun cancelledRequestsReleaseTheirPlace() {
        val probe = Probe()
        val limited = limitParallel(4, probe::fetch)

        val jobs = List(6) { n -> scope.launch { limited(request(n)) } }
        assertEquals(4, probe.active)

        // Отмена ожидающего запроса: место не занимает.
        jobs[5].cancel()
        assertEquals(4, probe.active)

        // Отмена выполняющегося освобождает место, его занимает ожидающий пятый.
        jobs[0].cancel()
        assertEquals(4, probe.active)
        assertEquals(5, probe.started)

        probe.gate.complete(Unit)
        assertEquals(0, probe.active)
        assertEquals(4, probe.peak)

        // Места не утекли: ворота открыты, и следующие 8 запросов проходят без ожидания.
        val more = List(8) { n -> scope.launch { limited(request(100 + n)) } }
        assertTrue(more.all { it.isCompleted })
        assertEquals(13, probe.started)
        assertEquals(0, probe.active)
    }
}
