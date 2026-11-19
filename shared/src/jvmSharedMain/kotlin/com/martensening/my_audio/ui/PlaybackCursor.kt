package com.martensening.my_audio.ui

import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.martensening.my_audio.ui.theme.RecordRed

/** Same fixed red playhead for track overview and envelope editor, in project frames. */
internal fun DrawScope.drawPlaybackCursor(frame: Long, windowStart: Long, windowFrames: Long) {
    if (windowFrames <= 0 || frame < windowStart || frame > windowStart + windowFrames) return
    val x = size.width * ((frame - windowStart).toDouble() / windowFrames).toFloat()
    drawLine(RecordRed, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
}
