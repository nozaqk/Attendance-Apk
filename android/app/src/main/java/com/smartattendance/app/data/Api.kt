package com.smartattendance.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Native API client for the Smart Attendance backend.
 * Mirrors the web client's behaviour (src/shared/api.ts):
 * - JSON requests with `Authorization: Bearer <token>` when a token exists
 * - Errors surfaced as [ApiException] with the server's code/message
 * - 401 (except invalid credentials) clears the session via [onUnauthorized]
 */
class ApiException(
    val status: Int,
    val code: String,
    message: String
) : Exception(message)

class ApiClient(
    private val tokenProvider: () -> String?,
    private val onUnauthorized: () -> Unit = {}
) {
    suspend fun get(path: String): JSONObject = request("GET", path, null)
    suspend fun post(path: String, body: JSONObject = JSONObject()): JSONObject =
        request("POST", path, body)
    suspend fun put(path: String, body: JSONObject): JSONObject = request("PUT", path, body)
    suspend fun delete(path: String): JSONObject = request("DELETE", path, null)

    private suspend fun request(method: String, path: String, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val token = tokenProvider()
            val connection = URL(BASE + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 20_000
                connection.readTimeout = 20_000
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                }
                if (token != null) {
                    connection.setRequestProperty("Authorization", "Bearer $token")
                }
                if (body != null) {
                    connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
                }
                val status = connection.responseCode
                val raw = if (status in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
                if (status == 204) return@withContext JSONObject()
                val data = try {
                    if (raw.isBlank()) JSONObject() else JSONObject(raw)
                } catch (_: Exception) {
                    JSONObject()
                }
                if (status !in 200..299) {
                    val err = data.optJSONObject("error")
                    val code = err?.optString("code")?.takeIf { it.isNotEmpty() } ?: "ERROR"
                    val message = err?.optString("message")?.takeIf { it.isNotEmpty() }
                        ?: "Request failed ($status)"
                    if (status == 401 && token != null && code != "INVALID_CREDENTIALS") {
                        onUnauthorized()
                    }
                    throw ApiException(status, code, message)
                }
                data
            } catch (e: ApiException) {
                throw e
            } catch (_: Exception) {
                throw ApiException(
                    0, "NETWORK",
                    "Cannot reach the server. Check your internet connection and try again."
                )
            } finally {
                connection.disconnect()
            }
        }

    companion object {
        const val BASE = "https://attendance-system-33pl.onrender.com"
    }
}
