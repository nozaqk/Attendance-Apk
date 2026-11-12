package com.smartattendance.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.data.ApiClient
import com.smartattendance.app.data.ApiException
import com.smartattendance.app.data.Department
import com.smartattendance.app.data.User
import kotlinx.coroutines.launch
import org.json.JSONObject

private val ROLE_LABEL = mapOf(
    "ADMIN" to "administrator", "LECTURER" to "lecturer",
    "STUDENT" to "student", "QA" to "quality assurance"
)

suspend fun doLogin(api: ApiClient, email: String, password: String, expectedRole: String): Pair<String, User> {
    val res = api.post("/api/auth/login", JSONObject().put("email", email).put("password", password))
    val user = User.fromJson(res.optJSONObject("user") ?: JSONObject())
    if (user.role != expectedRole) {
        throw Exception(
            "This is a ${expectedRole.lowercase()} portal. Your account is a " +
                "${ROLE_LABEL[user.role] ?: user.role.lowercase()} account."
        )
    }
    return res.optString("token") to user
}

@Composable
fun AuthShell(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text("📡", fontSize = 36.sp)
            Text("Smart Attendance", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text(title, color = Muted, fontSize = 14.sp)
            if (subtitle != null) Text(subtitle, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        AppCard { content() }
    }
}

@Composable
fun LecturerLoginScreen(
    api: ApiClient,
    onLoggedIn: (String, User) -> Unit,
    onForgot: () -> Unit,
    onChooseRole: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AuthShell("Lecturer portal", "Smart Attendance · Lecturer. Your account is created by the administrator.") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            err?.let { ErrorBox(it) }
            PrimaryButton("Sign in", busy = busy, onClick = {
                busy = true; err = null
                scope.launch {
                    try { val (tok, u) = doLogin(api, email.trim(), password, "LECTURER"); onLoggedIn(tok, u) }
                    catch (e: Exception) { err = e.message; busy = false }
                }
            })
            LinkText("Forgot password?", onForgot)
            LinkText("← Choose role", onChooseRole)
        }
    }
}

@Composable
fun StudentLoginScreen(
    api: ApiClient,
    onLoggedIn: (String, User) -> Unit,
    onForgot: () -> Unit,
    onChooseRole: () -> Unit
) {
    var mode by remember { mutableStateOf("in") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var password2 by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var secondName by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }
    var departmentId by remember { mutableStateOf("") }
    var stream by remember { mutableStateOf("REGULAR") }
    var depts by remember { mutableStateOf<List<Department>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            val res = api.get("/api/public/departments")
            val list = mutableListOf<Department>()
            val arr = res.optJSONArray("items")
            if (arr != null) for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list.add(Department.fromJson(it)) }
            depts = list
        } catch (_: Exception) {}
    }

    AuthShell(if (mode == "in") "Sign in" else "Create your account", "Student portal") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (mode == "in") {
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                err?.let { ErrorBox(it) }
                PrimaryButton("Sign in", busy = busy, onClick = {
                    busy = true; err = null
                    scope.launch {
                        try { val (tok, u) = doLogin(api, email, password, "STUDENT"); onLoggedIn(tok, u) }
                        catch (e: Exception) { err = e.message; busy = false }
                    }
                })
                LinkText("Forgot password?", onForgot)
                LinkText("New student? Create an account") { mode = "up"; err = null }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(firstName, { firstName = it }, label = { Text("First name") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(secondName, { secondName = it }, label = { Text("Second name") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(studentId, { studentId = it }, label = { Text("Index number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                DeptDropdown(depts, departmentId) { departmentId = it }
                if (depts.isEmpty()) NoticeBox("No departments have been set up yet. Ask the administrator.", Tone.GREY)
                StreamDropdown(stream) { stream = it }
                OutlinedTextField(password, { password = it }, label = { Text("Password") }, supportingText = { Text("At least 8 characters, with a letter and a digit") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(password2, { password2 = it }, label = { Text("Confirm password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                err?.let { ErrorBox(it) }
                PrimaryButton("Create account", busy = busy, onClick = {
                    if (password != password2) { err = "The two passwords do not match"; return@PrimaryButton }
                    busy = true; err = null
                    scope.launch {
                        try {
                            val body = JSONObject()
                                .put("firstName", firstName).put("secondName", secondName)
                                .put("studentId", studentId).put("email", email)
                                .put("departmentId", departmentId).put("stream", stream)
                                .put("password", password)
                            val res = api.post("/api/auth/register/student", body)
                            val token = res.optString("token")
                            // Temporarily use the new token to fetch the profile.
                            val me = ApiClient({ token }).get("/api/auth/me")
                            onLoggedIn(token, User.fromJson(me))
                        } catch (e: Exception) { err = e.message; busy = false }
                    }
                })
                LinkText("Already registered? Sign in") { mode = "in"; err = null }
            }
            LinkText("← Choose role", onChooseRole)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeptDropdown(depts: List<Department>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = depts.find { it.id == selected }?.name ?: "Select department…"
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(label, {}, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = {
            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
        })
        ExposedDropdownMenu(expanded, { expanded = false }) {
            depts.forEach {
                DropdownMenuItem(text = { Text(it.name) }, onClick = { onSelect(it.id); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val map = mapOf("REGULAR" to "Regular", "WEEKLY" to "Weekly", "EVENING" to "Evening")
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(map[selected] ?: selected, {}, label = { Text("Stream") }, readOnly = true, modifier = Modifier.fillMaxWidth().menuAnchor(), trailingIcon = {
            ExposedDropdownMenuDefaults.TrailingIcon(expanded)
        })
        ExposedDropdownMenu(expanded, { expanded = false }) {
            map.forEach { (k, v) ->
                DropdownMenuItem(text = { Text(v) }, onClick = { onSelect(k); expanded = false })
            }
        }
    }
}

@Composable
fun ForgotPasswordScreen(api: ApiClient, onBackToLogin: () -> Unit) {
    var step by remember { mutableStateOf("email") }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun sendCode() {
        busy = true; err = null; info = null
        try {
            api.post("/api/auth/forgot-password", JSONObject().put("email", email))
            step = "code"
            info = "If that email has an account, a 6-digit code has been sent. Check your inbox (and spam)."
        } catch (e: Exception) { err = e.message }
        busy = false
    }

    AuthShell(if (step == "email") "Reset your password" else "Enter the code") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (done) {
                NoticeBox("Password changed. Taking you to sign in…", Tone.GREEN)
            } else if (step == "email") {
                Text("Enter your account email and we will send you a 6-digit code.", color = Muted)
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                err?.let { ErrorBox(it) }
                PrimaryButton("Send code", busy = busy, onClick = { scope.launch { sendCode() } })
            } else {
                info?.let { NoticeBox(it, Tone.GREEN) }
                OutlinedTextField(code, { code = it.filter { c -> c.isDigit() }.take(6) }, label = { Text("6-digit code") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(pw, { pw = it }, label = { Text("New password") }, supportingText = { Text("At least 8 characters, with a letter and a digit") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(pw2, { pw2 = it }, label = { Text("Repeat new password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                err?.let { ErrorBox(it) }
                PrimaryButton("Change password", busy = busy, onClick = {
                    if (pw != pw2) { err = "The two passwords do not match"; return@PrimaryButton }
                    busy = true; err = null
                    scope.launch {
                        try {
                            api.post("/api/auth/reset-password", JSONObject().put("email", email).put("code", code).put("newPassword", pw))
                            done = true
                            kotlinx.coroutines.delay(1800)
                            onBackToLogin()
                        } catch (e: Exception) { err = e.message; busy = false }
                    }
                })
                LinkText("No code? Send a new code") { scope.launch { sendCode() } }
            }
            LinkText("Back to sign in", onBackToLogin)
        }
    }
}

@Composable
fun ForcePasswordChangeScreen(
    api: ApiClient,
    user: User,
    onDone: () -> Unit,
    onLogout: () -> Unit
) {
    var cur by remember { mutableStateOf("") }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AuthShell("Choose your own password") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            NoticeBox("Hello ${user.fullName}. You signed in with a temporary password. Set a new one to continue. Nobody else will know it.", Tone.YELLOW)
            OutlinedTextField(cur, { cur = it }, label = { Text("Temporary password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(pw, { pw = it }, label = { Text("New password") }, supportingText = { Text("At least 8 characters, with a letter and a digit") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(pw2, { pw2 = it }, label = { Text("Repeat new password") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            err?.let { ErrorBox(it) }
            PrimaryButton("Save and continue", busy = busy, onClick = {
                if (pw != pw2) { err = "The two passwords do not match"; return@PrimaryButton }
                busy = true; err = null
                scope.launch {
                    try {
                        api.post("/api/auth/change-password", JSONObject().put("currentPassword", cur).put("newPassword", pw))
                        onDone()
                    } catch (e: Exception) { err = e.message; busy = false }
                }
            })
            LinkText("Sign out", onLogout)
        }
    }
}
