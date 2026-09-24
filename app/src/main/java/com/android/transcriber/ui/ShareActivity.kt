package com.android.transcriber.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.transcriber.ui.components.GoogleAssistantDots
import com.android.transcriber.ui.components.GoogleBlue
import com.android.transcriber.ui.components.GoogleShimmerBar
import com.android.transcriber.ui.components.StreamingWordText
import com.android.transcriber.ui.theme.TranscriberTheme
import com.android.transcriber.ui.viewmodel.TranscriptionEngine
import com.android.transcriber.ui.viewmodel.TranscriptionUiState
import com.android.transcriber.ui.viewmodel.TranscriptionViewModel
import kotlinx.coroutines.delay
import java.util.Locale

class ShareActivity : ComponentActivity() {

    private val viewModel: TranscriptionViewModel by viewModels()
    private var audioUri: Uri? = null
    private var mediaPlayer: MediaPlayer? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        audioUri = extractAudioUri(intent)
        if (audioUri == null) {
            finish()
            return
        }

        setContent {
            TranscriberTheme {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val uiState by viewModel.uiState.collectAsState()
                val selectedLanguage by viewModel.selectedLanguage.collectAsState()
                val selectedEngine by viewModel.selectedEngine.collectAsState()

                var isPlaying by remember { mutableStateOf(false) }
                var showPlayer by remember { mutableStateOf(false) }
                var currentPos by remember { mutableIntStateOf(0) }
                var totalDuration by remember { mutableIntStateOf(0) }

                LaunchedEffect(isPlaying) {
                    while (isPlaying && mediaPlayer != null) {
                        try {
                            currentPos = mediaPlayer?.currentPosition ?: 0
                            totalDuration = mediaPlayer?.duration ?: 0
                        } catch (_: Exception) { }
                        delay(100)
                    }
                }

                ModalBottomSheet(
                    onDismissRequest = {
                        stopPlayback()
                        finish()
                    },
                    sheetState = sheetState,
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    dragHandle = {
                        Box(
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .width(36.dp)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 36.dp)
                            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = {
                                    stopPlayback()
                                    finish()
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Chiudi",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        AnimatedContent(
                            targetState = uiState,
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(280)) + scaleIn(initialScale = 0.98f))
                                    .togetherWith(fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.98f))
                            },
                            label = "content_state"
                        ) { state ->
                            when (state) {
                                is TranscriptionUiState.Idle -> {
                                    if (!showPlayer) {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(56.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.GraphicEq,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))

                                            Text(
                                                text = "Nota Vocale Ricevuta",
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                            )

                                            Text(
                                                text = "Transcriber v1.1 • Riconoscimento Vocale",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )

                                            Spacer(modifier = Modifier.height(14.dp))

                                            SingleChoiceSegmentedButtonRow(
                                                modifier = Modifier.fillMaxWidth(0.95f)
                                            ) {
                                                SegmentedButton(
                                                    selected = selectedEngine == TranscriptionEngine.GOOGLE,
                                                    onClick = { viewModel.setSelectedEngine(TranscriptionEngine.GOOGLE) },
                                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                                ) {
                                                    Text(
                                                        "Google Speech",
                                                        fontWeight = if (selectedEngine == TranscriptionEngine.GOOGLE) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                                SegmentedButton(
                                                    selected = selectedEngine == TranscriptionEngine.WHISPER,
                                                    onClick = { viewModel.setSelectedEngine(TranscriptionEngine.WHISPER) },
                                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                                ) {
                                                    Text(
                                                        "Whisper Turbo",
                                                        fontWeight = if (selectedEngine == TranscriptionEngine.WHISPER) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            SingleChoiceSegmentedButtonRow(
                                                modifier = Modifier.fillMaxWidth(0.95f)
                                            ) {
                                                SegmentedButton(
                                                    selected = selectedLanguage == "auto",
                                                    onClick = { viewModel.setSelectedLanguage("auto") },
                                                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                                                ) {
                                                    Text("Auto")
                                                }
                                                SegmentedButton(
                                                    selected = selectedLanguage == "it",
                                                    onClick = { viewModel.setSelectedLanguage("it") },
                                                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                                                ) {
                                                    Text("Italiano")
                                                }
                                                SegmentedButton(
                                                    selected = selectedLanguage == "en",
                                                    onClick = { viewModel.setSelectedLanguage("en") },
                                                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                                                ) {
                                                    Text("Inglese")
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(18.dp))

                                            Surface(
                                                onClick = {
                                                    audioUri?.let { viewModel.processAudioUri(it) }
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(18.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(42.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.primary),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Translate,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onPrimary,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(16.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            "Trascrivi in testo",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                        Text(
                                                            "Trascrizione immediata in questo popup",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                                        )
                                                    }

                                                    Icon(
                                                        Icons.Default.ChevronRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            Surface(
                                                onClick = {
                                                    showPlayer = true
                                                    audioUri?.let { startPlayback(it) { isPlaying = false } }
                                                    isPlaying = true
                                                },
                                                shape = RoundedCornerShape(20.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(18.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(42.dp)
                                                            .clip(CircleShape)
                                                            .background(MaterialTheme.colorScheme.secondaryContainer),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            Icons.Default.Headphones,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(16.dp))

                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            "Riproduci anonimamente",
                                                            style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        Text(
                                                            "Ascolta senza attivare le spunte blu",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                                        )
                                                    }

                                                    Icon(
                                                        Icons.Default.ChevronRight,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                                    )
                                                }
                                            }

                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(24.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                                .padding(20.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            WaveformBars(isAnimating = isPlaying)

                                            Spacer(modifier = Modifier.height(16.dp))

                                            Slider(
                                                value = if (totalDuration > 0) currentPos.toFloat() / totalDuration else 0f,
                                                onValueChange = { fraction ->
                                                    val target = (fraction * totalDuration).toInt()
                                                    mediaPlayer?.seekTo(target)
                                                    currentPos = target
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    formatMs(currentPos),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Text(
                                                    formatMs(totalDuration),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                FilledIconButton(
                                                    onClick = {
                                                        if (isPlaying) {
                                                            mediaPlayer?.pause()
                                                            isPlaying = false
                                                        } else {
                                                            mediaPlayer?.start()
                                                            isPlaying = true
                                                        }
                                                    },
                                                    modifier = Modifier.size(56.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(28.dp)
                                                    )
                                                }

                                                FilledTonalIconButton(
                                                    onClick = {
                                                        stopPlayback()
                                                        showPlayer = false
                                                    },
                                                    modifier = Modifier.size(44.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = null)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(14.dp))

                                            Button(
                                                onClick = {
                                                    stopPlayback()
                                                    audioUri?.let { viewModel.processAudioUri(it) }
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Icon(Icons.Default.Translate, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Trascrivi questo audio")
                                            }
                                        }
                                    }
                                }

                                is TranscriptionUiState.Converting,
                                is TranscriptionUiState.Transcribing -> {
                                    val partial = if (state is TranscriptionUiState.Transcribing) state.partialText else ""
                                    DynamicTranscribingStatus(
                                        partialText = partial,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }

                                is TranscriptionUiState.Success -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(CircleShape)
                                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    "Trascrizione completata",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            val wordCount = state.text.split("\\s+".toRegex()).count { it.isNotBlank() }
                                            Surface(
                                                color = MaterialTheme.colorScheme.secondaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    "$wordCount parole",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(14.dp))

                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 120.dp, max = 280.dp)
                                        ) {
                                            SelectionContainer(
                                                modifier = Modifier
                                                    .padding(18.dp)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                StreamingWordText(
                                                    fullText = state.text.ifBlank { "Nessun testo rilevato nella nota vocale." },
                                                    isStreaming = false,
                                                    speedMs = 28L,
                                                    textStyle = MaterialTheme.typography.bodyLarge,
                                                    textColor = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(20.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                    cb.setPrimaryClip(ClipData.newPlainText("Trascrizione", state.text))
                                                    Toast.makeText(this@ShareActivity, "Testo copiato", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Copia")
                                            }

                                            Button(
                                                onClick = {
                                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, state.text)
                                                    }
                                                    startActivity(Intent.createChooser(sendIntent, "Condividi testo"))
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Icon(Icons.Outlined.Share, null, Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Condividi")
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        FilledTonalButton(
                                            onClick = { finish() },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Text("Chiudi")
                                        }
                                    }
                                }

                                is TranscriptionUiState.Error -> {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Outlined.ErrorOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(44.dp)
                                        )

                                        Spacer(modifier = Modifier.height(12.dp))

                                        Text(
                                            "Errore di elaborazione",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))

                                        Text(
                                            text = state.message,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(20.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.resetState()
                                                    audioUri?.let { viewModel.processAudioUri(it) }
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text("Riprova")
                                            }

                                            Button(
                                                onClick = { finish() },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(14.dp)
                                            ) {
                                                Text("Chiudi")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startPlayback(uri: Uri, onComplete: () -> Unit) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@ShareActivity, uri)
                setOnCompletionListener { onComplete() }
                prepare()
                start()
            }
        } catch (_: Exception) {
            finish()
        }
    }

    private fun stopPlayback() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) { }
    }

    private fun extractAudioUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        val type = intent.type ?: return null
        if (!type.startsWith("audio/") && type != "application/ogg" && type != "application/octet-stream") return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }
}

@Composable
fun WaveformBars(isAnimating: Boolean, modifier: Modifier = Modifier) {
    val barCount = 18
    val infiniteTransition = rememberInfiniteTransition(label = "bars")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(barCount) { index ->
            val animDuration = 450 + (index * 60) % 400
            val animatedFraction by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = animDuration, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )

            val height = if (isAnimating) {
                12.dp + (32.dp * animatedFraction)
            } else {
                10.dp
            }

            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

@Composable
fun DynamicTranscribingStatus(
    partialText: String,
    modifier: Modifier = Modifier,
    headlineColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    val dynamicMessages = remember {
        listOf(
            "Ascoltando l'audio per te...",
            "Ci stiamo occupando delle onde audio...",
            "Traduco i borbottii in parole comprensibili...",
            "Decifro i segreti di questo vocale...",
            "Metto ordine tra le parole...",
            "Ascolto attentamente ogni sillaba...",
            "Un pizzico di magia ed è pronto...",
            "Lucidiamo le ultime virgole..."
        )
    }

    val subMessages = remember {
        listOf(
            "Isolo la voce ed elimino i rumori...",
            "Decodifico ogni frequenza audio...",
            "Ricostruisco la frase parola per parola...",
            "Verifico la grammatica e gli accenti...",
            "Controllo il ritmo e il significato...",
            "Applico la punteggiatura corretta...",
            "Quasi pronto, ultimi ritocchi...",
            "Un attimo e il testo compare qui..."
        )
    }

    var messageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2400)
            messageIndex = (messageIndex + 1) % dynamicMessages.size
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GoogleAssistantDots(
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        AnimatedContent(
            targetState = dynamicMessages[messageIndex],
            transitionSpec = {
                (fadeIn(animationSpec = tween(400)) + slideInVertically { it / 2 })
                    .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutVertically { -it / 2 })
            },
            label = "dynamic_transcribe_msg"
        ) { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = headlineColor,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Transcriber v1.1 • Riconoscimento intelligente",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Barra animata con sfumatura continua in stile Google
        GoogleShimmerBar(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(bottom = 14.dp),
            height = 5.dp
        )

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp, max = 220.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (partialText.isNotBlank() && !partialText.contains("%")) {
                    // Generazione progressiva in tempo reale delle parole ricevute
                    StreamingWordText(
                        fullText = partialText,
                        isStreaming = true,
                        speedMs = 24L,
                        textStyle = MaterialTheme.typography.bodyMedium,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val currentDetail = if (partialText.isNotBlank() && partialText.contains("%")) {
                        partialText
                    } else {
                        subMessages[messageIndex]
                    }
                    AnimatedContent(
                        targetState = currentDetail,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(350)) togetherWith fadeOut(animationSpec = tween(250))
                        },
                        label = "detail_msg"
                    ) { detailText ->
                        Text(
                            text = "$detailText ▍",
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
