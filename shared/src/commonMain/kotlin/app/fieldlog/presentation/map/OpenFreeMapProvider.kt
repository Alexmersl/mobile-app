package app.fieldlog.presentation.map

import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
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
): MapResourceProvider =
    MapResourceProvider(
        accepts = { request -> isOpenFreeMapUrl(request.url) },
        load = { request -> loadOpenFreeMap(request, fetch, now) },
    )

private suspend fun loadOpenFreeMap(
    request: MapResourceLoadRequest,
    fetch: suspend (MapHttpRequest) -> MapHttpResponse,
    now: () -> Instant,
): MapResourceLoad {
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
