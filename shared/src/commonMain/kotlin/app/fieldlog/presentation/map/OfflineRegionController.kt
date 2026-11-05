package app.fieldlog.presentation.map

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Управляет скачиванием районов и следит за лимитами, пока скачивание идёт. Живёт столько же,
 * сколько процесс: скачивание продолжается движком и после ухода с вкладки «Карта», а
 * лимиты нужно проверять всё это время, а не только пока вкладка на экране.
 */
internal class OfflineRegionController(
    private val store: OfflineRegionStore,
    private val scope: CoroutineScope,
) {
    val state: Flow<RegionsState> = store.state

    private val noticeState = MutableStateFlow<String?>(null)

    /** Последнее сообщение для человека (лимит, ошибка операции). Сбрасывает [dismissNotice]. */
    val notice: StateFlow<String?> = noticeState

    private val watchers = mutableMapOf<Long, Job>()

    init {
        scope.launch {
            store.state.collect { state ->
                if (state !is RegionsState.Ready) return@collect
                val present = state.regions.associateBy { it.id }
                watchers.keys.filter { it !in present }.forEach { watchers.remove(it)?.cancel() }
                state.regions.forEach { region ->
                    if (region.id !in watchers) watchers[region.id] = scope.launch { watchLimits(region) }
                }
            }
        }
    }

    fun dismissNotice() {
        noticeState.value = null
    }

    /** Создаёт пакет (лимит тайлов проверен до вызова) и запускает скачивание. */
    fun download(bounds: GeoBounds, plan: ZoomPlan, pixelRatio: Float) {
        scope.launch {
            try {
                val existing = (state.first { it !is RegionsState.Loading } as? RegionsState.Ready)
                    ?.regions.orEmpty()
                    .map { decodeRegionName(it.metadata.first()) }
                val region = NewRegion(bounds, plan, pixelRatio, name = nextRegionName(existing))
                store.resume(store.create(region))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                noticeState.value = operationErrorText("начать скачивание района")
            }
        }
    }

    fun pause(region: OfflineRegion) = store.pause(region)

    fun resume(region: OfflineRegion) = store.resume(region)

    /** Повтор после ошибки: пауза и снова старт, движок докачивает недостающее. */
    fun retry(region: OfflineRegion) {
        store.pause(region)
        store.resume(region)
    }

    /** Удаляет район. Идущая загрузка перед этим останавливается. */
    fun delete(region: OfflineRegion) {
        scope.launch {
            try {
                store.pause(region)
                store.delete(region)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                noticeState.value = operationErrorText("удалить район")
            }
        }
    }

    /** Движок размер пакета не ограничивает: при превышении лимита район останавливаем и удаляем. */
    private suspend fun watchLimits(region: OfflineRegion) {
        val breach = region.progress.map { limitBreach(it) }.first { it != null } ?: return
        val name = decodeRegionName(region.metadata.first())
        noticeState.value = "«$name»: ${breach.message}"
        delete(region)
    }
}

/** Единственный контроллер процесса. Создаётся при первом открытии вкладки «Карта». */
internal object OfflineRegions {
    val controller: OfflineRegionController by lazy {
        OfflineRegionController(
            store = offlineRegionStore(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
        )
    }
}
