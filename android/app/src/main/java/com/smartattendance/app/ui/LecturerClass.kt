package com.smartattendance.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.data.ApiClient
import com.smartattendance.app.data.BleScanner
import com.smartattendance.app.data.Course
import com.smartattendance.app.data.CurrentSession
import com.smartattendance.app.data.Room
import com.smartattendance.app.data.RosterItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

data class Finished(val session: CurrentSession, val present: Int, val absent: Int, val total: Int)

@Composable
fun LecturerClassScreen(api: ApiClient, scanner: BleScanner, onRequestBlePermissions: () -> Unit) {
    var current by remember { mutableStateOf<CurrentSession?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var finished by remember { mutableStateOf<Finished?>(null) }

    suspend fun loadCurrent() {
        try {
            val res = api.get("/api/lecturer/current")
            current = if (res.isNull("session")) null else CurrentSession.fromJson(res.optJSONObject("session")!!)
            err = null
        } catch (e: Exception) { if (!loaded) err = e.message }
        loaded = true
    }

    LaunchedEffect(Unit) {
        while (true) {
            loadCurrent()
            delay(5_000)
        }
    }

    Text("Class", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    if (!loaded) { LoadingRow(); return }
    if (err != null && current == null) { ErrorBox(err!!); return }
    val f = finished
    if (f != null) {
        FinishedView(f) { finished = null }
        return
    }
    val scope = rememberCoroutineScope()
    val s = current
    if (s == null) {
        ClassSelect(api) { scope.launch { loadCurrent() } }
    } else if (s.status == "SCHEDULED") {
        ClassVerify(api, s, scanner, onRequestBlePermissions, onReload = { scope.launch { loadCurrent() } })
    } else {
        ClassLive(api, s, scanner, onRequestBlePermissions) { fin -> finished = fin }
    }
}

@Composable
private fun ClassSelect(api: ApiClient, onCreated: () -> Unit) {
    var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var rooms by remember { mutableStateOf<List<Room>>(emptyList()) }
    var courseId by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val res = api.get("/api/lecturer/courses")
            val list = mutableListOf<Course>()
            val arr = res.optJSONArray("items")
            if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(Course.fromJson(it)) }
            courses = list
        } catch (_: Exception) {}
    }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val res = api.get("/api/lecturer/classrooms")
                val list = mutableListOf<Room>()
                val arr = res.optJSONArray("items")
                if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(Room.fromJson(it)) }
                rooms = list
            } catch (_: Exception) {}
            delay(10_000)
        }
    }

    AppCard(title = "1. Choose your course") {
        if (courses.isEmpty()) EmptyBox("No courses assigned", "Ask the administrator to assign a course to you.")
        else courses.forEach { c ->
            RoomButton("${c.code} · ${c.name}", selected = courseId == c.id, enabled = true) { courseId = c.id }
        }
    }
    AppCard(title = "2. Choose the classroom") {
        if (rooms.isEmpty()) EmptyBox("No classrooms registered")
        else rooms.forEach { r ->
            val taken = r.occupiedByCourse != null
            val noNode = r.nodeId == null || !r.nodeActive
            RoomButton(
                label = r.name,
                selected = roomId == r.id,
                enabled = !taken && !noNode,
                badge = when {
                    taken -> "TAKEN" to Tone.RED
                    noNode -> "NO NODE" to Tone.RED
                    r.nodeOnline -> "NODE ONLINE" to Tone.GREEN
                    else -> "NODE OFFLINE" to Tone.YELLOW
                },
                sub = listOfNotNull(r.building, r.floor?.let { "Floor $it" }, r.zone).joinToString(" · "),
                extra = when {
                    taken -> "Selected by ${r.occupiedByLecturer ?: "another lecturer"} lecturing ${r.occupiedByCourse}"
                    noNode -> "No Bluetooth node: attendance cannot be verified here."
                    !r.nodeOnline -> "The node has not reported recently. Check it is powered."
                    else -> null
                },
                extraColor = if (taken || noNode) Red else Yellow
            ) { roomId = r.id }
        }
    }
    err?.let { NoticeBox(it, Tone.RED) }
    PrimaryButton("Select course and room", enabled = courseId.isNotEmpty() && roomId.isNotEmpty(), busy = busy, onClick = {
        busy = true; err = null
        scope.launch {
            try {
                api.post("/api/lecturer/classes", JSONObject().put("courseId", courseId).put("classroomId", roomId))
                onCreated()
            } catch (e: Exception) { err = e.message; busy = false }
        }
    })
}

@Composable
private fun RoomButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    badge: Pair<String, Tone>? = null,
    sub: String? = null,
    extra: String? = null,
    extraColor: androidx.compose.ui.graphics.Color = Red,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) androidx.compose.ui.graphics.Color(0xFFF1F5FB) else CardWhite)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontWeight = FontWeight.Bold)
            badge?.let { Badge(it.first, it.second) }
        }
        sub?.takeIf { it.isNotEmpty() }?.let { Text(it, color = Muted, fontSize = 13.sp) }
        extra?.let { Text(it, color = extraColor, fontSize = 13.sp) }
    }
}

@Composable
private fun ClassVerify(api: ApiClient, s: CurrentSession, scanner: BleScanner, onRequestBlePermissions: () -> Unit, onReload: () -> Unit) {
    var verified by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var cancel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ok = verified || s.lecturerVerified

    AppCard(title = "${s.courseCode} · ${s.courseName}") {
        Text("${s.classroomName}${s.nodeId?.let { " · node $it" } ?: ""}", color = Muted, fontSize = 13.sp)
        NoticeBox("This room is reserved for you. Others cannot use it until you press Class over.", Tone.YELLOW)
    }
    AppCard(title = "Verify you are in the room") {
        BleVerifyPanel(
            scanner = scanner, active = true, sessionId = s.id, minGapMs = 5_000,
            idleLabel = "Waiting…", onVerified = { verified = true },
            onRequestPermissions = onRequestBlePermissions
        )
        if (ok) BigStatus("LECTURER VERIFIED ✓")
    }
    err?.let { NoticeBox(it, Tone.RED) }
    PrimaryButton("Start class", enabled = ok, busy = busy, onClick = {
        busy = true; err = null
        scope.launch {
            try {
                api.post("/api/lecturer/sessions/${s.id}/start")
                onReload()
            } catch (e: Exception) { err = e.message; busy = false }
        }
    })
    SecondaryButton("Cancel selection") { cancel = true }
    if (cancel) {
        ConfirmDialog(
            "Cancel selection?",
            "This releases ${s.classroomName} so another lecturer can use it.",
            "Cancel selection", true,
            onConfirm = {
                scope.launch {
                    try {
                        api.post("/api/lecturer/sessions/${s.id}/cancel")
                        cancel = false
                        onReload()
                    } catch (_: Exception) {}
                }
            },
            onCancel = { cancel = false }
        )
    }
}

@Composable
private fun ClassLive(api: ApiClient, s: CurrentSession, scanner: BleScanner, onRequestBlePermissions: () -> Unit, onEnded: (Finished) -> Unit) {
    var items by remember { mutableStateOf<List<RosterItem>>(emptyList()) }
    var end by remember { mutableStateOf(false) }
    var add by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(s.id) {
        while (true) {
            try {
                val res = api.get("/api/lecturer/sessions/${s.id}/attendance")
                val list = mutableListOf<RosterItem>()
                val arr = res.optJSONArray("items")
                if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(RosterItem.fromJson(it)) }
                items = list
            } catch (_: Exception) {}
            delay(5_000)
        }
    }
    val present = items.count { isPresent(it.status) }

    AppCard(title = "${s.courseCode} · ${s.courseName}", actions = { Badge("IN PROGRESS", Tone.GREEN) }) {
        Text(s.classroomName, color = Muted, fontSize = 13.sp)
        CountsHead(items.size, present, items.size - present)
    }
    AppCard(title = "Your presence") {
        BleVerifyPanel(
            scanner = scanner, active = true, sessionId = s.id, minGapMs = 60_000,
            idleLabel = "Waiting…", onRequestPermissions = onRequestBlePermissions
        )
    }
    AppCard(title = "Live attendance") {
        if (items.isEmpty()) EmptyBox("No students on this course yet")
        else items.forEach { RosterRow(it, live = true) }
        Text("Updates every 5 seconds.", color = Muted, fontSize = 12.sp)
    }
    SecondaryButton("+ Add student manually") { add = true }
    NoticeBox("The room stays reserved for you until you press Class over.", Tone.YELLOW)
    DangerButton("Class over") { end = true }
    if (add) {
        ManualAddDialog(api, s.id, onClose = { add = false }, onDone = {})
    }
    if (end) {
        ConfirmDialog(
            "Class over?",
            "Everyone not detected will be marked ABSENT, the room is released, and you will have 6 hours to correct attendance manually. Continue?",
            "Class over", true,
            onConfirm = {
                scope.launch {
                    try {
                        val res = api.post("/api/lecturer/sessions/${s.id}/end")
                        val finSession = CurrentSession.fromJson(res.optJSONObject("session") ?: JSONObject())
                        val finalRes = try {
                            api.get("/api/lecturer/sessions/${s.id}/attendance")
                        } catch (_: Exception) { null }
                        val finalItems = mutableListOf<RosterItem>()
                        val arr = finalRes?.optJSONArray("items")
                        if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { finalItems.add(RosterItem.fromJson(it)) }
                        val p = finalItems.count { isPresent(it.status) }
                        end = false
                        onEnded(Finished(finSession, p, finalItems.size - p, finalItems.size))
                    } catch (_: Exception) {}
                }
            },
            onCancel = { end = false }
        )
    }
}

@Composable
private fun FinishedView(f: Finished, onNew: () -> Unit) {
    Text("Class finished", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    AppCard(title = "${f.session.courseCode} · ${f.session.courseName}") {
        Text("${f.session.classroomName} · room released", color = Muted, fontSize = 13.sp)
        CountsHead(f.total, f.present, f.absent)
    }
    EditWindow(f.session.locked, f.session.editableUntil)
    Text("Open the class from Home to add students manually until ${fmtStamp(f.session.editableUntil)}.", color = Muted, fontSize = 13.sp)
    PrimaryButton("Done", onClick = onNew)
}
