package app.fieldlog.presentation.map

import kotlinx.coroutines.flow.Flow

/**
 * Офлайн-пакеты в терминах приложения. Библиотека MapLibre Compose отдаёт `OfflineStorage`
 * только в Android- и iOS-коде (не в commonMain), поэтому тонкий слой над ней написан в
 * androidMain и iosMain, а всё остальное — общее.
 */

/** Скачиваемый или скачанный район. Равенство по [id]. */
internal class OfflineRegion(
    val id: Long,
    val bounds: GeoBounds?,
    val minZoom: Double,
    val maxZoom: Double?,
    val metadata: Flow<ByteArray?>,
    val progress: Flow<RegionProgress>,
    /** Пакет библиотеки; общему коду он не нужен. */
    internal val handle: Any,
) {
    override fun equals(other: Any?): Boolean = other is OfflineRegion && other.id == id

    override fun hashCode(): Int = id.hashCode()
}

internal sealed interface RegionsState {
    data object Loading : RegionsState

    data class Failed(val message: String) : RegionsState

    data class Ready(val regions: List<OfflineRegion>) : RegionsState
}

/** Что нужно, чтобы создать пакет. Лимиты уже проверены до вызова ([checkRegion]). */
internal class NewRegion(
    val bounds: GeoBounds,
    val plan: ZoomPlan,
    val pixelRatio: Float,
    val name: String,
)

internal interface OfflineRegionStore {
    val state: Flow<RegionsState>

    /** Создаёт пакет на паузе. Скачивание начинает [resume]. */
    suspend fun create(region: NewRegion): OfflineRegion

    fun resume(region: OfflineRegion)

    fun pause(region: OfflineRegion)

    suspend fun delete(region: OfflineRegion)
}

/** Хранилище пакетов процесса: на общем движке карты. Вызывать с главного потока. */
internal expect fun offlineRegionStore(): OfflineRegionStore
