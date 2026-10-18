package com.personal.voyager.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.voyager.R
import com.personal.voyager.core.Fmt
import com.personal.voyager.dl.DlJob
import com.personal.voyager.dl.DoneItem
import com.personal.voyager.dl.Downloads
import com.personal.voyager.dl.JobStatus
import com.personal.voyager.dl.Storage
import com.personal.voyager.dl.Store
import com.personal.voyager.vm.AppViewModel
import com.personal.voyager.vm.Screen
import java.io.File

/**
 * Opens the file in the user's player as ITS OWN task (so leaving the player returns here and it is not stuck
 * inside this app), using the permanent MediaStore address (so the player can reopen it later without any
 * temporary permission).
 */
private fun openFile(ctx: Context, d: DoneItem) {
    Storage.resolveUri(ctx, d.uri, d.mime) { uri ->
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, d.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        view.clipData = ClipData.newRawUri("", uri)
        try {
            ctx.startActivity(view)
        } catch (e: ActivityNotFoundException) {
            try {
                ctx.startActivity(Intent.createChooser(view, "Open with").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e2: Exception) {
                Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

private fun shareFile(ctx: Context, d: DoneItem) {
    val send = Intent(Intent.ACTION_SEND).setType(d.mime)
        .putExtra(Intent.EXTRA_STREAM, Storage.uriFor(ctx, d.uri)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { ctx.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun CheckDot(on: Boolean) {
    Ic(if (on) R.drawable.ic_check_circle else R.drawable.ic_circle_outline, tint = if (on) Pal.Red else Pal.Gray, size = 26.dp)
}

@Composable
private fun DeleteDialog(count: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Pal.Card2,
        title = { Text(if (count == 1) "Delete this item?" else "Delete $count items?", style = Txt.Title) },
        text = { Text("The file will be removed from your phone.", style = Txt.Body.copy(color = Pal.Gray)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", style = Txt.Label.copy(color = Pal.RedBright)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", style = Txt.Label.copy(color = Pal.White)) } },
    )
}

// ---------------- job rows ----------------

@Composable
fun JobRow(
    job: DlJob,
    modifier: Modifier = Modifier,
    selectable: Boolean = false,
    selected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    showControls: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth()
            .then(if (selectable) Modifier.clickable(onClick = onToggleSelect) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectable) {
            CheckDot(selected)
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.size(width = 96.dp, height = 60.dp), contentAlignment = Alignment.Center) {
            if (job.isAudio) VinylThumb(job.thumb, 60.dp)
            else Thumb(job.thumb, Modifier.fillMaxSize(), RoundedCornerShape(8.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(job.base, style = Txt.Row, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(50)).background(Pal.Card2)) {
                Box(Modifier.fillMaxWidth(job.progress.coerceIn(0f, 1f)).height(3.dp).background(Pal.Red))
            }
            Spacer(Modifier.height(6.dp))
            Row {
                val left = when (job.status) {
                    JobStatus.ACTIVE -> if (job.speedBps > 0) Fmt.speed(job.speedBps) else "Starting…"
                    JobStatus.PROCESSING -> "Processing…"
                    JobStatus.QUEUED -> "Waiting"
                    JobStatus.PAUSED -> "Paused"
                    JobStatus.FAILED -> job.error ?: "Failed"
                }
                Text(
                    left,
                    style = Txt.Small.copy(color = if (job.status == JobStatus.FAILED) Pal.RedBright else Pal.Gray),
                    maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Text(String.format(java.util.Locale.US, "%.1f %%", job.progress * 100), style = Txt.Small)
            }
        }
        if (showControls && !selectable) {
            Spacer(Modifier.width(14.dp))
            if (job.status == JobStatus.PROCESSING) {
                // Nothing to tap while merging or encoding: pausing there would redo the work.
                Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator(color = Pal.Red, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            } else {
                val (icon, action) = when (job.status) {
                    JobStatus.PAUSED -> R.drawable.ic_play to { Downloads.resume(job.id) }
                    JobStatus.FAILED -> R.drawable.ic_refresh to { Downloads.retry(job.id) }
                    else -> R.drawable.ic_pause to { Downloads.pause(job.id) }
                }
                Box(
                    Modifier.size(32.dp).clip(CircleShape).border(1.5.dp, Pal.Gray, CircleShape).clickable(onClick = action),
                    contentAlignment = Alignment.Center,
                ) { Ic(icon, tint = Pal.White, size = 16.dp) }
            }
            Spacer(Modifier.width(4.dp))
        }
    }
}

// ---------------- Play tab home ----------------

@Composable
fun PlayHomeScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val jobs by Downloads.jobs.collectAsStateWithLifecycle()
    val done by Store.library.collectAsStateWithLifecycle()
    var filter by remember { mutableIntStateOf(0) }
    var menuOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf<String?>(null) }
    var deleteMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var confirmIds by remember { mutableStateOf<Set<String>?>(null) }
    var rowMenu by remember { mutableStateOf<String?>(null) }

    val q = query
    val shown = done.filter {
        (filter == 0 || (filter == 1 && it.isAudio) || (filter == 2 && !it.isAudio)) &&
            (q.isNullOrBlank() || it.base.contains(q, ignoreCase = true))
    }

    Column(Modifier.fillMaxSize()) {
        if (deleteMode) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(R.drawable.ic_close, { deleteMode = false; selected.clear() })
                Text("${selected.size} items", style = Txt.Title)
            }
            val all = shown.isNotEmpty() && shown.all { it.id in selected }
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (all) selected.clear() else {
                        selected.clear()
                        selected.addAll(shown.map { it.id })
                    }
                }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckDot(all)
                Spacer(Modifier.width(14.dp))
                Text("Select all", style = Txt.Body)
            }
        } else {
            if (jobs.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clickable { vm.push(Screen.Downloading) }.padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Downloading (${jobs.size})", style = Txt.Title, modifier = Modifier.weight(1f))
                    Ic(R.drawable.ic_chevron_right, tint = Pal.Gray)
                }
                JobRow(jobs.last(), showControls = false)
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Downloaded", style = Txt.Title, modifier = Modifier.weight(1f))
                Box {
                    IconTap(R.drawable.ic_filter, { menuOpen = true }, tint = if (filter == 0) Pal.White else Pal.RedBright)
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        listOf("All", "Audio", "Video").forEachIndexed { i, label ->
                            DropdownMenuItem(
                                text = { Text(label, style = Txt.Body.copy(color = if (i == filter) Pal.RedBright else Pal.White)) },
                                onClick = { filter = i; menuOpen = false },
                            )
                        }
                    }
                }
                IconTap(R.drawable.ic_delete, { deleteMode = true; selected.clear() })
                IconTap(R.drawable.ic_search, { query = if (query == null) "" else null }, tint = if (query == null) Pal.White else Pal.RedBright)
            }
            if (q != null) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(14.dp))
                        .background(Pal.Card2).padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    BasicTextField(
                        value = q, onValueChange = { query = it }, singleLine = true, textStyle = Txt.Body,
                        cursorBrush = SolidColor(Pal.Red), modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            Box { if (q.isEmpty()) Text("Filter by title", style = Txt.Body.copy(color = Pal.Gray)); inner() }
                        },
                    )
                }
            }
        }

        Box(Modifier.weight(1f)) {
            if (shown.isEmpty()) {
                CenterMessage(if (done.isEmpty()) "Nothing downloaded yet" else "No matches", Modifier.align(Alignment.Center))
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.id }) { d ->
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (deleteMode) { if (d.id in selected) selected.remove(d.id) else selected.add(d.id) } else openFile(ctx, d) }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (deleteMode) {
                                CheckDot(d.id in selected)
                                Spacer(Modifier.width(12.dp))
                            }
                            DoneThumb(d)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.base, style = Txt.Row, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Ic(if (d.isAudio) R.drawable.ic_music_note else R.drawable.ic_video, tint = Pal.Gray, size = 16.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text(listOfNotNull(if (d.durationSec > 0) Fmt.duration(d.durationSec) else null, Fmt.size(d.size).ifEmpty { null }).joinToString("  |  "), style = Txt.Small)
                                }
                            }
                            if (!deleteMode) {
                                Box {
                                    IconTap(R.drawable.ic_more_vert, { rowMenu = d.id }, tint = Pal.Gray)
                                    DropdownMenu(expanded = rowMenu == d.id, onDismissRequest = { rowMenu = null }) {
                                        DropdownMenuItem(text = { Text("Open", style = Txt.Body) }, onClick = { rowMenu = null; openFile(ctx, d) })
                                        DropdownMenuItem(text = { Text("Share", style = Txt.Body) }, onClick = { rowMenu = null; shareFile(ctx, d) })
                                        DropdownMenuItem(text = { Text("Delete", style = Txt.Body.copy(color = Pal.RedBright)) }, onClick = { rowMenu = null; confirmIds = setOf(d.id) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (deleteMode) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                PillButton("Delete", { confirmIds = selected.toSet() }, Modifier.fillMaxWidth(), enabled = selected.isNotEmpty())
            }
        }
    }

    confirmIds?.let { ids ->
        DeleteDialog(
            count = ids.size,
            onConfirm = {
                Store.removeDone(ids)
                confirmIds = null
                selected.clear()
                deleteMode = false
            },
            onDismiss = { confirmIds = null },
        )
    }
}

@Composable
private fun DoneThumb(d: DoneItem) {
    val model: Any? = d.thumbPath?.let { File(it) }
    Box(Modifier.size(width = 96.dp, height = 60.dp), contentAlignment = Alignment.Center) {
        if (d.isAudio) VinylThumb(model, 60.dp)
        else Thumb(model, Modifier.fillMaxSize(), RoundedCornerShape(8.dp))
    }
}

// ---------------- full "Downloading" screen ----------------

@Composable
fun DownloadingScreen(vm: AppViewModel) {
    val jobs by Downloads.jobs.collectAsStateWithLifecycle()
    var deleteMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var confirmIds by remember { mutableStateOf<Set<String>?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (deleteMode) {
            Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTap(R.drawable.ic_close, { deleteMode = false; selected.clear() })
                Text("${selected.size} items", style = Txt.Title)
            }
            val all = jobs.isNotEmpty() && jobs.all { it.id in selected }
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (all) selected.clear() else {
                        selected.clear()
                        selected.addAll(jobs.map { it.id })
                    }
                }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckDot(all)
                Spacer(Modifier.width(14.dp))
                Text("Select all", style = Txt.Body)
            }
        } else {
            val anyRunning = jobs.any { it.status != JobStatus.PAUSED && it.status != JobStatus.FAILED }
            TopBar("Downloading", { vm.pop() }) {
                if (jobs.isNotEmpty()) {
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Pal.Card2)
                            .clickable { if (anyRunning) Downloads.pauseAll() else Downloads.resumeAll() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) { Text(if (anyRunning) "Pause all" else "Resume all", style = Txt.Label.copy(color = Pal.White)) }
                    Spacer(Modifier.width(6.dp))
                }
                IconTap(R.drawable.ic_delete, { deleteMode = true; selected.clear() })
            }
        }

        Box(Modifier.weight(1f)) {
            if (jobs.isEmpty()) CenterMessage("No active downloads", Modifier.align(Alignment.Center))
            else LazyColumn(Modifier.fillMaxSize()) {
                items(jobs, key = { it.id }) { j ->
                    JobRow(
                        j,
                        selectable = deleteMode,
                        selected = j.id in selected,
                        onToggleSelect = { if (j.id in selected) selected.remove(j.id) else selected.add(j.id) },
                    )
                }
            }
        }

        if (deleteMode) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                PillButton("Delete", { confirmIds = selected.toSet() }, Modifier.fillMaxWidth(), enabled = selected.isNotEmpty())
            }
        }
    }

    confirmIds?.let { ids ->
        DeleteDialog(
            count = ids.size,
            onConfirm = {
                Downloads.remove(ids)
                confirmIds = null
                selected.clear()
                deleteMode = false
            },
            onDismiss = { confirmIds = null },
        )
    }
}
