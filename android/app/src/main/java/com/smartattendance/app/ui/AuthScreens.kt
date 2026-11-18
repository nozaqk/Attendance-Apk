package com.smartattendance.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartattendance.app.data.ApiClient
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

/**
 * Frame shared by every auth screen: brand header, one white form card, generous
 * 20dp gutters and a single vertical rhythm so nothing looks hand-placed.
 */
@Composable
fun AuthShell(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBg)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 36.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        BrandHeader(title, subtitle)
        AppCard(contentPadding = 20.dp, spacing = 14.dp) { content() }
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

    AuthShell("Lecturer portal", "Your account is created by the administrator") {
        AuthField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
        AuthField(password, { password = it }, "Password", isPassword = true)
        err?.let { ErrorBox(it) }
        PrimaryButton("Sign in", busy = busy, onClick = {
            busy = true; err = null
            scope.launch {
                try { val (tok, u) = doLogin(api, email.trim(), password, "LECTURER"); onLoggedIn(tok, u) }
                catch (e: Exception) { err = e.message; busy = false }
            }
        })
        TextAction("Forgot password?", modifier = Modifier.fillMaxWidth(), onClick = onForgot)
        DividerLine()
        SecondaryButton("Back", onClick = onChooseRole)
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

    AuthShell(
        if (mode == "in") "Student portal" else "Create your student account",
        if (mode == "in") "Sign in to verify attendance and follow your classes"
        else "Register once, then sign in with the same email"
    ) {
        if (mode == "in") {
            AuthField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
            AuthField(password, { password = it }, "Password", isPassword = true)
            err?.let { ErrorBox(it) }
            PrimaryButton("Sign in", busy = busy, onClick = {
                busy = true; err = null
                scope.launch {
                    try { val (tok, u) = doLogin(api, email, password, "STUDENT"); onLoggedIn(tok, u) }
                    catch (e: Exception) { err = e.message; busy = false }
                }
            })
            TextAction("Forgot password?", modifier = Modifier.fillMaxWidth(), onClick = onForgot)
            DividerLine()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
            ) {
                Text("New student?", color = Muted, fontSize = 14.sp)
                TextAction("Create an account") { mode = "up"; err = null }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AuthField(firstName, { firstName = it }, "First name", modifier = Modifier.weight(1f))
                AuthField(secondName, { secondName = it }, "Second name", modifier = Modifier.weight(1f))
            }
            AuthField(studentId, { studentId = it }, "Index number")
            AuthField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
            DeptDropdown(depts, departmentId) { departmentId = it }
            if (depts.isEmpty()) {
                NoticeBox("No departments have been set up yet. Ask the administrator.", Tone.GREY)
            }
            StreamDropdown(stream) { stream = it }
            AuthField(
                password, { password = it }, "Password",
                isPassword = true,
                supporting = "At least 8 characters, with a letter and a digit"
            )
            AuthField(password2, { password2 = it }, "Confirm password", isPassword = true)
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
            TextAction("Already registered? Sign in", modifier = Modifier.fillMaxWidth()) { mode = "in"; err = null }
        }
        DividerLine()
        SecondaryButton("Back", onClick = onChooseRole)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeptDropdown(depts: List<Department>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val value = depts.find { it.id == selected }?.name ?: "Select department"
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text("Department", fontSize = 14.sp) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = authFieldColors()
        )
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
        OutlinedTextField(
            value = map[selected] ?: selected,
            onValueChange = {},
            readOnly = true,
            label = { Text("Stream", fontSize = 14.sp) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = authFieldColors()
        )
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

    AuthShell(
        if (step == "email") "Reset your password" else "Enter the code",
        if (step == "email") "We will email you a 6-digit code"
        else "Use the code from the email to choose a new password"
    ) {
        if (done) {
            NoticeBox("Password changed. Taking you to sign in…", Tone.GREEN)
        } else if (step == "email") {
            Text(
                "Enter your account email and we will send you a 6-digit code.",
                color = Muted,
                fontSize = 14.sp
            )
            AuthField(email, { email = it }, "Email", keyboardType = KeyboardType.Email)
            err?.let { ErrorBox(it) }
            PrimaryButton("Send code", busy = busy, onClick = { scope.launch { sendCode() } })
        } else {
            info?.let { NoticeBox(it, Tone.GREEN) }
            AuthField(
                code, { code = it.filter { c -> c.isDigit() }.take(6) }, "6-digit code",
                keyboardType = KeyboardType.Number
            )
            AuthField(
                pw, { pw = it }, "New password",
                isPassword = true,
                supporting = "At least 8 characters, with a letter and a digit"
            )
            AuthField(pw2, { pw2 = it }, "Repeat new password", isPassword = true)
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
            TextAction("No code? Send a new code", modifier = Modifier.fillMaxWidth()) { scope.launch { sendCode() } }
        }
        DividerLine()
        TextAction("Back to sign in", modifier = Modifier.fillMaxWidth(), onClick = onBackToLogin)
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

    AuthShell("Choose your own password", "${user.fullName} · ${user.email}") {
        NoticeBox(
            "You signed in with a temporary password. Set a new one to continue. Nobody else will know it.",
            Tone.YELLOW
        )
        AuthField(cur, { cur = it }, "Temporary password", isPassword = true)
        AuthField(
            pw, { pw = it }, "New password",
            isPassword = true,
            supporting = "At least 8 characters, with a letter and a digit"
        )
        AuthField(pw2, { pw2 = it }, "Repeat new password", isPassword = true)
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
        DividerLine()
        TextAction("Sign out", modifier = Modifier.fillMaxWidth(), onClick = onLogout)
    }
}
