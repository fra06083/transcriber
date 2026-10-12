package com.android.transcriber.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import com.android.transcriber.data.model.TranscriptionRecord
import com.android.transcriber.ui.DynamicTranscribingStatus
import com.android.transcriber.ui.WaveformBars
import com.android.transcriber.ui.components.StreamingWordText
import com.android.transcriber.ui.viewmodel.TranscriptionEngine
import com.android.transcriber.ui.viewmodel.TranscriptionUiState
import com.android.transcriber.ui.viewmodel.TranscriptionViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen(
    viewModel: TranscriptionViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedLanguage by viewModel.selectedLanguage.collectAsState()
    val selectedEngine by viewModel.selectedEngine.collectAsState()
    val recentTranscriptions by viewModel.recentTranscriptions.collectAsState()

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.processAudioUri(it, sourceApp = "Transcriber") }
    }

    val isProcessing = uiState is TranscriptionUiState.Converting || uiState is TranscriptionUiState.Transcribing

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    if (isProcessing) {
                        Brush.sweepGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary,
                                MaterialTheme.colorScheme.primary
                            )
                        )
                    } else {
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.88f,
                targetValue = 1.12f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "scale"
            )

            Icon(
                imageVector = if (isProcessing) Icons.Default.GraphicEq else Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier
                    .size(40.dp)
                    .scale(if (isProcessing) scale else 1f),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Trascrizione Offline",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Transcriber v1.2 • Offline & Sistema",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            SegmentedButton(
                selected = selectedLanguage == "auto",
                onClick = { viewModel.setSelectedLanguage("auto") },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
            ) {
                Text("Auto", fontWeight = if (selectedLanguage == "auto") FontWeight.Bold else FontWeight.Normal)
            }
            SegmentedButton(
                selected = selectedLanguage == "it",
                onClick = { viewModel.setSelectedLanguage("it") },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
            ) {
                Text("Italiano", fontWeight = if (selectedLanguage == "it") FontWeight.Bold else FontWeight.Normal)
            }
            SegmentedButton(
                selected = selectedLanguage == "en",
                onClick = { viewModel.setSelectedLanguage("en") },
                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
            ) {
                Text("Inglese", fontWeight = if (selectedLanguage == "en") FontWeight.Bold else FontWeight.Normal)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = { audioPickerLauncher.launch("audio/*") },
            enabled = !isProcessing,
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scegli file audio", style = MaterialTheme.typography.titleSmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        AnimatedContent(
            targetState = uiState,
            transitionSpec = {
                (fadeIn(spring(stiffness = Spring.StiffnessLow)) + scaleIn(initialScale = 0.96f))
                    .togetherWith(fadeOut(tween(120)) + scaleOut(targetScale = 0.96f))
            },
            label = "state_transition"
        ) { state ->
            when (state) {
                is TranscriptionUiState.Idle -> {
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Condividi un vocale da WhatsApp",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Seleziona una nota vocale e tocca Condividi con transcriber per trascrivere o riprodurre anonimamente.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }

                is TranscriptionUiState.Converting,
                is TranscriptionUiState.Transcribing -> {
                    val partial = if (uiState is TranscriptionUiState.Transcribing) (uiState as TranscriptionUiState.Transcribing).partialText else ""
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        DynamicTranscribingStatus(
                            partialText = partial,
                            modifier = Modifier.padding(20.dp),
                            headlineColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                is TranscriptionUiState.Success -> {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "Trascrizione",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                val wordCount = state.text.split("\\s+".toRegex()).count { it.isNotBlank() }
                                AssistChip(
                                    onClick = { },
                                    label = { Text("$wordCount parole", style = MaterialTheme.typography.labelSmall) },
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )

                            SelectionContainer {
                                Text(
                                    text = state.text.ifBlank { "Nessun testo rilevato nel file audio." },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 24.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cb.setPrimaryClip(ClipData.newPlainText("Trascrizione", state.text))
                                        Toast.makeText(context, "Copiato negli appunti", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copia")
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Button(
                                    onClick = {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, state.text)
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Condividi trascrizione"))
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Outlined.Share, null, Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Condividi")
                                }
                            }
                        }
                    }
                }

                is TranscriptionUiState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Outlined.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    "Errore trascrizione",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Sezione Messaggi Trascritti Recenti
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Messaggi recenti",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            if (recentTranscriptions.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "${recentTranscriptions.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (recentTranscriptions.isEmpty()) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.HistoryEdu,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Nessuna trascrizione recente",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Le note vocali trascritte da WhatsApp o da file audio appariranno qui con orario e app usata.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                recentTranscriptions.forEach { record ->
                    RecentTranscriptionCard(
                        record = record,
                        onCopy = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Trascrizione", record.text))
                            Toast.makeText(context, "Copiato negli appunti", Toast.LENGTH_SHORT).show()
                        },
                        onShare = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, record.text)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Condividi trascrizione"))
                        },
                        onDelete = {
                            viewModel.deleteRecord(record)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))
    }
}

@Composable
private fun RecentTranscriptionCard(
    record: TranscriptionRecord,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isWhatsApp = record.sourceApp.contains("whatsapp", ignoreCase = true)
                    val isTelegram = record.sourceApp.contains("telegram", ignoreCase = true)
                    val badgeColor = when {
                        isWhatsApp -> androidx.compose.ui.graphics.Color(0xFF25D366)
                        isTelegram -> androidx.compose.ui.graphics.Color(0xFF229ED9)
                        else -> MaterialTheme.colorScheme.primary
                    }

                    Surface(
                        color = badgeColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = record.sourceApp,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (record.isWhisper) "Whisper" else "Google",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = formatRecordTimestamp(record.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            SelectionContainer {
                Text(
                    text = record.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "Copia",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Outlined.Share,
                        contentDescription = "Condividi",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Elimina",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

private fun formatRecordTimestamp(timestamp: Long): String {
    val now = Calendar.getInstance()
    val recordCal = Calendar.getInstance().apply { timeInMillis = timestamp }

    val isToday = now.get(Calendar.YEAR) == recordCal.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == recordCal.get(Calendar.DAY_OF_YEAR)

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeStr = timeFormat.format(Date(timestamp))

    return if (isToday) {
        "Oggi, $timeStr"
    } else {
        val dateFormat = SimpleDateFormat("dd/MM • HH:mm", Locale.getDefault())
        dateFormat.format(Date(timestamp))
    }
}
