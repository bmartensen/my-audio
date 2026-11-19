package com.martensening.my_audio.ui

import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.ui.theme.CheckGreen
import com.martensening.my_audio.ui.theme.RecordRed
import kotlin.math.log10
import kotlin.math.roundToInt

private const val FLOOR_DB = -60f
private const val YELLOW_DB = -12f
private const val RED_DB = -3f
private const val HOLD_MILLIS = 1500L

private val MeterGreen = CheckGreen
private val MeterYellow = Color(0xFFFDD835)
private val MeterRed = RecordRed

fun linearToDb(level: Float): Float = if (level <= 0f) FLOOR_DB else (20f * log10(level)).coerceAtLeast(FLOOR_DB)

private fun dbToFraction(db: Float) = ((db - FLOOR_DB) / -FLOOR_DB).coerceIn(0f, 1f)

/**
 * Horizontal peak meter in dBFS (-60..0 dB): green up to -12 dB, yellow up to -3 dB, red above,
 * with a held peak marker and readout. [level] is the linear peak (0..1) of the latest interval.
 */
@Composable
fun LevelMeter(level: Float, modifier: Modifier = Modifier, meterWidth: Dp = 180.dp) {
    var hold by remember { mutableFloatStateOf(0f) }
    var holdSince by remember { mutableLongStateOf(0L) }
    LaunchedEffect(level) {
        val now = System.currentTimeMillis()
        if (level >= hold || now - holdSince > HOLD_MILLIS) {
            hold = level
            holdSince = now
        }
    }
    val holdDb = linearToDb(hold)
    val clipping = holdDb > RED_DB
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
    ) {
        Canvas(
            Modifier
                .width(meterWidth)
                .height(8.dp)
        ) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(track, cornerRadius = radius)
            val lit = dbToFraction(linearToDb(level)) * size.width
            val zones = listOf(
                0f to dbToFraction(YELLOW_DB) to MeterGreen,
                dbToFraction(YELLOW_DB) to dbToFraction(RED_DB) to MeterYellow,
                dbToFraction(RED_DB) to 1f to MeterRed
            )
            for ((range, color) in zones) {
                val start = range.first * size.width
                val end = minOf(range.second * size.width, lit)
                if (end > start) drawRect(color, Offset(start, 0f), Size(end - start, size.height))
            }
            if (hold > 0f) {
                val x = dbToFraction(holdDb) * size.width
                val color = when {
                    holdDb > RED_DB -> MeterRed
                    holdDb > YELLOW_DB -> MeterYellow
                    else -> MeterGreen
                }
                drawRect(color, Offset((x - 1.5.dp.toPx()).coerceAtLeast(0f), 0f), Size(3.dp.toPx(), size.height))
            }
        }
        Text(
            text = if (hold > 0f) "${holdDb.roundToInt()} dB" else "−∞ dB",
            style = MaterialTheme.typography.labelSmall,
            color = if (clipping) MeterRed else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp)
        )
    }
}
