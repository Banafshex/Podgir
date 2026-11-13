package com.podgir.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import java.util.concurrent.TimeUnit

private val PlayerAccent = PlayGreen
private val Ink = Color(0xFF071A22)
private fun clock(ms: Long): String { val s = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0)); return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(episode: Episode, fa: Boolean, playing: Boolean, position: Long, duration: Long, speed: Float, sleepMinutes: Int, queue: List<Episode>, onClose: () -> Unit, onToggle: () -> Unit, onSeek: (Long) -> Unit, onSpeed: () -> Unit, onSleep: () -> Unit, rewindSeconds: Int, forwardSeconds: Int, favorite: Boolean, downloadStatus: Int, onFavorite: () -> Unit, onDownload: () -> Unit, onBookmark: (Long, String) -> Unit, onShare: () -> Unit, onPrevious: () -> Unit, onSkip: () -> Unit, onRemove: (Episode) -> Unit, onMove: (Int, Int) -> Unit) {
    var showQueue by remember { mutableStateOf(false) }
    var timedPage by remember { mutableIntStateOf(0) }
    var showBookmark by remember { mutableStateOf(false) }
    var bookmarkPosition by remember { mutableLongStateOf(0L) }
    var note by remember { mutableStateOf("") }

    val scale by animateFloatAsState(if (playing) 1f else .92f, animationSpec = spring(dampingRatio = .75f, stiffness = Spring.StiffnessLow), label = "artwork")
    val pulse = rememberInfiniteTransition(label = "glow")
    val glow by pulse.animateFloat(.35f, .65f, infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "glow alpha")
    Dialog(onDismissRequest = { if (showQueue) showQueue = false else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        DialogSystemBars(dark = true, status = Color(0xFF161B35), navigation = Color(0xFF0B0D17))
        Box(Modifier.fillMaxSize().background(Color(0xFF0B0D17))) {
            PlayerAmbientBackground(Modifier.matchParentSize())
            Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            AnimatedContent(targetState = showQueue, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) }, label = "player page") { showingQueue ->
                if (showingQueue) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showQueue = false }) { Icon(Icons.Default.ArrowBack, if (fa) "بازگشت" else "Back", tint = Color.White) }
                            Text(if (fa) "صف پخش" else "Play queue", Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Spacer(Modifier.width(48.dp))
                        }
                        Text(if (fa) "در حال پخش" else "NOW PLAYING", color = PlayerAccent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(14.dp))
                        QueueRow(episode, fa, current = true)
                        Spacer(Modifier.height(28.dp))
                        Text(if (fa) "قسمت‌های بعدی · ${queue.size}" else "UP NEXT · ${queue.size}", color = Color.White.copy(.65f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(Modifier.height(12.dp))
                        if (queue.isEmpty()) Text(if (fa) "صف خالی است. از صفحهٔ قسمت‌ها، یک قسمت به صف اضافه کنید." else "Your queue is empty. Add episodes from a podcast page.", color = Color.White.copy(.65f), modifier = Modifier.padding(vertical = 24.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(queue, key = { _, item -> item.audio }) { index, item ->
                                QueueRow(item, fa, onRemove = { onRemove(item) }, onUp = if (index > 0) ({ onMove(index, index - 1) }) else null, onDown = if (index < queue.lastIndex) ({ onMove(index, index + 1) }) else null)
                            }
                        }
                    }
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        // A scrollable layout keeps every control reachable on small screens and with large text.
                        val artwork = (maxWidth - 80.dp).coerceAtMost(maxHeight * .30f).coerceAtMost(300.dp).coerceAtLeast(120.dp)
                        val scrollState = rememberScrollState()
                        val latestClose by rememberUpdatedState(onClose)
                        Column(Modifier.fillMaxSize().swipeDownToClose(scrollState) { latestClose() }.verticalScroll(scrollState).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.width(38.dp).height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = .32f)))
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, if (fa) "بستن پخش‌کننده" else "Close player", tint = Color.White, modifier = Modifier.size(30.dp)) }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(if (fa) "در حال شنیدن" else "NOW PLAYING", color = Color.White.copy(.65f), style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp); Text(episode.podcast, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
                                IconButton(onClick = { showQueue = true }) { Icon(Icons.Default.QueueMusic, if (fa) "صف پخش" else "Queue", tint = Color.White) }
                            }
                            Spacer(Modifier.height(24.dp))
                            Box(contentAlignment = Alignment.Center) {
                                Box(Modifier.size(artwork).scale(scale).shadow(24.dp, RoundedCornerShape(24.dp), ambientColor = Color(0xFF9CEDE6).copy(alpha = glow), spotColor = Color(0xFF9CEDE6)).background(Color(0xFF28344E), RoundedCornerShape(24.dp)))
                                AsyncImage(model = episode.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(artwork).scale(scale).clip(RoundedCornerShape(24.dp)).background(Color(0xFF28344E)))
                            }
                            Spacer(Modifier.height(24.dp))
                            Text(episode.title, modifier = Modifier.fillMaxWidth(), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(6.dp))
                            Text(episode.podcast, modifier = Modifier.fillMaxWidth(), color = Color.White.copy(.65f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(14.dp))
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Column(Modifier.fillMaxWidth()) {
                                    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                                    Slider(value = fraction, onValueChange = { onSeek((it * duration).toLong()) }, enabled = duration > 0,
                                        thumb = { Box(Modifier.size(14.dp).background(PlayerAccent, CircleShape)) },
                                        track = { state -> LinearProgressIndicator(progress = { state.value }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = PlayerAccent, trackColor = Color.White.copy(.22f)) })
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(clock(position), color = Color.White.copy(.65f), style = MaterialTheme.typography.labelSmall); Text(clock(duration), color = Color.White.copy(.65f), style = MaterialTheme.typography.labelSmall) }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                    IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, if (fa) "قسمت قبلی" else "Previous episode", tint = Color.White, modifier = Modifier.size(30.dp)) }
                                    SkipButton(seconds = rewindSeconds, backwards = true, description = if (fa) "$rewindSeconds ثانیه عقب" else "Back $rewindSeconds seconds") { onSeek((position - rewindSeconds * 1000L).coerceAtLeast(0)) }
                                    Surface(onClick = onToggle, color = PlayerAccent, shape = CircleShape, modifier = Modifier.size(64.dp)) { Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) { if (fa) "توقف" else "Pause" } else { if (fa) "پخش" else "Play" }, tint = Ink, modifier = Modifier.size(36.dp)) } }
                                    SkipButton(seconds = forwardSeconds, backwards = false, description = if (fa) "$forwardSeconds ثانیه جلو" else "Forward $forwardSeconds seconds") { onSeek(position + forwardSeconds * 1000L) }
                                    IconButton(onClick = onSkip, enabled = queue.isNotEmpty()) { Icon(Icons.Default.SkipNext, if (fa) "قسمت بعدی" else "Next episode", tint = if (queue.isEmpty()) Color.White.copy(.3f) else Color.White, modifier = Modifier.size(30.dp)) }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = onSpeed) { Text("${speed}×", color = Color.White, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = onSleep) { Icon(Icons.Default.Bedtime, null, tint = Color.White.copy(.7f), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (sleepMinutes == 0) { if (fa) "تایمر خواب" else "Sleep timer" } else "${sleepMinutes} min", color = Color.White.copy(.8f)) }
                                IconButton(onClick = { showQueue = true }) { Icon(Icons.Default.QueueMusic, if (fa) "صف پخش" else "Queue", tint = Color.White) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (fa) "علاقه‌مندی" else "Favorite", tint = if (favorite) PlayerAccent else Color.White.copy(.8f)) }
                                IconButton(onClick = { bookmarkPosition = position; note = ""; showBookmark = true }) { Icon(Icons.Default.BookmarkAdd, if (fa) "ذخیره لحظه" else "Save moment", tint = Color.White.copy(.8f)) }
                                IconButton(onClick = onDownload, enabled = downloadStatus == 0 || downloadStatus == android.app.DownloadManager.STATUS_FAILED) { Icon(if (downloadStatus == android.app.DownloadManager.STATUS_SUCCESSFUL) Icons.Default.CheckCircle else Icons.Default.Download, if (fa) "دانلود" else "Download", tint = if (downloadStatus == android.app.DownloadManager.STATUS_SUCCESSFUL) PlayerAccent else Color.White.copy(.8f)) }
                                IconButton(onClick = onShare) { Icon(Icons.Default.Share, if (fa) "اشتراک‌گذاری فایل صوتی" else "Share audio link", tint = Color.White.copy(.8f)) }
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                TextButton(onClick = { timedPage = 1 }) { Icon(Icons.Default.ViewList, null, tint = Color.White.copy(.8f)); Spacer(Modifier.width(6.dp)); Text(if (fa) "فصل‌ها" else "Chapters", color = Color.White.copy(.8f)) }
                                TextButton(onClick = { timedPage = 2 }) { Icon(Icons.Default.Subtitles, null, tint = Color.White.copy(.8f)); Spacer(Modifier.width(6.dp)); Text(if (fa) "متن قسمت" else "Transcript", color = Color.White.copy(.8f)) }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
            }
        }
    }
    if (timedPage != 0) TimedContentScreen(episode, timedPage == 1, fa, position, onSeek, onClose = { timedPage = 0 })
    if (showBookmark) AlertDialog(onDismissRequest = { showBookmark = false }, title = { Text(if (fa) "ذخیرهٔ لحظه" else "Save moment") }, text = { Column { Text(clock(bookmarkPosition)); Spacer(Modifier.height(12.dp)); OutlinedTextField(value = note, onValueChange = { if (it.length <= 500) note = it }, label = { Text(if (fa) "یادداشت (اختیاری)" else "Note (optional)") }, maxLines = 4) } }, confirmButton = { TextButton(onClick = { onBookmark(bookmarkPosition, note); note = ""; showBookmark = false }) { Text(if (fa) "ذخیره" else "Save") } }, dismissButton = { TextButton(onClick = { showBookmark = false }) { Text(if (fa) "انصراف" else "Cancel") } })
}

@Composable private fun QueueRow(e: Episode, fa: Boolean, current: Boolean = false, onRemove: (() -> Unit)? = null, onUp: (() -> Unit)? = null, onDown: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.08f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = e.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(.1f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(e.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(e.podcast, color = Color.White.copy(.55f), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        if (current) Icon(Icons.Default.GraphicEq, null, tint = PlayerAccent) else {
            Column { IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null, modifier = Modifier.size(25.dp)) { Icon(Icons.Default.KeyboardArrowUp, if (fa) "بالا" else "Move up", tint = Color.White.copy(if (onUp == null) .2f else .8f)) }; IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null, modifier = Modifier.size(25.dp)) { Icon(Icons.Default.KeyboardArrowDown, if (fa) "پایین" else "Move down", tint = Color.White.copy(if (onDown == null) .2f else .8f)) } }
            IconButton(onClick = { onRemove?.invoke() }) { Icon(Icons.Default.Close, if (fa) "حذف از صف" else "Remove from queue", tint = Color.White.copy(.7f)) }
        }
    }
}

/** Mirrored circular arrows with a separate centered number, independent of UI direction. */
@Composable private fun SkipButton(seconds: Int, backwards: Boolean, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = description }) {
        Box(Modifier.size(36.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension * .37f
                val cx = center.x; val cy = center.y
                val start = if (backwards) -45f else -135f
                val sweep = if (backwards) -270f else 270f
                drawArc(Color.White, startAngle = start, sweepAngle = sweep, useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius), size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
                val angle = Math.toRadians((start + sweep).toDouble())
                val x = cx + radius * kotlin.math.cos(angle).toFloat(); val y = cy + radius * kotlin.math.sin(angle).toFloat()
                val direction = if (backwards) -1f else 1f
                val dx = -kotlin.math.sin(angle).toFloat() * direction; val dy = kotlin.math.cos(angle).toFloat() * direction
                val length = 5.dp.toPx(); val half = 2.8.dp.toPx()
                val arrow = Path().apply { moveTo(x + dx * length * .5f, y + dy * length * .5f); lineTo(x - dx * length * .6f - dy * half, y - dy * length * .6f + dx * half); lineTo(x - dx * length * .6f + dy * half, y - dy * length * .6f - dx * half); close() }
                drawPath(arrow, Color.White)
            }
            Text("$seconds", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

/** Observe a deliberate downward gesture without consuming slider or scroll events. */
private fun Modifier.swipeDownToClose(scroll: ScrollState, onClose: () -> Unit): Modifier = pointerInput(scroll) {
    val threshold = 84.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startedAtTop = scroll.value == 0
        var last = down.position
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change != null) last = change.position
            val pressed = change?.pressed == true
        } while (pressed)
        val dy = last.y - down.position.y
        val dx = kotlin.math.abs(last.x - down.position.x)
        if (startedAtTop && scroll.value == 0 && dy > threshold && dy > dx * 1.5f) onClose()
    }
}
