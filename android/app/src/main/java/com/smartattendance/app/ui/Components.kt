package com.smartattendance.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.R

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
fun AppCard(
    title: String? = null,
    actions: @Composable (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    spacing: Dp = 8.dp,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardWhite),
        border = BorderStroke(1.dp, Line)
    ) {
        Column(modifier = Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(spacing)) {
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
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit
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
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit
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
    modifier: Modifier = Modifier.fillMaxWidth(),
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit
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

// ---------------------------------------------------------------------------
// Auth screens (role picker, sign in, password reset) share these pieces so the
// spacing, typography and field styling stay identical on every screen.
// ---------------------------------------------------------------------------

/** White fields with the web palette's border; navy focus ring. */
@Composable
fun authFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Navy,
    unfocusedBorderColor = Line,
    disabledBorderColor = Line,
    focusedContainerColor = CardWhite,
    unfocusedContainerColor = CardWhite,
    disabledContainerColor = CardWhite,
    focusedLabelColor = Navy,
    unfocusedLabelColor = Muted,
    cursorColor = Navy
)

/**
 * The one text field used by every auth form: consistent height, radius, label
 * size, keyboard type and an optional Show/Hide toggle for passwords.
 */
@Composable
fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    isPassword: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    supporting: String? = null,
    enabled: Boolean = true
) {
    var revealed by remember { mutableStateOf(false) }
    // Built as a typed value: a @Composable lambda inside `?.let {}` loses its
    // composable annotation during inference.
    val supportingContent: (@Composable () -> Unit)? = if (supporting != null) {
        { Text(supporting, fontSize = 12.sp, color = Muted) }
    } else {
        null
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 14.sp) },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = TextDark),
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else keyboardType),
        visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (isPassword) {
            {
                TextButton(
                    onClick = { revealed = !revealed },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp)
                ) {
                    Text(
                        if (revealed) "Hide" else "Show",
                        color = Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            null
        },
        supportingText = supportingContent,
        colors = authFieldColors()
    )
}

/** Logo, product name and the current portal/step, identical on every auth screen. */
@Composable
fun BrandHeader(title: String, subtitle: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = painterResource(R.drawable.smartiq_emblem),
            contentDescription = null,
            modifier = Modifier.size(width = 70.dp, height = 62.dp),
            contentScale = ContentScale.Fit
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("SmartIQ", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Muted)
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = Muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/** Compact text-only action (links such as "Forgot password?"). */
@Composable
fun TextAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(36.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        Text(label, color = Blue, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** Hairline separator used to group actions inside a card. */
@Composable
fun DividerLine(modifier: Modifier = Modifier.fillMaxWidth()) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = Line)
}
