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
import androidx.compose.ui.focus.onFocusChanged
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

private val Purple = Color(0xFF5152B8)

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
    val colors = if (dark) darkColorScheme(primary = NeonCyan, onPrimary = Color(0xFF071C23), primaryContainer = Color(0xFF253049), onPrimaryContainer = Color(0xFFDDEAFF), background = Color(0xFF0B0E1A), surface = Color(0xFF252E45), surfaceVariant = Color(0xFF303A53), onSurface = Color(0xFFF1F3FC), onSurfaceVariant = Color(0xFFB9C1D3)) else lightColorScheme(primary = Purple, primaryContainer = Color(0xFFE3E4FF), onPrimaryContainer = Color(0xFF1B2057), background = Color(0xFFE9EDF7), surface = Color.White, surfaceVariant = Color(0xFFEAEDF7), onSurface = Color(0xFF1B2034))
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        MaterialTheme(colorScheme = colors, typography = if (fa) persianTypography() else Typography()) {
            var tab by remember { mutableStateOf(0) }
            var selected by remember { mutableStateOf<Podcast?>(null) }
            var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
            var oldestFirst by remember { mutableStateOf(false) }
            var nextAvailable by remember { mutableStateOf(false) }
            var results by remember { mutableStateOf<List<Podcast>>(emptyList()) }
            var recommendations by remember { mutableStateOf<List<Podcast>>(emptyList()) }
            var chartLoading by remember { mutableStateOf(false) }
            var chartError by remember { mutableStateOf(false) }
            var chartRetry by remember { mutableIntStateOf(0) }
            var category by remember { mutableStateOf<PodcastCategory?>(null) }
            var categoryQuery by remember { mutableStateOf("") }
            var recentSearches by remember { mutableStateOf(repo.searchHistory()) }
            var searchFocused by remember { mutableStateOf(false) }
            var playlists by remember { mutableStateOf(repo.playlists()) }
            var playlistId by remember { mutableStateOf<String?>(null) }
            var deletePlaylist by remember { mutableStateOf<Playlist?>(null) }
            var showNewPlaylist by remember { mutableStateOf(false) }
            var newPlaylistName by remember { mutableStateOf("") }
            var addEpisode by remember { mutableStateOf<Episode?>(null) }
            var clearSearchConfirm by remember { mutableStateOf(false) }
            var showPlaylists by remember { mutableStateOf(false) }
            var skipSilence by remember { mutableStateOf(repo.boolean("skipSilence", false)) }
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
            var downloadSettingsPage by remember { mutableStateOf(false) }
            var infoPodcast by remember { mutableStateOf<Podcast?>(null) }
            var lastBackPress by remember { mutableLongStateOf(0L) }
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
            var downloadsPublic by remember { mutableStateOf(repo.boolean("downloadsPublic", false) && Build.VERSION.SDK_INT >= 29) }
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
            val updater = remember { AppUpdater(context.applicationContext) }
            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            var showUpdate by remember { mutableStateOf(false) }
            var updateMessage by remember { mutableStateOf("") }
            var checkingUpdate by remember { mutableStateOf(false) }
            var pendingBackup by remember { mutableStateOf<LibraryBackup?>(null) }
            fun toast(message: String) { android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show() }
            fun checkUpdate(manual: Boolean) {
                if (checkingUpdate) return
                scope.launch {
                    checkingUpdate = true
                    try {
                        val found = updater.latest()
                        if (found != null) { updateInfo = found; showUpdate = true }
                        else if (manual) { updateInfo = null; updateMessage = t("برنامه به‌روز است.", "Podgir is up to date."); showUpdate = true }
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { if (manual) { updateInfo = null; updateMessage = t("بررسی ممکن نشد؛ اینترنت را بررسی کنید.", "Could not check for updates; check your connection."); showUpdate = true } }
                    finally { checkingUpdate = false }
                }
            }
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
            BackHandler {
                if (expanded || infoPodcast != null || savedPage != 0 || historyPage || downloadsPage || downloadSettingsPage || selected != null || category != null || playlistId != null || showPlaylists) lastBackPress = 0L
                when {
                    expanded -> expanded = false
                    infoPodcast != null -> infoPodcast = null
                    savedPage != 0 -> savedPage = 0
                    historyPage -> historyPage = false
                    downloadsPage -> downloadsPage = false
                    downloadSettingsPage -> downloadSettingsPage = false
                    selected != null -> { request?.cancel(); selected = null; error = ""; loading = false }
                    playlistId != null -> playlistId = null
                    showPlaylists -> showPlaylists = false
                    category != null -> { category = null; categoryQuery = "" }
                    else -> {
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (lastBackPress != 0L && now - lastBackPress <= 2000L) (context as? ComponentActivity)?.finish()
                        else { lastBackPress = now; android.widget.Toast.makeText(context, t("برای خروج دوباره بازگشت را بزنید", "Press back again to exit"), android.widget.Toast.LENGTH_SHORT).show() }
                    }
                }
            }
            fun rememberSearch(q: String) { recentSearches = updatedSearchHistory(recentSearches, q); repo.saveSearchHistory(recentSearches) }
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
            fun open(p: Podcast) { request?.cancel(); episodeSearchLoading = false; oldestFirst = repo.sortOldest(p.feed); autoFeed = repo.autoDownload(p); selected = p; episodes = emptyList(); request = scope.launch { loading = true; error = ""; try { episodes = repo.episodes(p) } catch (ex: Exception) { if (ex is kotlinx.coroutines.CancellationException) throw ex; error = t("دریافت قسمت‌ها ممکن نشد.", "Could not load episodes.") }; loading = false } }
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
            pendingBackup?.let { backup ->
                AlertDialog(onDismissRequest = { pendingBackup = null },
                    title = { Text(t("بازیابی کتابخانه؟", "Restore library?")) },
                    text = { Text(if (backup.queue != null) t("اشتراک‌ها، تاریخچه، علاقه‌مندی‌ها، یادداشت‌ها، پیشرفت و صف جایگزین می‌شوند؛ پلی‌لیست‌ها فقط در صورت وجود در پشتیبان جایگزین می‌شوند. دانلودها باقی می‌مانند؛ دانلود خودکار خاموش و پخش متوقف می‌شود.", "Follows, history, favorites, notes, progress and queue will be replaced; playlists are replaced only when included in the backup. Downloads remain; auto-download is disabled and playback pauses.") else t("اشتراک‌ها، تاریخچه، علاقه‌مندی‌ها، یادداشت‌ها و پیشرفت جایگزین می‌شوند. دانلودها و صف باقی می‌مانند؛ دانلود خودکار خاموش و پخش متوقف می‌شود.", "Follows, history, favorites, notes and progress will be replaced. Downloads and queue remain; auto-download is disabled and playback pauses.")) },
                    confirmButton = { TextButton(onClick = {
                        player?.pause(); repo.restoreLibraryBackup(backup); backup.queue?.let { setQueue(it.filterNot { e -> e.audio == current?.audio }) }
                        val restoredPosition = backup.progress.firstOrNull { it.audio == current?.audio }?.position
                        if (restoredPosition != null) { player?.seekTo(restoredPosition); position = restoredPosition }
                        subscriptions = repo.subscriptions(); history = repo.history(); favorites = repo.favorites(); bookmarks = repo.bookmarks(); playlists = repo.playlists()
                        selected?.let { autoFeed = repo.autoDownload(it) }
                        pendingBackup = null; toast(t("کتابخانه بازیابی شد.", "Library restored."))
                    }) { Text(t("بازیابی", "Restore")) } }, dismissButton = { TextButton(onClick = { pendingBackup = null }) { Text(t("انصراف", "Cancel")) } })
            }
            fun play(e: Episode, order: List<Episode>? = null) {
                if (player == null) return
                val same = player.currentMediaItem?.mediaId == e.audio
                if (same && order == null) {
                    if (isFinished(player.currentPosition, player.duration)) player.seekTo(0)
                    player.play(); return
                }
                val before = player.currentMediaItem?.mediaId
                if (before != null && player.currentPosition > 0) { repo.savePosition(before, player.currentPosition); repo.saveDuration(before, player.duration) }
                val start = if (same) resumePosition(player.currentPosition, player.duration) else resumePosition(repo.position(e.audio), repo.duration(e.audio))
                queue = if (order != null) episodesAfter(order, e.audio) else queue.filterNot { it.audio == e.audio }
                repo.saveQueue(queue)
                current = e; position = start; duration = repo.duration(e.audio); repo.saveLastEpisode(e); repo.record(e); history = repo.history()
                player.setMediaItems(listOf(item(e)) + queue.map { item(it) }, 0, start)
                player.prepare(); player.play()
            }
            LaunchedEffect(player) {
                val e = current
                if (player != null) speed = player.playbackParameters.speed
                if (player != null && e != null && player.currentMediaItem == null) {
                    player.setMediaItems(listOf(item(e)) + queue.map { item(it) }, 0, resumePosition(repo.position(e.audio), repo.duration(e.audio)))
                    player.prepare()
                }
            }
            LaunchedEffect(Unit) { delay(1500); checkUpdate(false) }
            LaunchedEffect(category, chartRetry) {
                chartLoading = true; chartError = false; recommendations = emptyList()
                try { recommendations = repo.chart(category?.id) }
                catch (ex: kotlinx.coroutines.CancellationException) { throw ex }
                catch (_: Exception) { chartError = true }
                finally { chartLoading = false }
            }
            LaunchedEffect(query, category, tab) {
                if (tab == 1 && category == null && query.trim().length >= 3 && !query.startsWith("https://", true)) { request?.cancel(); results = emptyList(); loading = true; error = ""; delay(450); search(query.trim(), false) }
                else if (category == null && query.trim().length < 3) { request?.cancel(); results = emptyList(); loading = false; error = "" }
            }
            LaunchedEffect(player) {
                while (true) {
                    if (player != null) {
                        val active = repo.lastEpisode()
                        if (active != null && player.currentMediaItem?.mediaId == active.audio && active.audio != current?.audio) current = active
                        queue = repo.queue(); history = repo.history(); downloads = repo.downloads(); currentDownloadStatus = current?.let { repo.downloadStatus(it) } ?: 0
                        playing = player.isPlaying; position = player.currentPosition.coerceAtLeast(0); duration = player.duration.coerceAtLeast(0); nextAvailable = player.hasNextMediaItem()
                        current?.let { if (position > 0 && player.currentMediaItem?.mediaId == it.audio) { repo.savePosition(it.audio, position); repo.saveDuration(it.audio, duration) } }
                        if (repo.long("sleepDeadline") == 0L) sleepMinutes = 0
                    }
                    delay(1000)
                }
            }
            val orderedEpisodes = remember(episodes, oldestFirst) { sortedEpisodes(episodes, oldestFirst) }
            Scaffold(modifier = Modifier.fillMaxSize().background(colors.background).statusBarsPadding(), containerColor = MaterialTheme.colorScheme.background, bottomBar = {
                Column {
                    AnimatedVisibility(visible = current != null) {
                        current?.let { e -> MiniPlayer(e, playing, position, duration, onOpen = { expanded = true }, onToggle = { if (playing) player?.pause() else player?.play() }) }
                    }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        val labels = listOf(t("خانه", "Home"), t("دسته‌بندی", "Categories"), t("کتابخانه", "Library"), t("تنظیمات", "Settings"))
                        val icons = listOf(Icons.Default.Home, Icons.Default.GridView, Icons.Default.LibraryMusic, Icons.Default.Settings)
                        labels.forEachIndexed { i, label -> NavigationBarItem(selected = tab == i && selected == null, onClick = { tab = i; selected = null; category = null; categoryQuery = ""; playlistId = null; showPlaylists = false; downloadsPage = false; downloadSettingsPage = false; historyPage = false; savedPage = 0; lastBackPress = 0L; error = "" }, icon = { Icon(icons[i], label) }, label = { Text(label, fontSize = 10.sp) }) }
                    }
                }
            }) { padding ->
                LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 24.dp), modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val detail = selected
                    if (downloadSettingsPage) {
                        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { downloadSettingsPage = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("بازگشت", "Back")) }; Text(t("تنظیمات دانلود", "Download settings"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) } }
                        item { val choices = listOf(t("فقط وای‌فای", "Wi-Fi only"), t("وای‌فای و اینترنت همراه", "Wi-Fi and mobile data")); OptionSetting(t("شبکهٔ دانلود", "Download network"), choices[if (wifiOnly) 0 else 1], choices) { i -> wifiOnly = i == 0; repo.setBoolean("wifiOnly", wifiOnly) } }
                        if (Build.VERSION.SDK_INT >= 29) item { val choices = listOf(t("حافظهٔ اختصاصی برنامه", "App-specific storage"), t("پوشهٔ Downloads دستگاه", "Device Downloads folder")); OptionSetting(t("محل ذخیرهٔ فایل", "File location"), choices[if (downloadsPublic) 1 else 0], choices) { i -> downloadsPublic = i == 1; repo.setBoolean("downloadsPublic", downloadsPublic) } }
                        else item { Message(t("در این نسخهٔ اندروید فایل‌ها در حافظهٔ اختصاصی برنامه ذخیره می‌شوند.", "On this Android version, files are saved in app-specific storage.")) }
                        item { SavedEntry(t("کیفیت فایل", "Audio quality"), t("کیفیت اصلی ناشر", "Original publisher quality"), Icons.Default.HighQuality) { toast(t("هر قسمت با فایل اصلی ارائه‌شده در فید دریافت می‌شود؛ این نسخه انتخاب کیفیت جایگزین را پشتیبانی نمی‌کند.", "Episodes use the original file provided by the feed; this version does not support choosing an alternative quality.")) } }
                        item { Message(t("تنظیمات شبکه و محل ذخیره فقط برای دانلودهای جدید اعمال می‌شوند. کیفیت را ناشر تعیین می‌کند و برنامه فایل را تبدیل نمی‌کند.", "Network and location settings apply to new downloads only. Quality is determined by the publisher; files are not transcoded.")) }
                    } else if (showPlaylists) {
                        val list = playlists.firstOrNull { it.id == playlistId }
                        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { if (playlistId != null) playlistId = null else showPlaylists = false }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("بازگشت", "Back")) }; Text(list?.name ?: t("پلی‌لیست‌ها", "Playlists"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); if (list == null) IconButton(onClick = { newPlaylistName = ""; showNewPlaylist = true }) { Icon(Icons.Default.Add, t("پلی‌لیست جدید", "New playlist")) } } }
                        if (list == null) {
                            if (playlists.isEmpty()) item { Message(t("با دکمهٔ + یک پلی‌لیست بسازید.", "Create a playlist with the + button.")) }
                            items(playlists, key = { it.id }) { pl -> Row(verticalAlignment = Alignment.CenterVertically) { Text(pl.name + " · " + pl.episodes.size, Modifier.weight(1f).clickable { playlistId = pl.id }.padding(16.dp)); IconButton(onClick = { deletePlaylist = pl }) { Icon(Icons.Default.DeleteOutline, t("حذف پلی‌لیست", "Delete playlist")) } } }
                        } else {
                            if (list.episodes.isEmpty()) item { Message(t("از کارت هر قسمت آن را به پلی‌لیست اضافه کنید.", "Add episodes from their cards.")) }
                            items(list.episodes, key = { it.audio }) { e -> Row(verticalAlignment = Alignment.CenterVertically) { Text(e.title, Modifier.weight(1f).clickable { play(e, list.episodes) }.padding(16.dp)); IconButton(onClick = { playlists = playlists.map { if (it.id == list.id) it.copy(episodes = it.episodes.filterNot { ep -> ep.audio == e.audio }) else it }; repo.savePlaylists(playlists) }) { Icon(Icons.Default.Close, t("حذف از پلی‌لیست", "Remove from playlist")) } } }
                        }
                    } else if (savedPage == 1) {
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
                        if (downloads.isNotEmpty()) item { DownloadSummary(downloads, repo, fa, current?.audio) { downloads = repo.downloads() } }
                        if (downloads.isEmpty()) item { Message(t("هنوز چیزی دانلود نکرده‌اید.", "No downloads yet.")) }
                        items(downloads, key = { it.audio }) { e -> DownloadCard(e, fa, repo, onPlay = { play(e) }, onRemove = { repo.removeDownload(e); downloads = repo.downloads() }, onRetry = { runCatching { repo.download(e) }.onFailure { error = t("دانلود شروع نشد.", "Could not start download.") }; downloads = repo.downloads() }) }
                        if (error.isNotEmpty()) item { Message(error) }
                    } else if (detail != null) {
                        item { Row(verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = { selected = null; error = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("بازگشت", "Back")) }; Text(t("پادکست", "PODCAST"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) } }
                        item { Row(verticalAlignment = Alignment.CenterVertically) { Cover(detail.image, 112); Spacer(Modifier.width(18.dp)); Column { Text(detail.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text(detail.author, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)); Button(onClick = { val wasFollowing = subscriptions.any { it.feed == detail.feed }; repo.toggle(detail); subscriptions = repo.subscriptions(); if (!wasFollowing && episodes.isNotEmpty()) repo.setSeenEpisode(detail, episodes.first().audio); autoFeed = repo.autoDownload(detail) }, shape = RoundedCornerShape(14.dp)) { Text(if (subscriptions.any { it.feed == detail.feed }) t("دنبال می‌کنید ✓", "Following ✓") else t("دنبال کردن +", "Follow +")) } } } }
                        if (subscriptions.any { it.feed == detail.feed }) item { SettingRow(t("دانلود خودکار قسمت‌های جدید", "Automatically download new episodes"), t("بررسی دوره‌ای؛ حداکثر ۳ قسمت جدید در هر نوبت", "Periodic check; up to 3 new episodes per check"), autoFeed) { autoFeed = it; repo.setAutoDownload(detail, it) } }
                        item { OutlinedButton(onClick = { infoPodcast = detail }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Icon(Icons.Default.Info, null); Spacer(Modifier.width(8.dp)); Text(t("دربارهٔ پادکست", "About this podcast")) } }
                        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(t("قسمت‌ها", "Episodes"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            val choices = listOf(t("جدیدترین", "Newest first"), t("قدیمی‌ترین", "Oldest first"))
                            SortMenu(choices, if (oldestFirst) 1 else 0) { i -> oldestFirst = i == 1; repo.setSortOldest(detail.feed, oldestFirst) }
                        } }
                        if (loading) item { Loading() }
                        if (error.isNotEmpty()) item { Message(error) }
                        if (!loading && error.isEmpty() && episodes.isEmpty()) item { Message(t("قسمتی پیدا نشد.", "No episodes found.")) }
                        items(orderedEpisodes, key = { it.audio }) { e -> EpisodeCard(e, fa, repo, livePosition = if (current?.audio == e.audio) position else null, liveDuration = if (current?.audio == e.audio) duration else null, onPlay = { play(e, orderedEpisodes) }, onQueue = { if (current?.audio != e.audio && queue.none { it.audio == e.audio }) setQueue(queue + e) }, queued = queue.any { it.audio == e.audio }, onPlaylist = { addEpisode = e }, onDownload = { runCatching { repo.download(e) }.onFailure { error = t("دانلود شروع نشد.", "Could not start download.") }; downloads = repo.downloads() }) }
                    } else when (tab) {
                        0 -> {
                            item { Header(fa) }
                            item { Hero(fa, onClick = { tab = 1 }) }
                            val continuing = continueListening(history, { if (it.audio == current?.audio) position else repo.position(it.audio) }, { if (it.audio == current?.audio && duration > 0) duration else repo.duration(it.audio) })
                            if (continuing.isNotEmpty()) {
                                item { SectionTitle(t("ادامه شنیدن", "Continue listening"), "") }
                                items(continuing, key = { "history:" + it.audio }) { e -> HistoryEpisode(e, repo, fa) { play(e) } }
                            }
                            item { SectionTitle(t("پیشنهادها", "Discover"), t("جدول اپل آمریکا", "Apple US charts")) }
                            item { Text(t("بر اساس رتبهٔ جدول اپل؛ تعداد شنونده یا دنبال‌کننده در دسترس نیست.", "Apple chart order; listener and follower counts are unavailable."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            if (chartLoading) item { Loading() }
                            if (chartError) item { Message(t("دریافت پیشنهادها ممکن نشد.", "Could not load recommendations.")) }
                            if (recommendations.isNotEmpty()) item { LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) { items(recommendations.take(12), key = { it.feed }) { p -> FeaturedPodcast(p) { open(p) } } } }
                        }
                        1 -> {
                            item { Text(category?.let { if (fa) it.fa else it.en } ?: t("دسته‌بندی و کشف", "Categories & discovery"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold) }
                            if (category != null) item { TextButton(onClick = { category = null; categoryQuery = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text(t("همهٔ دسته‌ها", "All categories")) } }
                            item { OutlinedTextField(value = if (category == null) query else categoryQuery, onValueChange = { if (category == null) query = it else categoryQuery = it }, modifier = Modifier.fillMaxWidth().onFocusChanged { searchFocused = it.isFocused }, singleLine = true, placeholder = { Text(if (category == null) t("جست‌وجوی پادکست یا آدرس RSS", "Search podcasts or RSS URL") else t("جست‌وجو در همین فهرست", "Search this list")) }, shape = RoundedCornerShape(18.dp), leadingIcon = { Icon(Icons.Default.Search, null) }, trailingIcon = { if ((if (category == null) query else categoryQuery).isNotEmpty()) IconButton(onClick = { if (category == null) query = "" else categoryQuery = "" }) { Icon(Icons.Default.Close, t("پاک‌کردن", "Clear")) } }, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search), keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { if (category == null) { if (query.startsWith("https://")) open(Podcast(query, "RSS", "", query)) else if (query.isNotBlank()) { rememberSearch(query); search(query) } } })) }
                            if (category == null && searchFocused && recentSearches.isNotEmpty()) {
                                item { SectionTitle(t("جست‌وجوهای اخیر", "Recent searches"), "") }
                                items(recentSearches, key = { "recent:" + it }) { term -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(term, Modifier.weight(1f).clickable { query = term; rememberSearch(term); search(term) }.padding(12.dp)); IconButton(onClick = { recentSearches = recentSearches.filterNot { it == term }; repo.saveSearchHistory(recentSearches) }) { Icon(Icons.Default.Close, t("حذف", "Remove")) } } }
                                item { HorizontalDivider() }
                            }
                            if (category == null && query.trim().length < 3 && !query.startsWith("https://")) {
                                item { SectionTitle(t("دسته‌ها", "Categories"), "") }
                                items(podcastCategories.chunked(3)) { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { cat -> Surface(onClick = { category = cat; categoryQuery = "" }, modifier = Modifier.weight(1f).aspectRatio(1f), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer) { Box(contentAlignment = Alignment.Center) { Text(if (fa) cat.fa else cat.en, modifier = Modifier.padding(6.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer) } } }; repeat(3 - row.size) { Spacer(Modifier.weight(1f)) } } }
                            }
                            if (category == null && query.startsWith("https://")) item { Button(onClick = { open(Podcast(query.trim(), "RSS", "", query.trim())) }) { Text(t("باز کردن RSS", "Open RSS")) } }
                            if (category == null && query.trim().length >= 3 && !query.startsWith("https://")) {
                                if (loading) item { Loading() }
                                if (error.isNotEmpty()) item { Message(error) }
                                if (!loading && error.isEmpty() && results.isEmpty()) item { Message(t("نتیجه‌ای پیدا نشد.", "No podcasts found.")) }
                                items(results, key = { "result:" + it.feed }) { p -> PodcastRow(p) { rememberSearch(query); open(p) } }
                                if (episodeSearchLoading || episodeSearchDone) item { SectionTitle(t("قسمت‌های دنبال‌شده", "Episodes from followed podcasts"), "${episodeResults.size}") }
                                if (episodeSearchLoading) item { Loading() }
                                if (episodeSearchDone && episodeSearchErrors > 0) item { Message(t("$episodeSearchErrors فید در دسترس نبود؛ نتایج ممکن است ناقص باشند.", "$episodeSearchErrors feeds could not be loaded; results may be incomplete.")) }
                                items(episodeResults, key = { "search:" + it.audio }) { e -> EpisodeCard(e, fa, repo, onPlay = { play(e) }, onQueue = { if (current?.audio != e.audio && queue.none { it.audio == e.audio }) setQueue(queue + e) }, queued = queue.any { it.audio == e.audio }, onPlaylist = { addEpisode = e }, onDownload = { runCatching { repo.download(e) }.onFailure { error = t("دانلود شروع نشد.", "Could not start download.") }; downloads = repo.downloads() }) }
                            }
                            if (category != null || query.trim().length < 3) {
                                item { SectionTitle(if (category == null) t("پیشنهادها", "Suggestions") else t("جدول این دسته", "Category chart"), "") }
                                item { Text(t("ترتیب جدول Apple Podcasts در آمریکا است، نه تعداد شنونده یا دنبال‌کننده.", "Apple Podcasts US chart order, not listener or follower counts."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                if (chartLoading) item { Loading() }
                                if (chartError) item { Message(t("دریافت جدول ممکن نشد. اتصال اینترنت را بررسی کنید.", "Could not load chart. Check your connection.")); TextButton(onClick = { chartRetry++ }) { Text(t("تلاش دوباره", "Try again")) } }
                                val shown = if (category == null || categoryQuery.isBlank()) recommendations else recommendations.filter { searchMatches(it.title, categoryQuery) || searchMatches(it.author, categoryQuery) }
                                if (!chartLoading && !chartError && shown.isEmpty()) item { Message(t("نتیجه‌ای پیدا نشد.", "No results found.")) }
                                items(shown, key = { "chart:" + it.feed }) { p -> PodcastRow(p) { open(p) } }
                            }
                        }
                        2 -> {
                            item { Text(t("کتابخانهٔ من", "Your library"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold); Text(t("همهٔ صداهای محبوبت، یک‌جا", "All your favorite voices in one place"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item { SectionTitle(t("دنبال‌شده‌ها", "Following"), "${subscriptions.size}") }
                            if (subscriptions.isEmpty()) item { Message(t("هنوز پادکستی را دنبال نکرده‌اید. از بخش جست‌وجو شروع کنید.", "You haven't followed any podcasts yet. Start exploring.")) }
                            items(subscriptions, key = { it.feed }) { p -> PodcastRow(p) { open(p) } }
                            item { SavedEntry(t("پلی‌لیست‌ها", "Playlists"), "${playlists.size}", Icons.Default.QueueMusic) { showPlaylists = true; playlistId = null } }
                            item { DownloadEntry(fa, downloads.size) { downloadsPage = true } }
                            item { HistoryEntry(fa) { historyPage = true } }
                            item { SavedEntry(t("علاقه‌مندی‌ها", "Favorites"), "${favorites.size}", Icons.Default.Favorite) { savedPage = 1 } }
                            item { SavedEntry(t("لحظه‌های ذخیره‌شده", "Saved moments"), "${bookmarks.size}", Icons.Default.Bookmark) { savedPage = 2 } }
                            item { SavedEntry(t("پشتیبان کتابخانه", "Back up library"), t("اشتراک‌ها، تاریخچه، یادداشت‌ها، پیشرفت و صف", "Follows, history, notes, progress and queue"), Icons.Default.Backup) { exportBackup.launch("podgir-backup.json") } }
                            item { SavedEntry(t("بازیابی پشتیبان", "Restore backup"), t("از فایل پشتیبان پادگیر", "From a Podgir backup file"), Icons.Default.Restore) { importBackup.launch(arrayOf("application/json", "text/*", "application/octet-stream")) } }
                            item { SavedEntry(t("خروجی اشتراک‌ها", "Export subscriptions"), t("فایل OPML", "OPML file"), Icons.Default.FileUpload) { exportOpml.launch("podgir-subscriptions.opml") } }
                            item { SavedEntry(t("ورود اشتراک‌ها", "Import subscriptions"), t("فایل OPML با فیدهای HTTPS", "OPML file with HTTPS feeds"), Icons.Default.FileDownload) { importOpml.launch(arrayOf("text/*", "application/xml", "application/octet-stream")) } }
                            item { Text(t("OPML فقط اشتراک‌ها را ذخیره می‌کند، نه دانلودها، یادداشت‌ها یا پیشرفت پخش.", "OPML includes subscriptions only, not downloads, notes, or listening progress."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        else -> {
                            item { Text(t("تنظیمات", "Settings"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold); Text(t("پادگیر را برای خودت بساز", "Make Podgir yours"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            item { val modes = listOf(t("همراه با سیستم", "System default"), t("روشن", "Light"), t("تاریک", "Dark")); OptionSetting(t("ظاهر برنامه", "Appearance"), modes[themeMode.coerceIn(0, 2)], modes) { themeMode = it; repo.setLong("themeMode", it.toLong()) } }
                            item { LanguageSetting(fa) { fa = it; repo.setBoolean("fa", it) } }
                            item { SettingRow(t("حذف سکوت", "Skip silence"), t("مکث‌های طولانی هنگام پخش کوتاه می‌شوند", "Shorten long silences during playback"), skipSilence) { skipSilence = it; repo.setBoolean("skipSilence", it); context.startService(android.content.Intent(context, PlaybackService::class.java).setAction("com.podgir.app.SKIP_SILENCE")) } }
                            item { SavedEntry(t("پاک کردن تاریخچهٔ جست‌وجو", "Clear search history"), t("حذف همهٔ جست‌وجوهای اخیر", "Remove all recent searches"), Icons.Default.Search) { clearSearchConfirm = true } }
                            item { SettingRow(t("پخش خودکار قسمت بعدی", "Autoplay next episode"), t("قسمت بعدی در صف", "Next episode in the queue"), autoplay) { autoplay = it; repo.setBoolean("autoplay", it); context.startService(android.content.Intent(context, PlaybackService::class.java).setAction("com.podgir.app.AUTOPLAY")) } }
                            item { SettingRow(t("اعلان قسمت جدید", "New episode notifications"), t("برای پادکست‌های دنبال‌شده", "For followed podcasts"), notifyNew) { notifyNew = it; repo.setBoolean("newEpisodeNotifications", it) } }
                            item { SettingRow(t("یادآور شنیدن", "Listening reminder"), t("تقریباً روزانه؛ زمان دقیق وابسته به اندروید", "Approximately daily; timing depends on Android"), reminder) { reminder = it; repo.setBoolean("listeningReminder", it); ReminderWorker.configure(context, it) } }
                            if ((notifyNew || reminder) && !notificationsAllowed) item { Column { Message(t("اعلان‌های پادگیر در تنظیمات اندروید غیرفعال‌اند.", "Podgir notifications are disabled in Android settings.")); TextButton(onClick = { val intent = if (Build.VERSION.SDK_INT >= 26) android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName) else android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName)); context.startActivity(intent) }) { Text(t("تنظیمات اعلان", "Notification settings")) } } }
                            item { SavedEntry(t("به‌روزرسانی برنامه", "App updates"), if (checkingUpdate) t("در حال بررسی…", "Checking…") else t("بررسی نسخهٔ جدید", "Check for a new version"), Icons.Default.SystemUpdate) { checkUpdate(true) } }
                            item { SavedEntry(t("تنظیمات دانلود", "Download settings"), t("شبکه، محل ذخیره و کیفیت", "Network, location and quality"), Icons.Default.Download) { downloadSettingsPage = true } }
                            item { val values = (2..12).map { it * 25 }; OptionSetting(t("سرعت پیش‌فرض", "Default speed"), "${defaultSpeed / 100f}×", values.map { "${it / 100f}×" }) { i -> defaultSpeed = values[i]; repo.setLong("defaultSpeed", defaultSpeed.toLong()); speed = defaultSpeed / 100f; player?.setPlaybackSpeed(speed) } }
                            item { val values = listOf(5, 10, 15, 20, 30, 45, 60); OptionSetting(t("پرش به عقب", "Skip backward"), "$rewindSeconds s", values.map { "$it s" }) { i -> rewindSeconds = values[i]; repo.setLong("rewindSeconds", rewindSeconds.toLong()) } }
                            item { val values = listOf(5, 10, 15, 20, 30, 45, 60); OptionSetting(t("پرش به جلو", "Skip forward"), "$forwardSeconds s", values.map { "$it s" }) { i -> forwardSeconds = values[i]; repo.setLong("forwardSeconds", forwardSeconds.toLong()) } }
                            item { Message(t("پادگیر رایگان است و به حساب کاربری نیاز ندارد. اشتراک‌ها و دانلودها روی همین دستگاه نگهداری می‌شوند.", "Podgir is free and requires no account. Your follows and downloads stay on this device.")) }
                        }
                    }
                }
            }
            deletePlaylist?.let { pl -> AlertDialog(onDismissRequest = { deletePlaylist = null }, title = { Text(t("حذف پلی‌لیست؟", "Delete playlist?")) }, text = { Text(pl.name) }, confirmButton = { TextButton(onClick = { playlists = playlists.filterNot { it.id == pl.id }; repo.savePlaylists(playlists); deletePlaylist = null }) { Text(t("حذف", "Delete")) } }, dismissButton = { TextButton(onClick = { deletePlaylist = null }) { Text(t("انصراف", "Cancel")) } }) }
            if (clearSearchConfirm) AlertDialog(onDismissRequest = { clearSearchConfirm = false }, title = { Text(t("پاک کردن جست‌وجوها؟", "Clear search history?")) }, confirmButton = { TextButton(onClick = { recentSearches = emptyList(); repo.saveSearchHistory(emptyList()); clearSearchConfirm = false }) { Text(t("پاک کردن", "Clear")) } }, dismissButton = { TextButton(onClick = { clearSearchConfirm = false }) { Text(t("انصراف", "Cancel")) } })
            if (showNewPlaylist) AlertDialog(onDismissRequest = { showNewPlaylist = false }, title = { Text(t("پلی‌لیست جدید", "New playlist")) }, text = { OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it.take(100) }, singleLine = true, label = { Text(t("نام", "Name")) }) }, confirmButton = { TextButton(enabled = newPlaylistName.isNotBlank(), onClick = { playlists = playlists + Playlist(java.util.UUID.randomUUID().toString(), newPlaylistName.trim()); repo.savePlaylists(playlists); showNewPlaylist = false }) { Text(t("ساخت", "Create")) } }, dismissButton = { TextButton(onClick = { showNewPlaylist = false }) { Text(t("انصراف", "Cancel")) } })
            addEpisode?.let { e -> AlertDialog(onDismissRequest = { addEpisode = null }, title = { Text(t("افزودن به پلی‌لیست", "Add to playlist")) }, text = { Column { if (playlists.isEmpty()) Text(t("ابتدا در کتابخانه یک پلی‌لیست بسازید.", "First create a playlist in your library.")); playlists.forEach { pl -> TextButton(onClick = { playlists = playlists.map { if (it.id == pl.id) it.copy(episodes = (it.episodes + e).distinctBy { ep -> ep.audio }) else it }; repo.savePlaylists(playlists); addEpisode = null; toast(t("اضافه شد", "Added")) }) { Text(pl.name) } } } }, confirmButton = { TextButton(onClick = { addEpisode = null }) { Text(t("بستن", "Close")) } }) }
            if (showUpdate) UpdateDialog(updateInfo, updateMessage, fa, updater) { val wasAvailable = updateInfo != null; showUpdate = false; if (wasAvailable) toast(t("بعداً از تنظیمات ← به‌روزرسانی برنامه می‌توانید نصب کنید.", "You can update later from Settings → App updates.")) }
            infoPodcast?.let { p -> PodcastInfoDialog(p, fa, repo) { infoPodcast = null } }
            if (expanded && current != null) PlayerScreen(
                episode = current!!, fa = fa, playing = playing, position = position, duration = duration, speed = speed, sleepMinutes = sleepMinutes, queue = queue, nextAvailable = nextAvailable,
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
@Composable private fun Header(fa: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column {
            Text(if (fa) "یک دنیای تازه برای شنیدن" else "YOUR SPACE TO LISTEN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp)); Text(if (fa) "پادگیر" else "Podgir", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(46.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.GraphicEq, null, tint = MaterialTheme.colorScheme.primary) } }
    }
}
@Composable private fun Hero(fa: Boolean, onClick: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(Color(0xFF171D38), Color(0xFF20284A), Color(0xFF151B31)))).clickable(onClick = onClick)) {
        val artSize = if (maxWidth < 320.dp) 66.dp else 92.dp
        // Artwork is confined to its own column, away from the text.
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(NeonCyan)); Spacer(Modifier.width(7.dp))
                    Text(if (fa) "کشف در پادگیر" else "DISCOVER ON PODGIR", color = NeonCyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(12.dp))
                Text(if (fa) "صدای بعدیِ محبوبت\nاینجاست." else "Your next favorite\nlisten is here.", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                Spacer(Modifier.height(15.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = NeonCyan) { Text(if (fa) "کشف پادکست  ←" else "Explore podcasts  →", modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = Color(0xFF10242A), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
            }
            Spacer(Modifier.width(12.dp))
            Box(Modifier.size(artSize).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF313F60), Color(0xFF222C48)))), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().padding(17.dp)) {
                    val heights = listOf(.25f, .52f, .72f, .46f, .92f, .6f, .35f)
                    val step = size.width / heights.size
                    heights.forEachIndexed { i, height ->
                        val bar = size.height * height
                        drawRoundRect(brush = Brush.verticalGradient(listOf(NeonCyan, NeonViolet)), topLeft = Offset(i * step + step * .24f, (size.height - bar) / 2), size = androidx.compose.ui.geometry.Size(step * .46f, bar), cornerRadius = androidx.compose.ui.geometry.CornerRadius(step))
                    }
                }
            }
        }
    }
}
@Composable private fun SectionTitle(title: String, extra: String) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold); Text(extra, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) } }
@Composable private fun PodcastRow(p: Podcast, onClick: () -> Unit) { Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Cover(p.image, 68); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(p.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(4.dp)); Text(p.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary) } } }
@Composable private fun EpisodeCard(e: Episode, fa: Boolean, repo: Repository, livePosition: Long? = null, liveDuration: Long? = null, onPlay: () -> Unit, onQueue: () -> Unit, queued: Boolean, onPlaylist: () -> Unit, onDownload: () -> Unit) {
    val position = livePosition ?: repo.position(e.audio)
    val duration = (liveDuration ?: 0).takeIf { it > 0 } ?: repo.duration(e.audio)
    val finished = isFinished(position, duration)
    val progress = progressPercent(position, duration)
    var status by remember(e.audio) { mutableIntStateOf(repo.downloadStatus(e)) }
    LaunchedEffect(e.audio) { while (true) { status = repo.downloadStatus(e); delay(2000) } }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Column(Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Cover(e.image, 54); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(e.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(e.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) } }
        if (e.description.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(e.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (position > 0) { Spacer(Modifier.height(8.dp)); Text(if (finished) { if (fa) "تموم کردی ✓" else "Completed ✓" } else if (duration > 0) { if (fa) "$progress٪ شنیده‌شده" else "$progress% listened" } else { if (fa) "در حال شنیدن" else "In progress" }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPlay, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = PlayGreen, contentColor = Color(0xFF182512))) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text(if (position > 0 && !finished) { if (fa) "ادامه" else "Resume" } else { if (fa) "پخش" else "Play" }) }
            IconButton(onClick = onQueue, enabled = !queued) { Icon(if (queued) Icons.Default.CheckCircle else Icons.Default.PlaylistAdd, if (fa) "افزودن به صف" else "Add to queue", tint = if (queued) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onPlaylist) { Icon(Icons.Default.LibraryAdd, if (fa) "افزودن به پلی‌لیست" else "Add to playlist") }
            IconButton(onClick = onDownload, enabled = status == 0 || status == DownloadManager.STATUS_FAILED) { Icon(if (status == DownloadManager.STATUS_SUCCESSFUL) Icons.Default.CheckCircle else Icons.Default.Download, if (fa) "دانلود" else "Download", tint = if (status == DownloadManager.STATUS_SUCCESSFUL) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    } }
}
@Composable private fun SettingRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) { Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) { Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(checked = checked, onCheckedChange = onChange) } } }
@Composable private fun Message(text: String) { Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f), modifier = Modifier.fillMaxWidth()) { Text(text, modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start) } }
@Composable private fun Loading() { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp) } }
