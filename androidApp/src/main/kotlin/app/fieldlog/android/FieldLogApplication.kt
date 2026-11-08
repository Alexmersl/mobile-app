package app.fieldlog.android

import android.app.Application
import app.fieldlog.data.PinRepository
import app.fieldlog.data.createPinRepository

class FieldLogApplication : Application() {
    /**
     * Хранилище меток. Создаётся при первом обращении, а не при старте процесса: файл БД
     * открывается вне главного потока запуска. Экраны к нему подключит задача 7.
     */
    val pinRepository: PinRepository by lazy { createPinRepository(this) }
}
