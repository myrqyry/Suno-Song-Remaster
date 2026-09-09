package com.example.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AudioMetadata
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioPurple

@Composable
fun MetadataEditorScreen(
    metadata: AudioMetadata,
    currentFileName: String,
    onSaveMetadata: (AudioMetadata) -> Unit,
    onApplyToAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    var title by remember(metadata) { mutableStateOf(metadata.title) }
    var artist by remember(metadata) { mutableStateOf(metadata.artist) }
    var album by remember(metadata) { mutableStateOf(metadata.album) }
    var genre by remember(metadata) { mutableStateOf(metadata.genre) }
    var year by remember(metadata) { mutableStateOf(metadata.year) }
    var track by remember(metadata) { mutableStateOf(metadata.track) }
    var comment by remember(metadata) { mutableStateOf(metadata.comment) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            tonalElevation = 2.dp
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AUDIO METADATA (ID3 / RIFF INFO)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = null,
                        tint = StudioPurple
                    )
                }

                Text(
                    text = "Current file: $currentFileName",
                    style = MaterialTheme.typography.bodySmall,
                    color = StudioCyan
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Track Title (INAM)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("meta_title_input")
                )

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist (IART)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("meta_artist_input")
                )

                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album (IPRD)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("meta_album_input")
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = genre,
                        onValueChange = { genre = it },
                        label = { Text("Genre (IGNR)") },
                        modifier = Modifier
                            .weight(1.5f)
                            .testTag("meta_genre_input")
                    )
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it },
                        label = { Text("Year (ICRD)") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("meta_year_input")
                    )
                    OutlinedTextField(
                        value = track,
                        onValueChange = { track = it },
                        label = { Text("Track #") },
                        modifier = Modifier
                            .weight(0.8f)
                            .testTag("meta_track_input")
                    )
                }

                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment / Mastering Notes (ICMT)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("meta_comment_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onSaveMetadata(
                                AudioMetadata(
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    genre = genre,
                                    year = year,
                                    track = track,
                                    comment = comment
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = StudioPurple),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("save_metadata_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Tags")
                    }

                    OutlinedButton(
                        onClick = onApplyToAll,
                        modifier = Modifier
                            .weight(1.2f)
                            .testTag("apply_all_metadata_button")
                    ) {
                        Icon(imageVector = Icons.Default.LibraryMusic, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Apply to All Tracks")
                    }
                }
            }
        }
    }
}
