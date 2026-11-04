package app.fieldlog.presentation.map

import org.maplibre.compose.map.DefaultMapRuntime

private var configured = false

/**
 * Настраивает общий движок карты: запросы к OpenFreeMap идут через [openFreeMapResourceProvider]
 * с User-Agent приложения.
 *
 * Библиотека разрешает настройку только до создания первой карты, повторный вызов бросает
 * исключение. Здесь вызов идемпотентен: после первого успешного (или после того, как движок
 * уже создан) функция ничего не делает.
 */
fun configureMapRuntime() {
    if (configured) return
    try {
        DefaultMapRuntime.configure {
            resourceProvider = openFreeMapResourceProvider()
        }
    } catch (_: IllegalStateException) {
        // Движок уже создан (например, пересоздана Activity): настроить поздно, оставляем как есть.
    }
    configured = true
}
