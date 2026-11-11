package com.smartattendance.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.data.ApiClient
import com.smartattendance.app.data.BleScanner
import com.smartattendance.app.data.Course
import com.smartattendance.app.data.CurrentSession
import com.smartattendance.app.data.LecturerCourse
import com.smartattendance.app.data.LecturerSession
import com.smartattendance.app.data.ResultData
import com.smartattendance.app.data.Room
import com.smartattendance.app.data.RosterItem
import com.smartattendance.app.data.StudentRow
import com.smartattendance.app.data.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private val STREAM_LABEL = mapOf("REGULAR" to "Regular", "WEEKLY" to "Weekly", "EVENING" to "Evening")
fun streamLabel(s: String?) = if (s == null) "—" else STREAM_LABEL[s] ?: s
fun isPresent(status: String) = status != "ABSENT"

fun fmtTime(iso: String?): String {
    if (iso == null) return "—"
    return try {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        val d = f.parse(iso.substring(0, 19)) ?: return "—"
        java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(d)
    } catch (_: Exception) { "—" }
}

fun fmtStamp(iso: String?): String {
    if (iso == null) return "—"
    return try {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        val d = f.parse(iso.substring(0, 19)) ?: return "—"
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(d)
    } catch (_: Exception) { "—" }
}

@Composable
fun LecturerShell(
    api: ApiClient,
    user: User,
    scanner: BleScanner,
    onRequestBlePermissions: () -> Unit,
    onRefreshUser: () -> Unit,
    onLogout: () -> Unit
) {
    if (user.mustChangePassword) {
        ForcePasswordChangeScreen(api, user, onDone = onRefreshUser, onLogout = onLogout)
        return
    }
    var tab by remember { mutableStateOf("home") }
    var resultId by remember { mutableStateOf<String?>(null) }
    Scaffold(
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 1.dp, color = Line)
                NavigationBar(containerColor = CardWhite, tonalElevation = 0.dp) {
                    listOf(
                        Triple("home", "🏠", "Home"),
                        Triple("students", "👥", "Students"),
                        Triple("courses", "📚", "Courses"),
                        Triple("class", "📡", "Class"),
                        Triple("setting", "⚙️", "Setting")
                    ).forEach { (route, icon, label) ->
                        NavigationBarItem(
                            selected = tab == route,
                            onClick = { tab = route; resultId = null },
                            icon = { Text(icon, fontSize = 20.sp) },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Navy,
                                selectedTextColor = Navy,
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted,
                                indicatorColor = Color.Transparent
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                resultId != null -> LecturerResultScreen(api = api, id = resultId!!, onBack = { resultId = null })
                tab == "home" -> LecturerHomeScreen(api) { resultId = it }
                tab == "students" -> LecturerStudentsScreen(api)
                tab == "courses" -> LecturerCoursesScreen(api)
                tab == "class" -> LecturerClassScreen(api, scanner, onRequestBlePermissions)
                tab == "setting" -> LecturerSettingScreen(user, onLogout)
            }
        }
    }
}

@Composable
private fun LecturerHomeScreen(api: ApiClient, onOpen: (String) -> Unit) {
    var items by remember { mutableStateOf<List<LecturerSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val res = api.get("/api/lecturer/sessions")
                val list = mutableListOf<LecturerSession>()
                val arr = res.optJSONArray("items")
                if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(LecturerSession.fromJson(it)) }
                items = list; err = null
            } catch (e: Exception) { if (items.isEmpty()) err = e.message }
            loading = false
            delay(15_000)
        }
    }
    Text("Class results", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    AppCard {
        if (loading && items.isEmpty()) LoadingRow()
        else if (err != null && items.isEmpty()) ErrorBox(err!!)
        else if (items.isEmpty()) EmptyBox("No classes yet", "Classes you start from the Class tab will appear here with their results.")
        else items.forEach { s ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardWhite)
                    .clickable { onOpen(s.id) }
                    .padding(vertical = 10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.courseCode, fontWeight = FontWeight.Bold)
                        Badge(if (s.status == "ACTIVE") "IN PROGRESS" else if (s.locked) "LOCKED" else "ENDED", if (s.status == "ACTIVE") Tone.GREEN else Tone.GREY)
                    }
                    Text(s.courseName)
                    Text("${s.sessionDate} · ${s.classroomName}", color = Muted, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Badge("Present ${s.presentCount}", Tone.GREEN)
                        Badge("Absent ${s.absentCount}", Tone.RED)
                        if (!s.locked && s.editableUntil != null) Badge("Edit until ${fmtStamp(s.editableUntil)}", Tone.YELLOW)
                    }
                }
            }
        }
    }
}

@Composable
private fun LecturerResultScreen(api: ApiClient, id: String, onBack: () -> Unit) {
    var data by remember { mutableStateOf<ResultData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(id, tick) {
        while (true) {
            try {
                data = ResultData.fromJson(api.get("/api/lecturer/sessions/$id/attendance")); err = null
            } catch (e: Exception) { if (data == null) err = e.message }
            loading = false
            delay(10_000)
        }
    }
    LinkText("← Class results", onBack)
    if (loading && data == null) { LoadingRow(); return }
    if (err != null && data == null) { ErrorBox(err!!); return }
    val d = data ?: return
    val present = d.total - d.absent
    AppCard(title = "${d.courseCode} · ${d.courseName}") {
        Text("${d.sessionDate} · ${d.classroomName}", color = Muted, fontSize = 13.sp)
        Badge(d.status, if (d.status == "ACTIVE") Tone.GREEN else Tone.GREY)
        CountsHead(d.total, present, d.absent)
        Text("Bluetooth verified ${d.verified + d.present + d.late} · Manual ${d.manual}", color = Muted, fontSize = 13.sp)
    }
    if (d.status == "ENDED") EditWindow(d.locked, d.editableUntil)
    if (d.status == "ACTIVE") Text("This class is still in progress. Go to the Class tab to manage it.", color = Muted, fontSize = 13.sp)
    if ((d.status == "ENDED" && !d.locked) || d.status == "ACTIVE") {
        SecondaryButton("+ Add student manually") { adding = true }
    }
    AppCard(title = "Roster") {
        if (d.items.isEmpty()) Text("No students on this class.", color = Muted, fontSize = 13.sp)
        else d.items.forEach { RosterRow(it, live = true) }
    }
    if (adding) {
        ManualAddDialog(api, id, onClose = { adding = false }, onDone = { tick++ })
    }
}

@Composable
fun CountsHead(total: Int, present: Int, absent: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(
            Triple(total, "Students", Muted),
            Triple(present, "Present", Green),
            Triple(absent, "Absent", Red)
        ).forEach { (n, l, c) ->
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(CardWhite).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$n", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c)
                    Text(l, color = Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun RosterRow(r: RosterItem, live: Boolean = false) {
    val yes = isPresent(r.status)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        MarkCircle(yes)
        Column(Modifier.weight(1f)) {
            Text(r.fullName, fontWeight = FontWeight.SemiBold)
            Text("${r.studentId}${r.department?.let { " · $it" } ?: ""}", color = Muted, fontSize = 12.sp)
            if (live && r.firstDetectedAt != null) {
                Text("First ${fmtTime(r.firstDetectedAt)} · Last ${fmtTime(r.lastDetectedAt)} · ${r.observationCount} obs${r.lastRssi?.let { " · $it dBm" } ?: ""}", color = Muted, fontSize = 12.sp)
            }
            r.note?.let { Text("Note: $it", color = Muted, fontSize = 12.sp) }
        }
        Column(horizontalAlignment = Alignment.End) {
            Badge(r.status)
            if (r.method == "BLE") Badge("Bluetooth verified", Tone.GREEN)
            if (r.method == "LECTURER_MANUAL") Badge("LECTURER_MANUAL", Tone.YELLOW)
        }
    }
}

@Composable
fun EditWindow(locked: Boolean, editableUntil: String?) {
    if (locked) NoticeBox("ATTENDANCE LOCKED", Tone.RED)
    else if (editableUntil != null) NoticeBox("Attendance editing closes: ${fmtStamp(editableUntil)}", Tone.YELLOW)
}

@Composable
fun ManualAddDialog(api: ApiClient, sessionId: String, onClose: () -> Unit, onDone: () -> Unit) {
    var studentId by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var ok by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Manual attendance", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("For a student without a phone, with a flat battery or who forgot it. Recorded as LECTURER_MANUAL, separate from Bluetooth-verified attendance.", color = Muted, fontSize = 13.sp)
                OutlinedTextField(studentId, { studentId = it }, label = { Text("Student index number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                err?.let { ErrorBox(it) }
                ok?.let { NoticeBox(it, Tone.GREEN) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    busy = true; err = null; ok = null
                    scope.launch {
                        try {
                            val body = JSONObject().put("sessionId", sessionId).put("studentId", studentId.trim())
                            if (note.trim().isNotEmpty()) body.put("note", note.trim())
                            val res = api.post("/api/lecturer/attendance/manual", body)
                            val rec = res.optJSONObject("record") ?: JSONObject()
                            ok = "${rec.optString("fullName")} (${rec.optString("studentId")}) recorded as MANUAL present."
                            studentId = ""; note = ""
                            onDone()
                        } catch (e: Exception) { err = e.message }
                        busy = false
                    }
                }
            ) { Text("Mark present manually", color = Navy, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Close", color = TextDark) } }
    )
}

@Composable
private fun LecturerStudentsScreen(api: ApiClient) {
    var courseId by remember { mutableStateOf("") }
    var q by remember { mutableStateOf("") }
    var courses by remember { mutableStateOf<List<Course>>(emptyList()) }
    var rows by remember { mutableStateOf<List<StudentRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(courseId) {
        loading = true
        try {
            val path = if (courseId.isEmpty()) "/api/lecturer/students" else "/api/lecturer/students?courseId=${java.net.URLEncoder.encode(courseId, "UTF-8")}"
            val res = api.get(path)
            val list = mutableListOf<StudentRow>()
            val arr = res.optJSONArray("items")
            if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(StudentRow.fromJson(it)) }
            rows = list; err = null
        } catch (e: Exception) { err = e.message }
        loading = false
    }
    LaunchedEffect(Unit) {
        try {
            val res = api.get("/api/lecturer/courses")
            val list = mutableListOf<Course>()
            val arr = res.optJSONArray("items")
            if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(Course.fromJson(it)) }
            courses = list
        } catch (_: Exception) {}
    }
    Text("Students", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    OutlinedTextField(q, { q = it }, label = { Text("Search name or index number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
    CourseDropdown(courses, courseId) { courseId = it }
    val needle = q.trim().lowercase()
    val filtered = rows.filter { needle.isEmpty() || it.fullName.lowercase().contains(needle) || it.studentId.lowercase().contains(needle) }
    AppCard(title = "${filtered.size} student${if (filtered.size == 1) "" else "s"}") {
        if (loading && rows.isEmpty()) LoadingRow()
        else if (err != null && rows.isEmpty()) ErrorBox(err!!)
        else if (filtered.isEmpty()) EmptyBox("No students found", if (q.isNotEmpty()) "Try a different search." else "Students appear here once they pick a department and semester matching your courses.")
        else filtered.forEach { s ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(s.fullName, fontWeight = FontWeight.SemiBold)
                    Badge(streamLabel(s.stream), Tone.BLUE)
                }
                Text(s.studentId, color = Muted, fontSize = 12.sp)
                Text("${s.department ?: "No department"}${s.semester?.let { " · Semester $it" } ?: ""}", color = Muted, fontSize = 12.sp)
                Text(s.courses.joinToString(", "), color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CourseDropdown(courses: List<Course>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = courses.find { it.id == selected }?.let { "${it.code} · ${it.name}" } ?: "All my courses"
    androidx.compose.material3.ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(label, {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = {
            androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded)
        })
        ExposedDropdownMenu(expanded, { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(text = { Text("All my courses") }, onClick = { onSelect(""); expanded = false })
            courses.forEach {
                androidx.compose.material3.DropdownMenuItem(text = { Text("${it.code} · ${it.name}") }, onClick = { onSelect(it.id); expanded = false })
            }
        }
    }
}

@Composable
private fun LecturerCoursesScreen(api: ApiClient) {
    var items by remember { mutableStateOf<List<LecturerCourse>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            val res = api.get("/api/lecturer/courses")
            val list = mutableListOf<LecturerCourse>()
            val arr = res.optJSONArray("items")
            if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(LecturerCourse.fromJson(it)) }
            items = list
        } catch (e: Exception) { err = e.message }
        loading = false
    }
    Text("My courses", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    if (loading) { LoadingRow(); return }
    if (err != null) { ErrorBox(err!!); return }
    if (items.isEmpty()) { AppCard { EmptyBox("No courses assigned", "The administrator assigns courses to you.") }; return }
    items.forEach { c ->
        AppCard(title = "${c.code} · ${c.name}") {
            c.description?.let { Text(it, color = Muted, fontSize = 13.sp) }
            Text("Taken by", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            if (c.eligibility.isEmpty()) Text("No department/semester set yet.", color = Muted, fontSize = 13.sp)
            else Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                c.eligibility.forEach { (code, sem) -> Badge("$code · Sem $sem", Tone.BLUE) }
            }
        }
    }
}

@Composable
private fun LecturerSettingScreen(user: User, onLogout: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Text("Setting", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    AppCard(title = "Profile") {
        Text(user.fullName, fontWeight = FontWeight.Bold)
        Text(user.email, color = Muted, fontSize = 13.sp)
        Text("Staff ID: ${user.staffId ?: "—"}", color = Muted, fontSize = 13.sp)
        user.departmentName?.let { Text(it, color = Muted, fontSize = 13.sp) }
    }
    DangerButton("Log out") { confirm = true }
    if (confirm) {
        ConfirmDialog("Log out?", "You will need to sign in again.", "Log out", false, onConfirm = onLogout, onCancel = { confirm = false })
    }
}
