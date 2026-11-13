package com.podgir.app

import android.app.DownloadManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@Composable fun MiniPlayer(e: Episode, playing: Boolean, position: Long, duration: Long, onOpen: () -> Unit, onToggle: () -> Unit) {
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(18.dp), color = Color(0xFF3F3A53), shadowElevation = 8.dp) {
        Column {
            Row(Modifier.padding(start = 9.dp, end = 8.dp, top = 8.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = e.image, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF635A77)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) { Text(e.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(e.podcast, color = Color.White.copy(.65f), style = MaterialTheme.typography.labelSmall, maxLines = 1) }
                IconButton(onClick = onToggle) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(30.dp)) }
            }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { LinearProgressIndicator(progress = { if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(3.dp), color = Color(0xFFD1F36B), trackColor = Color.White.copy(.18f)) }
        }
    }
}

@Composable fun FeaturedPodcast(p: Podcast, onClick: () -> Unit) {
    Column(Modifier.width(150.dp).clickable(onClick = onClick)) {
        Box {
            AsyncImage(model = p.image, contentDescription = null, modifier = Modifier.size(150.dp).clip(RoundedCornerShape(19.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
            Surface(color = Color(0xFFD1F36B), shape = CircleShape, modifier = Modifier.align(Alignment.BottomEnd).padding(9.dp).size(32.dp), shadowElevation = 4.dp) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF171A0D), modifier = Modifier.size(22.dp)) } }
        }
        Spacer(Modifier.height(10.dp))
        Text(p.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(p.author, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun LanguageSetting(fa: Boolean, onChange: (Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(onClick = { menu = true }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Language, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(if (fa) "زبان برنامه" else "App language", fontWeight = FontWeight.Bold); Text(if (fa) "فارسی" else "English", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Box { Icon(Icons.Default.ExpandMore, null); DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) { DropdownMenuItem(text = { Text("فارسی") }, onClick = { onChange(true); menu = false }, trailingIcon = { if (fa) Icon(Icons.Default.Check, null) }); DropdownMenuItem(text = { Text("English") }, onClick = { onChange(false); menu = false }, trailingIcon = { if (!fa) Icon(Icons.Default.Check, null) }) } }
        }
    }
}

@Composable fun DownloadEntry(fa: Boolean, count: Int, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) { Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(48.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Download, null, tint = MaterialTheme.colorScheme.primary) } }
        Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(if (fa) "مدیریت دانلودها" else "Download manager", fontWeight = FontWeight.Bold); Text(if (fa) "$count قسمت" else "$count episodes", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }; Icon(Icons.Default.ChevronRight, null)
    } }
}

@Composable fun DownloadCard(e: Episode, fa: Boolean, repo: Repository, onPlay: () -> Unit, onRemove: () -> Unit, onRetry: () -> Unit) {
    var status by remember(e.audio) { mutableIntStateOf(repo.downloadStatus(e)) }
    var progress by remember(e.audio) { mutableStateOf(repo.downloadProgress(e)) }
    LaunchedEffect(e.audio) { while (true) { status = repo.downloadStatus(e); progress = repo.downloadProgress(e); delay(1000) } }
    val complete = status == DownloadManager.STATUS_SUCCESSFUL
    val failed = status == DownloadManager.STATUS_FAILED || status == 0
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = e.image, contentDescription = null, modifier = Modifier.size(55.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold); Text(e.podcast, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
            IconButton(onClick = onRemove) { Icon(Icons.Default.DeleteOutline, if (fa) "حذف دانلود" else "Remove download") }
        }
        Spacer(Modifier.height(12.dp))
        if (!complete && !failed) { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp)) else LinearProgressIndicator(progress = { progress ?: 0f }, modifier = Modifier.fillMaxWidth().height(4.dp)) }; Spacer(Modifier.height(8.dp)) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(when { complete -> if (fa) "آمادهٔ پخش آفلاین" else "Available offline"; failed -> if (fa) "دانلود ناموفق" else "Download failed"; status == DownloadManager.STATUS_PAUSED -> if (fa) "در انتظار شبکه" else "Waiting for network"; else -> (if (fa) "در حال دانلود" else "Downloading") + (progress?.let { " · ${(it * 100).toInt()}%" } ?: "") }, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            if (complete) TextButton(onClick = onPlay) { Text(if (fa) "پخش" else "Play") } else if (failed) TextButton(onClick = onRetry) { Text(if (fa) "تلاش دوباره" else "Retry") }
        }
    } }
}

@Composable fun HistoryEntry(fa: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.History, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)); Spacer(Modifier.width(16.dp))
            Text(if (fa) "تاریخچه شنیدن" else "Listening history", Modifier.weight(1f), fontWeight = FontWeight.Bold)
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable fun HistoryEpisode(e: Episode, repo: Repository, fa: Boolean, onPlay: () -> Unit) {
    val duration = repo.duration(e.audio)
    val progress = if (duration > 0) (repo.position(e.audio).toFloat() / duration).coerceIn(0f, 1f) else 0f
    Surface(onClick = onPlay, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = e.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(e.podcast, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) { LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape)) }
            }
            Spacer(Modifier.width(8.dp)); Text(if (duration > 0) "${(progress * 100).toInt()}%" else if (fa) "ادامه" else "Resume", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.Default.PlayArrow, if (fa) "پخش" else "Play", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable fun OptionSetting(title: String, value: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Surface(onClick = { expanded = true }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            Box { Icon(Icons.Default.ExpandMore, null); DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { options.forEachIndexed { index, option -> DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelect(index) }, trailingIcon = { if (option == value) Icon(Icons.Default.Check, null) }) } } }
        }
    }
}

@Composable fun SavedEntry(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)); Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable fun BookmarkRow(bookmark: Bookmark, fa: Boolean, onPlay: () -> Unit, onDelete: () -> Unit) {
    val seconds = bookmark.position / 1000
    val stamp = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
    Surface(onClick = onPlay, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = bookmark.episode.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(54.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) {
                Text(bookmark.episode.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                Text(stamp, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                if (bookmark.note.isNotBlank()) Text(bookmark.note, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, if (fa) "حذف نشان" else "Delete bookmark") }
        }
    }
}

@Composable fun DownloadSummary(episodes: List<Episode>, repo: Repository, fa: Boolean, onChanged: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var count by remember { mutableIntStateOf(0) }
    var bytes by remember { mutableLongStateOf(0L) }
    var confirm by remember { mutableStateOf(false) }
    LaunchedEffect(episodes) {
        while (true) {
            val (n, size) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val complete = episodes.filter { repo.downloadStatus(it) == DownloadManager.STATUS_SUCCESSFUL }
                complete.size to complete.sumOf { repo.completedDownloadBytes(it) }
            }
            count = n; bytes = size; delay(5000)
        }
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (fa) "فضای دانلودها" else "Download storage", fontWeight = FontWeight.Bold)
                Text("$count · ${android.text.format.Formatter.formatFileSize(context, bytes)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { confirm = true }, enabled = count > 0) { Text(if (fa) "پاک‌سازی" else "Clear") }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text(if (fa) "حذف دانلودهای کامل؟" else "Remove completed downloads?") }, text = { Text(if (fa) "فایل‌های دانلودشده از دستگاه حذف می‌شوند؛ اشتراک‌ها و تاریخچه باقی می‌مانند. فایل در حال پخش نیز ممکن است حذف شود." else "Downloaded files will be removed from this device. Follows and history stay. The currently playing file may also be removed.") }, confirmButton = { TextButton(onClick = { episodes.filter { repo.downloadStatus(it) == DownloadManager.STATUS_SUCCESSFUL }.forEach { repo.removeDownload(it) }; confirm = false; onChanged() }) { Text(if (fa) "حذف فایل‌ها" else "Remove files") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text(if (fa) "انصراف" else "Cancel") } })
}
