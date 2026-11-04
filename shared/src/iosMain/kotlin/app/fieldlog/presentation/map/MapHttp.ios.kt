package app.fieldlog.presentation.map

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequestReloadIgnoringLocalCacheData
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import platform.posix.memcpy

private const val TIMEOUT_SECONDS = 30.0

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual suspend fun fetchMapHttp(request: MapHttpRequest): MapHttpResponse =
    suspendCancellableCoroutine { continuation ->
        val url = NSURL.URLWithString(request.url)
        if (url == null) {
            continuation.resumeWithException(IllegalArgumentException("Некорректный URL: ${request.url}"))
            return@suspendCancellableCoroutine
        }
        val nsRequest = NSMutableURLRequest.requestWithURL(url)
        // Свой кэш у движка карты уже есть: кэш NSURLSession не нужен.
        nsRequest.setCachePolicy(NSURLRequestReloadIgnoringLocalCacheData)
        nsRequest.setTimeoutInterval(TIMEOUT_SECONDS)
        request.headers.forEach { (name, value) -> nsRequest.setValue(value, forHTTPHeaderField = name) }

        val task = NSURLSession.sharedSession.dataTaskWithRequest(nsRequest) { data, response, error ->
            val http = response as? NSHTTPURLResponse
            if (http == null) {
                continuation.resumeWithException(
                    RuntimeException(error?.localizedDescription ?: "Нет ответа сервера"),
                )
            } else {
                continuation.resume(
                    MapHttpResponse(
                        status = http.statusCode.toInt(),
                        body = data?.toByteArray() ?: ByteArray(0),
                        etag = http.valueForHTTPHeaderField("ETag"),
                        cacheControl = http.valueForHTTPHeaderField("Cache-Control"),
                        retryAfter = http.valueForHTTPHeaderField("Retry-After"),
                    ),
                )
            }
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
    }

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    }
    return bytes
}
