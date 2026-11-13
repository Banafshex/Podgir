package com.podgir.app

import android.Manifest
import android.app.DownloadManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private val Purple = Color(0xFF6A58E8)
private val Mint = Color(0xFFB7F0D5)

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        FeedRefreshWorker.schedule(applicationContext)
        if (Repository(applicationContext).boolean("listeningReminder", false)) ReminderWorker.configure(applicationContext, true)
        val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        val future = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
        controllerFuture = future
        future.addListener({ if (!isDestroyed) controller = runCatching { future.get() }.getOrNull() }, ContextCompat.getMainExecutor(this))
        setContent { PodgirApp(controller) }
    }
    override fun onDestroy() { controllerFuture?.let { MediaController.releaseFuture(it) }; controller = null; super.onDestroy() }
}

@Composable
fun PodgirApp(player: MediaController?) {
    val context = LocalContext.current
    val repo = remember { Repository(context.applicationContext) }
    var fa by remember { mutableStateOf(repo.boolean("fa", true)) }
    // Preserve existing users' manual setting; 0 follows the system, 1 is light, 2 is dark.
    var themeMode by remember { mutableIntStateOf(repo.long("themeMode", if (repo.boolean("dark", true)) 2 else 1).toInt()) }
    val dark = when (themeMode) { 0 -> isSystemInDarkTheme(); 1 -> false; else -> true }
    SideEffect { (context as? ComponentActivity)?.window?.let { configureSystemBars(it, dark, true, Color.Transparent, Color.Transparent) } }
    fun t(f: String, e: String) = if (fa) f else e
    val colors = if (dark) darkColorScheme(primary = Color(0xFFD1F36B), onPrimary = Color(0xFF171A0D), primaryContainer = Color(0xFF343D21), background = Color(0xFF101017), surface = Color(0xFF1B1B25), surfaceVariant = Color(0xFF292935), onSurface = Color(0xFFF5F3FA), onSurfaceVariant = Color(0xFFB8B6C2)) else lightColorScheme(primary = Purple, background = Color(0xFFF8F7FC), surface = Color.White, surfaceVariant = Color(0xFFEFEDF7), onSurface = Color(0xFF242136))
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = colors) {
            var tab by remember { mutableStateOf(0) }
            var selected by remember { mutableStateOf<Podcast?>(null) }
            var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
            var results by remember { mutableStateOf<List<Podcast>>(emptyList()) }
            var episodeResults by remember { mutableStateOf<List<Episode>>(emptyList()) }
            var episodeSearchLoading by remember { mutableStateOf(false) }
            var episodeSearchDone by remember { mutableStateOf(false) }
            var episodeSearchErrors by remember { mutableIntStateOf(0) }
            var query by remember { mutableStateOf("") }
            var loading by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf("") }
            var subscriptions by remember { mutableStateOf(repo.subscriptions()) }
            var downloads by remember { mutableStateOf(repo.downloads()) }
            var history by remember { mutableStateOf(repo.history()) }
            var favorites by remember { mutableStateOf(repo.favorites()) }
            var bookmarks by remember { mutableStateOf(repo.bookmarks()) }
            var savedPage by remember { mutableIntStateOf(0) }
            var historyPage by remember { mutableStateOf(false) }
            var queue by remember { mutableStateOf(repo.queue()) }
            var downloadsPage by remember { mutableStateOf(false) }
            var current by remember { mutableStateOf(repo.lastEpisode()) }
            var playing by remember { mutableStateOf(false) }
            var position by remember { mutableLongStateOf(0L) }
            var duration by remember { mutableLongStateOf(0L) }
            var speed by remember { mutableFloatStateOf(repo.long("defaultSpeed", 100) / 100f) }
            var defaultSpeed by remember { mutableIntStateOf(repo.long("defaultSpeed", 100).toInt()) }
            var rewindSeconds by remember { mutableIntStateOf(repo.long("rewindSeconds", 15).toInt()) }
            var forwardSeconds by remember { mutableIntStateOf(repo.long("forwardSeconds", 30).toInt()) }
            var currentDownloadStatus by remember { mutableIntStateOf(0) }
            var expanded by remember { mutableStateOf(false) }
            var sleepMinutes by remember { mutableIntStateOf(if (repo.long("sleepDeadline") > System.currentTimeMillis()) ((repo.long("sleepDeadline") - System.currentTimeMillis()) / 60000L).toInt().coerceAtLeast(1) else 0) }
            var wifiOnly by remember { mutableStateOf(repo.boolean("wifiOnly", false)) }
            var autoplay by remember { mutableStateOf(repo.boolean("autoplay", true)) }
            var notifyNew by remember { mutableStateOf(repo.boolean("newEpisodeNotifications", true)) }
            var reminder by remember { mutableStateOf(repo.boolean("listeningReminder", false)) }
            var notificationsAllowed by remember { mutableStateOf(androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) }
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) notificationsAllowed = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            var autoFeed by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            var request by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
            var pendingBackup by remember { mutableStateOf<LibraryBackup?>(null) }
            fun toast(message: String) { android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show() }
            val importOpml = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) scope.launch {
                    try {
                        val feeds = withContext(Dispatchers.IO) { Opml.read(context, uri) }
                        val count = repo.addSubscriptions(feeds); subscriptions = repo.subscriptions()
                        toast(t("$count پادکست اضافه شد؛ فقط فیدهای HTTPS وارد می‌شوند.", "$count podcasts added; only HTTPS feeds are imported."))
                    } catch (_: Exception) { toast(t("خواندن فایل OPML ممکن نشد.", "Could not read the OPML file.")) }
                }
            }
            val exportOpml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { uri ->
                if (uri != null) scope.launch {
                    try { val feeds = repo.subscriptions(); withContext(Dispatchers.IO) { Opml.write(context, uri, feeds) }; toast(t("اشتراک‌ها ذخیره شدند.", "Subscriptions exported.")) }
                    catch (_: Exception) { toast(t("ذخیرهٔ فایل ممکن نشد.", "Could not write the file.")) }
                }
            }
            val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) scope.launch {
                    try { pendingBackup = withContext(Dispatchers.IO) { BackupFiles.read(context, uri) } }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { toast(t("فایل پشتیبان معتبر نیست یا خوانده نمی‌شود.", "The backup is invalid or cannot be read.")) }
                }
            }
            val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                if (uri != null) scope.launch {
                    try { val data = repo.libraryBackup(); withContext(Dispatchers.IO) { BackupFiles.write(context, uri, data) }; toast(t("فایل پشتیبان ذخیره شد.", "Backup saved.")) }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { toast(t("ذخیرهٔ پشتیبان ممکن نشد.", "Could not save the backup.")) }
                }
            }
            pendingBackup?.let { backup ->
                AlertDialog(onDismissRequest = { pendingBackup = null },
                    title = { Text(t("بازیابی کتابخانه؟", "Restore library?")) },
                    text = { Text(t("اشتراک‌ها، تاریخچه، علاقه‌مندی‌ها، یادداشت‌ها و پیشرفت فعلی جایگزین می‌شوند. دانلودها و صف حذف نمی‌شوند؛ دانلود خودکار برای اشتراک‌ها خاموش می‌شود. پخش متوقف می‌شود.", "This replaces your follows, history, favorites, notes and progress. Downloads and queue remain; automatic downloads are disabled for follows. Playback will pause.")) },
                    confirmButton = { TextButton(onClick = {
                        player?.pause(); repo.restoreLibraryBackup(backup)
                        val restoredPosition = backup.progress.firstOrNull { it.audio == current?.audio }?.position
                        if (restoredPosition != null) { player?.seekTo(restoredPosition); position = restoredPosition }
                        subscriptions = repo.subscriptions(); history = repo.history(); favorites = repo.favorites(); bookmarks = repo.bookmarks()
                        selected?.let { autoFeed = repo.autoDownload(it) }
                        pendingBackup = null; toast(t("کتابخانه بازیابی شد.", "Library restored."))
                    }) { Text(t("بازیابی", "Restore")) } }, dismissButton = { TextButton(onClick = { pendingBackup = null }) { Text(t("انصراف", "Cancel")) } })
            }
            BackHandler(enabled = selected != null || expanded || downloadsPage || historyPage || savedPage != 0) { if (expanded) expanded = false else if (savedPage != 0) savedPage = 0 else if (historyPage) historyPage = false else if (downloadsPage) downloadsPage = false else { selected = null; error = "" } }
            fun search(q: String, includeEpisodes: Boolean = true) {
                request?.cancel(); episodeResults = emptyList(); episodeSearchDone = false; episodeSearchLoading = false
                request = scope.launch {
                    loading = true; error = ""
                    try { results = repo.search(q) } catch (ex: Exception) { if (ex is kotlinx.coroutines.CancellationException) throw ex; results = emptyList(); error = t("دریافت پادکست‌ها ممکن نشد. اتصال اینترنت را بررسی کنید.", "Could not load podcasts. Check your connection.") }
                    loading = false
                    if (includeEpisodes && subscriptions.isNotEmpty()) {
                        episodeSearchLoading = true
                        val (found, failures) = repo.searchFollowedEpisodes(q, subscriptions)
                        episodeResults = found; episodeSearchErrors = failures; episodeSearchDone = true; episodeSearchLoading = false
                    }
                }
            }
            fun open(p: Podcast) { request?.cancel(); episodeSearchLoading = false; autoFeed = repo.autoDownload(p); selected = p; episodes = emptyList(); request = scope.launch { loading = true; error = ""; try { episodes = repo.episodes(p) } catch (ex: Exception) { if (ex is kotlinx.coroutines.CancellationException) throw ex; error = t("دریافت قسمت‌ها ممکن نشد.", "Could not load episodes.") }; loading = false } }
            fun item(e: Episode): MediaItem {
                val metadata = MediaMetadata.Builder().setTitle(e.title).setArtist(e.podcast).setArtworkUri(android.net.Uri.parse(e.image)).build()
                return MediaItem.Builder().setUri(repo.playbackUri(e)).setMediaId(e.audio).setMediaMetadata(metadata).build()
            }
            fun setQueue(list: List<Episode>) {
                queue = list; repo.saveQueue(list)
                if (player != null && player.currentMediaItem != null) {
                    val next = player.currentMediaItemIndex + 1
                    if (next < player.mediaItemCount) player.removeMediaItems(next, player.mediaItemCount)
                    if (list.isNotEmpty()) player.addMediaItems(list.map { item(it) })
                }
            }
            fun play(e: Episode) {
                if (player == null) return
                current?.let { repo.savePosition(it.audio, player.currentPosition) }
                queue = queue.filterNot { it.audio == e.audio }; repo.saveQueue(queue)
                current = e; repo.saveLastEpisode(e); repo.record(e); history = repo.history()
                player.setMediaItems(listOf(item(e)) + queue.map { item(it) })
                player.prepare(); player.seekTo(repo.position(e.audio)); player.play()
            }
            LaunchedEffect(player) {
                val e = current
                if (player != null) speed = player.playbackParameters.speed
                if (player != null && e != null && player.currentMediaItem == null) {
                    player.setMediaItems(listOf(item(e)) + queue.map { item(it) })
                    player.prepare(); player.seekTo(repo.position(e.audio))
                }
            }
            LaunchedEffect(Unit) { search("پادکست فارسی", false) }
            LaunchedEffect(player) {
                while (true) {
                    if (player != null) {
                        val active = repo.lastEpisode()
                        if (active != null && player.currentMediaItem?.mediaId == active.audio && active.audio != current?.audio) current = active
                        queue = repo.queue(); history = repo.history(); downloads = repo.downloads(); currentDownloadStatus = current?.let { repo.downloadStatus(it) } ?: 0
                        playing = player.isPlaying; position = player.currentPosition.coerceAtLeast(0); duration = player.duration.coerceAtLeast(0)
                        current?.let { if (position > 0) { repo.savePosition(it.audio, position); repo.saveDuration(it.audio, duration) } }
                        if (repo.long("sleepDeadline") == 0L) sleepMinutes = 0
                    }
                    delay(1000)
                }
            }
            Scaffold(modifier = Modifier.fillMaxSize().background(colors.background).statusBarsPadding(), containerColor = MaterialTheme.colorScheme.background, bottomBar = {
                Column {
                    AnimatedVisibility(visible = current != null) {
                        current?.let { e -> MiniPlayer(e, playing, position, duration, onOpen = { expanded = true }, onToggle = { if (playing) player?.pause() else player?.play() }) }
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        val labels = listOf(t("خانه", "Home"), t("جست‌وجو", "Search"), t("کتابخانه", "Library"), t("تنظیمات", "Settings"))
                        val icons = listOf(Icons.Default.Home, Icons.Default.Search, Icons.Default.LibraryMusic, Icons.Default.Settings)
                        labels.forEachIndexed { i, label -> NavigationBarItem(selected = tab == i && selected == null, onClick = { tab = i; selected = null; downloadsPage = false; historyPage = false; savedPage = 0; error = "" }, icon = { Icon(icons[i], label) }, label = { Text(label, fontSize = 10.sp) }) }
                    }
                }
            }) { padding ->
                LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp), modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val detail = selected
                    if (savedPage == 1) {
                        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { savedPage = 0 }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("بازگشت", "Back")) }; Text(t("علاقه‌مندی‌ها", "Favorites"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } }
                        if (favorites.isEmpty()) item { Message(t("هنوز قسمتی را دوست نداشته‌اید.", "No favorite episodes yet.")) }
                        items(favorites, key = { it.audio }) { e -> HistoryEpisode(e, repo, fa) { play(e) } }
                    } else if (savedPage == 2) {
                        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { savedPage = 0 }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("بازگشت", "Back")) }; Text(t("لحظه‌های ذخیره‌شده", "Saved moments"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } }
                        if (bookmarks.isEmpty()) item { Message(t("از صفحهٔ پخش، لحظه‌ای را ذخیره کنید.", "Save a moment from the player.")) }
                        items(bookmarks, key = { it.id }) { b -> BookmarkRow(b, fa, onPlay = { if (current?.audio != b.episode.audio) play(b.episode); player?.seekTo(b.position); player?.play(); expanded = true }, onDelete = { repo.deleteBookmark(b.id); bookmarks = repo.bookmarks() }) }
                    } else if (historyPage) {
                        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { historyPage = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("بازگشت", "Back")) }; Text(t("تاریخچه شنیدن", "Listening history"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); TextButton(onClick = { repo.clearHistory(); history = emptyList() }) { Text(t("پاک‌کردن", "Clear")) } } }
                        if (history.isEmpty()) item { Message(t("تاریخچه‌ای وجود ندارد.", "No listening history yet.")) }
                        items(history, key = { it.audio }) { e -> HistoryEpisode(e, repo, fa) { play(e) } }
                    } else if (downloadsPage) {
                        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { downloadsPage = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("بازگشت", "Back")) }; Column { Text(t("مدیریت دانلود", "Download manager"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold); Text(t("فایل‌های شما روی همین دستگاه", "Your files on this device"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) } } }
                        if (downloads.isNotEmpty()) item { DownloadSummary(downloads, repo, fa) { downloads = repo.downloads() } }
                        if (downloads.isEmpty()) item { Message(t("هنوز چیزی دانلود نکرده‌اید.", "No downloads yet.")) }
                        items(downloads, key = { it.audio }) { e -> DownloadCard(e, fa, repo, onPlay = { play(e) }, onRemove = { repo.removeDownload(e); downloads = repo.downloads() }, onRetry = { runCatching { repo.download(e) }.onFailure { error = t("دانلود شروع نشد.", "Could not start download.") }; downloads = repo.downloads() }) }
                        if (error.isNotEmpty()) item { Message(error) }
                    } else if (detail != null) {
                        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { selected = null; error = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("بازگشت", "Back")) }; Text(t("پادکست", "PODCAST"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) } }
                        item { Row(verticalAlignment = Alignment.CenterVertically) { Cover(detail.image, 112); Spacer(Modifier.width(18.dp)); Column { Text(detail.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text(detail.author, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)); Button(onClick = { val wasFollowing = subscriptions.any { it.feed == detail.feed }; repo.toggle(detail); subscriptions = repo.subscriptions(); if (!wasFollowing && episodes.isNotEmpty()) repo.setSeenEpisode(detail, episodes.first().audio); autoFeed = repo.autoDownload(detail) }, shape = RoundedCornerShape(14.dp)) { Text(if (subscriptions.any { it.feed == detail.feed }) t("دنبال می‌کنید ✓", "Following ✓") else t("دنبال کردن +", "Follow +")) } } } }
                        if (subscriptions.any { it.feed == detail.feed }) item { SettingRow(t("دانلود خودکار قسمت‌های جدید", "Automatically download new episodes"), t("بررسی دوره‌ای؛ حداکثر ۳ قسمت جدید در هر نوبت", "Periodic check; up to 3 new episodes per check"), autoFeed) { autoFeed = it; repo.setAutoDownload(detail, it) } }
                        item { SectionTitle(t("قسمت‌ها", "Episodes"), "${episodes.size}") }
                        if (loading) item { Loading() }
                        if (error.isNotEmpty()) item { Message(error) }
                        if (!loading && error.isEmpty() && episodes.isEmpty()) item { Message(t("قسمتی پیدا نشد.", "No episodes found.")) }
                        items(episodes, key = { it.audio }) { e -> EpisodeCard(e, fa, repo, onPlay = { play(e) }, onQueue = { if (current?.audio != e.audio && queue.none { it.audio == e.audio }) setQueue(queue + e) }, queued = queue.any { it.audio == e.audio }, onDownload = { runCatching { repo.download(e) }.onFailure { error = t("دانلود شروع نشد.", "Could not start download.") }; downloads = repo.downloads() }) }
                    } else when (tab) {
                        0 -> {
                            item { Header(fa) }
                            item { Hero(fa, onClick = { tab = 1 }) }
                            if (history.isNotEmpty()) {
                                item { SectionTitle(t("ادامه شنیدن", "Continue listening"), "") }
                                items(history.take(3), key = { "history:" + it.audio }) { e -> HistoryEpisode(e, repo, fa) { play(e) } }
                            }
                            item { SectionTitle(t("برای شروع گوش دادن", "Find your next listen"), t("فارسی", "Persian")) }
                            item { Text(t("چند پیشنهاد برای کشف دنیای پادکست‌های فارسی", "Explore a selection of Persian podcasts"), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                            if (loading) item { Loading() }
                            if (error.isNotEmpty()) item { Message(error); TextButton(onClick = { search("پادکست فارسی", false) }) { Text(t("تلاش دوباره", "Try again")) } }
                            if (results.isNotEmpty()) item { LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) { items(results.take(12), key = { it.feed }) { p -> FeaturedPodcast(p) { open(p) } } } }
                            item { SectionTitle(t("بیشتر کشف کنید", "More to discover"), "") }
                            items(results.drop(12).take(12), key = { it.feed }) { p -> PodcastRow(p) { open(p) } }
                        }
                        1 -> {
                            item { Text(t("کشف پادکست", "Discover"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold); Text(t("یک دنیای تازه برای شنیدن", "A world of stories awaits"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item { Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.weight(1f), singleLine = true, placeholder = { Text(t("نام پادکست یا آدرس RSS...", "Podcast or RSS URL...")) }, shape = RoundedCornerShape(18.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { if (query.startsWith("https://")) open(Podcast(query, "RSS", "", query)) else if (query.isNotBlank()) search(query) })); Spacer(Modifier.width(8.dp)); FilledIconButton(onClick = { if (query.startsWith("https://")) open(Podcast(query, "RSS", "", query)) else if (query.isNotBlank()) search(query) }, modifier = Modifier.size(52.dp)) { Icon(Icons.Default.ArrowForward, t("جست‌وجو", "Search")) } } }
                            item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf("پادکست فارسی", "داستان", "تاریخ", "علم", "technology")) { term -> SuggestionChip(onClick = { query = term; search(term) }, label = { Text(term) }) } } }
                            if (loading) item { Loading() }
                            if (error.isNotEmpty()) item { Message(error) }
                            if (!loading && error.isEmpty() && results.isEmpty()) item { Message(t("نتیجه‌ای پیدا نشد.", "No podcasts found.")) }
                            if (results.isNotEmpty()) item { SectionTitle(t("پادکست‌ها", "Podcasts"), "${results.size}") }
                            items(results, key = { it.feed }) { p -> PodcastRow(p) { open(p) } }
                            if (episodeSearchLoading || episodeSearchDone) item { SectionTitle(t("قسمت‌های دنبال‌شده", "Episodes from followed podcasts"), "${episodeResults.size}") }
                            if (episodeSearchLoading) item { Loading() }
                            if (episodeSearchDone) item { Text(t("جست‌وجو در حداکثر ۲۰ فید و ۲۰۰ قسمت اخیر هر فید انجام می‌شود.", "Search covers up to 20 followed feeds and their latest 200 episodes."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (episodeSearchDone && episodeSearchErrors > 0) item { Message(t("$episodeSearchErrors فید در دسترس نبود؛ نتایج ممکن است ناقص باشند.", "$episodeSearchErrors feeds could not be loaded; results may be incomplete.")) }
                            if (episodeSearchDone && episodeResults.isEmpty()) item { Message(t("قسمت مرتبطی در فیدهای قابل دریافت پیدا نشد.", "No matching episodes were found in available feeds.")) }
                            items(episodeResults, key = { "search:" + it.audio }) { e -> EpisodeCard(e, fa, repo, onPlay = { play(e) }, onQueue = { if (current?.audio != e.audio && queue.none { it.audio == e.audio }) setQueue(queue + e) }, queued = queue.any { it.audio == e.audio }, onDownload = { runCatching { repo.download(e) }.onFailure { error = t("دانلود شروع نشد.", "Could not start download.") }; downloads = repo.downloads() }) }
                            item { Text(t("نتایج پادکست‌ها از Apple Podcasts ارائه می‌شوند.", "Podcast results provided by Apple Podcasts."), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        2 -> {
                            item { Text(t("کتابخانهٔ من", "Your library"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold); Text(t("همهٔ صداهای محبوبت، یک‌جا", "All your favorite voices in one place"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item { SectionTitle(t("دنبال‌شده‌ها", "Following"), "${subscriptions.size}") }
                            if (subscriptions.isEmpty()) item { Message(t("هنوز پادکستی را دنبال نکرده‌اید. از بخش جست‌وجو شروع کنید.", "You haven't followed any podcasts yet. Start exploring.")) }
                            items(subscriptions, key = { it.feed }) { p -> PodcastRow(p) { open(p) } }
                            item { DownloadEntry(fa, downloads.size) { downloadsPage = true } }
                            item { HistoryEntry(fa) { historyPage = true } }
                            item { SavedEntry(t("علاقه‌مندی‌ها", "Favorites"), "${favorites.size}", Icons.Default.Favorite) { savedPage = 1 } }
                            item { SavedEntry(t("لحظه‌های ذخیره‌شده", "Saved moments"), "${bookmarks.size}", Icons.Default.Bookmark) { savedPage = 2 } }
                            item { SavedEntry(t("پشتیبان کتابخانه", "Back up library"), t("اشتراک‌ها، تاریخچه، یادداشت‌ها و پیشرفت", "Follows, history, notes and progress"), Icons.Default.Backup) { exportBackup.launch("podgir-backup.json") } }
                            item { SavedEntry(t("بازیابی پشتیبان", "Restore backup"), t("از فایل پشتیبان پادگیر", "From a Podgir backup file"), Icons.Default.Restore) { importBackup.launch(arrayOf("application/json", "text/*", "application/octet-stream")) } }
                            item { SavedEntry(t("خروجی اشتراک‌ها", "Export subscriptions"), t("فایل OPML", "OPML file"), Icons.Default.FileUpload) { exportOpml.launch("podgir-subscriptions.opml") } }
                            item { SavedEntry(t("ورود اشتراک‌ها", "Import subscriptions"), t("فایل OPML با فیدهای HTTPS", "OPML file with HTTPS feeds"), Icons.Default.FileDownload) { importOpml.launch(arrayOf("text/*", "application/xml", "application/octet-stream")) } }
                            item { Text(t("OPML فقط اشتراک‌ها را ذخیره می‌کند، نه دانلودها، یادداشت‌ها یا پیشرفت پخش.", "OPML includes subscriptions only, not downloads, notes, or listening progress."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        else -> {
                            item { Text(t("تنظیمات", "Settings"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold); Text(t("پادگیر را برای خودت بساز", "Make Podgir yours"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item { val modes = listOf(t("همراه با سیستم", "System default"), t("روشن", "Light"), t("تاریک", "Dark")); OptionSetting(t("ظاهر برنامه", "Appearance"), modes[themeMode.coerceIn(0, 2)], modes) { themeMode = it; repo.setLong("themeMode", it.toLong()) } }
                            item { LanguageSetting(fa) { fa = it; repo.setBoolean("fa", it) } }
                            item { SettingRow(t("پخش خودکار قسمت بعدی", "Autoplay next episode"), t("قسمت بعدی در صف", "Next episode in the queue"), autoplay) { autoplay = it; repo.setBoolean("autoplay", it); context.startService(android.content.Intent(context, PlaybackService::class.java).setAction("com.podgir.app.AUTOPLAY")) } }
                            item { SettingRow(t("اعلان قسمت جدید", "New episode notifications"), t("برای پادکست‌های دنبال‌شده", "For followed podcasts"), notifyNew) { notifyNew = it; repo.setBoolean("newEpisodeNotifications", it) } }
                            item { SettingRow(t("یادآور شنیدن", "Listening reminder"), t("تقریباً روزانه؛ زمان دقیق وابسته به اندروید", "Approximately daily; timing depends on Android"), reminder) { reminder = it; repo.setBoolean("listeningReminder", it); ReminderWorker.configure(context, it) } }
                            if ((notifyNew || reminder) && !notificationsAllowed) item { Column { Message(t("اعلان‌های پادگیر در تنظیمات اندروید غیرفعال‌اند.", "Podgir notifications are disabled in Android settings.")); TextButton(onClick = { val intent = if (Build.VERSION.SDK_INT >= 26) android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName) else android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName)); context.startActivity(intent) }) { Text(t("تنظیمات اعلان", "Notification settings")) } } }
                            item { SettingRow(t("دانلود فقط با وای‌فای", "Downloads on Wi-Fi only"), t("برای دانلودهای جدید", "For new downloads"), wifiOnly) { wifiOnly = it; repo.setBoolean("wifiOnly", it) } }
                            item { val values = (2..12).map { it * 25 }; OptionSetting(t("سرعت پیش‌فرض", "Default speed"), "${defaultSpeed / 100f}×", values.map { "${it / 100f}×" }) { i -> defaultSpeed = values[i]; repo.setLong("defaultSpeed", defaultSpeed.toLong()); speed = defaultSpeed / 100f; player?.setPlaybackSpeed(speed) } }
                            item { val values = listOf(5, 10, 15, 20, 30, 45, 60); OptionSetting(t("پرش به عقب", "Skip backward"), "$rewindSeconds s", values.map { "$it s" }) { i -> rewindSeconds = values[i]; repo.setLong("rewindSeconds", rewindSeconds.toLong()) } }
                            item { val values = listOf(5, 10, 15, 20, 30, 45, 60); OptionSetting(t("پرش به جلو", "Skip forward"), "$forwardSeconds s", values.map { "$it s" }) { i -> forwardSeconds = values[i]; repo.setLong("forwardSeconds", forwardSeconds.toLong()) } }
                            item { Message(t("پادگیر رایگان است و به حساب کاربری نیاز ندارد. اشتراک‌ها و دانلودها روی همین دستگاه نگهداری می‌شوند.", "Podgir is free and requires no account. Your follows and downloads stay on this device.")) }
                        }
                    }
                }
            }
            if (expanded && current != null) PlayerScreen(
                episode = current!!, fa = fa, playing = playing, position = position, duration = duration, speed = speed, sleepMinutes = sleepMinutes, queue = queue,
                rewindSeconds = rewindSeconds, forwardSeconds = forwardSeconds, favorite = favorites.any { it.audio == current?.audio }, downloadStatus = currentDownloadStatus,
                onFavorite = { current?.let { repo.toggleFavorite(it); favorites = repo.favorites() } },
                onBookmark = { at, note -> current?.let { repo.addBookmark(it, at, note); bookmarks = repo.bookmarks(); android.widget.Toast.makeText(context, t("لحظه ذخیره شد", "Moment saved"), android.widget.Toast.LENGTH_SHORT).show() } },
                onDownload = { current?.let { e -> runCatching { repo.download(e) }.onSuccess { downloads = repo.downloads(); currentDownloadStatus = repo.downloadStatus(e); android.widget.Toast.makeText(context, t("دانلود شروع شد", "Download started"), android.widget.Toast.LENGTH_SHORT).show() }.onFailure { android.widget.Toast.makeText(context, t("دانلود شروع نشد", "Could not start download"), android.widget.Toast.LENGTH_SHORT).show() } } },
                onShare = { current?.let { e -> val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "${e.title}\n${e.audio}"); context.startActivity(android.content.Intent.createChooser(intent, t("اشتراک لینک صوت", "Share audio link"))) } },
                onClose = { expanded = false }, onToggle = { if (playing) player?.pause() else player?.play() },
                onSeek = { player?.seekTo(it.coerceIn(0L, if (duration > 0) duration else Long.MAX_VALUE)); position = it },
                onSpeed = { speed = if (speed >= 3f) .5f else speed + .25f; player?.setPlaybackSpeed(speed) },
                onSleep = { sleepMinutes = when (sleepMinutes) { 0 -> 15; 15 -> 30; 30 -> 60; else -> 0 }; val deadline = if (sleepMinutes == 0) 0L else System.currentTimeMillis() + sleepMinutes * 60000L; repo.setLong("sleepDeadline", deadline); context.startService(android.content.Intent(context, PlaybackService::class.java).setAction("com.podgir.app.SLEEP_TIMER").putExtra("deadline", deadline)) },
                onPrevious = { if (player?.hasPreviousMediaItem() == true) player.seekToPreviousMediaItem() else player?.seekTo(0) },
                onSkip = { if (player?.hasNextMediaItem() == true) { player.seekToNextMediaItem(); player.play() } },
                onRemove = { e -> setQueue(queue.filterNot { it.audio == e.audio }) },
                onMove = { from, to -> val list = queue.toMutableList(); val entry = list.removeAt(from); list.add(to, entry); setQueue(list) }
            )
        }
    }
}

private fun formatTime(ms: Long): String { val s = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0)); return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }

@Composable private fun Cover(url: String, size: Int) { AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(size.dp).clip(RoundedCornerShape((size / 5).dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
@Composable private fun Header(fa: Boolean) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(if (fa) "سلام، خوش اومدی ✨" else "HELLO, LISTENER ✨", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp)); Text(if (fa) "پادگیر" else "Podgir", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black) }; Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(46.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Headphones, null, tint = MaterialTheme.colorScheme.primary) } } } }
@Composable private fun Hero(fa: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "hero")
    val shift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Reverse), label = "hero motion")
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF7A60D5), Color(0xFF49347E), Color(0xFF252139)))).clickable(onClick = onClick)) {
        Canvas(Modifier.matchParentSize()) {
            val x = size.width * .85f; val y = size.height * (.46f + shift * .03f)
            for (i in 1..5) drawCircle(Color.White.copy(alpha = .07f), radius = size.minDimension * (.13f + i * .09f), center = Offset(x, y), style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(Color(0xFFD1F36B).copy(alpha = .75f), radius = 10.dp.toPx(), center = Offset(x, y))
        }
        Column(Modifier.padding(24.dp)) {
            Text(if (fa) "پیشنهاد امروز  ✦" else "YOUR DAILY DISCOVERY  ✦", color = Color(0xFFD1F36B), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp)); Text(if (fa) "یه دنیا قصه،\nبرای گوش‌های تو." else "A world of stories,\njust for you.", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp)
            Spacer(Modifier.height(18.dp)); Surface(shape = RoundedCornerShape(50), color = Color(0xFFD1F36B)) { Text(if (fa) "شروع کشف  ←" else "Start exploring  →", modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp), color = Color(0xFF193023), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
        }
    }
}
@Composable private fun SectionTitle(title: String, extra: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Text(extra, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) } }
@Composable private fun PodcastRow(p: Podcast, onClick: () -> Unit) { Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Cover(p.image, 68); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(p.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(4.dp)); Text(p.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary) } } }
@Composable private fun EpisodeCard(e: Episode, fa: Boolean, repo: Repository, onPlay: () -> Unit, onQueue: () -> Unit, queued: Boolean, onDownload: () -> Unit) {
    var status by remember(e.audio) { mutableIntStateOf(repo.downloadStatus(e)) }
    LaunchedEffect(e.audio) { while (true) { status = repo.downloadStatus(e); delay(2000) } }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Cover(e.image, 54); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(e.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(e.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) } }
        if (e.description.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(e.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPlay, modifier = Modifier.weight(1f)) { Icon(Icons.Default.PlayCircle, null); Spacer(Modifier.width(5.dp)); Text(if (fa) "پخش" else "Play") }
            IconButton(onClick = onQueue, enabled = !queued) { Icon(if (queued) Icons.Default.CheckCircle else Icons.Default.PlaylistAdd, if (fa) "افزودن به صف" else "Add to queue", tint = if (queued) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onDownload, enabled = status == 0 || status == DownloadManager.STATUS_FAILED) { Icon(if (status == DownloadManager.STATUS_SUCCESSFUL) Icons.Default.CheckCircle else Icons.Default.Download, if (fa) "دانلود" else "Download", tint = if (status == DownloadManager.STATUS_SUCCESSFUL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    } }
}
@Composable private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) { Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = checked, onCheckedChange = onChange) } } }
@Composable private fun Message(text: String) { Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), modifier = Modifier.fillMaxWidth()) { Text(text, modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start) } }
@Composable private fun Loading() { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) } }
