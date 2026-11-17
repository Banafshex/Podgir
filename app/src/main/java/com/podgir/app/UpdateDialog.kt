package com.podgir.app

import android.app.DownloadManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UpdateDialog(info: UpdateInfo?, message: String, fa: Boolean, updater: AppUpdater, onLater: () -> Unit) {
    var status by remember(info?.version) { mutableStateOf(UpdateDownload(0, null)) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var permissionHint by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(info?.version) {
        while (info != null) {
            status = if (updater.downloadVersion() == info.version) withContext(Dispatchers.IO) { updater.status() } else UpdateDownload(0, null)
            delay(800)
        }
    }
    val downloading = status.status in listOf(DownloadManager.STATUS_PENDING, DownloadManager.STATUS_RUNNING, DownloadManager.STATUS_PAUSED)
    val complete = status.status == DownloadManager.STATUS_SUCCESSFUL
    AlertDialog(onDismissRequest = onLater,
        title = { Text(if (info != null) { if (fa) "نسخهٔ جدید پادگیر" else "Podgir update" } else { if (fa) "بررسی به‌روزرسانی" else "Check for updates" }) },
        text = { Column {
            if (info == null) Text(message) else {
                Text(if (fa) "نسخهٔ ${info.version} آماده است. اکنون دریافت و نصب می‌کنید؟" else "Version ${info.version} is available. Download and install it now?")
                if (downloading) {
                    Spacer(Modifier.height(16.dp))
                    if (status.percent == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { (status.percent ?: 0) / 100f }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text((if (status.status == DownloadManager.STATUS_PAUSED) { if (fa) "در انتظار شبکه" else "Waiting for network" } else { if (fa) "در حال دانلود" else "Downloading" }) + (status.percent?.let { " · $it%" } ?: ""), style = MaterialTheme.typography.bodySmall)
                }
                if (complete) { Spacer(Modifier.height(8.dp)); Text(if (fa) "دانلود کامل شد؛ برای نصب تأیید اندروید لازم است." else "Download complete. Android will ask you to confirm installation.", style = MaterialTheme.typography.bodySmall) }
                if (permissionHint) { Spacer(Modifier.height(8.dp)); Text(if (fa) "اجازهٔ نصب را در تنظیمات بدهید، برگردید و دوباره نصب را بزنید." else "Allow installation in settings, return here and tap Install again.", style = MaterialTheme.typography.bodySmall) }
                if (status.status == DownloadManager.STATUS_FAILED || error.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text(if (error.isNotEmpty()) error else if (fa) "دانلود ناموفق بود؛ دوباره تلاش کنید." else "Download failed. Try again.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        } },
        confirmButton = {
            if (info == null) TextButton(onClick = onLater) { Text(if (fa) "بستن" else "Close") }
            else TextButton(enabled = !busy && !downloading, onClick = {
                if (!complete) {
                    error = ""; permissionHint = false
                    runCatching { updater.start(info) }.onSuccess { status = updater.status() }.onFailure { error = if (fa) "شروع دانلود ممکن نشد." else "Could not start the download." }
                } else {
                    scope.launch {
                        busy = true; error = ""
                        try {
                            val file = withContext(Dispatchers.IO) { updater.verifyDownloaded() }
                            if (updater.canInstall()) updater.install(file) else { permissionHint = true; updater.openPermissionSettings() }
                        } catch (_: Exception) { error = if (fa) "فایل یا امضای به‌روزرسانی معتبر نیست، یا نصب‌کننده در دسترس نیست." else "The update or signature is invalid, or the installer is unavailable." }
                        busy = false
                    }
                }
            }) { Text(if (busy) { if (fa) "در حال بررسی…" else "Verifying…" } else if (complete) { if (fa) "نصب" else "Install" } else { if (fa) "دریافت" else "Download" }) }
        },
        dismissButton = { if (info != null) TextButton(onClick = onLater) { Text(if (fa) "بعداً" else "Later") } }
    )
}
