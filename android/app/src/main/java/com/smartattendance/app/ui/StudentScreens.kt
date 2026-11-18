package com.smartattendance.app.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.smartattendance.app.data.Attendance
import com.smartattendance.app.data.BleScanner
import com.smartattendance.app.data.Dashboard
import com.smartattendance.app.data.Department
import com.smartattendance.app.data.NotifItem
import com.smartattendance.app.data.NotificationService
import com.smartattendance.app.data.NotifyHelper
import com.smartattendance.app.data.PdfExport
import com.smartattendance.app.data.Profile
import com.smartattendance.app.data.StudentCourse
import com.smartattendance.app.data.StudentSession
import com.smartattendance.app.data.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun StudentShell(
    context: Context,
    api: ApiClient,
    user: User,
    scanner: BleScanner,
    openNotificationsTick: Int = 0,
    onRequestBlePermissions: () -> Unit,
    onRefreshUser: () -> Unit,
    onLogout: () -> Unit
) {
    var tab by remember { mutableStateOf("home") }
    var showNotifs by remember { mutableStateOf(false) }
    val ready = user.semester != null && user.departmentId != null

    // Opened from a notification tap: show the feed.
    LaunchedEffect(openNotificationsTick) {
        if (openNotificationsTick > 0) showNotifs = true
    }

    // Fallback while the app is in the foreground and the background service could not
    // run (notification permission denied, OEM killed it). Both paths share the seen-id
    // set, so an alert is never shown twice.
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences(NotificationService.PREFS, Context.MODE_PRIVATE)
        while (true) {
            if (!NotificationService.isRunning) {
                try {
                    val res = api.get("/api/student/notifications")
                    val seen = prefs.getStringSet(NotificationService.KEY_SEEN, emptySet())
                        ?.toMutableSet() ?: mutableSetOf()
                    val arr = res.optJSONArray("items")
                    var changed = false
                    if (arr != null) for (i in 0 until arr.length()) {
                        val n = arr.optJSONObject(i) ?: continue
                        val id = n.optString("id")
                        if (!n.optBoolean("read", false) && !seen.contains(id)) {
                            NotifyHelper.show(context, id, n.optString("title"), n.optString("body"))
                            seen.add(id)
                            changed = true
                        }
                    }
                    if (changed) {
                        prefs.edit()
                            .putStringSet(
                                NotificationService.KEY_SEEN,
                                seen.toList().takeLast(NotificationService.MAX_SEEN).toSet()
                            )
                            .apply()
                    }
                } catch (_: Exception) {}
            }
            delay(15_000)
        }
    }

    // Background alerts: (re)issue the restricted device token, then make sure the
    // foreground service is polling. Runs on every student session, so a reinstall,
    // reboot or expired token heals as soon as the app is opened.
    LaunchedEffect(Unit) {
        try {
            val tokenRes = api.post("/api/student/notifications/device-token")
            val deviceToken = tokenRes.optString("token")
            if (deviceToken.isNotEmpty()) {
                context.getSharedPreferences(NotificationService.PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(NotificationService.KEY_API_BASE, ApiClient.BASE)
                    .putString(NotificationService.KEY_DEVICE_TOKEN, deviceToken)
                    .apply()
                NotificationService.ensureRunning(context)
            }
        } catch (_: Exception) {}
    }

    if (!ready) {
        // First-time setup: force the Settings form (mirrors web redirect).
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            StudentSettingsScreen(api, onSaved = onRefreshUser, onLogout = onLogout, firstTime = true)
        }
        return
    }

    Scaffold(
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 1.dp, color = Line)
                NavigationBar(containerColor = CardWhite, tonalElevation = 0.dp) {
                    listOf(
                        Triple("home", "🏠", "Home"),
                        Triple("class", "📡", "Class"),
                        Triple("course", "📚", "Course"),
                        Triple("setting", "⚙️", "Setting")
                    ).forEach { (route, icon, label) ->
                        NavigationBarItem(
                            selected = tab == route && !showNotifs,
                            onClick = { tab = route; showNotifs = false },
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
            if (showNotifs) {
                StudentNotificationsScreen(api) { showNotifs = false }
            } else when (tab) {
                "home" -> StudentHomeScreen(api, onOpenNotifs = { showNotifs = true }, onOpenClass = { tab = "class" })
                "class" -> StudentClassScreen(context, api, user, scanner, onRequestBlePermissions)
                "course" -> StudentCoursesScreen(api)
                "setting" -> StudentSettingsScreen(api, onSaved = onRefreshUser, onLogout = onLogout, firstTime = false)
            }
        }
    }
}

@Composable
private fun StudentHomeScreen(api: ApiClient, onOpenNotifs: () -> Unit, onOpenClass: () -> Unit) {
    var period by remember { mutableStateOf("day") }
    var data by remember { mutableStateOf<Dashboard?>(null) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(period) {
        while (true) {
            try {
                data = Dashboard.fromJson(api.get("/api/student/dashboard?period=$period")); err = null
            } catch (e: Exception) { if (data == null) err = e.message }
            loading = false
            delay(20_000)
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardWhite),
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        listOf("day" to "Today", "week" to "This week").forEach { (k, l) ->
            Box(
                modifier = Modifier.weight(1f)
                    .background(if (period == k) Navy else CardWhite)
                    .clickable { period = k }
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(l, color = if (period == k) CardWhite else Muted, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
        }
    }
    if (loading && data == null) { LoadingRow(); return }
    if (err != null && data == null) { ErrorBox(err!!); return }
    val d = data ?: return
    d.liveClass?.let { live ->
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(GreenBg).clickable { onOpenClass() }.padding(12.dp)
        ) {
            Column {
                Text("${live.courseName} is in session", fontWeight = FontWeight.Bold, color = Green)
                Text("${live.classroomName} · tap Class to verify attendance", color = Green, fontSize = 13.sp)
            }
        }
    }
    if (d.unread > 0) LinkText("${d.unread} unread notification${if (d.unread > 1) "s" else ""}", onOpenNotifs)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(
            Triple(d.stats["PRESENT"] ?: 0, "Present", Green),
            Triple(d.stats["LATE"] ?: 0, "Late", Yellow),
            Triple(d.stats["MANUAL"] ?: 0, "Manual", Yellow),
            Triple(d.stats["ABSENT"] ?: 0, "Absent", Red)
        ).forEach { (n, l, c) ->
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(CardWhite).padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$n", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = c)
                    Text(l, color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
    Text("Overall totals across all your classes.", color = Muted, fontSize = 12.sp)
    AppCard(title = if (period == "day") "Today's classes" else "Classes ${d.from} to ${d.to}") {
        if (d.items.isEmpty()) EmptyBox(if (period == "day") "No classes today yet" else "No classes this week", "Classes appear here once your lecturer starts them.")
        else d.items.forEach { s ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Text(fmtTime(s.startedAt ?: s.openedAt), fontWeight = FontWeight.Bold, color = Navy, fontSize = 13.sp)
                Column(Modifier.weight(1f)) {
                    Text(s.courseName, fontWeight = FontWeight.Bold)
                    Text("${s.courseCode} · ${s.classroomName}${if (period == "week") " · ${s.sessionDate}" else ""}", color = Muted, fontSize = 12.sp)
                    Text("${timeRange(s)}${if (s.status == "ACTIVE") " · in session" else ""}", color = Muted, fontSize = 12.sp)
                }
                AttBadge(s)
            }
        }
    }
}

@Composable
private fun AttBadge(s: StudentSession) {
    val a = s.attendance
    if (a != null) Badge(if (a.method == "LECTURER_MANUAL" && a.status == "PRESENT") "MANUAL" else a.status)
    else if (s.status == "ACTIVE") Badge("PENDING", Tone.YELLOW)
    else Badge("NOT RECORDED", Tone.GREY)
}

fun timeRange(s: StudentSession) = "${fmtTime(s.startedAt ?: s.openedAt)}${s.endedAt?.let { " – ${fmtTime(it)}" } ?: ""}"

@Composable
private fun StudentClassScreen(context: Context, api: ApiClient, user: User, scanner: BleScanner, onRequestBlePermissions: () -> Unit) {
    var items by remember { mutableStateOf<List<StudentSession>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val res = api.get("/api/student/classes/live")
                val list = mutableListOf<StudentSession>()
                val arr = res.optJSONArray("items")
                if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(StudentSession.fromJson(it)) }
                items = list; err = null
            } catch (e: Exception) { if (items.isEmpty()) err = e.message }
            loading = false
            delay(10_000)
        }
    }
    SecondaryButton("Export my activity") { exporting = true }
    if (loading && items.isEmpty()) LoadingRow()
    else if (err != null && items.isEmpty()) ErrorBox(err!!)
    else if (items.isEmpty()) AppCard { EmptyBox("No class in session right now", "When your lecturer starts a class for one of your courses it will show up here and you can verify your attendance.") }
    else items.forEach { s -> LiveCard(api, s, scanner, onRequestBlePermissions) }
    if (exporting) {
        ExportDialog(context, api, user, onClose = { exporting = false })
    }
}

@Composable
private fun LiveCard(api: ApiClient, s: StudentSession, scanner: BleScanner, onRequestBlePermissions: () -> Unit) {
    var resAtt by remember { mutableStateOf<Attendance?>(null) }
    var verified by remember { mutableStateOf(false) }
    val att = resAtt ?: s.attendance
    AppCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${s.courseName} is in session", fontWeight = FontWeight.Bold)
            Badge("LIVE", Tone.GREEN)
        }
        Text("${s.courseCode} · ${s.classroomName}${s.classroomBuilding?.let { ", $it" } ?: ""}", color = Muted, fontSize = 13.sp)
        Text("Started ${fmtTime(s.startedAt ?: s.openedAt)}${s.lecturerName?.let { " · $it" } ?: ""}", color = Muted, fontSize = 13.sp)
        BleVerifyPanel(
            scanner = scanner, active = true, sessionId = s.id, minGapMs = 20_000,
            idleLabel = "Waiting", onVerified = { verified = true },
            onRequestPermissions = onRequestBlePermissions
        )
        att?.let {
            if (verified) BigStatus("ATTENDANCE VERIFIED ✓")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Badge(it.status)
                Text(if (it.method == "LECTURER_MANUAL") "Recorded by lecturer" else "Verified by Bluetooth", color = Muted, fontSize = 13.sp)
            }
            Text("Observations: ${it.observationCount} · First detected ${fmtTime(it.firstDetectedAt)} · Last detected ${fmtTime(it.lastDetectedAt)}", color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ExportDialog(context: Context, api: ApiClient, user: User, onClose: () -> Unit) {
    var busy by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Export my activity", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Download your class attendance as a document.", color = Muted, fontSize = 13.sp)
                PrimaryButton("Last 7 days (weekly)", busy = busy == "week", onClick = {
                    busy = "week"; err = null
                    scope.launch {
                        try {
                            PdfExport.export(context, api, PdfExport.Period.WEEK, user.fullName, user.studentId)
                            done = true
                        } catch (e: Exception) { err = e.message }
                        busy = null
                    }
                })
                SecondaryButton("This month (monthly)", busy = busy == "month", onClick = {
                    busy = "month"; err = null
                    scope.launch {
                        try {
                            PdfExport.export(context, api, PdfExport.Period.MONTH, user.fullName, user.studentId)
                            done = true
                        } catch (e: Exception) { err = e.message }
                        busy = null
                    }
                })
                if (done) NoticeBox("Saved to your Downloads folder.", Tone.GREEN)
                err?.let { ErrorBox(it) }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close", color = TextDark) } }
    )
}

@Composable
private fun StudentCoursesScreen(api: ApiClient) {
    var semester by remember { mutableStateOf<Int?>(null) }
    var mine by remember { mutableStateOf<List<StudentCourse>>(emptyList()) }
    var avail by remember { mutableStateOf<List<StudentCourse>>(emptyList()) }
    var showAdd by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf<StudentCourse?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        val m = api.get("/api/student/courses")
        semester = if (m.isNull("semester")) null else m.optInt("semester")
        val ml = mutableListOf<StudentCourse>()
        val marr = m.optJSONArray("items")
        if (marr != null) for (i in 0 until marr.length()) marr.optJSONObject(i)?.let { ml.add(StudentCourse.fromJson(it)) }
        mine = ml
        val a = api.get("/api/student/courses/available")
        val al = mutableListOf<StudentCourse>()
        val aarr = a.optJSONArray("items")
        if (aarr != null) for (i in 0 until aarr.length()) aarr.optJSONObject(i)?.let { al.add(StudentCourse.fromJson(it)) }
        avail = al
    }

    LaunchedEffect(Unit) {
        try { load() } catch (e: Exception) { err = e.message }
    }

    AppCard(title = semester?.let { "Semester $it courses" } ?: "My courses") {
        if (mine.isEmpty()) EmptyBox("No courses yet", "Courses for your department and semester appear here once the administrator sets them up.")
        else mine.forEach { c ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontWeight = FontWeight.Bold)
                    Text("${c.code}${c.lecturer?.let { " · $it" } ?: " · No lecturer assigned"}", color = Muted, fontSize = 12.sp)
                }
                if (c.isExtra) {
                    Column(horizontalAlignment = Alignment.End) {
                        Badge("Added", Tone.BLUE)
                        LinkText("Remove") { remove = c }
                    }
                }
            }
        }
    }
    AppCard(title = "Add courses from other semesters") {
        if (!showAdd) {
            Text("Taking a course from another semester (for example a carry-over)? Add it here so you can be marked for its classes.", color = Muted, fontSize = 13.sp)
            SecondaryButton("Browse") { showAdd = true }
        } else {
            SecondaryButton("Hide") { showAdd = false }
            err?.let { NoticeBox(it, Tone.RED) }
            val groups = avail.groupBy { it.semester ?: 0 }.toSortedMap()
            if (groups.isEmpty()) EmptyBox("Nothing to add", "There are no other courses in your department.")
            else groups.forEach { (sem, list) ->
                Text("SEMESTER ${if (sem == 0) "?" else sem}", color = Muted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                list.forEach { c ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name, fontWeight = FontWeight.Bold)
                            Text("${c.code}${c.lecturer?.let { " · $it" } ?: ""}", color = Muted, fontSize = 12.sp)
                        }
                        SecondaryButton("Add", busy = busyId == c.id, modifier = Modifier.width(96.dp), onClick = {
                            busyId = c.id; err = null
                            scope.launch {
                                try {
                                    api.post("/api/student/courses/extra", JSONObject().put("courseId", c.id))
                                    load()
                                } catch (e: Exception) { err = e.message }
                                busyId = null
                            }
                        })
                    }
                }
            }
        }
    }
    remove?.let { c ->
        ConfirmDialog(
            "Remove course",
            "Remove ${c.name} (${c.code}) from your courses? You will no longer be marked for its classes.",
            "Remove", true,
            onConfirm = {
                scope.launch {
                    try {
                        api.delete("/api/student/courses/extra/${c.id}")
                        remove = null
                        load()
                    } catch (_: Exception) {}
                }
            },
            onCancel = { remove = null }
        )
    }
}

@Composable
private fun StudentNotificationsScreen(api: ApiClient, onBack: () -> Unit) {
    var items by remember { mutableStateOf<List<NotifItem>>(emptyList()) }
    var unread by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    suspend fun load() {
        val res = api.get("/api/student/notifications")
        unread = res.optInt("unread")
        val list = mutableListOf<NotifItem>()
        val arr = res.optJSONArray("items")
        if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(NotifItem.fromJson(it)) }
        items = list
    }
    LaunchedEffect(Unit) {
        try { load(); err = null } catch (e: Exception) { err = e.message }
        loading = false
    }
    LinkText("← Back", onBack)
    AppCard(title = "Notifications", actions = {
        if (unread > 0) TextButton(onClick = {
            scope.launch {
                try { api.post("/api/student/notifications/read"); load() } catch (_: Exception) {}
            }
        }) { Text("Mark all read", color = Navy) }
    }) {
        if (loading) LoadingRow()
        else if (err != null && items.isEmpty()) ErrorBox(err!!)
        else if (items.isEmpty()) EmptyBox("No notifications", "You will be told when a class starts or attendance is finalised.")
        else items.forEach { n ->
            Column {
                Text(n.title, fontWeight = FontWeight.Bold)
                Text(n.body, fontSize = 13.sp)
                Text(fmtDateTime(n.createdAt), color = Muted, fontSize = 12.sp)
            }
        }
    }
}

fun fmtDateTime(iso: String?): String {
    if (iso == null) return "—"
    return try {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        val d = f.parse(iso.substring(0, 19)) ?: return "—"
        java.text.SimpleDateFormat("yyyy MMM dd, hh:mm a", java.util.Locale.getDefault()).format(d)
    } catch (_: Exception) { "—" }
}

@Composable
private fun StudentSettingsScreen(api: ApiClient, onSaved: () -> Unit, onLogout: () -> Unit, firstTime: Boolean) {
    var profile by remember { mutableStateOf<Profile?>(null) }
    var depts by remember { mutableStateOf<List<Department>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(firstTime) }
    var confirmOut by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            profile = Profile.fromJson(api.get("/api/student/profile"))
            val res = api.get("/api/student/departments")
            val list = mutableListOf<Department>()
            val arr = res.optJSONArray("items")
            if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(Department.fromJson(it)) }
            depts = list
        } catch (e: Exception) { err = e.message }
        loading = false
    }

    if (loading) { LoadingRow(); return }
    if (err != null && profile == null) { ErrorBox(err!!); return }
    val p = profile ?: return
    val isFirst = p.semester == null || p.department == null

    if (editing || isFirst) {
        ProfileForm(api, p, depts, isFirst, onSaved = {
            profile = Profile.fromJson(api.get("/api/student/profile"))
            editing = false
            onSaved()
        }, onCancel = { editing = false })
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.clip(androidx.compose.foundation.shape.CircleShape).background(Navy).padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("👤", fontSize = 24.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(p.fullName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Student", color = Muted, fontSize = 13.sp)
                    Text("Index number: ${p.studentId}", color = Muted, fontSize = 13.sp)
                }
                LinkText("✎ Edit") { editing = true }
            }
        }
        AppCard(title = "Academic Details") {
            InfoRow("🏢", "Department", p.department?.name ?: "Not chosen")
            InfoRow("📅", "Semester", p.semester?.let { "Semester $it" } ?: "Not chosen")
            InfoRow("🌳", "Stream", streamLabel(p.stream))
        }
        AppCard(title = "Personal Information") {
            InfoRow("👤", "Name", p.fullName)
            InfoRow("🪪", "Index number", p.studentId)
            InfoRow("✉️", "Email", p.email)
        }
        DangerButton("Log out") { confirmOut = true }
        if (confirmOut) {
            ConfirmDialog("Log out", "Sign out of this device?", "Log out", false, onConfirm = onLogout, onCancel = { confirmOut = false })
        }
    }
}

@Composable
private fun InfoRow(icon: String, k: String, v: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, fontSize = 18.sp)
        Text(k, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(0.4f))
        Text(v, fontSize = 13.sp, modifier = Modifier.weight(0.6f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileForm(
    api: ApiClient,
    p: Profile,
    depts: List<Department>,
    firstTime: Boolean,
    onSaved: suspend () -> Unit,
    onCancel: () -> Unit
) {
    var fullName by remember { mutableStateOf(p.fullName) }
    var departmentId by remember { mutableStateOf(p.department?.id ?: "") }
    var semester by remember { mutableStateOf(p.semester?.toString() ?: "") }
    var stream by remember { mutableStateOf(p.stream) }
    var confirmDept by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val dept = depts.find { it.id == departmentId } ?: p.department
    val count = dept?.semesterCount ?: p.semesterCount
    val deptChanged = p.department != null && departmentId != p.department.id
    LaunchedEffect(count) {
        if (semester.isNotEmpty() && (semester.toIntOrNull() ?: 0) > count) semester = ""
    }

    suspend fun save() {
        busy = true; err = null
        try {
            val body = JSONObject().put("fullName", fullName).put("stream", stream)
            if (departmentId.isNotEmpty()) body.put("departmentId", departmentId)
            if (semester.isNotEmpty()) body.put("semester", semester.toInt())
            api.put("/api/student/settings", body)
            onSaved()
        } catch (e: Exception) { err = e.message }
        busy = false
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (firstTime) NoticeBox("Welcome! Choose your department and semester, then save. This unlocks Home, Class and Course.", Tone.YELLOW)
        AppCard(title = "Academic Details") {
            var deptExpanded by remember { mutableStateOf(false) }
            androidx.compose.material3.ExposedDropdownMenuBox(deptExpanded, { deptExpanded = !deptExpanded }) {
                OutlinedTextField(dept?.name ?: "Select department…", {}, label = { Text("Department") }, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = {
                    androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(deptExpanded)
                })
                ExposedDropdownMenu(deptExpanded, { deptExpanded = false }) {
                    depts.forEach {
                        androidx.compose.material3.DropdownMenuItem(text = { Text(it.name) }, onClick = { departmentId = it.id; deptExpanded = false })
                    }
                }
            }
            if (deptChanged) NoticeBox("Changing department removes any extra courses you added.", Tone.YELLOW)
            var semExpanded by remember { mutableStateOf(false) }
            androidx.compose.material3.ExposedDropdownMenuBox(semExpanded, { semExpanded = !semExpanded }) {
                OutlinedTextField(if (semester.isEmpty()) "Select semester…" else "Semester $semester", {}, label = { Text("Semester") }, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = {
                    androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(semExpanded)
                })
                ExposedDropdownMenu(semExpanded, { semExpanded = false }) {
                    for (i in 1..count) {
                        androidx.compose.material3.DropdownMenuItem(text = { Text("Semester $i") }, onClick = { semester = i.toString(); semExpanded = false })
                    }
                }
            }
            var streamExpanded by remember { mutableStateOf(false) }
            androidx.compose.material3.ExposedDropdownMenuBox(streamExpanded, { streamExpanded = !streamExpanded }) {
                OutlinedTextField(streamLabel(stream), {}, label = { Text("Stream") }, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = {
                    androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(streamExpanded)
                })
                ExposedDropdownMenu(streamExpanded, { streamExpanded = false }) {
                    listOf("REGULAR" to "Regular", "WEEKLY" to "Weekly", "EVENING" to "Evening").forEach { (k, v) ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(v) }, onClick = { stream = k; streamExpanded = false })
                    }
                }
            }
        }
        AppCard(title = "Personal Information") {
            OutlinedTextField(fullName, { fullName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(p.studentId, {}, label = { Text("Index number") }, modifier = Modifier.fillMaxWidth(), enabled = false)
            OutlinedTextField(p.email, {}, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), enabled = false)
        }
        err?.let { ErrorBox(it) }
        PrimaryButton("Save details", busy = busy, onClick = {
            if (semester.isEmpty()) { err = "Choose your semester"; return@PrimaryButton }
            if (deptChanged) confirmDept = true else scope.launch { save() }
        })
        if (!firstTime) SecondaryButton("Cancel", onClick = onCancel)
        if (confirmDept) {
            ConfirmDialog(
                "Change department?",
                "Moving to ${dept?.name} will remove all extra courses you added from other semesters. Your semester courses will be updated to the new department.",
                "Change department", true,
                onConfirm = { confirmDept = false; scope.launch { save() } },
                onCancel = { confirmDept = false }
            )
        }
    }
}
