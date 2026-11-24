package com.martensening.my_audio.ui

import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.ui.theme.RecordRed

/** Same fixed red playhead for track overview and envelope editor, in project frames. */
internal fun DrawScope.drawPlaybackCursor(frame: Long, windowStart: Long, windowFrames: Long) {
    if (windowFrames <= 0 || frame < windowStart || frame > windowStart + windowFrames) return
    val x = size.width * ((frame - windowStart).toDouble() / windowFrames).toFloat()
    drawLine(RecordRed, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
}

/** The start marker where Play and Record begin: a thin line with a small flag at the top, in [color]. */
internal fun DrawScope.drawStartMarker(frame: Long, windowStart: Long, windowFrames: Long, color: Color) {
    if (windowFrames <= 0 || frame < windowStart || frame > windowStart + windowFrames) return
    val x = size.width * ((frame - windowStart).toDouble() / windowFrames).toFloat()
    drawLine(color, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.5.dp.toPx())
    val flag = 6.dp.toPx()
    drawPath(Path().apply {
        moveTo(x, 0f)
        lineTo(x + flag, 0f)
        lineTo(x, flag)
        close()
    }, color)
}
