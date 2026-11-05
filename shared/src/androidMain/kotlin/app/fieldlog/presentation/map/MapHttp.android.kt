package app.fieldlog.presentation.map

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

private const val CONNECT_TIMEOUT_MS = 15_000
private const val READ_TIMEOUT_MS = 30_000

/** Блокирующее чтение живёт на IO, а ожидающая корутина — отдельно, чтобы отмену было видно сразу. */
private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

internal actual suspend fun fetchMapHttp(request: MapHttpRequest): MapHttpResponse {
    val connection = URL(request.url).openConnection() as HttpURLConnection
    val read = ioScope.async {
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
    try {
        return read.await()
    } finally {
        // Отмена (пауза или отмена скачивания, уход с экрана): `HttpURLConnection` корутинами не
        // прерывается, поэтому закрываем соединение сами, иначе чтение висело бы до таймаута 30 с.
        if (read.isActive) {
            connection.disconnect()
            read.cancel()
            // Запрос считается законченным, когда соединение закрыто: пока ждём, место в
            // ограничителе на 4 запроса не освобождается.
            withContext(NonCancellable) { read.join() }
        }
    }
}
