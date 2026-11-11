package com.smartattendance.app.data

import android.content.Context

/**
 * Per-role token storage so lecturer and student sessions stay separate
 * (mirrors the web apps' separate origins/localStorage).
 */
class AuthStore(context: Context, private val file: String) {
    private val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)

    fun getToken(): String? = prefs.getString(KEY, null)?.takeIf { it.isNotEmpty() }

    fun setToken(token: String?) {
        if (token == null) prefs.edit().remove(KEY).apply()
        else prefs.edit().putString(KEY, token).apply()
    }

    companion object {
        private const val KEY = "token"
        fun lecturer(context: Context) = AuthStore(context, "lecturer_auth")
        fun student(context: Context) = AuthStore(context, "student_auth")
    }
}
