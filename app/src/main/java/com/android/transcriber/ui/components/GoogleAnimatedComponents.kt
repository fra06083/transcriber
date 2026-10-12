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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// Brand colors
val GoogleBlue = Color(0xFF4285F4)
val GoogleRed = Color(0xFFEA4335)
val GoogleYellow = Color(0xFFFBBC05)
val GoogleGreen = Color(0xFF34A853)
val GeminiPurple = Color(0xFF9C27B0)
val GeminiCyan = Color(0xFF00E5FF)

// Animated multicolor shimmer progress bar
@Composable
fun GoogleShimmerBar(
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    isAnimating: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "offset"
    )

    val gradient = Brush.linearGradient(
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
            .background(if (isAnimating) gradient else Brush.linearGradient(listOf(GoogleBlue, GeminiCyan)))
    )
}

// Pulsing 4-dot assistant wave
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

    val infiniteTransition = rememberInfiniteTransition(label = "dots")

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
                label = "dot_y_$index"
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

// Progressive typewriter text animation
@Composable
fun StreamingWordText(
    fullText: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    speedMs: Long = 18L,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    cursorColor: Color = GoogleBlue
) {
    val cleanTarget = fullText.trim()
    var displayedLength by remember { mutableIntStateOf(0) }

    LaunchedEffect(cleanTarget) {
        if (cleanTarget.isEmpty()) {
            displayedLength = 0
            return@LaunchedEffect
        }

        while (displayedLength < cleanTarget.length) {
            val step = if (cleanTarget.length - displayedLength > 25) 4 else 2
            displayedLength = minOf(displayedLength + step, cleanTarget.length)
            delay(speedMs)
        }
    }

    val visibleText = remember(cleanTarget, displayedLength) {
        if (cleanTarget.isEmpty() || displayedLength <= 0) ""
        else cleanTarget.take(displayedLength)
    }

    // Blinking cursor
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    val showCursor = isStreaming || (displayedLength < cleanTarget.length)

    val annotatedText = remember(visibleText, showCursor, cursorAlpha) {
        buildAnnotatedString {
            append(visibleText)
            if (showCursor) {
                if (visibleText.isNotEmpty()) {
                    append(" ")
                }
                withStyle(SpanStyle(color = cursorColor.copy(alpha = cursorAlpha))) {
                    append("▍")
                }
            }
        }
    }

    Text(
        text = annotatedText,
        style = textStyle,
        color = textColor,
        lineHeight = (textStyle.fontSize.value * 1.45f).sp,
        modifier = modifier
    )
}
