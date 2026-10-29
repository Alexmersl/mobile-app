package app.fieldlog.presentation.map

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapPolicyTest {
    @Test
    fun initialCameraWithoutLocationIsRussiaCenter() {
        val camera = initialCamera(null)
        assertEquals(55.75, camera.point.latitude)
        assertEquals(37.62, camera.point.longitude)
        assertEquals(5.0, camera.zoom)
    }

    @Test
    fun initialCameraWithKnownLocationIsThatLocation() {
        val here = GeoPoint(59.93, 30.33)
        val camera = initialCamera(here)
        assertEquals(here, camera.point)
        assertEquals(MapDefaults.LOCATION_ZOOM, camera.zoom)
    }

    @Test
    fun cameraOnLocationZoomsInButNeverOut() {
        val here = GeoPoint(56.0, 38.0)
        assertEquals(MapDefaults.LOCATION_ZOOM, cameraOnLocation(here, currentZoom = 5.0).zoom)
        assertEquals(17.0, cameraOnLocation(here, currentZoom = 17.0).zoom)
        assertEquals(here, cameraOnLocation(here, currentZoom = 5.0).point)
    }

    @Test
    fun geoPointRejectsImpossibleCoordinates() {
        assertFailsWith<IllegalArgumentException> { GeoPoint(91.0, 0.0) }
        assertFailsWith<IllegalArgumentException> { GeoPoint(0.0, 181.0) }
    }

    @Test
    fun styleIsOpenFreeMapAndNotOfficialOsmTiles() {
        assertTrue(MapDefaults.STYLE_URL.startsWith("https://tiles.openfreemap.org/"))
        assertTrue("openstreetmap.org" !in MapDefaults.STYLE_URL)
    }

    @Test
    fun attributionNamesAllRequiredSources() {
        val text = MapDefaults.ATTRIBUTION
        assertTrue("OpenFreeMap" in text)
        assertTrue("OpenMapTiles" in text)
        assertTrue("© OpenStreetMap contributors" in text)
    }

    @Test
    fun userAgentIsSentOnlyToOpenFreeMap() {
        val ofm = requestHeaders("https://tiles.openfreemap.org/planet/20261025_080012_pt/5/19/9.pbf")
        assertEquals(mapOf("User-Agent" to MapDefaults.USER_AGENT), ofm)
        assertEquals(emptyMap(), requestHeaders("https://example.com/tiles/1.pbf"))
    }

    @Test
    fun userAgentIdentifiesTheApp() {
        assertTrue(MapDefaults.USER_AGENT.startsWith("FieldLog/"))
        assertTrue("app.fieldlog" in MapDefaults.USER_AGENT)
    }

    @Test
    fun buttonRequestsPermissionUntilGranted() {
        assertEquals(LocationButtonAction.RequestPermission, locationButtonAction(LocationAccess.NotAsked, hasFix = false))
        assertEquals(LocationButtonAction.RequestPermission, locationButtonAction(LocationAccess.Denied, hasFix = false))
    }

    @Test
    fun buttonCentersWhenGrantedAndHasFix() {
        assertEquals(LocationButtonAction.CenterOnLocation, locationButtonAction(LocationAccess.Granted, hasFix = true))
        assertEquals(LocationButtonAction.WaitForFix, locationButtonAction(LocationAccess.Granted, hasFix = false))
    }

    @Test
    fun buttonPointsToSettingsAfterPermanentDenial() {
        assertEquals(
            LocationButtonAction.ExplainSettings,
            locationButtonAction(LocationAccess.DeniedPermanently, hasFix = false),
        )
    }

    @Test
    fun hintExplainsDenialAndStaysSilentWhenAllIsFine() {
        assertNull(locationHint(LocationAccess.Granted, hasFix = true))
        assertNull(locationHint(LocationAccess.NotAsked, hasFix = false))
        assertNotNull(locationHint(LocationAccess.Granted, hasFix = false))
        assertNotNull(locationHint(LocationAccess.Denied, hasFix = false))
        assertNotNull(locationHint(LocationAccess.DeniedPermanently, hasFix = false))
    }

    @Test
    fun permissionIsRequestedOncePerLaunchAndOnlyWhenAskable() {
        assertTrue(shouldAutoRequestPermission(LocationAccess.NotAsked, alreadyAsked = false))
        assertTrue(shouldAutoRequestPermission(LocationAccess.Denied, alreadyAsked = false))
        assertEquals(false, shouldAutoRequestPermission(LocationAccess.NotAsked, alreadyAsked = true))
        assertEquals(false, shouldAutoRequestPermission(LocationAccess.Granted, alreadyAsked = false))
        assertEquals(false, shouldAutoRequestPermission(LocationAccess.DeniedPermanently, alreadyAsked = false))
    }

    @Test
    fun autoCenterOnlyOnceAndNotAfterUserMovedMap() {
        assertTrue(shouldAutoCenter(alreadyCentered = false, userMovedCamera = false))
        assertEquals(false, shouldAutoCenter(alreadyCentered = true, userMovedCamera = false))
        assertEquals(false, shouldAutoCenter(alreadyCentered = false, userMovedCamera = true))
    }
}
