package app.fieldlog.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraMoveReason
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.layers.LocationIndicatorLayer
import org.maplibre.compose.location.LocationPermission
import org.maplibre.compose.location.LocationState
import org.maplibre.compose.location.rememberDefaultLocationProvider
import org.maplibre.compose.location.rememberLocationState
import org.maplibre.compose.location.rememberSystemSettingsLauncher
import org.maplibre.compose.map.MapState
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.overlay.CompassButton
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

/** Системное окно разрешения показываем один раз за запуск; вкладка может пересоздаваться. */
private var permissionAskedThisLaunch = false

/**
 * Вкладка «Карта»: карта OpenFreeMap, видимая атрибуция, положение человека и кнопка «Моё положение».
 *
 * Библиотека MapLibre Compose 0.20.0 в беты: API может меняться между минорными версиями.
 */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    // До rememberMapState: после создания первой карты настроить движок уже нельзя.
    remember { configureMapRuntime() }

    val locationState = rememberLocationState(provider = rememberDefaultLocationProvider())
    val settings = rememberSystemSettingsLauncher()
    val scope = rememberCoroutineScope()

    val start = initialCamera(locationState.lastLocation?.position?.toGeoPoint())
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(MapDefaults.STYLE_URL),
        initialCameraPosition = CameraPosition(
            center = start.point.toPosition(),
            zoom = start.zoom,
        ),
    ) {
        // Индикатор рисуется только когда есть положение: без разрешения lastLocation == null
        // и слой ничего не показывает.
        LocationIndicatorLayer(id = "user-location", locationState = locationState)
    }

    val access = locationState.permission.toLocationAccess()
    val location = locationState.lastLocation?.position?.toGeoPoint()
    // Положение, оставшееся после отзыва разрешения, не считаем актуальным для кнопки и подсказки.
    val hasFix = access == LocationAccess.Granted && location != null

    LaunchedEffect(access) {
        if (shouldAutoRequestPermission(access, permissionAskedThisLaunch)) {
            permissionAskedThisLaunch = true
            locationState.requestPermission()
        }
    }

    AutoCenterOnFirstFix(mapState, locationState)

    Box(modifier = modifier.fillMaxSize()) {
        MaplibreMap(state = mapState, modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(8.dp)) {
                CompassButton(modifier = Modifier.align(Alignment.TopEnd))
                Attribution(Modifier.align(Alignment.BottomStart))
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.End,
        ) {
            locationHint(access, hasFix)?.let { hint ->
                Chip(text = hint, modifier = Modifier.padding(bottom = 8.dp))
            }
            LocationButton(
                onClick = {
                    when (locationButtonAction(access, hasFix)) {
                        LocationButtonAction.RequestPermission -> locationState.requestPermission()
                        LocationButtonAction.CenterOnLocation -> {
                            if (location != null) {
                                val target = cameraOnLocation(location, mapState.cameraPosition.zoom)
                                scope.launch {
                                    mapState.animateCamera(
                                        CameraUpdate(center = target.point.toPosition(), zoom = target.zoom),
                                    )
                                }
                            }
                        }
                        LocationButtonAction.WaitForFix -> Unit
                        LocationButtonAction.ExplainSettings -> {
                            if (settings.canOpenApplicationSettings) settings.openApplicationSettings()
                        }
                    }
                },
            )
        }
    }
}

/** Один раз подлетает к человеку при первом положении, если карту ещё не трогали руками. */
@Composable
private fun AutoCenterOnFirstFix(mapState: MapState, locationState: LocationState) {
    var centered by remember { mutableStateOf(false) }
    var userMoved by remember { mutableStateOf(false) }

    LaunchedEffect(mapState) {
        snapshotFlow { mapState.isCameraMoving && mapState.cameraMoveReason == CameraMoveReason.Gesture }
            .filter { it }
            .first()
        userMoved = true
    }

    LaunchedEffect(mapState, locationState) {
        val first = snapshotFlow { locationState.lastLocation }.filter { it != null }.first()
        if (shouldAutoCenter(alreadyCentered = centered, userMovedCamera = userMoved) && first != null) {
            centered = true
            val target = cameraOnLocation(first.position.toGeoPoint(), mapState.cameraPosition.zoom)
            mapState.animateCamera(CameraUpdate(center = target.point.toPosition(), zoom = target.zoom))
        }
    }
}

@Composable
private fun Attribution(modifier: Modifier = Modifier) {
    // Всегда видна поверх карты. Не полагаемся на встроенную кнопку «i»: она сворачивается жестом.
    Chip(text = MapDefaults.ATTRIBUTION, modifier = modifier, fontSize = 11, bold = false)
}

@Composable
private fun LocationButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Моё положение",
            style = TextStyle(color = Color(0xFF1B5E20), fontSize = 15.sp, fontWeight = FontWeight.Bold),
        )
    }
}

@Composable
private fun Chip(text: String, modifier: Modifier = Modifier, fontSize: Int = 13, bold: Boolean = false) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.85f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        BasicText(
            text = text,
            style = TextStyle(
                color = Color(0xFF222222),
                fontSize = fontSize.sp,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            ),
        )
    }
}

private fun Position.toGeoPoint() = GeoPoint(latitude = latitude, longitude = longitude)

private fun GeoPoint.toPosition() = Position(longitude = longitude, latitude = latitude)

private fun LocationPermission.toLocationAccess(): LocationAccess = when (this) {
    is LocationPermission.Granted -> LocationAccess.Granted
    // iOS до первого вопроса и Android до первого вопроса дают NotGranted(canRequest = true) без
    // rationale: это «ещё не спрашивали». Отказ, после которого спросить можно, на Android несёт
    // shouldShowRationale. Отказ без возможности спросить — canRequest == false.
    is LocationPermission.NotGranted -> when {
        canRequest == false -> LocationAccess.DeniedPermanently
        shouldShowRationale -> LocationAccess.Denied
        else -> LocationAccess.NotAsked
    }
    LocationPermission.NotDetermined -> LocationAccess.NotAsked
    else -> LocationAccess.NotAsked
}
