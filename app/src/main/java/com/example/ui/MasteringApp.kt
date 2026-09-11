package com.example.ui

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.LevelMeter
import com.example.ui.components.SpectrumVisualizer
import com.example.ui.components.TransportControls
import com.example.ui.screens.BatchQueueScreen
import com.example.ui.screens.MasteringConsoleScreen
import com.example.ui.screens.MetadataEditorScreen
import com.example.ui.screens.WaveformEditorScreen
import com.example.ui.theme.StudioCyan
import com.example.ui.theme.StudioGreen
import com.example.ui.theme.StudioPurple
import com.example.viewmodel.MasteringViewModel
import java.util.Locale

enum class AppNavTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    MASTERING("Mastering", Icons.Default.Tune),
    EDITOR("Waveform DAW", Icons.Default.GraphicEq),
    METADATA("Tags", Icons.Default.Bookmark),
    BATCH("Batch Queue", Icons.AutoMirrored.Filled.QueueMusic)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasteringApp(
    viewModel: MasteringViewModel = viewModel()
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(AppNavTab.MASTERING) }
    var showDemoMenu by remember { mutableStateOf(false) }

    val activeBuffer by viewModel.activeBuffer.collectAsState()
    val currentFileName by viewModel.currentFileName.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val lufsResult by viewModel.lufsResult.collectAsState()
    val metadata by viewModel.metadata.collectAsState()
    val isPlaying by viewModel.player.isPlaying.collectAsState()
    val currentSec by viewModel.player.currentPositionSec.collectAsState()
    val durationSec by viewModel.player.durationSec.collectAsState()
    val isBypass by viewModel.player.isBypass.collectAsState()
    val meterData by viewModel.player.meterData.collectAsState()
    val statusMsg by viewModel.statusMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val selStart by viewModel.selectionStart.collectAsState()
    val selEnd by viewModel.selectionEnd.collectAsState()
    val canUndo by viewModel.canUndo.collectAsState()
    val fadeDuration by viewModel.fadeDuration.collectAsState()
    val loopEnabled by viewModel.loopEnabled.collectAsState()

    val batchQueue by viewModel.batchQueue.collectAsState()
    val isBatchProcessing by viewModel.isBatchProcessing.collectAsState()
    val batchProgress by viewModel.batchProgress.collectAsState()
    val batchStatusText by viewModel.batchStatusText.collectAsState()

    LaunchedEffect(statusMsg) {
        statusMsg?.let { msg -> snackbarHostState.showSnackbar(msg.text) }
    }

    var isAddingToBatch by remember { mutableStateOf(false) }
    val openAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            var displayName = "audio_track.wav"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    displayName = cursor.getString(nameIndex)
                }
            }
            if (isAddingToBatch) {
                viewModel.addBatchItem(displayName, uri)
            } else {
                viewModel.loadAudioFromUri(context, uri, displayName)
            }
        }
    }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/wav")
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.let { stream ->
                viewModel.exportActiveTrack(stream) {}
            }
        }
    }

    val batchFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Some document providers grant session access without persistable flags.
            }

            val parentDocumentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )

            viewModel.runBatchProcessing(context) { item ->
                val fileName = "${item.name.substringBeforeLast(".")}_mastered.wav"
                val documentUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    parentDocumentUri,
                    "audio/wav",
                    fileName
                )
                documentUri?.let { context.contentResolver.openOutputStream(it) }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = null,
                                tint = StudioPurple,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                "Suno Song Remaster",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = currentFileName,
                            style = MaterialTheme.typography.bodySmall,
                            color = StudioCyan,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showDemoMenu = true },
                            modifier = Modifier.testTag("demo_menu_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Demo Tracks",
                                tint = StudioCyan
                            )
                        }
                        DropdownMenu(
                            expanded = showDemoMenu,
                            onDismissRequest = { showDemoMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Synthwave Anthem (Demo)") },
                                onClick = {
                                    showDemoMenu = false
                                    viewModel.loadDemoTrack("synthwave")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Acoustic Sunset (Demo)") },
                                onClick = {
                                    showDemoMenu = false
                                    viewModel.loadDemoTrack("acoustic")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Lo-Fi Chill Beat (Demo)") },
                                onClick = {
                                    showDemoMenu = false
                                    viewModel.loadDemoTrack("lofi")
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            isAddingToBatch = false
                            openAudioLauncher.launch(arrayOf("audio/*"))
                        },
                        modifier = Modifier.testTag("open_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Open Audio File"
                        )
                    }

                    IconButton(
                        onClick = {
                            val baseName = currentFileName.substringBeforeLast(".")
                            exportFileLauncher.launch("${baseName}_mastered.wav")
                        },
                        modifier = Modifier.testTag("export_wav_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export WAV",
                            tint = StudioGreen
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                AppNavTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        icon = { Icon(imageVector = tab.icon, contentDescription = tab.title) },
                        label = {
                            Text(
                                tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = StudioPurple,
                            indicatorColor = StudioPurple
                        ),
                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}")
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                color = Color.Transparent
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val measuredLufs = lufsResult?.integratedLufs
                        val lufsText = if (measuredLufs != null && measuredLufs > -70f) {
                            String.format(
                                Locale.US,
                                "Measured: %.1f LUFS  |  Target: %d LUFS",
                                measuredLufs,
                                settings.targetLufs
                            )
                        } else {
                            "Target: ${settings.targetLufs} LUFS  |  Loudness meter active"
                        }
                        Text(
                            text = lufsText,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = StudioCyan
                        )

                        if (isLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = StudioPurple
                                )
                                Text("Processing...", fontSize = 10.sp, color = StudioPurple)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LevelMeter(
                            meterData = meterData,
                            modifier = Modifier.weight(1.2f)
                        )
                        SpectrumVisualizer(
                            spectrumBands = meterData.spectrumBands,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    TransportControls(
                        isPlaying = isPlaying,
                        currentSec = currentSec,
                        durationSec = durationSec,
                        isBypass = isBypass,
                        onPlayPause = {
                            if (isPlaying) viewModel.player.pause() else viewModel.player.play()
                        },
                        onStop = { viewModel.player.stop() },
                        onSeek = { viewModel.player.seekTo(it) },
                        onToggleBypass = { viewModel.toggleBypass() }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    AppNavTab.MASTERING -> {
                        MasteringConsoleScreen(
                            settings = settings,
                            onSettingsChanged = { viewModel.updateSettings(it) },
                            onPresetSelected = { viewModel.setPreset(it) }
                        )
                    }

                    AppNavTab.EDITOR -> {
                        WaveformEditorScreen(
                            buffer = activeBuffer,
                            currentSec = currentSec,
                            durationSec = durationSec,
                            selectionStart = selStart,
                            selectionEnd = selEnd,
                            fadeDuration = fadeDuration,
                            loopEnabled = loopEnabled,
                            isPlaying = isPlaying,
                            canUndo = canUndo,
                            onSeek = { viewModel.player.seekTo(it) },
                            onSelectRange = { start, end -> viewModel.setSelection(start, end) },
                            onClearSelection = { viewModel.clearSelection() },
                            onPlayPause = {
                                if (isPlaying) viewModel.player.pause() else viewModel.player.play()
                            },
                            onStop = { viewModel.player.stop() },
                            onToggleLoop = { viewModel.toggleLoop() },
                            onFadeDurationChanged = { viewModel.setFadeDuration(it) },
                            onApplyFade = { viewModel.applyFade(it) },
                            onTrim = { viewModel.trimToSelection() },
                            onCut = { viewModel.deleteSelection() },
                            onSilence = { viewModel.silenceSelection() },
                            onNormalize = { viewModel.normalizePeak() },
                            onReverse = { viewModel.reverseSelectionOrAll() },
                            onUndo = { viewModel.undoEdit() },
                            onReset = { viewModel.resetToOriginal() }
                        )
                    }

                    AppNavTab.METADATA -> {
                        MetadataEditorScreen(
                            metadata = metadata,
                            currentFileName = currentFileName,
                            onSaveMetadata = { viewModel.updateMetadata(it) },
                            onApplyToAll = { viewModel.applyMetadataToAllBatch() }
                        )
                    }

                    AppNavTab.BATCH -> {
                        BatchQueueScreen(
                            queue = batchQueue,
                            isProcessing = isBatchProcessing,
                            progress = batchProgress,
                            statusText = batchStatusText,
                            onPickAudioFile = {
                                isAddingToBatch = true
                                openAudioLauncher.launch(arrayOf("audio/*"))
                            },
                            onAddSampleTrack = { sampleType ->
                                val name = when (sampleType) {
                                    "acoustic" -> "Acoustic Sunset Track"
                                    "lofi" -> "Lo-Fi Midnight Beat Track"
                                    else -> "Synthwave Anthem Track"
                                }
                                viewModel.addBatchItem(name, null, sampleType)
                            },
                            onRemoveItem = { viewModel.removeBatchItem(it) },
                            onClearQueue = { viewModel.clearBatch() },
                            onStartBatch = { batchFolderLauncher.launch(null) }
                        )
                    }
                }
            }
        }
    }
}
