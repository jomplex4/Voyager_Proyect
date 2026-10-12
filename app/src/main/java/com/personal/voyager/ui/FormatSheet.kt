package com.personal.voyager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personal.voyager.R
import com.personal.voyager.vm.SheetData
import com.personal.voyager.vm.SheetRow

@Composable
private fun FormatRow(row: SheetRow, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(if (row.isAudio) R.drawable.ic_music_note else R.drawable.ic_video, tint = Pal.Gray, size = 24.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(row.label, style = Txt.Row)
            if (row.subtitle != null) Text(row.subtitle, style = Txt.Small, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (row.size.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(row.size, style = Txt.Small.copy(color = Pal.Gray))
        }
        Spacer(Modifier.width(14.dp))
        Ic(if (selected) R.drawable.ic_check_circle else R.drawable.ic_circle_outline, tint = if (selected) Pal.Red else Pal.Gray, size = 26.dp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSheet(data: SheetData, onDismiss: () -> Unit) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selected by remember(data) { mutableStateOf(data.preselect) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Pal.Card,
        contentColor = Pal.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        scrimColor = Color(0x99000000),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 8.dp).size(width = 44.dp, height = 4.dp).clip(RoundedCornerShape(50)).background(Pal.Line))
        },
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                if (data.fromClipboard) "Video you copied" else "Download video as",
                style = Txt.Title, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumb(data.thumb, Modifier.size(width = 112.dp, height = 63.dp), RoundedCornerShape(8.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(data.title, style = Txt.Row, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(data.domain, style = Txt.Small)
                }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                if (data.audio.isNotEmpty()) {
                    Text("Music", style = Txt.Label, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp))
                    data.audio.forEach { FormatRow(it, selected == it.id) { selected = it.id } }
                }
                if (data.video.isNotEmpty()) {
                    Text("Video", style = Txt.Label, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp))
                    data.video.forEach { FormatRow(it, selected == it.id) { selected = it.id } }
                }
            }
            Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp)) {
                PillButton(
                    "Download",
                    onClick = { selected?.let { data.onConfirm(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selected != null,
                )
            }
        }
    }
}

@Composable
fun LoadingOverlay(onCancel: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0x99000000)).clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.clip(RoundedCornerShape(20.dp)).background(Pal.Card2).padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = Pal.Red, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text("Loading formats…", style = Txt.Label.copy(color = Pal.White))
        }
    }
}
