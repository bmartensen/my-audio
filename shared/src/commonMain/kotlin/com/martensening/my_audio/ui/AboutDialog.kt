package com.martensening.my_audio.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.martensening.my_audio.resources.Res
import com.martensening.my_audio.resources.*
import com.martensening.my_audio.ui.theme.RecordRed
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

// A small stage in the colours of the app icon: graphite, piano lacquer, nickel, ivory and the red
// record light. Fixed in light and dark mode, like the icon itself.
private val StageTop = Color(0xFF2E333B)
private val StageBottom = Color(0xFF121418)
private val Ivory = Color(0xFFF4EFE4)
private val Nickel = Color(0xFFB4BBC5)
private val Muted = Color(0xFF8E959F)

/**
 * About My Audio: logo, version, credits, contact, privacy policy and the open-source licences
 * (LICENSE, shown verbatim). Music notes drift in slowly from below while the dialog is open.
 */
@Composable
fun AboutDialog(version: String, onDismiss: () -> Unit) {
    var showLicenses by rememberSaveable { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PlatformBackHandler(enabled = showLicenses) { showLicenses = false }
        // Side by side on wide windows (desktop, phone in landscape), stacked on narrow ones.
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            val wide = maxWidth >= 680.dp
            val shape = RoundedCornerShape(28.dp)
            Box(
                Modifier
                    .padding(horizontal = 20.dp, vertical = if (wide) 12.dp else 20.dp)
                    .widthIn(max = if (wide) 760.dp else 420.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(StageTop, StageBottom)))
                    .border(1.dp, Nickel.copy(alpha = 0.22f), shape)
            ) {
                DriftingNotes(Modifier.matchParentSize())
                Crossfade(targetState = showLicenses, label = "about-page") { licenses ->
                    if (licenses) LicensesPage(onBack = { showLicenses = false })
                    else AboutPage(version, wide, onShowLicenses = { showLicenses = true }, onDismiss = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun AboutPage(version: String, wide: Boolean, onShowLicenses: () -> Unit, onDismiss: () -> Unit) {
    val scroll = rememberScrollState()
    if (wide) {
        // Landscape phones leave about 350 dp of height: close and footer move under the masthead,
        // credits and links are set tighter.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 28.dp, vertical = 16.dp)
        ) {
            Column(Modifier.weight(0.9f), horizontalAlignment = Alignment.CenterHorizontally) {
                Masthead(version, compact = true)
                CloseButton(onDismiss)
                Copyright()
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1.1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Credits(compact = true)
                StaffDivider(Modifier.padding(vertical = 6.dp))
                Links(version, onShowLicenses, rowPadding = 5.dp)
            }
        }
    } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Masthead(version, compact = false)
            StaffDivider(Modifier.padding(vertical = 12.dp))
            Credits(compact = false)
            StaffDivider(Modifier.padding(vertical = 12.dp))
            Links(version, onShowLicenses, rowPadding = 8.dp)
            CloseButton(onDismiss)
            Copyright()
        }
    }
}

/** Logo, name, version and tagline. */
@Composable
private fun Masthead(version: String, compact: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (compact) BreathingLogo(box = 100.dp, logo = 76.dp) else BreathingLogo(box = 128.dp, logo = 96.dp)
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))
        Text(
            stringResource(Res.string.app_name),
            color = Ivory,
            fontSize = if (compact) 26.sp else 30.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(6.dp))
        VersionChip(stringResource(Res.string.about_version, version))
        Spacer(Modifier.height(if (compact) 8.dp else 10.dp))
        Text(
            stringResource(Res.string.about_tagline),
            color = Nickel,
            style = MaterialTheme.typography.bodyLarge,
            fontStyle = FontStyle.Italic
        )
    }
}

@Composable
private fun Credits(compact: Boolean) {
    val nameSize = if (compact) 18.sp else 21.sp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Credit(
            role = stringResource(Res.string.about_role_concept),
            name = stringResource(Res.string.about_person_concept),
            nameSize = nameSize
        )
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))
        Text(
            stringResource(Res.string.about_community_credit),
            color = Muted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        // The sample clip used to audition effects when a project has nothing to play (CC0, see licenses/THIRD_PARTY_NOTICES.md).
        Text(
            stringResource(Res.string.about_sample_credit),
            color = Muted,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Links(version: String, onShowLicenses: () -> Unit, rowPadding: Dp) {
    val uriHandler = LocalUriHandler.current
    val open: (String) -> Unit = { uri -> runCatching { uriHandler.openUri(uri) } }
    val email = stringResource(Res.string.about_email)
    val privacyUrl = stringResource(Res.string.about_privacy_url)
    val websiteUrl = stringResource(Res.string.about_website_url)
    Column {
        LinkRow(Res.drawable.ic_mail, stringResource(Res.string.about_contact), email, rowPadding) {
            open("mailto:$email?subject=My%20Audio%20$version")
        }
        LinkRow(Res.drawable.ic_privacy, stringResource(Res.string.about_privacy), null, rowPadding) { open(privacyUrl) }
        LinkRow(Res.drawable.ic_public, stringResource(Res.string.about_website), null, rowPadding) { open(websiteUrl) }
        LinkRow(Res.drawable.ic_license, stringResource(Res.string.about_licenses), null, rowPadding, external = false, onClick = onShowLicenses)
    }
}

@Composable
private fun CloseButton(onDismiss: () -> Unit) {
    TextButton(onClick = onDismiss, modifier = Modifier.padding(top = 4.dp)) {
        Text(stringResource(Res.string.about_close), color = Ivory, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Copyright() {
    Text(
        stringResource(Res.string.about_copyright),
        color = Muted,
        style = MaterialTheme.typography.labelSmall,
        textAlign = TextAlign.Center
    )
}

/** Original-project and complete third-party notices, selectable and lazily rendered. */
@Composable
private fun LicensesPage(onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(painterResource(Res.drawable.ic_arrow_back), stringResource(Res.string.action_back), tint = Ivory)
            }
            Text(stringResource(Res.string.about_licenses), color = Ivory, style = MaterialTheme.typography.titleLarge)
        }
        Box(
            Modifier
                // Takes what the window leaves below the title, at most a comfortable reading height.
                .weight(1f, fill = false)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.35f))
        ) {
            LazyColumn(Modifier.fillMaxWidth().padding(14.dp)) {
                items(LicenseSections) { section ->
                    SelectionContainer {
                        Text(
                            section,
                            color = Ivory.copy(alpha = 0.88f),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }
    }
}

/** The app icon on a soft, slowly pulsing glow, like a stage light. */
@Composable
private fun BreathingLogo(box: Dp, logo: Dp) {
    val transition = rememberInfiniteTransition(label = "logo")
    val breath by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(box).drawBehind {
            drawCircle(
                Brush.radialGradient(
                    listOf(Ivory.copy(alpha = 0.10f + 0.08f * breath), Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2f
                )
            )
        }
    ) {
        Image(
            painterResource(Res.drawable.about_logo),
            contentDescription = null,
            modifier = Modifier.size(logo).scale(1f + 0.025f * breath)
        )
    }
}

@Composable
private fun VersionChip(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f))
            .border(1.dp, Nickel.copy(alpha = 0.25f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        // The record light of the icon.
        Box(Modifier.size(7.dp).clip(CircleShape).background(RecordRed))
        Text(text, color = Nickel, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Credit(role: String, name: String, nameSize: TextUnit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            role.uppercase(),
            color = Muted,
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 1.6.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(name, color = Ivory, fontSize = nameSize, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LinkRow(icon: DrawableResource, label: String, value: String?, verticalPadding: Dp, external: Boolean = true, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = verticalPadding)
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Nickel, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(label, color = Ivory, style = MaterialTheme.typography.bodyLarge)
            if (value != null) Text(value, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(
            painterResource(if (external) Res.drawable.ic_open_in_new else Res.drawable.ic_chevron_right),
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** Five hairlines of a stave, fading out to both sides, with two small notes resting on it. */
@Composable
private fun StaffDivider(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(22.dp)) {
        val gap = size.height / 5f
        val fade = Brush.horizontalGradient(listOf(Color.Transparent, Nickel.copy(alpha = 0.35f), Color.Transparent))
        for (line in 0 until 5) {
            val y = gap / 2f + line * gap
            drawLine(fade, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val unit = gap * 0.95f
        withTransform({ translate(size.width * 0.47f, gap * 3.5f) }) { drawNote(NoteKind.Quarter, unit, Ivory.copy(alpha = 0.7f)) }
        withTransform({ translate(size.width * 0.53f, gap * 2.5f) }) { drawNote(NoteKind.Quarter, unit, Ivory.copy(alpha = 0.7f)) }
    }
}

private enum class NoteKind { Quarter, Eighth, Sixteenth, Beamed }

private class FloatingNote(
    val x: Float,          // horizontal position as a fraction of the width
    val speed: Float,      // stage heights per second
    val delay: Float,      // seconds until the note first rises into view
    val sway: Float,       // dp to each side
    val swayPeriod: Float, // seconds
    val phase: Float,
    val tilt: Float,       // degrees to each side
    val unit: Float,       // note head width in dp
    val kind: NoteKind,
    val color: Color,
    val alpha: Float
)

private val floatingNotes: List<FloatingNote> = Random(0x6A7).let { random ->
    List(18) { index ->
        FloatingNote(
            x = (index + random.nextFloat()) / 18f,
            speed = 1f / (16f + random.nextFloat() * 12f),
            // Staggered so the notes drift in one after another rather than all at once.
            delay = index * 0.9f * random.nextFloat() + random.nextFloat() * 2f,
            sway = 8f + random.nextFloat() * 22f,
            swayPeriod = 5f + random.nextFloat() * 5f,
            phase = random.nextFloat() * 2f * PI.toFloat(),
            tilt = 6f + random.nextFloat() * 14f,
            unit = 7f + random.nextFloat() * 8f,
            kind = NoteKind.entries[random.nextInt(NoteKind.entries.size)],
            color = if (index % 9 == 4) RecordRed else if (index % 3 == 0) Nickel else Ivory,
            alpha = 0.10f + random.nextFloat() * 0.22f
        )
    }
}

/** Notes that rise slowly from below, swaying and fading in and out; purely decorative. */
@Composable
private fun DriftingNotes(modifier: Modifier) {
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { seconds = (it - start) / 1_000_000_000f }
    }
    Canvas(modifier) {
        val time = seconds
        for (note in floatingNotes) {
            val elapsed = time - note.delay
            if (elapsed < 0f) continue
            val unit = note.unit.dp.toPx()
            val margin = unit * 4f
            val progress = (elapsed * note.speed) % 1f
            val y = size.height + margin - progress * (size.height + 2f * margin)
            val wave = sin(2f * PI.toFloat() * elapsed / note.swayPeriod + note.phase)
            val x = note.x * size.width + wave * note.sway.dp.toPx()
            val fade = sin(PI.toFloat() * progress).coerceAtLeast(0f).pow(0.8f)
            withTransform({
                translate(x, y)
                rotate(wave * note.tilt, pivot = Offset.Zero)
            }) {
                drawNote(note.kind, unit, note.color.copy(alpha = note.alpha * fade))
            }
        }
    }
}

/** Draws a note with its head centred on the origin and the stem pointing up; [unit] is the head width. */
private fun DrawScope.drawNote(kind: NoteKind, unit: Float, color: Color) {
    fun head(dx: Float, dy: Float) = rotate(-22f, pivot = Offset(dx, dy)) {
        drawOval(color, topLeft = Offset(dx - unit * 0.62f, dy - unit * 0.44f), size = Size(unit * 1.24f, unit * 0.88f))
    }
    fun stem(dx: Float, dy: Float, top: Float) =
        drawRect(color, topLeft = Offset(dx + unit * 0.47f, top), size = Size(unit * 0.13f, dy - top - unit * 0.1f))
    fun flag(dx: Float, top: Float) {
        val sx = dx + unit * 0.6f
        val path = Path().apply {
            moveTo(sx, top)
            cubicTo(sx + unit * 0.1f, top + unit * 0.9f, sx + unit * 1.25f, top + unit * 1.1f, sx + unit * 0.75f, top + unit * 2.4f)
            cubicTo(sx + unit * 0.95f, top + unit * 1.45f, sx + unit * 0.25f, top + unit * 1.25f, sx, top + unit * 0.95f)
            close()
        }
        drawPath(path, color)
    }
    val top = -unit * 3.3f
    head(0f, 0f)
    stem(0f, 0f, top)
    when (kind) {
        NoteKind.Quarter -> Unit
        NoteKind.Eighth -> flag(0f, top)
        NoteKind.Sixteenth -> {
            flag(0f, top)
            flag(0f, top + unit * 0.8f)
        }
        NoteKind.Beamed -> {
            val dx = unit * 2.1f
            val dy = -unit * 0.35f
            head(dx, dy)
            stem(dx, dy, top + dy)
            val left = unit * 0.47f
            val beam = Path().apply {
                moveTo(left, top)
                lineTo(dx + left + unit * 0.13f, top + dy)
                lineTo(dx + left + unit * 0.13f, top + dy + unit * 0.45f)
                lineTo(left, top + unit * 0.45f)
                close()
            }
            drawPath(beam, color)
        }
    }
}
