package app.fieldlog.presentation.map

import kotlin.math.max

/**
 * Чистая логика вкладки «Карта»: без Compose, MapLibre и платформ,
 * чтобы её можно было проверить в commonTest.
 */

/** Точка на земле в градусах. */
data class GeoPoint(val latitude: Double, val longitude: Double) {
    init {
        require(latitude in -90.0..90.0) { "Широта вне диапазона: $latitude" }
        require(longitude in -180.0..180.0) { "Долгота вне диапазона: $longitude" }
    }
}

/** Куда смотрит камера: центр и зум. */
data class CameraTarget(val point: GeoPoint, val zoom: Double)

object MapDefaults {
    /** Стиль OpenFreeMap (проверен 29 окт 2026: отдаёт style.json, источник `planet`). */
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    /** Хост, которому нужен уникальный User-Agent приложения (условия OpenFreeMap). */
    const val OPENFREEMAP_URL_PREFIX = "https://tiles.openfreemap.org/"

    /**
     * Уникальный User-Agent приложения (OpenFreeMap требует его при загрузке районов,
     * в том числе просит указывать его у всех запросов приложения).
     */
    const val USER_AGENT = "FieldLog/0.1.0 (app.fieldlog; Android+iOS)"

    /**
     * Видимая атрибуция. OpenFreeMap требует «OpenFreeMap © OpenMapTiles Data from OpenStreetMap»;
     * OpenMapTiles и OSMF принимают «© OpenMapTiles © OpenStreetMap contributors».
     * Эта строка содержит все названия из обоих вариантов.
     */
    const val ATTRIBUTION = "OpenFreeMap © OpenMapTiles © OpenStreetMap contributors"

    /** Центр России: показываем, пока положение неизвестно. */
    val RUSSIA_CENTER = GeoPoint(latitude = 55.75, longitude = 37.62)
    const val RUSSIA_ZOOM = 5.0

    /** Зум, к которому камера подлетает к человеку, если сейчас карта дальше. */
    const val LOCATION_ZOOM = 14.0
}

/** Камера при старте: у человека, если положение уже известно, иначе центр России. */
fun initialCamera(location: GeoPoint?): CameraTarget =
    if (location != null) {
        CameraTarget(location, MapDefaults.LOCATION_ZOOM)
    } else {
        CameraTarget(MapDefaults.RUSSIA_CENTER, MapDefaults.RUSSIA_ZOOM)
    }

/** Камера для кнопки «Моё положение»: центр на человеке, зум не меньше [MapDefaults.LOCATION_ZOOM]. */
fun cameraOnLocation(location: GeoPoint, currentZoom: Double): CameraTarget =
    CameraTarget(location, max(currentZoom, MapDefaults.LOCATION_ZOOM))

/**
 * Заголовки для запроса карты. User-Agent приложения добавляется ко всем запросам к OpenFreeMap
 * (стиль, тайлы, шрифты, спрайты). Чужим хостам заголовки не отдаются.
 */
fun requestHeaders(url: String): Map<String, String> =
    if (url.startsWith(MapDefaults.OPENFREEMAP_URL_PREFIX)) {
        mapOf("User-Agent" to MapDefaults.USER_AGENT)
    } else {
        emptyMap()
    }

/** Разрешение на геолокацию в терминах приложения (без типов библиотеки). */
enum class LocationAccess {
    /** Ещё не спрашивали или платформа не сообщила. */
    NotAsked,
    Granted,

    /** Отказ, но спросить ещё можно. */
    Denied,

    /** Отказ без возможности спросить снова: только системные настройки. */
    DeniedPermanently,
}

/** Что делает кнопка «Моё положение» сейчас. */
enum class LocationButtonAction {
    RequestPermission,
    CenterOnLocation,

    /** Разрешение есть, но первого положения ещё нет. */
    WaitForFix,

    /** Спросить снова нельзя: подсказка про системные настройки. */
    ExplainSettings,
}

fun locationButtonAction(access: LocationAccess, hasFix: Boolean): LocationButtonAction =
    when (access) {
        LocationAccess.NotAsked, LocationAccess.Denied -> LocationButtonAction.RequestPermission
        LocationAccess.Granted ->
            if (hasFix) LocationButtonAction.CenterOnLocation else LocationButtonAction.WaitForFix
        LocationAccess.DeniedPermanently -> LocationButtonAction.ExplainSettings
    }

/**
 * Системный запрос разрешения при первом открытии карты: один раз за запуск приложения,
 * и только если спросить ещё можно. Дальше человек просит сам кнопкой.
 */
fun shouldAutoRequestPermission(access: LocationAccess, alreadyAsked: Boolean): Boolean =
    !alreadyAsked && (access == LocationAccess.NotAsked || access == LocationAccess.Denied)

/** Подсказка под картой. `null` — подсказка не нужна. Индикатор положения при отказе не рисуется. */
fun locationHint(access: LocationAccess, hasFix: Boolean): String? =
    when (access) {
        LocationAccess.Granted -> if (hasFix) null else "Определяем положение…"
        LocationAccess.NotAsked -> null
        LocationAccess.Denied -> "Положение не показано: нет доступа к геолокации"
        LocationAccess.DeniedPermanently ->
            "Положение не показано. Разрешить доступ можно в настройках телефона"
    }

/**
 * Один раз подлетаем к человеку, когда пришло первое положение, если он сам ещё не двигал карту.
 * Дальше камеру ведёт только человек и кнопка.
 */
fun shouldAutoCenter(alreadyCentered: Boolean, userMovedCamera: Boolean): Boolean =
    !alreadyCentered && !userMovedCamera
