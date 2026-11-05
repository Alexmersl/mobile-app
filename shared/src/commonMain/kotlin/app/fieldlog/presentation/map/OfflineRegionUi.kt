package app.fieldlog.presentation.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.maplibre.compose.camera.CameraUpdate
import org.maplibre.compose.map.MapState
import org.maplibre.spatialk.geojson.Position

/** Что сейчас показывает офлайн-слой поверх карты. */
internal enum class OfflineMode {
    /** Обычная карта: только кнопки «Скачать район» и «Районы». */
    Browse,

    /** Рамка на карте и панель оценки перед скачиванием. */
    Select,

    /** Список скачанных и скачиваемых районов. */
    List,
}

private val Green = Color(0xFF1B5E20)
private val Red = Color(0xFFB00020)
private val TextColor = Color(0xFF222222)
private val Muted = Color(0xFF666666)
private val PanelColor = Color.White.copy(alpha = 0.97f)

// Рамка выбора: отступы от краёв карты. Снизу остаётся место под панель и атрибуцию.
private val FrameSide = 20.dp
private val FrameTop = 84.dp
private val FrameBottomGap = 236.dp
private val MinFrameSide = 96.dp

/**
 * Слой «Офлайн-районы» поверх карты.
 *
 * Жест выбора района: рамка стоит на экране неподвижно, человек обычными жестами карты
 * (перетаскивание, щипок, двойной тап) подводит нужное место под рамку. Скачивается то, что
 * внутри рамки, начиная с масштаба, при котором она сейчас показана.
 */
@Composable
internal fun OfflineRegionLayer(
    mapState: MapState,
    mode: OfflineMode,
    onModeChange: (OfflineMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = remember { OfflineRegions.controller }
    val regionsState by controller.state.collectAsState(RegionsState.Loading)
    val notice by controller.notice.collectAsState()
    val regions = (regionsState as? RegionsState.Ready)?.regions.orEmpty()

    Box(modifier = modifier.fillMaxSize()) {
        when (mode) {
            OfflineMode.Browse -> BrowseButtons(
                regionsState = regionsState,
                regions = regions,
                onSelect = { onModeChange(OfflineMode.Select) },
                onList = { onModeChange(OfflineMode.List) },
            )
            OfflineMode.Select -> SelectionOverlay(
                mapState = mapState,
                controller = controller,
                regionsReady = regionsState is RegionsState.Ready,
                onCancel = { onModeChange(OfflineMode.Browse) },
                onStarted = { onModeChange(OfflineMode.List) },
            )
            OfflineMode.List -> RegionList(
                mapState = mapState,
                controller = controller,
                regionsState = regionsState,
                onClose = { onModeChange(OfflineMode.Browse) },
            )
        }

        notice?.let { text ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = if (mode == OfflineMode.Browse) 64.dp else 8.dp, end = 64.dp),
            ) {
                Card(text = text, color = Red, onClick = controller::dismissNotice, hint = "Нажмите, чтобы скрыть")
            }
        }
    }
}

@Composable
private fun BrowseButtons(
    regionsState: RegionsState,
    regions: List<OfflineRegion>,
    onSelect: () -> Unit,
    onList: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PillButton(text = "Скачать район", onClick = onSelect)
        val label = when (regionsState) {
            is RegionsState.Ready -> if (regions.isEmpty()) "Районы" else "Районы: ${regions.size}"
            else -> "Районы"
        }
        PillButton(text = label, onClick = onList)
    }
}

// --- Выбор района -----------------------------------------------------------------------------

@Composable
private fun SelectionOverlay(
    mapState: MapState,
    controller: OfflineRegionController,
    regionsReady: Boolean,
    onCancel: () -> Unit,
    onStarted: () -> Unit,
) {
    var requestedMaxZoom by remember { mutableIntStateOf(MAX_DETAIL_ZOOM) }
    val density = LocalDensity.current.density

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val frame = frameOf(maxWidth, maxHeight)

        // Читаем камеру и вид в композиции: рамка пересчитывается при каждом движении карты.
        val camera = mapState.cameraPosition
        val viewport = mapState.viewport
        val bounds = remember(camera, viewport, frame) {
            if (viewport == null) null else frameBounds(mapState, frame)
        }
        val check = checkRegion(bounds, camera.zoom, requestedMaxZoom)
        val plan = zoomPlan(camera.zoom, requestedMaxZoom)

        Dim(frame, maxWidth, maxHeight)
        Box(
            modifier = Modifier
                .offset(frame.left, frame.top)
                .size(frame.width, frame.height)
                .border(3.dp, Green),
        )

        Box(Modifier.align(Alignment.TopCenter).padding(start = 8.dp, end = 64.dp, top = 8.dp)) {
            Card(text = "Двигайте и масштабируйте карту: скачается то, что внутри рамки.")
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 8.dp, end = 8.dp, bottom = 40.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PanelColor)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val (text, color) = when (check) {
                is RegionCheck.Allowed ->
                    "Примерно ${tilesText(check.tiles)} из ${OfflineLimits.MAX_TILES}. " +
                        "Масштаб ${check.plan.minZoom}–${check.plan.maxZoom}." to TextColor
                is RegionCheck.TooBig -> check.message to Red
                is RegionCheck.Unknown -> check.message to Muted
            }
            Label(text, color = color)
            if (check is RegionCheck.Allowed) {
                Label(
                    "Размер заранее неизвестен: пакет больше ${OfflineLimits.MAX_BYTES / 1_000_000} МБ будет остановлен и удалён.",
                    color = Muted,
                    size = 12,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("Детальность до масштаба", color = Muted)
                PillButton(text = "−", enabled = plan.maxZoom > plan.minZoom, onClick = { requestedMaxZoom = plan.maxZoom - 1 })
                Label("${plan.maxZoom}", bold = true)
                PillButton(text = "+", enabled = plan.maxZoom < MAX_DETAIL_ZOOM, onClick = { requestedMaxZoom = plan.maxZoom + 1 })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(text = "Отмена", onClick = onCancel)
                PillButton(
                    text = "Скачать",
                    primary = true,
                    enabled = regionsReady && check is RegionCheck.Allowed && bounds != null,
                    onClick = {
                        // Лимит проверен до создания пакета: сюда доходит только Allowed.
                        if (check is RegionCheck.Allowed && bounds != null) {
                            controller.download(bounds = bounds, plan = check.plan, pixelRatio = density)
                            onStarted()
                        }
                    },
                )
            }
        }
    }
}

/** Рамка в dp в координатах карты. */
private class Frame(val left: Dp, val top: Dp, val width: Dp, val height: Dp)

private fun frameOf(mapWidth: Dp, mapHeight: Dp): Frame {
    val width = (mapWidth - FrameSide * 2).coerceAtLeast(MinFrameSide)
    val height = (mapHeight - FrameTop - FrameBottomGap).coerceAtLeast(MinFrameSide)
    return Frame(left = FrameSide, top = FrameTop, width = width, height = height)
}

/** Район под рамкой: охват четырёх углов (карта может быть повёрнута). */
private fun frameBounds(mapState: MapState, frame: Frame): GeoBounds? {
    val corners = listOf(
        DpOffset(frame.left, frame.top),
        DpOffset(frame.left + frame.width, frame.top),
        DpOffset(frame.left + frame.width, frame.top + frame.height),
        DpOffset(frame.left, frame.top + frame.height),
    ).map { mapState.positionFromScreenLocation(it) ?: return null }
    return boundsOf(corners.map { GeoPoint(it.latitude.coerceIn(-90.0, 90.0), it.longitude) })
}

/** Затемнение вне рамки: четыре полосы, сквозь них жесты доходят до карты. */
@Composable
private fun Dim(frame: Frame, mapWidth: Dp, mapHeight: Dp) {
    val dim = Color.Black.copy(alpha = 0.3f)
    val right = frame.left + frame.width
    val bottom = frame.top + frame.height
    Box(Modifier.fillMaxWidth().height(frame.top).background(dim))
    Box(Modifier.offset(y = bottom).fillMaxWidth().height((mapHeight - bottom).coerceAtLeast(0.dp)).background(dim))
    Box(Modifier.offset(y = frame.top).width(frame.left).height(frame.height).background(dim))
    Box(Modifier.offset(x = right, y = frame.top).width((mapWidth - right).coerceAtLeast(0.dp)).height(frame.height).background(dim))
}

// --- Список районов ---------------------------------------------------------------------------

@Composable
private fun androidx.compose.foundation.layout.BoxScope.RegionList(
    mapState: MapState,
    controller: OfflineRegionController,
    regionsState: RegionsState,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 8.dp, end = 8.dp, bottom = 40.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PanelColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Label("Скачанные районы", bold = true, size = 17)
            PillButton(text = "Закрыть", onClick = onClose)
        }
        when (regionsState) {
            RegionsState.Loading -> Label("Загрузка списка…", color = Muted)
            is RegionsState.Failed -> Label(regionsState.message, color = Red)
            is RegionsState.Ready -> {
                if (regionsState.regions.isEmpty()) {
                    Label("Пока нет районов. Нажмите «Скачать район» на карте.", color = Muted)
                } else {
                    Column(
                        modifier = Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        regionsState.regions.forEach { region ->
                            key(region.id) {
                                RegionRow(
                                    region = region,
                                    controller = controller,
                                    onShow = {
                                        region.bounds?.let { bounds ->
                                            val center = bounds.center
                                            scope.launch {
                                                mapState.animateCamera(
                                                    CameraUpdate(
                                                        center = Position(longitude = center.longitude, latitude = center.latitude),
                                                        zoom = region.minZoom,
                                                    ),
                                                )
                                            }
                                            onClose()
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RegionRow(region: OfflineRegion, controller: OfflineRegionController, onShow: () -> Unit) {
    // Потоки привязаны к пакету; при пересоздании обёртки держим первый, чтобы не мигало «Ожидание».
    val metadata by remember(region.id) { region.metadata }.collectAsState(null)
    val progress by remember(region.id) { region.progress }.collectAsState(RegionProgress.NotReported)
    var confirmDelete by remember(region.id) { mutableStateOf(false) }

    val name = decodeRegionName(metadata)
    val status = describeProgress(progress)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Label(name, bold = true)
        Label(status.text, color = if (status.state == RegionState.Failed) Red else Muted, size = 13)
        status.fraction?.let { fraction ->
            if (status.state != RegionState.Complete) ProgressBar(fraction)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (confirmDelete) {
                Label("Удалить район?", color = Red)
                PillButton(text = "Да", onClick = { controller.delete(region) })
                PillButton(text = "Нет", onClick = { confirmDelete = false })
            } else {
                when (status.state) {
                    RegionState.Downloading -> {
                        PillButton(text = "Пауза", onClick = { controller.pause(region) })
                        // Отмена удаляет недокачанный пакет сразу.
                        PillButton(text = "Отменить", onClick = { controller.delete(region) })
                    }
                    RegionState.Paused, RegionState.Waiting -> {
                        PillButton(text = "Продолжить", primary = true, onClick = { controller.resume(region) })
                        PillButton(text = "Удалить", onClick = { confirmDelete = true })
                    }
                    RegionState.Failed -> {
                        PillButton(text = "Продолжить", primary = true, onClick = { controller.retry(region) })
                        PillButton(text = "Удалить", onClick = { confirmDelete = true })
                    }
                    RegionState.Complete -> {
                        if (region.bounds != null) PillButton(text = "Показать", onClick = onShow)
                        PillButton(text = "Удалить", onClick = { confirmDelete = true })
                    }
                }
            }
        }
    }
}

// --- Мелкие элементы --------------------------------------------------------------------------

@Composable
private fun ProgressBar(fraction: Float) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFDDDDDD))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(Green))
    }
}

@Composable
private fun Label(text: String, color: Color = TextColor, bold: Boolean = false, size: Int = 14) {
    BasicText(
        text = text,
        style = TextStyle(color = color, fontSize = size.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
    )
}

@Composable
private fun Card(text: String, color: Color = TextColor, hint: String? = null, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Label(text, color = color, size = 13)
        if (hint != null) Label(hint, color = Muted, size = 11)
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true) {
    val background = when {
        !enabled -> Color(0xFFCCCCCC)
        primary -> Green
        else -> Color.White
    }
    val foreground = when {
        !enabled -> Color(0xFF777777)
        primary -> Color.White
        else -> Green
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            .border(1.dp, if (enabled) Green else Color(0xFFCCCCCC), RoundedCornerShape(20.dp))
            .let { if (enabled) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = text, style = TextStyle(color = foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold))
    }
}
