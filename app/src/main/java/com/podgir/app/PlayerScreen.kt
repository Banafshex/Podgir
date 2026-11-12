package com.podgir.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

private val Lime = Color(0xFFD1F36B)
private val Ink = Color(0xFF111118)
private fun clock(ms: Long): String { val s = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0)); return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

@Composable
fun PlayerScreen(episode: Episode, fa: Boolean, playing: Boolean, position: Long, duration: Long, speed: Float, sleepMinutes: Int, queue: List<Episode>, onClose: () -> Unit, onToggle: () -> Unit, onSeek: (Long) -> Unit, onSpeed: () -> Unit, onSleep: () -> Unit, onSkip: () -> Unit, onRemove: (Episode) -> Unit, onMove: (Int, Int) -> Unit) {
    var showQueue by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (playing) 1f else .92f, animationSpec = spring(dampingRatio = .75f, stiffness = Spring.StiffnessLow), label = "artwork")
    val pulse = rememberInfiniteTransition(label = "glow")
    val glow by pulse.animateFloat(.35f, .65f, infiniteRepeatable(tween(2500), RepeatMode.Reverse), label = "glow alpha")
    Dialog(onDismissRequest = { if (showQueue) showQueue = false else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF594A78), Color(0xFF242032), Color(0xFF101017), Color(0xFF101017)))).statusBarsPadding().navigationBarsPadding()) {
            AnimatedContent(targetState = showQueue, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) }, label = "player page") { showingQueue ->
                if (showingQueue) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showQueue = false }) { Icon(Icons.Default.ArrowBack, if (fa) "بازگشت" else "Back", tint = Color.White) }
                            Text(if (fa) "صف پخش" else "Play queue", Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            Spacer(Modifier.width(48.dp))
                        }
                        Text(if (fa) "در حال پخش" else "NOW PLAYING", color = Lime, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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
                        val artwork = (maxWidth - 76.dp).coerceAtMost((maxHeight * .38f)).coerceAtMost(340.dp)
                        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, if (fa) "بستن پخش‌کننده" else "Close player", tint = Color.White, modifier = Modifier.size(30.dp)) }
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Text(if (fa) "در حال شنیدن" else "NOW PLAYING", color = Color.White.copy(.65f), style = MaterialTheme.typography.labelSmall, letterSpacing = 2.sp); Text(episode.podcast, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge) }
                                IconButton(onClick = { showQueue = true }) { Icon(Icons.Default.QueueMusic, if (fa) "صف پخش" else "Queue", tint = Color.White) }
                            }
                            Spacer(Modifier.weight(.7f))
                            Box(contentAlignment = Alignment.Center) {
                                Box(Modifier.size(artwork * 1.05f).scale(scale).shadow(32.dp, RoundedCornerShape(30.dp), ambientColor = Color(0xFFB99BFF).copy(alpha = glow), spotColor = Color(0xFFB99BFF)).background(Color(0xFF55466B), RoundedCornerShape(30.dp)))
                                AsyncImage(model = episode.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(artwork).scale(scale).clip(RoundedCornerShape(26.dp)).background(Color(0xFF55466B)))
                            }
                            Spacer(Modifier.weight(.7f))
                            Column(Modifier.fillMaxWidth()) {
                                Text(episode.title, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(6.dp))
                                Text(episode.podcast, color = Color.White.copy(.62f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            }
                            Spacer(Modifier.height(18.dp))
                            // Media progress always flows left-to-right, independent of UI language.
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Column {
                                    Slider(value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f, onValueChange = { onSeek((it * duration).toLong()) }, colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = Color.White.copy(.23f)))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(clock(position), color = Color.White.copy(.6f), style = MaterialTheme.typography.labelSmall); Text(clock(duration), color = Color.White.copy(.6f), style = MaterialTheme.typography.labelSmall) }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                    IconButton(onClick = { onSeek((position - 15000).coerceAtLeast(0)) }) { Icon(Icons.Default.Replay, if (fa) "۱۵ ثانیه عقب" else "Back 15 seconds", tint = Color.White, modifier = Modifier.size(30.dp)) }
                                    Surface(onClick = onToggle, color = Lime, shape = CircleShape, modifier = Modifier.size(72.dp)) { Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play", tint = Ink, modifier = Modifier.size(38.dp)) } }
                                    IconButton(onClick = { onSeek(position + 30000) }) { Icon(Icons.Default.Forward30, if (fa) "۳۰ ثانیه جلو" else "Forward 30 seconds", tint = Color.White, modifier = Modifier.size(30.dp)) }
                                    IconButton(onClick = onSkip, enabled = queue.isNotEmpty()) { Icon(Icons.Default.SkipNext, if (fa) "قسمت بعدی" else "Next episode", tint = if (queue.isEmpty()) Color.White.copy(.3f) else Color.White) }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = onSpeed) { Text("${speed}×", color = Color.White, fontWeight = FontWeight.Bold) }
                                TextButton(onClick = onSleep) { Icon(Icons.Default.Bedtime, null, tint = Color.White.copy(.7f), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(if (sleepMinutes == 0) { if (fa) "تایمر خواب" else "Sleep timer" } else "${sleepMinutes} min", color = Color.White.copy(.8f)) }
                                IconButton(onClick = { showQueue = true }) { Icon(Icons.Default.QueueMusic, if (fa) "صف پخش" else "Queue", tint = Color.White) }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun QueueRow(e: Episode, fa: Boolean, current: Boolean = false, onRemove: (() -> Unit)? = null, onUp: (() -> Unit)? = null, onDown: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.White.copy(.08f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = e.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(50.dp).clip(RoundedCornerShape(10.dp)).background(Color.White.copy(.1f)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(e.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(e.podcast, color = Color.White.copy(.55f), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        if (current) Icon(Icons.Default.GraphicEq, null, tint = Lime) else {
            Column { IconButton(onClick = { onUp?.invoke() }, enabled = onUp != null, modifier = Modifier.size(25.dp)) { Icon(Icons.Default.KeyboardArrowUp, if (fa) "بالا" else "Move up", tint = Color.White.copy(if (onUp == null) .2f else .8f)) }; IconButton(onClick = { onDown?.invoke() }, enabled = onDown != null, modifier = Modifier.size(25.dp)) { Icon(Icons.Default.KeyboardArrowDown, if (fa) "پایین" else "Move down", tint = Color.White.copy(if (onDown == null) .2f else .8f)) } }
            IconButton(onClick = { onRemove?.invoke() }) { Icon(Icons.Default.Close, if (fa) "حذف از صف" else "Remove from queue", tint = Color.White.copy(.7f)) }
        }
    }
}
