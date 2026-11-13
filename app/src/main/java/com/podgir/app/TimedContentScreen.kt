package com.podgir.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun TimedContentScreen(episode: Episode, chaptersMode: Boolean, fa: Boolean, position: Long, onSeek: (Long) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { Repository(context.applicationContext) }
    val url = if (chaptersMode) episode.chaptersUrl else episode.transcriptUrl
    var loading by remember(url) { mutableStateOf(false) }
    var error by remember(url) { mutableStateOf(false) }
    var chapters by remember(url) { mutableStateOf<List<Chapter>>(emptyList()) }
    var transcript by remember(url) { mutableStateOf<List<TranscriptCue>>(emptyList()) }
    var retry by remember { mutableIntStateOf(0) }
    var follow by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    LaunchedEffect(url, retry) {
        if (url.isNotEmpty()) {
            loading = true; error = false
            try { if (chaptersMode) chapters = repo.chapters(url) else transcript = repo.transcript(url) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = true }
            loading = false
        }
    }
    val active = if (chaptersMode) chapters.indexOfLast { position >= it.startMs } else transcript.indexOfFirst { position >= it.startMs && position < it.endMs }
    LaunchedEffect(active, follow, loading) { if (follow && !loading && active >= 0) listState.animateScrollToItem(active) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (chaptersMode) { if (fa) "فصل‌ها" else "Chapters" } else { if (fa) "متن زمان‌دار" else "Transcript" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(episode.title, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, if (fa) "بستن" else "Close") }
                }
                if (loading) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else if (url.isEmpty()) Text(if (fa) "ناشر برای این قسمت ${if (chaptersMode) "فصل" else "متن زمان‌دار سازگار"} ارائه نکرده است." else "The publisher did not provide ${if (chaptersMode) "chapters" else "a supported timed transcript"} for this episode.", modifier = Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (error) Column(Modifier.padding(vertical = 24.dp)) { Text(if (fa) "دریافت اطلاعات ممکن نشد. اتصال اینترنت را بررسی کنید." else "Could not load this content. Check your connection."); TextButton(onClick = { retry++ }) { Text(if (fa) "تلاش دوباره" else "Try again") } }
                else if ((chaptersMode && chapters.isEmpty()) || (!chaptersMode && transcript.isEmpty())) Text(if (fa) "دادهٔ قابل نمایش پیدا نشد." else "No supported content was found.", modifier = Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(if (fa) "برای پرش، روی متن بزنید" else "Tap an item to seek", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant); TextButton(onClick = { follow = !follow }) { Text(if (follow) { if (fa) "دنبال‌کردن: روشن" else "Follow: on" } else { if (fa) "دنبال‌کردن: خاموش" else "Follow: off" }) } }
                    LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (chaptersMode) itemsIndexed(chapters) { i, chapter -> TimedRow(chapter.startMs, chapter.title, i == active) { onSeek(chapter.startMs) } }
                        else itemsIndexed(transcript) { i, cue -> TimedRow(cue.startMs, cue.text, i == active) { onSeek(cue.startMs) } }
                    }
                }
            }
        }
    }
}

@Composable private fun TimedRow(at: Long, text: String, selected: Boolean, onClick: () -> Unit) {
    val seconds = at / 1000
    val stamp = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
    Row(Modifier.fillMaxWidth().background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.Top) {
        Text(stamp, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.width(14.dp))
        Text(text, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
    }
}
