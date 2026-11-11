package com.smartattendance.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tone { GREEN, YELLOW, RED, GREY, BLUE }

private val STATUS_TONE = mapOf(
    "PRESENT" to Tone.GREEN, "VERIFIED" to Tone.GREEN, "ONLINE" to Tone.GREEN,
    "ACTIVE" to Tone.GREEN, "BLE" to Tone.GREEN, "LATE" to Tone.YELLOW,
    "MANUAL" to Tone.YELLOW, "LECTURER_MANUAL" to Tone.YELLOW,
    "SCHEDULED" to Tone.YELLOW, "PENDING" to Tone.YELLOW, "ABSENT" to Tone.RED,
    "OFFLINE" to Tone.RED, "ERROR" to Tone.RED, "LOCKED" to Tone.RED,
    "CANCELLED" to Tone.RED, "ENDED" to Tone.GREY, "NONE" to Tone.GREY
)

fun toneFor(status: String, explicit: Tone? = null): Tone =
    explicit ?: STATUS_TONE[status] ?: Tone.GREY

@Composable
fun Badge(text: String, tone: Tone? = null) {
    val t = toneFor(text, tone)
    val (bg, fg) = when (t) {
        Tone.GREEN -> GreenBg to Green
        Tone.YELLOW -> YellowBg to Yellow
        Tone.RED -> RedBg to Red
        Tone.BLUE -> BlueBg to Blue
        Tone.GREY -> GreyBg to Muted
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun AppCard(title: String? = null, actions: @Composable (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Line)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null || actions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (title != null) Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (actions != null) actions()
                }
            }
            content()
        }
    }
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    busy: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Navy, contentColor = Color.White)
    ) {
        Text(if (busy) "Please wait…" else label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    busy: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Line)
    ) {
        Text(if (busy) "Please wait…" else label, color = TextDark, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DangerButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    busy: Boolean = false
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Red, contentColor = Color.White)
    ) {
        Text(if (busy) "Please wait…" else label, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LinkText(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = Blue, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
    }
}

@Composable
fun NoticeBox(text: String, tone: Tone = Tone.YELLOW) {
    val (bg, fg) = when (tone) {
        Tone.GREEN -> GreenBg to Green
        Tone.YELLOW -> YellowBg to Yellow
        Tone.RED -> RedBg to Red
        Tone.BLUE -> BlueBg to Blue
        Tone.GREY -> GreyBg to Muted
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(12.dp)
    ) {
        Text(text, color = fg, fontSize = 14.sp)
    }
}

@Composable
fun ErrorBox(message: String, onRetry: (() -> Unit)? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NoticeBox("Something went wrong. $message", Tone.RED)
        if (onRetry != null) LinkText("Try again", onRetry)
    }
}

@Composable
fun EmptyBox(title: String, hint: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (hint != null) Text(hint, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun LoadingRow(label: String = "Loading…") {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Navy, strokeWidth = 2.dp)
        Text(label, color = Muted, fontSize = 14.sp)
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    danger: Boolean,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = if (danger) Red else Navy, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("Cancel", color = TextDark) }
        }
    )
}

@Composable
fun MarkCircle(present: Boolean) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (present) GreenBg else RedBg),
        contentAlignment = Alignment.Center
    ) {
        Text(if (present) "✓" else "✗", color = if (present) Green else Red, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Spacer8() { Spacer(Modifier.height(8.dp)) }
@Composable
fun Spacer16() { Spacer(Modifier.height(16.dp)) }
