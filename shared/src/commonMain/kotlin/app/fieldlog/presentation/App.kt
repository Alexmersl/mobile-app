package app.fieldlog.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fieldlog.presentation.map.MapScreen

const val APP_TITLE = "FieldLog"

private val BarBackground = Color(0xFFF2F2F2)
private val Divider = Color(0xFFCCCCCC)
private val ActiveColor = Color(0xFF1B5E20)
private val InactiveColor = Color(0xFF555555)

/** Корень приложения: три вкладки и нижняя панель. Общий для Android и iOS. */
@Composable
fun App() {
    // Индекс (Int), а не enum: он сохраняется в Bundle на Android без своего Saver.
    var selectedIndex by rememberSaveable { mutableIntStateOf(AppTab.Default.ordinal) }
    val selected = AppTab.fromIndex(selectedIndex)

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.Center,
        ) {
            when (selected) {
                AppTab.Map -> MapScreen()
                else -> TabPlaceholder(selected)
            }
        }
        TabBar(selected = selected, onSelect = { selectedIndex = it.ordinal })
    }
}

/** Заглушка вкладки. Содержимое вкладок появится в следующих задачах плана. */
@Composable
private fun TabPlaceholder(tab: AppTab) {
    BasicText(
        text = tab.placeholder,
        style = TextStyle(color = Color.Black, fontSize = 24.sp, fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun TabBar(selected: AppTab, onSelect: (AppTab) -> Unit) {
    // Фон панели доходит до края экрана, а сами вкладки не заходят под системные панели.
    Column(modifier = Modifier.fillMaxWidth().background(BarBackground)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .height(56.dp),
        ) {
            AppTab.entries.forEach { tab ->
                val isSelected = tab == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(tab) }),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = tab.title,
                        style = TextStyle(
                            color = if (isSelected) ActiveColor else InactiveColor,
                            fontSize = 16.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        ),
                    )
                }
            }
        }
    }
}
