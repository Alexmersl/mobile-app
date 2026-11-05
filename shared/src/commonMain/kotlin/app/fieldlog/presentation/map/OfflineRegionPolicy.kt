package app.fieldlog.presentation.map

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Чистая логика офлайн-районов: подсчёт тайлов, лимиты OpenFreeMap, названия, тексты состояний.
 * Без Compose, MapLibre и платформ: проверяется в commonTest.
 */

/** Условия OpenFreeMap на скачивание района (docs/plan.md, «Офлайн-карта»). */
object OfflineLimits {
    /** Один пакет не больше 3000 тайлов. */
    const val MAX_TILES = 3000L

    /**
     * Один пакет не больше 50 МБ. Считаем десятичные мегабайты (1 МБ = 1 000 000 Б): так
     * граница строже, чем у двоичных 50 МиБ.
     */
    const val MAX_BYTES = 50_000_000L

    /**
     * Порог по размеру для идущей загрузки: 90% от [MAX_BYTES]. Живой счётчик движка занижает
     * размер: у района из 842 тайлов он показал 41,8 МБ, а после перечитывания (перезапуск
     * приложения) тот же пакет показал 45,0 МБ (+7,6%). Остальные 10% — запас на это отставание.
     */
    const val MAX_BYTES_WHILE_DOWNLOADING = MAX_BYTES / 10 * 9

    /** Не больше 4 одновременных запросов. Ограничитель стоит в [openFreeMapResourceProvider]. */
    const val MAX_PARALLEL_REQUESTS = 4
}

/** Прямоугольник на земле. Через 180-й меридиан не переходит: `west < east`. */
data class GeoBounds(val west: Double, val south: Double, val east: Double, val north: Double) {
    init {
        require(west in -180.0..180.0 && east in -180.0..180.0) { "Долгота вне диапазона" }
        require(south in -90.0..90.0 && north in -90.0..90.0) { "Широта вне диапазона" }
        require(west < east) { "Западная граница должна быть левее восточной" }
        require(south < north) { "Южная граница должна быть ниже северной" }
    }

    val center: GeoPoint get() = GeoPoint(latitude = (south + north) / 2, longitude = (west + east) / 2)
}

/**
 * Охват углов рамки: для набора точек (углы рамки, карта может быть повёрнута) даёт
 * прямоугольник, внутри которого лежат все точки. Долгота обрезается до ±180.
 * `null`, если рамка вырождена.
 */
fun boundsOf(points: List<GeoPoint>): GeoBounds? {
    if (points.size < 2) return null
    val west = points.minOf { it.longitude }.coerceIn(-180.0, 180.0)
    val east = points.maxOf { it.longitude }.coerceIn(-180.0, 180.0)
    val south = points.minOf { it.latitude }
    val north = points.maxOf { it.latitude }
    if (west >= east || south >= north) return null
    return GeoBounds(west, south, east, north)
}

/** Источник тайлов в стиле: чем он ограничен по масштабу и как движок переводит зум камеры. */
data class TileSourceSpec(val isRaster: Boolean, val tileSize: Int, val minZoom: Int, val maxZoom: Int)

/** Источники тайлов стиля OpenFreeMap Liberty. */
object OpenFreeMapTileSources {
    /**
     * Проверено 5 нояб 2026 по style.json (https://tiles.openfreemap.org/styles/liberty) и
     * TileJSON https://tiles.openfreemap.org/planet:
     *  - `openmaptiles`: векторные тайлы, зумы 0–14, 512 px;
     *  - `ne2_shaded`: растр Natural Earth, зумы 0–6, 256 px.
     * Если стиль на сервере поменяется, оценка разойдётся с фактом; для этого есть проверка
     * по реальному числу скачанных тайлов ([limitBreach]).
     */
    val LIBERTY: List<TileSourceSpec> = listOf(
        TileSourceSpec(isRaster = false, tileSize = 512, minZoom = 0, maxZoom = 14),
        TileSourceSpec(isRaster = true, tileSize = 256, minZoom = 0, maxZoom = 6),
    )
}

/** Верхний зум, дальше которого у векторных тайлов OpenFreeMap нет новых данных. */
const val MAX_DETAIL_ZOOM = 14

/**
 * Зум тайлов, который движок берёт для зума камеры [cameraZoom]: поправка на размер тайла,
 * у векторных вниз, у растровых к ближайшему (как в MapLibre Native, `coveringZoomLevel`).
 */
fun coveringZoom(cameraZoom: Double, source: TileSourceSpec): Int {
    val scaled = cameraZoom + log2(512.0 / source.tileSize)
    return if (source.isRaster) floor(scaled + 0.5).toInt() else floor(scaled).toInt()
}

/** Сколько тайлов зума [zoom] покрывает [bounds]. Столбцы и строки `floor(min)..ceil(max)-1`. */
fun tilesAtZoom(bounds: GeoBounds, zoom: Int): Long {
    if (zoom < 0 || zoom > 30) return 0
    val n = (1L shl zoom).toDouble()
    val xMin = (bounds.west + 180.0) / 360.0 * n
    val xMax = (bounds.east + 180.0) / 360.0 * n
    // Широты за пределами Web Mercator обрезаются, как у движка.
    val yTop = mercatorY(bounds.north) * n
    val yBottom = mercatorY(bounds.south) * n
    val columns = span(xMin, xMax, n)
    val rows = span(yTop, yBottom, n)
    return columns * rows
}

private const val MERCATOR_MAX_LATITUDE = 85.0511287798066

private fun mercatorY(latitude: Double): Double {
    val clamped = latitude.coerceIn(-MERCATOR_MAX_LATITUDE, MERCATOR_MAX_LATITUDE)
    val s = sin(clamped * PI / 180.0)
    return 0.5 - ln((1 + s) / (1 - s)) / (4 * PI)
}

private fun span(from: Double, to: Double, n: Double): Long {
    val first = floor(from).coerceIn(0.0, n - 1)
    val lastExclusive = ceil(to).coerceIn(1.0, n)
    return max(0.0, lastExclusive - first).toLong()
}

/**
 * Сколько тайлов скачает движок для района [bounds] и зумов камеры [minZoom]..[maxZoom]:
 * сумма по источникам стиля, у каждого свой диапазон зумов тайлов.
 */
fun countTiles(
    bounds: GeoBounds,
    minZoom: Double,
    maxZoom: Double,
    sources: List<TileSourceSpec> = OpenFreeMapTileSources.LIBERTY,
): Long {
    var total = 0L
    for (source in sources) {
        val from = max(coveringZoom(minZoom, source), source.minZoom)
        val to = min(coveringZoom(maxZoom, source), source.maxZoom)
        for (z in from..to) total += tilesAtZoom(bounds, z)
    }
    return total
}

/** Зумы камеры скачиваемого района. */
data class ZoomPlan(val minZoom: Int, val maxZoom: Int) {
    init {
        require(minZoom in 0..MAX_DETAIL_ZOOM && maxZoom in minZoom..MAX_DETAIL_ZOOM) {
            "Неверный диапазон зума: $minZoom..$maxZoom"
        }
    }
}

/**
 * Зум района: от зума, на котором человек выбрал рамку, до [requestedMaxZoom].
 * Дальше (мельче) района человек в офлайне не увидит ничего, ближе 14 новых данных нет.
 */
fun zoomPlan(cameraZoom: Double, requestedMaxZoom: Int = MAX_DETAIL_ZOOM): ZoomPlan {
    val min = floor(cameraZoom).toInt().coerceIn(0, MAX_DETAIL_ZOOM)
    return ZoomPlan(minZoom = min, maxZoom = requestedMaxZoom.coerceIn(min, MAX_DETAIL_ZOOM))
}

/** Результат проверки района до создания пакета. */
sealed interface RegionCheck {
    /** Влезает в лимит: можно создавать пакет. */
    data class Allowed(val tiles: Long, val plan: ZoomPlan) : RegionCheck

    /** Не влезает: пакет не создаётся, человеку показывают [message]. */
    data class TooBig(val tiles: Long, val plan: ZoomPlan, val message: String) : RegionCheck

    /** Рамку нельзя перевести в район (карта ещё не готова или рамка вырождена). */
    data class Unknown(val message: String) : RegionCheck
}

/** Проверка района по лимиту тайлов. Вызывается ДО создания пакета. */
fun checkRegion(
    bounds: GeoBounds?,
    cameraZoom: Double,
    requestedMaxZoom: Int = MAX_DETAIL_ZOOM,
    sources: List<TileSourceSpec> = OpenFreeMapTileSources.LIBERTY,
    limit: Long = OfflineLimits.MAX_TILES,
): RegionCheck {
    if (bounds == null) return RegionCheck.Unknown("Карта ещё загружается")
    val plan = zoomPlan(cameraZoom, requestedMaxZoom)
    val tiles = countTiles(bounds, plan.minZoom.toDouble(), plan.maxZoom.toDouble(), sources)
    if (tiles <= limit) return RegionCheck.Allowed(tiles, plan)

    val head = "Слишком много тайлов: ${tilesText(tiles)} при лимите $limit."
    val fits = (plan.maxZoom - 1 downTo plan.minZoom).firstOrNull { zoom ->
        countTiles(bounds, plan.minZoom.toDouble(), zoom.toDouble(), sources) <= limit
    }
    val advice = if (fits != null) {
        val fitTiles = countTiles(bounds, plan.minZoom.toDouble(), fits.toDouble(), sources)
        "Приблизьте карту, чтобы район стал меньше, или уменьшите детализацию до масштаба $fits (около ${tilesText(fitTiles)})."
    } else {
        "Приблизьте карту, чтобы район стал меньше: даже самая грубая детализация не влезает."
    }
    return RegionCheck.TooBig(tiles, plan, "$head $advice")
}

/** «1 тайл», «2 тайла», «5 тайлов». */
fun tilesText(count: Long): String = "$count ${pluralRu(count, "тайл", "тайла", "тайлов")}"

/** Русская форма по числу: 1 / 2–4 / 5+ (11–14 — «много»). */
fun pluralRu(count: Long, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1L -> one
        mod10 in 2..4 -> few
        else -> many
    }
}

/** «8,3 МБ» из байтов (десятичные мегабайты, одна цифра после запятой). */
fun megabytesText(bytes: Long): String {
    val tenths = (max(0L, bytes) + 50_000L) / 100_000L
    return "${tenths / 10},${tenths % 10} МБ"
}

// --- Название района и metadata --------------------------------------------------------------

private const val METADATA_PREFIX = "FL1:"
private const val NAME_PREFIX = "Район "
const val UNNAMED_REGION = "Район без названия"

/**
 * Библиотека не читает metadata, это просто байты (UTF-8). Формат: `FL1:<название>`.
 * Версия в префиксе позволит позже добавить поля.
 */
fun encodeRegionMetadata(name: String): ByteArray = (METADATA_PREFIX + name).encodeToByteArray()

/** Название района из metadata. Чужие или пустые байты дают [UNNAMED_REGION]. */
fun decodeRegionName(metadata: ByteArray?): String {
    if (metadata == null) return UNNAMED_REGION
    val text = metadata.decodeToString()
    if (!text.startsWith(METADATA_PREFIX)) return UNNAMED_REGION
    return text.removePrefix(METADATA_PREFIX).ifBlank { UNNAMED_REGION }
}

/** Следующее название «Район N»: номер на единицу больше наибольшего среди существующих. */
fun nextRegionName(existing: List<String>): String {
    val highest = existing.mapNotNull { name ->
        if (name.startsWith(NAME_PREFIX)) name.removePrefix(NAME_PREFIX).toIntOrNull() else null
    }.maxOrNull() ?: 0
    return "$NAME_PREFIX${highest + 1}"
}

// --- Состояние скачивания ---------------------------------------------------------------------

/** Причина сбоя скачивания в терминах приложения (без типов библиотеки). */
enum class RegionErrorKind { Connection, RateLimit, Server, Other }

/** Прогресс пакета в терминах приложения. Платформенный слой переводит в это из библиотеки. */
sealed interface RegionProgress {
    /** Движок ещё не сообщил состояние. */
    data object NotReported : RegionProgress

    data class Known(
        val phase: Phase,
        val completedTiles: Long,
        val completedResources: Long,
        /** Все скачанные ресурсы пакета: тайлы, стиль, спрайты, шрифты. */
        val completedBytes: Long,
        val requiredResources: Long,
        val requiredIsPrecise: Boolean,
    ) : RegionProgress

    data class Failed(val kind: RegionErrorKind) : RegionProgress

    /** Движок сам остановился на своём лимите тайлов. */
    data class TileLimit(val limit: Long) : RegionProgress

    enum class Phase { Paused, Downloading, Complete }
}

/** Что показывать и какие кнопки давать. */
enum class RegionState { Waiting, Downloading, Paused, Complete, Failed }

data class RegionStatusView(val state: RegionState, val text: String, val fraction: Float?)

fun describeProgress(progress: RegionProgress): RegionStatusView = when (progress) {
    RegionProgress.NotReported -> RegionStatusView(RegionState.Waiting, "Ожидание…", null)
    is RegionProgress.Failed -> RegionStatusView(RegionState.Failed, errorText(progress.kind), null)
    is RegionProgress.TileLimit -> RegionStatusView(
        RegionState.Failed,
        "Достигнут лимит тайлов (${progress.limit}). Район нужно удалить и выбрать поменьше.",
        null,
    )
    is RegionProgress.Known -> {
        val sizes = "${tilesText(progress.completedTiles)}, ${megabytesText(progress.completedBytes)}"
        when (progress.phase) {
            RegionProgress.Phase.Complete ->
                RegionStatusView(RegionState.Complete, "Готов: $sizes", 1f)
            RegionProgress.Phase.Downloading ->
                RegionStatusView(RegionState.Downloading, "Скачивание ${percentText(progress)}: $sizes", fraction(progress))
            RegionProgress.Phase.Paused ->
                RegionStatusView(RegionState.Paused, "Пауза ${percentText(progress)}: $sizes", fraction(progress))
        }
    }
}

private fun fraction(progress: RegionProgress.Known): Float {
    if (progress.requiredResources <= 0) return 0f
    // Пока число нужных ресурсов неточное, оно растёт: не показываем 100% раньше времени.
    val cap = if (progress.requiredIsPrecise) 1f else 0.99f
    return min(cap, progress.completedResources.toFloat() / progress.requiredResources.toFloat())
}

private fun percentText(progress: RegionProgress.Known): String {
    val percent = (fraction(progress) * 100).toInt()
    return if (progress.requiredIsPrecise) "$percent%" else "~$percent%"
}

fun errorText(kind: RegionErrorKind): String = when (kind) {
    RegionErrorKind.Connection ->
        "Нет связи с сервером карты. Проверьте сеть и нажмите «Продолжить»."
    RegionErrorKind.RateLimit ->
        "Сервер карты просит подождать: слишком много запросов. Нажмите «Продолжить» чуть позже."
    RegionErrorKind.Server ->
        "Сервер карты сейчас не отвечает. Нажмите «Продолжить» позже."
    RegionErrorKind.Other ->
        "Часть района скачать не удалось. Нажмите «Продолжить»."
}

/** Превышение лимита уже идущей загрузки. Пакет в этом случае останавливают и удаляют. */
sealed interface LimitBreach {
    val message: String

    data class Tiles(val count: Long) : LimitBreach {
        override val message get() =
            "Скачано ${tilesText(count)} при лимите ${OfflineLimits.MAX_TILES}. Район удалён: выберите поменьше."
    }

    data class Bytes(val bytes: Long) : LimitBreach {
        override val message get() =
            "Район не помещается в лимит ${OfflineLimits.MAX_BYTES / 1_000_000} МБ: скачано уже ${megabytesText(bytes)}. " +
                "Район удалён: выберите поменьше или уменьшите детализацию."
    }

    data class EngineTileLimit(val limit: Long) : LimitBreach {
        override val message get() = "Движок карты остановил загрузку на лимите тайлов ($limit). Район удалён."
    }
}

private fun bytesLimitFor(phase: RegionProgress.Phase): Long =
    if (phase == RegionProgress.Phase.Downloading) {
        OfflineLimits.MAX_BYTES_WHILE_DOWNLOADING
    } else {
        // Пауза, готовый район и район после перезапуска: число уже перечитано, запас не нужен.
        OfflineLimits.MAX_BYTES
    }

/**
 * Лимиты OpenFreeMap движок не проверяет (размер он не ограничивает вовсе), поэтому каждое
 * обновление прогресса сверяется с ними здесь.
 */
fun limitBreach(progress: RegionProgress): LimitBreach? = when (progress) {
    is RegionProgress.Known -> when {
        progress.completedTiles > OfflineLimits.MAX_TILES -> LimitBreach.Tiles(progress.completedTiles)
        progress.completedBytes > bytesLimitFor(progress.phase) -> LimitBreach.Bytes(progress.completedBytes)
        else -> null
    }
    is RegionProgress.TileLimit -> LimitBreach.EngineTileLimit(progress.limit)
    else -> null
}

/** Текст ошибки операции без технических подробностей. */
fun operationErrorText(action: String): String = "Не удалось $action. Попробуйте ещё раз."
