package com.podgir.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

@Composable
fun PodcastInfoDialog(podcast: Podcast, fa: Boolean, repo: Repository, onClose: () -> Unit) {
    var description by remember(podcast.feed) { mutableStateOf("") }
    var loading by remember(podcast.feed) { mutableStateOf(true) }
    var failed by remember(podcast.feed) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(podcast.feed, retry) {
        loading = true; failed = false
        try { description = repo.podcastDescription(podcast) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    AlertDialog(onDismissRequest = onClose,
        title = { Text(if (fa) "دربارهٔ پادکست" else "About this podcast") },
        text = { Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
            Text(podcast.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            if (podcast.author.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text(podcast.author, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(16.dp))
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                failed -> { Text(if (fa) "دریافت توضیحات ممکن نشد." else "Could not load the description."); TextButton(onClick = { retry++ }) { Text(if (fa) "تلاش دوباره" else "Try again") } }
                description.isBlank() -> Text(if (fa) "ناشر توضیحی برای این پادکست ارائه نکرده است." else "The publisher has not provided a description.")
                else -> Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        } },
        confirmButton = { TextButton(onClick = onClose) { Text(if (fa) "بستن" else "Close") } }
    )
}
