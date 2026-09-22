package com.android.transcriber.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Colori ufficiali Google & Gemini
val GoogleBlue = Color(0xFF4285F4)
val GoogleRed = Color(0xFFEA4335)
val GoogleYellow = Color(0xFFFBBC05)
val GoogleGreen = Color(0xFF34A853)
val GeminiPurple = Color(0xFF9C27B0)
val GeminiCyan = Color(0xFF00E5FF)

/**
 * Barra di avanzamento orizzontale animata con sfumatura continua in stile Google / Gemini.
 * Crea un flusso cromatico liquido e continuo che elimina qualsiasi effetto statico o a scatti.
 */
@Composable
fun GoogleShimmerBar(
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    isAnimating: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "google_shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_offset"
    )

    val googleGradient = Brush.linearGradient(
        colors = listOf(
            GoogleBlue,
            GeminiPurple,
            GeminiCyan,
            GoogleGreen,
            GoogleYellow,
            GoogleRed,
            GoogleBlue
        ),
        start = Offset(offset, 0f),
        end = Offset(offset + 600f, 0f)
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(if (isAnimating) googleGradient else Brush.linearGradient(listOf(GoogleBlue, GeminiCyan)))
    )
}

/**
 * I 4 punti pulsanti animati di Google Assistant con rimbalzo a onda sfalsato.
 */
@Composable
fun GoogleAssistantDots(
    modifier: Modifier = Modifier,
    dotSize: Dp = 9.dp,
    dotSpacing: Dp = 7.dp
) {
    val dots = listOf(
        GoogleBlue,
        GoogleRed,
        GoogleYellow,
        GoogleGreen
    )

    val infiniteTransition = rememberInfiniteTransition(label = "dots_wave")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing),
        verticalAlignment = Alignment.CenterVertically
    ) {
        dots.forEachIndexed { index, color ->
            val translationY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -7f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1100
                        0f at 0
                        -7f at (200 + index * 90) using FastOutSlowInEasing
                        0f at (450 + index * 90) using FastOutSlowInEasing
                        0f at 1100
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "dot_anim_$index"
            )

            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.25f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 1100
                        1f at 0
                        1.25f at (200 + index * 90) using FastOutSlowInEasing
                        1f at (450 + index * 90) using FastOutSlowInEasing
                        1f at 1100
                    },
                    repeatMode = RepeatMode.Restart
                ),
                label = "dot_scale_$index"
            )

            Box(
                modifier = Modifier
                    .offset(y = translationY.dp)
                    .size((dotSize.value * scale).dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

/**
 * Genera e visualizza le parole progressivamente (effetto Typewriter / Streaming)
 * come in Gemini e Google Assistant, evitando la comparsa improvvisa di tutto il blocco di testo.
 *
 * @param fullText Il testo completo o parziale da mostrare progressivamente.
 * @param isStreaming Indica se la trascrizione è ancora in corso.
 * @param speedMs Ritardo tra la comparsa di una parola e la successiva (default: 32ms).
 */
@Composable
fun StreamingWordText(
    fullText: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    speedMs: Long = 32L,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    cursorColor: Color = GoogleBlue
) {
    val words = remember(fullText) {
        if (fullText.isBlank()) emptyList() else fullText.split("\\s+".toRegex()).filter { it.isNotBlank() }
    }

    var displayedCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(fullText) {
        val totalWords = words.size
        // Se il testo si azzera o cambia radicalmente
        if (totalWords < displayedCount) {
            displayedCount = totalWords
        }
        // Fa apparire le parole gradualmente una alla volta
        while (displayedCount < totalWords) {
            displayedCount++
            delay(speedMs)
        }
    }

    // Cursore pulsante mentre sta ancora scrivendo o elaborando
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    val currentText = remember(displayedCount, words) {
        words.take(displayedCount).joinToString(" ")
    }

    val showCursor = isStreaming || (displayedCount < words.size)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = buildString {
                append(currentText)
                if (showCursor && currentText.isNotEmpty()) {
                    append(" ")
                }
            },
            style = textStyle,
            color = textColor,
            lineHeight = (textStyle.fontSize.value * 1.45f).sp
        )

        if (showCursor) {
            Box(
                modifier = Modifier
                    .padding(bottom = 3.dp, start = 2.dp)
                    .width(7.dp)
                    .height((textStyle.fontSize.value * 1.1f).dp)
                    .clip(CircleShape)
                    .background(cursorColor.copy(alpha = cursorAlpha))
            )
        }
    }
}
