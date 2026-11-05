package app.fieldlog.presentation.map

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.offline.DownloadProgress
import org.maplibre.compose.offline.DownloadStatus
import org.maplibre.compose.offline.OfflinePack
import org.maplibre.compose.offline.OfflinePackDefinition
import org.maplibre.compose.offline.OfflineStorage
import org.maplibre.compose.offline.OfflineStorageState
import org.maplibre.compose.offline.offlineStorage
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position

// Тот же код лежит в androidMain: тип OfflineStorage есть только в платформенных наборах исходников.

internal actual fun offlineRegionStore(): OfflineRegionStore {
    // Настройка движка (User-Agent и лимитер запросов) возможна только до его создания.
    configureMapRuntime()
    return LibraryOfflineRegionStore(DefaultMapRuntime.instance.offlineStorage)
}

private class LibraryOfflineRegionStore(private val storage: OfflineStorage) : OfflineRegionStore {
    override val state: Flow<RegionsState> = storage.state.map { current ->
        when (current) {
            is OfflineStorageState.Ready ->
                RegionsState.Ready(current.packs.map { it.toRegion() }.sortedBy { it.id })
            is OfflineStorageState.Failed -> RegionsState.Failed("Не удалось открыть хранилище карт")
            else -> RegionsState.Loading
        }
    }

    override suspend fun create(region: NewRegion): OfflineRegion {
        val pack = storage.create(
            definition = OfflinePackDefinition.TilePyramid(
                styleUrl = MapDefaults.STYLE_URL,
                bounds = BoundingBox(
                    southwest = Position(longitude = region.bounds.west, latitude = region.bounds.south),
                    northeast = Position(longitude = region.bounds.east, latitude = region.bounds.north),
                ),
                pixelRatio = region.pixelRatio,
                minZoom = region.plan.minZoom.toDouble(),
                maxZoom = region.plan.maxZoom.toDouble(),
            ),
            metadata = encodeRegionMetadata(region.name),
        )
        return pack.toRegion()
    }

    override fun resume(region: OfflineRegion) = storage.resume(region.handle as OfflinePack)

    override fun pause(region: OfflineRegion) = storage.pause(region.handle as OfflinePack)

    override suspend fun delete(region: OfflineRegion) = storage.delete(region.handle as OfflinePack)
}

private fun OfflinePack.toRegion(): OfflineRegion {
    val definition = definition
    val bounds = (definition as? OfflinePackDefinition.TilePyramid)?.bounds?.let { box ->
        runCatching {
            GeoBounds(
                west = box.southwest.longitude,
                south = box.southwest.latitude,
                east = box.northeast.longitude,
                north = box.northeast.latitude,
            )
        }.getOrNull()
    }
    return OfflineRegion(
        // Публичного id у пакета нет; hashCode библиотеки считается от id региона.
        id = hashCode().toLong(),
        bounds = bounds,
        minZoom = definition.minZoom,
        maxZoom = definition.maxZoom,
        metadata = metadata,
        progress = downloadProgress.map { it.toRegionProgress() },
        handle = this,
    )
}

private fun DownloadProgress.toRegionProgress(): RegionProgress = when (this) {
    is DownloadProgress.Healthy -> RegionProgress.Known(
        phase = when (status) {
            DownloadStatus.Complete -> RegionProgress.Phase.Complete
            DownloadStatus.Downloading -> RegionProgress.Phase.Downloading
            else -> RegionProgress.Phase.Paused
        },
        completedTiles = completedTileCount,
        completedResources = completedResourceCount,
        completedBytes = completedResourceBytes,
        requiredResources = requiredResourceCount,
        requiredIsPrecise = isRequiredResourceCountPrecise,
    )
    is DownloadProgress.Error -> RegionProgress.Failed(
        when (reason.value) {
            "Connection" -> RegionErrorKind.Connection
            "RateLimit" -> RegionErrorKind.RateLimit
            "Server" -> RegionErrorKind.Server
            else -> RegionErrorKind.Other
        },
    )
    is DownloadProgress.TileLimitExceeded -> RegionProgress.TileLimit(limit)
    else -> RegionProgress.NotReported
}
