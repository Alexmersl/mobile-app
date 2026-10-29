package app.fieldlog

import androidx.compose.ui.window.ComposeUIViewController
import app.fieldlog.presentation.App

@Suppress("unused", "FunctionName") // вызывается из Swift
fun MainViewController() = ComposeUIViewController { App() }
