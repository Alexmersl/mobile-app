package app.fieldlog.presentation.map

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000

internal actual suspend fun fetchMapHttp(request: MapHttpRequest): MapHttpResponse =
    withContext(Dispatchers.IO) {
        val connection = URL(request.url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val status = connection.responseCode
            val stream = if (status < 400) connection.inputStream else connection.errorStream
            val body = if (status == 204 || status == 304) ByteArray(0) else stream?.use { it.readBytes() } ?: ByteArray(0)
            MapHttpResponse(
                status = status,
                body = body,
                etag = connection.getHeaderField("ETag"),
                cacheControl = connection.getHeaderField("Cache-Control"),
                retryAfter = connection.getHeaderField("Retry-After"),
            )
        } finally {
            connection.disconnect()
        }
    }
