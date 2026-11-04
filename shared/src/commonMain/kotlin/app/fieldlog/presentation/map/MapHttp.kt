package app.fieldlog.presentation.map

/** Запрос к серверу карты. Заголовки ставятся как есть, в том числе `User-Agent`. */
internal class MapHttpRequest(val url: String, val headers: Map<String, String>)

/** Ответ сервера карты. Нужные заголовки уже достаны, остальные не нужны. */
internal class MapHttpResponse(
    val status: Int,
    val body: ByteArray,
    val etag: String? = null,
    val cacheControl: String? = null,
    val retryAfter: String? = null,
)

/**
 * GET средствами платформы: Android — `HttpURLConnection`, iOS — `NSURLSession`.
 * Без внешних зависимостей. Сетевую ошибку бросает исключением.
 */
internal expect suspend fun fetchMapHttp(request: MapHttpRequest): MapHttpResponse
