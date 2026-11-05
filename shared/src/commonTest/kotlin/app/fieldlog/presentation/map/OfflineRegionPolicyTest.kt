package app.fieldlog.presentation.map

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OfflineRegionPolicyTest {
    private val world = GeoBounds(west = -180.0, south = -85.0, east = 180.0, north = 85.0)

    // 1° × 1° около Минска-Смоленска; числа тайлов посчитаны независимо (Python, Web Mercator).
    private val degree = GeoBounds(west = 30.0, south = 55.0, east = 31.0, north = 56.0)

    // Район ~10 × 11 км в Москве.
    private val moscow = GeoBounds(west = 37.55, south = 55.70, east = 37.70, north = 55.80)

    // --- Подсчёт тайлов ---

    @Test
    fun wholeWorldHasFourToTheZoomTiles() {
        assertEquals(1, tilesAtZoom(world, 0))
        assertEquals(16, tilesAtZoom(world, 2))
        assertEquals(1024, tilesAtZoom(world, 5))
        assertEquals(4096, tilesAtZoom(world, 6))
    }

    @Test
    fun smallAreaInsideOneTileCountsOne() {
        assertEquals(1, tilesAtZoom(GeoBounds(10.0, 10.0, 11.0, 11.0), 3))
    }

    @Test
    fun areaAcrossTileEdgesCountsEveryTileItTouches() {
        // Пересекает меридиан 0° и экватор: на зуме 1 это 2 × 2 тайла.
        assertEquals(4, tilesAtZoom(GeoBounds(-1.0, -1.0, 1.0, 1.0), 1))
    }

    @Test
    fun tileCountsMatchTheIndependentCalculation() {
        assertEquals(listOf(9L, 24, 77, 252, 1008, 3772), (9..14).map { tilesAtZoom(degree, it) })
        assertEquals(listOf(4L, 4, 6, 20, 72), (10..14).map { tilesAtZoom(moscow, it) })
    }

    @Test
    fun coveringZoomFollowsTileSize() {
        val vector = OpenFreeMapTileSources.LIBERTY.first { !it.isRaster }
        val raster = OpenFreeMapTileSources.LIBERTY.first { it.isRaster }
        assertEquals(10, coveringZoom(10.9, vector))
        // 256-пиксельный растр берёт на зум глубже и округляет к ближайшему.
        assertEquals(11, coveringZoom(10.0, raster))
        assertEquals(11, coveringZoom(9.5, raster))
        assertEquals(10, coveringZoom(9.4, raster))
    }

    @Test
    fun countAddsVectorAndRasterSourcesWithTheirOwnZoomLimits() {
        // Векторные z0..2 = 1 + 4 + 16; растр: зумы камеры 0..2 → тайлов z1..3 = 4 + 16 + 64.
        assertEquals(21 + 84, countTiles(world, minZoom = 0.0, maxZoom = 2.0))
    }

    @Test
    fun rasterIsNotDownloadedAboveItsMaxZoomAndVectorStopsAt14() {
        // С зума камеры 10 растр (максимум 6) не участвует вовсе.
        val expected = (10..14).sumOf { tilesAtZoom(moscow, it) }
        assertEquals(expected, countTiles(moscow, minZoom = 10.0, maxZoom = 14.0))
        // Зум выше 14 новых векторных тайлов не даёт.
        assertEquals(expected, countTiles(moscow, minZoom = 10.0, maxZoom = 18.0))
    }

    // --- Лимит до создания пакета ---

    @Test
    fun smallRegionIsAllowedWithItsTileCount() {
        val check = assertIs<RegionCheck.Allowed>(checkRegion(moscow, cameraZoom = 10.4, requestedMaxZoom = 14))
        assertEquals(ZoomPlan(10, 14), check.plan)
        assertEquals(4L + 4 + 6 + 20 + 72, check.tiles)
    }

    @Test
    fun regionOverTheLimitIsRefusedWithAdvice() {
        val check = assertIs<RegionCheck.TooBig>(checkRegion(degree, cameraZoom = 9.2, requestedMaxZoom = 14))
        assertEquals(9L + 24 + 77 + 252 + 1008 + 3772, check.tiles)
        assertTrue("3000" in check.message, check.message)
        // 9..13 = 1370 тайлов: это наибольшая детализация, которая влезает.
        assertTrue("масштаба 13" in check.message, check.message)
        assertTrue("Приблизьте карту" in check.message, check.message)
    }

    @Test
    fun limitIsInclusive() {
        val exactly = countTiles(moscow, 10.0, 14.0)
        assertIs<RegionCheck.Allowed>(checkRegion(moscow, 10.0, 14, limit = exactly))
        assertIs<RegionCheck.TooBig>(checkRegion(moscow, 10.0, 14, limit = exactly - 1))
    }

    @Test
    fun hopelessRegionAsksToZoomIn() {
        val check = assertIs<RegionCheck.TooBig>(checkRegion(world, cameraZoom = 5.0, requestedMaxZoom = 14))
        assertTrue("даже самая грубая" in check.message, check.message)
    }

    @Test
    fun unknownBoundsAreNotAllowed() {
        assertIs<RegionCheck.Unknown>(checkRegion(null, cameraZoom = 10.0))
    }

    @Test
    fun zoomPlanIsClamped() {
        assertEquals(ZoomPlan(10, 14), zoomPlan(10.99))
        assertEquals(ZoomPlan(14, 14), zoomPlan(16.5))
        assertEquals(ZoomPlan(0, 5), zoomPlan(-1.0, requestedMaxZoom = 5))
        assertEquals(ZoomPlan(10, 10), zoomPlan(10.0, requestedMaxZoom = 4))
    }

    @Test
    fun boundsCoverAllCornersOfARotatedFrame() {
        val corners = listOf(GeoPoint(55.0, 37.0), GeoPoint(55.2, 37.1), GeoPoint(55.1, 37.3), GeoPoint(54.9, 37.2))
        val bounds = assertNotNull(boundsOf(corners))
        assertEquals(GeoBounds(west = 37.0, south = 54.9, east = 37.3, north = 55.2), bounds)
        assertNull(boundsOf(listOf(GeoPoint(55.0, 37.0), GeoPoint(55.0, 37.0))))
    }

    @Test
    fun boundsRejectInvertedBoxes() {
        assertFailsWith<IllegalArgumentException> { GeoBounds(west = 10.0, south = 0.0, east = 5.0, north = 1.0) }
    }

    // --- Тексты ---

    @Test
    fun russianPlurals() {
        assertEquals("1 тайл", tilesText(1))
        assertEquals("2 тайла", tilesText(2))
        assertEquals("5 тайлов", tilesText(5))
        assertEquals("11 тайлов", tilesText(11))
        assertEquals("21 тайл", tilesText(21))
        assertEquals("112 тайлов", tilesText(112))
        assertEquals("0 тайлов", tilesText(0))
    }

    @Test
    fun megabytesAreDecimalWithOneDigit() {
        assertEquals("0,0 МБ", megabytesText(0))
        assertEquals("8,3 МБ", megabytesText(8_300_000))
        assertEquals("50,0 МБ", megabytesText(OfflineLimits.MAX_BYTES))
        assertEquals("0,1 МБ", megabytesText(60_000))
    }

    @Test
    fun regionNameSurvivesMetadataRoundTrip() {
        assertEquals("Район 7", decodeRegionName(encodeRegionMetadata("Район 7")))
        assertEquals("Лес «Тихий»", decodeRegionName(encodeRegionMetadata("Лес «Тихий»")))
        assertEquals(UNNAMED_REGION, decodeRegionName(null))
        assertEquals(UNNAMED_REGION, decodeRegionName(ByteArray(0)))
        assertEquals(UNNAMED_REGION, decodeRegionName("чужое".encodeToByteArray()))
        assertEquals(UNNAMED_REGION, decodeRegionName(encodeRegionMetadata("")))
        assertContentEquals("FL1:Район 1".encodeToByteArray(), encodeRegionMetadata("Район 1"))
    }

    @Test
    fun nextNameIsOneMoreThanTheHighestNumber() {
        assertEquals("Район 1", nextRegionName(emptyList()))
        assertEquals("Район 2", nextRegionName(listOf("Район 1")))
        assertEquals("Район 6", nextRegionName(listOf("Район 5", "Район 2", UNNAMED_REGION, "Лес")))
    }

    // --- Прогресс и лимиты во время скачивания ---

    private fun known(
        phase: RegionProgress.Phase,
        tiles: Long = 0,
        bytes: Long = 0,
        done: Long = 0,
        required: Long = 100,
        precise: Boolean = true,
    ) = RegionProgress.Known(phase, tiles, done, bytes, required, precise)

    @Test
    fun downloadingShowsPercentTilesAndSize() {
        val view = describeProgress(
            known(RegionProgress.Phase.Downloading, tiles = 12, bytes = 8_300_000, done = 40, required = 160),
        )
        assertEquals(RegionState.Downloading, view.state)
        assertEquals("Скачивание 25%: 12 тайлов, 8,3 МБ", view.text)
        assertEquals(0.25f, view.fraction)
    }

    @Test
    fun impreciseTotalIsMarkedAndNeverShowsFullBeforeCompletion() {
        val view = describeProgress(
            known(RegionProgress.Phase.Downloading, done = 100, required = 100, precise = false),
        )
        assertTrue(view.text.startsWith("Скачивание ~99%"), view.text)
    }

    @Test
    fun pausedAndCompleteStates() {
        assertEquals(RegionState.Paused, describeProgress(known(RegionProgress.Phase.Paused, done = 10)).state)
        val done = describeProgress(known(RegionProgress.Phase.Complete, tiles = 5, bytes = 1_000_000, done = 9))
        assertEquals(RegionState.Complete, done.state)
        assertEquals("Готов: 5 тайлов, 1,0 МБ", done.text)
        assertEquals(RegionState.Waiting, describeProgress(RegionProgress.NotReported).state)
    }

    @Test
    fun networkErrorsAreExplainedInRussian() {
        RegionErrorKind.entries.forEach { kind ->
            val view = describeProgress(RegionProgress.Failed(kind))
            assertEquals(RegionState.Failed, view.state)
            assertTrue("«Продолжить»" in view.text, view.text)
        }
        assertTrue("сеть" in errorText(RegionErrorKind.Connection))
    }

    @Test
    fun runningDownloadOverTheLimitsIsFlagged() {
        val ok = known(
            RegionProgress.Phase.Downloading,
            tiles = OfflineLimits.MAX_TILES,
            bytes = OfflineLimits.MAX_BYTES_WHILE_DOWNLOADING,
        )
        assertNull(limitBreach(ok))
        assertIs<LimitBreach.Tiles>(limitBreach(known(RegionProgress.Phase.Downloading, tiles = 3001)))
        // Во время загрузки порог ниже на 10%: живой счётчик отстаёт.
        assertIs<LimitBreach.Bytes>(
            limitBreach(known(RegionProgress.Phase.Downloading, bytes = OfflineLimits.MAX_BYTES_WHILE_DOWNLOADING + 1)),
        )
        assertEquals(45_000_000L, OfflineLimits.MAX_BYTES_WHILE_DOWNLOADING)
        // Готовый или поставленный на паузу район проверяется по полному лимиту.
        assertNull(limitBreach(known(RegionProgress.Phase.Complete, bytes = OfflineLimits.MAX_BYTES)))
        assertIs<LimitBreach.Bytes>(
            limitBreach(known(RegionProgress.Phase.Complete, bytes = OfflineLimits.MAX_BYTES + 1)),
        )
        assertIs<LimitBreach.Bytes>(
            limitBreach(known(RegionProgress.Phase.Paused, bytes = OfflineLimits.MAX_BYTES + 1)),
        )
        assertIs<LimitBreach.EngineTileLimit>(limitBreach(RegionProgress.TileLimit(6000)))
        assertNull(limitBreach(RegionProgress.Failed(RegionErrorKind.Connection)))
        assertNull(limitBreach(RegionProgress.NotReported))
    }

    @Test
    fun limitConstantsMatchThePlan() {
        assertEquals(3000L, OfflineLimits.MAX_TILES)
        assertEquals(50_000_000L, OfflineLimits.MAX_BYTES)
        assertEquals(4, OfflineLimits.MAX_PARALLEL_REQUESTS)
    }
}
