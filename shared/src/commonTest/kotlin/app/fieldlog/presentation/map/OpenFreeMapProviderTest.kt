package app.fieldlog.presentation.map

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import org.maplibre.compose.resource.MapResourceError
import org.maplibre.compose.resource.MapResourceLoad

class OpenFreeMapProviderTest {
    private val now = Instant.fromEpochSeconds(1_000_000)

    @Test
    fun everyRequestCarriesTheAppUserAgent() {
        val headers = mapRequestHeaders(range = null, priorEtag = null)
        assertEquals(mapOf("User-Agent" to MapDefaults.USER_AGENT), headers)
    }

    @Test
    fun rangeAndEtagAreAddedNextToUserAgent() {
        val headers = mapRequestHeaders(range = 10L to 99L, priorEtag = "\"abc\"")
        assertEquals(MapDefaults.USER_AGENT, headers["User-Agent"])
        assertEquals("bytes=10-99", headers["Range"])
        assertEquals("\"abc\"", headers["If-None-Match"])
    }

    @Test
    fun okResponseBecomesBytesWithCacheMetadata() {
        val body = byteArrayOf(1, 2, 3)
        val load = toResourceLoad(
            MapHttpResponse(200, body, etag = "\"e\"", cacheControl = "public, max-age=600"),
            now,
        )
        val bytes = assertIs<MapResourceLoad.Bytes>(load)
        assertContentEquals(body, bytes.bytes)
        assertEquals("\"e\"", bytes.etag)
        assertEquals(now + 600.seconds, bytes.expires)
        assertEquals(false, bytes.mustRevalidate)
    }

    @Test
    fun noContentAndNotModifiedMapToTheirCases() {
        assertIs<MapResourceLoad.NoContent>(toResourceLoad(MapHttpResponse(204, ByteArray(0)), now))
        assertIs<MapResourceLoad.NotModified>(toResourceLoad(MapHttpResponse(304, ByteArray(0)), now))
    }

    @Test
    fun errorStatusesMapToEngineReasons() {
        fun reason(status: Int) =
            assertIs<MapResourceLoad.Failed>(toResourceLoad(MapHttpResponse(status, ByteArray(0)), now)).reason
        assertEquals(MapResourceError.NotFound, reason(404))
        assertEquals(MapResourceError.RateLimit, reason(429))
        assertEquals(MapResourceError.Server, reason(503))
        assertEquals(MapResourceError.Other, reason(403))
    }

    @Test
    fun retryAfterSecondsBecomesAnInstant() {
        val failed = assertIs<MapResourceLoad.Failed>(
            toResourceLoad(MapHttpResponse(429, ByteArray(0), retryAfter = " 30 "), now),
        )
        assertEquals(now + 30.seconds, failed.retryAfter)
    }

    @Test
    fun cacheControlIsParsed() {
        assertEquals(86400L, maxAgeSeconds("public, max-age=86400, immutable"))
        assertEquals(0L, maxAgeSeconds("MAX-AGE=0"))
        assertNull(maxAgeSeconds("no-store"))
        assertNull(maxAgeSeconds("max-age=abc"))
        assertNull(maxAgeSeconds(null))
        assertTrue(mustRevalidate("no-cache"))
        assertTrue(mustRevalidate("max-age=5, must-revalidate"))
        assertEquals(false, mustRevalidate("public, max-age=5"))
        assertEquals(false, mustRevalidate(null))
    }

    @Test
    fun offlinePackSkipsIdeographGlyphRangesOnly() {
        fun font(range: String) = "https://tiles.openfreemap.org/fonts/Noto%20Sans%20Regular/$range.pbf"
        assertEquals(0, glyphRangeStart(font("0-255")))
        assertEquals(19968, glyphRangeStart(font("19968-20223")))
        // Латиница, кириллица, знаки остаются.
        assertEquals(false, isSkippedOfflineGlyphRange(font("0-255")))
        assertEquals(false, isSkippedOfflineGlyphRange(font("1024-1279")))
        assertEquals(false, isSkippedOfflineGlyphRange(font("11520-11775")))
        // С U+2E00 и выше — иероглифы, хангыль, формы представления: пропускаются.
        assertEquals(true, isSkippedOfflineGlyphRange(font("11776-12031")))
        assertEquals(true, isSkippedOfflineGlyphRange(font("19968-20223")))
        assertEquals(true, isSkippedOfflineGlyphRange(font("65280-65535")))
        // Не глифы — не трогаем.
        assertNull(glyphRangeStart("https://tiles.openfreemap.org/planet/2026/14/9904/5121.pbf"))
        assertNull(glyphRangeStart("https://tiles.openfreemap.org/fonts/x/broken.pbf"))
        assertEquals(false, isSkippedOfflineGlyphRange("https://tiles.openfreemap.org/styles/liberty"))
    }
}
