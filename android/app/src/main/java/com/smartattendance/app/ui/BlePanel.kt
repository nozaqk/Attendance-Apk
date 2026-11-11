package com.smartattendance.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.data.BleScanner

/**
 * Native equivalent of the web BleVerifyPanel (src/shared/ble.tsx).
 * Shows permission / bluetooth / scanning state, the last node sighting,
 * and the server's verdict. Scanning runs while [active] is true.
 */
@Composable
fun BleVerifyPanel(
    scanner: BleScanner,
    active: Boolean,
    sessionId: String?,
    minGapMs: Long,
    idleLabel: String,
    onVerified: () -> Unit = {},
    onRequestPermissions: () -> Unit
) {
    val status by scanner.status.collectAsState()
    val last by scanner.last.collectAsState()
    val result by scanner.result.collectAsState()
    val error by scanner.error.collectAsState()

    DisposableEffect(active, sessionId, minGapMs) {
        if (active) scanner.start(sessionId, minGapMs)
        onDispose { if (active) scanner.stop() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!status.permissionsGranted) {
            NoticeBox("Bluetooth permission is needed.", Tone.YELLOW)
            SecondaryButton("Grant permission", onClick = onRequestPermissions)
        } else if (!status.bluetoothOn) {
            NoticeBox("Bluetooth is turned off. Turn it on to verify attendance.", Tone.RED)
        }
        status.message?.let { Text(it, color = Muted, fontSize = 14.sp) }
        Text(
            if (active) (if (status.scanning) "Scanning for the classroom node…" else "Starting scan…")
            else idleLabel,
            color = Muted, fontSize = 14.sp
        )
        last?.let {
            Text("Node ${it.nodeId} detected · signal ${it.rssi} dBm", fontSize = 14.sp)
        }
        error?.let { NoticeBox(it, Tone.RED) }
        result?.let {
            NoticeBox(
                if (it.verified) "${it.message} ✓" else it.message,
                if (it.verified) Tone.GREEN else Tone.YELLOW
            )
        }
        LaunchedEffect(result?.verified) {
            if (result?.verified == true) onVerified()
        }
    }
}

@Composable
fun BigStatus(text: String, green: Boolean = true) {
    val bg = if (green) GreenBg else YellowBg
    val fg = if (green) Green else Yellow
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
