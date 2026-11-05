package app.fieldlog.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.fieldlog.presentation.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Движок карты держит ответы сервера в нативной памяти через JavaCPP. Его предохранитель
        // «физическая память процесса больше предела» считает всю память процесса (карта, кэш,
        // видеопамять), а не только свои буферы, и на скачивании района бросает OutOfMemoryError
        // посреди запроса: приложение падает. Предохранитель отключаем (0), пока не создан движок;
        // от нехватки памяти система всё равно защищает сама.
        System.setProperty("org.bytedeco.javacpp.maxPhysicalBytes", "0")
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}
