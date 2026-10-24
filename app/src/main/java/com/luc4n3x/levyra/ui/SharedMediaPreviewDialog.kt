package com.luc4n3x.levyra.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.luc4n3x.levyra.feature.sharedmedia.BulkLinkCaptureSummary
import com.luc4n3x.levyra.feature.sharedmedia.SharedMediaKind
import com.luc4n3x.levyra.feature.sharedmedia.SharedMediaPreview
import com.luc4n3x.levyra.ui.components.CircleIconButton
import com.luc4n3x.levyra.ui.components.EmptyCover
import com.luc4n3x.levyra.ui.i18n.BulkLinkCaptureCopy
import com.luc4n3x.levyra.ui.i18n.bulkLinkCaptureCopy
import com.luc4n3x.levyra.ui.theme.*

@Composable
internal fun SharedMediaPreviewDialog(
    preview: SharedMediaPreview,
    languageCode: String,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onQueue: () -> Unit,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    onSavePlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    val italian = languageCode.equals("it", ignoreCase = true)
    val bulkCopy = remember(languageCode) { bulkLinkCaptureCopy(languageCode) }
    val bulkSummary = preview.bulkSummary
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = LevyraInk.copy(alpha = 0.98f),
            shape = RoundedCornerShape(30.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    if (preview.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = preview.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(78.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                    } else {
                        EmptyCover(
                            Modifier
                                .size(78.dp)
                                .clip(RoundedCornerShape(18.dp))
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = if (italian) "Condiviso con Levyra" else "Shared with Levyra",
                            color = LevyraCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = preview.title.ifBlank { bulkCopy.title.takeIf { bulkSummary != null }.orEmpty() },
                            color = LevyraText,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (preview.subtitle.isNotBlank()) Text(
                            text = preview.subtitle,
                            color = LevyraMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    CircleIconButton(
                        icon = Icons.Rounded.Close,
                        tint = LevyraText,
                        background = Color.White.copy(alpha = 0.08f),
                        onClick = onDismiss
                    )
                }
                when {
                    preview.loading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp, color = LevyraCyan)
                        }
                    }
                    preview.error.isNotBlank() -> {
                        Text(
                            text = preview.error,
                            color = LevyraOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    bulkSummary != null && !preview.playable -> {
                        BulkLinkCaptureSummaryText(bulkSummary, bulkCopy)
                        Text(
                            text = bulkCopy.noTracks,
                            color = LevyraOrange,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    preview.playable -> {
                        bulkSummary?.let { BulkLinkCaptureSummaryText(it, bulkCopy) }
                        if (bulkSummary == null) Text(
                            text = if (italian) "${preview.tracks.size} ${if (preview.tracks.size == 1) "brano pronto" else "brani pronti"}" else "${preview.tracks.size} ${if (preview.tracks.size == 1) "track ready" else "tracks ready"}",
                            color = LevyraMuted,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SharedMediaAction(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Rounded.PlayArrow,
                                title = if (italian) "Riproduci" else "Play",
                                primary = true,
                                onClick = onPlay
                            )
                            SharedMediaAction(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Rounded.Download,
                                title = if (italian) "Scarica" else "Download",
                                primary = false,
                                onClick = onDownload
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SharedMediaAction(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Rounded.SkipNext,
                                title = if (italian) "Dopo" else "Play next",
                                primary = false,
                                onClick = onPlayNext
                            )
                            SharedMediaAction(
                                modifier = Modifier.weight(1f),
                                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                                title = if (italian) "In coda" else "Queue",
                                primary = false,
                                onClick = onQueue
                            )
                        }
                        if (preview.request.kind == SharedMediaKind.LevyraPlaylist) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                SharedMediaAction(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Rounded.LibraryAdd,
                                    title = if (italian) "Importa" else "Import",
                                    primary = false,
                                    onClick = onImport
                                )
                            }
                        }
                        if (preview.request.kind == SharedMediaKind.BulkLinks) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                SharedMediaAction(
                                    modifier = Modifier.weight(1f),
                                    icon = Icons.Rounded.LibraryAdd,
                                    title = bulkCopy.savePlaylist,
                                    primary = false,
                                    onClick = onSavePlaylist
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BulkLinkCaptureSummaryText(summary: BulkLinkCaptureSummary, copy: BulkLinkCaptureCopy) {
    val lines = buildList {
        add(copy.detected(summary.detectedLinks))
        add(copy.found(summary.resolvedTracks))
        if (summary.duplicates > 0) add(copy.duplicates(summary.duplicates))
        if (summary.unrecognized > 0) add(copy.unrecognized(summary.unrecognized))
    }
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                color = LevyraMuted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SharedMediaAction(
    modifier: Modifier,
    icon: ImageVector,
    title: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (primary) LevyraCyan else Color.White.copy(alpha = 0.08f),
        contentColor = if (primary) LevyraBlack else LevyraText,
        shape = RoundedCornerShape(16.dp),
        border = if (primary) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
        modifier = modifier.pressable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}
