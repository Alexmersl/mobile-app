package app.fieldlog.presentation

/**
 * Вкладки приложения. Порядок констант = порядок в нижней панели.
 * Чистый Kotlin: без Compose, чтобы логику можно было проверить в commonTest.
 */
enum class AppTab(val title: String, val placeholder: String) {
    Map(title = "Карта", placeholder = "Карта скоро"),
    Dictionary(title = "Словарь", placeholder = "Скоро"),
    Achievements(title = "Ачивки", placeholder = "Скоро"),
    ;

    companion object {
        /** Вкладка при старте. */
        val Default: AppTab = Map

        /**
         * Вкладка по сохранённому индексу. Если индекс не из диапазона
         * (например, после изменения набора вкладок), возвращается [Default].
         */
        fun fromIndex(index: Int): AppTab = entries.getOrNull(index) ?: Default
    }
}
