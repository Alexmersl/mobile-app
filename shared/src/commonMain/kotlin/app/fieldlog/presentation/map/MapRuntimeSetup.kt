package app.fieldlog.presentation.map

import org.maplibre.compose.map.DefaultMapRuntime
import org.maplibre.compose.resource.MapRequestInterceptor

private var configured = false

/**
 * Настраивает общий движок карты: User-Agent приложения на запросах к OpenFreeMap.
 *
 * Библиотека разрешает настройку только до создания первой карты, повторный вызов бросает
 * исключение. Здесь вызов идемпотентен: после первого успешного (или после того, как движок
 * уже создан) функция ничего не делает.
 */
fun configureMapRuntime() {
    if (configured) return
    try {
        DefaultMapRuntime.configure {
            requestInterceptor = MapRequestInterceptor(headers = { request -> requestHeaders(request.url) })
        }
    } catch (_: IllegalStateException) {
        // Движок уже создан (например, пересоздана Activity): настроить поздно, оставляем как есть.
    }
    configured = true
}
