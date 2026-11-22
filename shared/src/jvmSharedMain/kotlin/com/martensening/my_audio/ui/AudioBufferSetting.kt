package com.martensening.my_audio.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.martensening.my_audio.audio.AudioBuffer
import com.martensening.my_audio.audio.AudioSettings
import com.martensening.my_audio.resources.*
import org.jetbrains.compose.resources.stringResource

/**
 * The global stream buffer as one compact choice: Automatic | Short | Long. Settings carry no explanation;
 * the user guide describes latency, dropouts and the separate calibration per choice.
 */
@Composable
fun AudioBufferChoice(settings: AudioSettings, modifier: Modifier = Modifier, enabled: Boolean = true, onChange: () -> Unit = {}) {
    val selected by settings.buffer.collectAsState()
    val choices = AudioBuffer.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        choices.forEachIndexed { index, buffer ->
            SegmentedButton(
                selected = selected == buffer,
                onClick = {
                    settings.setBuffer(buffer)
                    onChange()
                },
                shape = SegmentedButtonDefaults.itemShape(index, choices.size),
                enabled = enabled
            ) {
                Text(stringResource(buffer.label()), maxLines = 1)
            }
        }
    }
}

private fun AudioBuffer.label() = when (this) {
    AudioBuffer.Automatic -> Res.string.audio_buffer_automatic
    AudioBuffer.Short -> Res.string.audio_buffer_short
    AudioBuffer.Long -> Res.string.audio_buffer_long
}
