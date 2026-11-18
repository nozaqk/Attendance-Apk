package com.smartattendance.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.smartattendance.app.data.ApiClient
import com.smartattendance.app.data.AuthStore
import com.smartattendance.app.data.BleScanner
import com.smartattendance.app.data.NotificationService
import com.smartattendance.app.data.NotifyHelper
import com.smartattendance.app.data.User
import com.smartattendance.app.ui.AppTheme
import com.smartattendance.app.ui.ForgotPasswordScreen
import com.smartattendance.app.ui.LecturerLoginScreen
import com.smartattendance.app.ui.LecturerShell
import com.smartattendance.app.ui.Role
import com.smartattendance.app.ui.RolePicker
import com.smartattendance.app.ui.StudentLoginScreen
import com.smartattendance.app.ui.StudentShell
import kotlinx.coroutines.launch

/**
 * True native entry point (Jetpack Compose). No WebView, no web code.
 *
 * Flow: role picker -> portal login -> portal shell (tabs).
 * Lecturer and student keep separate tokens/sessions and operate separately.
 * Bluetooth verification, notifications and PDF export are all native.
 */
class MainActivity : ComponentActivity() {

    /**
     * Bumped whenever the activity is opened from a notification, so the student shell
     * can jump to the notifications feed (cold start and taps while it is already open).
     */
    private var notificationTick by mutableStateOf(0)

    private val blePermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* status refresh happens via scanner polling */ }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* best effort */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.smartattendance.app.data.NotifyHelper.ensureChannels(this)

        if (ApiClient.BASE.contains("yourdomain.com")) {
            android.util.Log.e(
                "SmartAttendance",
                "API_BASE_URL still points at the placeholder domain (${ApiClient.BASE}). " +
                    "Rebuild with -PAPI_BASE_URL=https://<your-real-domain>."
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (intent?.getBooleanExtra(NotifyHelper.EXTRA_OPEN_NOTIFICATIONS, false) == true) {
            notificationTick = 1
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1003)
        }

        setContent {
            AppTheme {
                AppRoot(
                    onRequestBlePermissions = {
                        blePermLauncher.launch(BleScanner.requiredPermissions())
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(NotifyHelper.EXTRA_OPEN_NOTIFICATIONS, false)) {
            notificationTick += 1
        }
    }

    @Composable
    private fun AppRoot(onRequestBlePermissions: () -> Unit) {
        var role by remember { mutableStateOf<Role?>(null) }
        when (val r = role) {
            null -> RolePicker { role = it }
            Role.LECTURER -> LecturerFlow(onRequestBlePermissions) { role = null }
            Role.STUDENT -> StudentFlow(onRequestBlePermissions) { role = null }
        }
    }

    @Composable
    private fun LecturerFlow(onRequestBlePermissions: () -> Unit, onChooseRole: () -> Unit) {
        val store = remember { AuthStore.lecturer(this) }
        var user by remember { mutableStateOf<User?>(null) }
        var loading by remember { mutableStateOf(store.getToken() != null) }
        var showForgot by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val api = remember {
            ApiClient({ store.getToken() }, onUnauthorized = { store.setToken(null) })
        }

        suspend fun refresh() {
            val token = store.getToken()
            if (token == null) {
                user = null
                loading = false
                return
            }
            try {
                val me = api.get("/api/auth/me")
                val u = User.fromJson(me)
                if (u.role != "LECTURER") {
                    store.setToken(null)
                    user = null
                } else {
                    user = u
                }
            } catch (_: Exception) {
                store.setToken(null)
                user = null
            }
            loading = false
        }

        LaunchedEffect(Unit) { refresh() }

        fun logout() {
            scope.launch {
                try { api.post("/api/auth/logout") } catch (_: Exception) {}
                store.setToken(null)
                user = null
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = com.smartattendance.app.ui.Navy)
            }
            return
        }
        val u = user
        if (u == null) {
            if (showForgot) {
                ForgotPasswordScreen(api, onBackToLogin = { showForgot = false })
            } else {
                LecturerLoginScreen(
                    api,
                    onLoggedIn = { token, loggedUser ->
                        store.setToken(token)
                        user = loggedUser
                    },
                    onForgot = { showForgot = true },
                    onChooseRole = onChooseRole
                )
            }
            return
        }
        val scanner = remember { BleScanner(this, api, scope) }
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { scanner.stop() } }
        LecturerShell(
            api = api,
            user = u,
            scanner = scanner,
            onRequestBlePermissions = onRequestBlePermissions,
            onRefreshUser = { scope.launch { refresh() } },
            onLogout = { logout() }
        )
    }

    @Composable
    private fun StudentFlow(onRequestBlePermissions: () -> Unit, onChooseRole: () -> Unit) {
        val store = remember { AuthStore.student(this) }
        var user by remember { mutableStateOf<User?>(null) }
        var loading by remember { mutableStateOf(store.getToken() != null) }
        var showForgot by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val api = remember {
            ApiClient({ store.getToken() }, onUnauthorized = { store.setToken(null) })
        }

        suspend fun refresh() {
            val token = store.getToken()
            if (token == null) {
                user = null
                loading = false
                return
            }
            try {
                val me = api.get("/api/auth/me")
                val u = User.fromJson(me)
                if (u.role != "STUDENT") {
                    store.setToken(null)
                    user = null
                } else {
                    user = u
                }
            } catch (_: Exception) {
                store.setToken(null)
                user = null
            }
            loading = false
        }

        LaunchedEffect(Unit) { refresh() }

        fun logout() {
            scope.launch {
                try { api.post("/api/auth/logout") } catch (_: Exception) {}
                store.setToken(null)
                user = null
                // Also stops background polling and revokes the restricted feed token.
                NotificationService.stopAndForget(this@MainActivity)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = com.smartattendance.app.ui.Navy)
            }
            return
        }
        val u = user
        if (u == null) {
            if (showForgot) {
                ForgotPasswordScreen(api, onBackToLogin = { showForgot = false })
            } else {
                StudentLoginScreen(
                    api,
                    onLoggedIn = { token, loggedUser ->
                        store.setToken(token)
                        user = loggedUser
                    },
                    onForgot = { showForgot = true },
                    onChooseRole = onChooseRole
                )
            }
            return
        }
        val scanner = remember { BleScanner(this, api, scope) }
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { scanner.stop() } }
        StudentShell(
            context = this,
            api = api,
            user = u,
            scanner = scanner,
            openNotificationsTick = notificationTick,
            onRequestBlePermissions = onRequestBlePermissions,
            onRefreshUser = { scope.launch { refresh() } },
            onLogout = { logout() }
        )
    }
}
