package app.fieldlog.presentation.map

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.maplibre.compose.resource.MapResourceError
import org.maplibre.compose.resource.MapResourceLoad
import org.maplibre.compose.resource.MapResourceLoadRequest
import org.maplibre.compose.resource.MapResourceProvider

/**
 * Загрузчик ресурсов карты для OpenFreeMap с User-Agent приложения.
 *
 * Почему не заголовки интерцептора: MapLibre Native (FFI) считает `User-Agent` заголовком
 * транспорта и не принимает его из `MapRequestInterceptor.headers`, а Android-транспорт шлёт
 * `MapLibreNative/1.0`. Провайдер заменяет HTTP-клиент движка для запросов к OpenFreeMap
 * (стиль, тайлы, спрайты, шрифты, в том числе при скачивании районов), остальные хосты не трогает.
 */
internal fun openFreeMapResourceProvider(
    fetch: suspend (MapHttpRequest) -> MapHttpResponse = ::fetchMapHttp,
    now: () -> Instant = { Clock.System.now() },
    maxParallel: Int = OfflineLimits.MAX_PARALLEL_REQUESTS,
): MapResourceProvider {
    // Один ограничитель на весь провайдер: условие OpenFreeMap — не больше 4 запросов одновременно,
    // и для просмотра карты, и для скачивания района.
    val limited = limitParallel(maxParallel, fetch)
    return MapResourceProvider(
        accepts = { request -> isOpenFreeMapUrl(request.url) },
        load = { request -> loadOpenFreeMap(request, limited, now) },
    )
}

/**
 * Не больше [maxParallel] одновременных вызовов [fetch]. Остальные ждут свободного места;
 * отменённый ожидающий вызов место не занимает, отменённый выполняющийся — освобождает.
 */
internal fun limitParallel(
    maxParallel: Int,
    fetch: suspend (MapHttpRequest) -> MapHttpResponse,
): suspend (MapHttpRequest) -> MapHttpResponse {
    val permits = Semaphore(maxParallel)
    return { request -> permits.withPermit { fetch(request) } }
}

private suspend fun loadOpenFreeMap(
    request: MapResourceLoadRequest,
    fetch: suspend (MapHttpRequest) -> MapHttpResponse,
    now: () -> Instant,
): MapResourceLoad {
    // Скачивание района: диапазоны глифов иероглифов в пакет не берём (см. isSkippedOfflineGlyphRange).
    if (request.usage == MapResourceLoadRequest.Usage.Offline && isSkippedOfflineGlyphRange(request.url)) {
        return MapResourceLoad.NoContent()
    }
    val http = MapHttpRequest(
        url = request.url,
        headers = mapRequestHeaders(
            range = request.range?.let { it.first to it.last },
            priorEtag = request.priorEtag,
        ),
    )
    return try {
        toResourceLoad(fetch(http), now())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        MapResourceLoad.Failed(MapResourceError.Connection, error.message ?: "network error")
    }
}

/** Заголовки запроса: User-Agent приложения, диапазон и проверка кэша. */
internal fun mapRequestHeaders(range: Pair<Long, Long>?, priorEtag: String?): Map<String, String> =
    buildMap {
        put("User-Agent", MapDefaults.USER_AGENT)
        if (range != null) put("Range", "bytes=${range.first}-${range.second}")
        if (priorEtag != null) put("If-None-Match", priorEtag)
    }

/** Ответ сервера в терминах движка. */
internal fun toResourceLoad(response: MapHttpResponse, now: Instant): MapResourceLoad {
    val expires = maxAgeSeconds(response.cacheControl)?.let { now + it.seconds }
    return when (response.status) {
        200, 206 -> MapResourceLoad.Bytes(
            bytes = response.body,
            etag = response.etag,
            mustRevalidate = mustRevalidate(response.cacheControl),
            expires = expires,
        )
        204 -> MapResourceLoad.NoContent(expires = expires)
        304 -> MapResourceLoad.NotModified(expires = expires)
        404 -> MapResourceLoad.Failed(MapResourceError.NotFound, "HTTP 404")
        429 -> MapResourceLoad.Failed(
            MapResourceError.RateLimit,
            "HTTP 429",
            retryAfter = response.retryAfter?.trim()?.toLongOrNull()?.let { now + it.seconds },
        )
        in 500..599 -> MapResourceLoad.Failed(MapResourceError.Server, "HTTP ${response.status}")
        else -> MapResourceLoad.Failed(MapResourceError.Other, "HTTP ${response.status}")
    }
}

/** `max-age` из `Cache-Control` в секундах. */
internal fun maxAgeSeconds(cacheControl: String?): Long? =
    cacheControl?.split(',')
        ?.map { it.trim().lowercase() }
        ?.firstOrNull { it.startsWith("max-age=") }
        ?.removePrefix("max-age=")
        ?.toLongOrNull()
        ?.takeIf { it >= 0 }

internal fun mustRevalidate(cacheControl: String?): Boolean =
    cacheControl?.split(',')
        ?.map { it.trim().lowercase() }
        ?.any { it == "no-cache" || it == "must-revalidate" } == true

/**
 * Первый код символа в диапазоне глифов из URL `.../fonts/<шрифт>/<от>-<до>.pbf`;
 * `null`, если это не запрос глифов.
 */
internal fun glyphRangeStart(url: String): Int? {
    if ("/fonts/" !in url) return null
    val name = url.substringAfterLast('/').removeSuffix(".pbf")
    return name.substringBefore('-', missingDelimiterValue = "").toIntOrNull()
}

/** Диапазоны глифов с этого кода и выше (CJK, хангыль, формы представления) в пакет не качаем. */
internal const val OFFLINE_GLYPH_LIMIT = 0x2E00

/**
 * Библиотека качает в каждый пакет все 256 диапазонов глифов каждого шрифта стиля, в том числе
 * иероглифы: замер 5 нояб 2026 — около 34 МБ на один шрифт (Noto Sans Regular, 252 диапазона),
 * больше 67 МБ за первые два шрифта Liberty при лимите пакета 50 МБ. Отключить это в библиотеке нельзя, поэтому на скачивании района
 * диапазоны от [OFFLINE_GLYPH_LIMIT] отдаются пустыми. Латиница, кириллица, цифры и знаки
 * (до U+2DFF) остаются. Цена: в скачанном районе без сети не рисуются подписи иероглифами.
 * Онлайн-просмотр это не затрагивает: правило только для загрузки пакета.
 */
internal fun isSkippedOfflineGlyphRange(url: String): Boolean =
    glyphRangeStart(url)?.let { it >= OFFLINE_GLYPH_LIMIT } == true
