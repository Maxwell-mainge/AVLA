package com.avla.app.utils

import com.avla.app.R
import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Sends push notifications directly from the app using a Firebase service
 * account key (res/raw/fcm_service_account.json) — no Cloud Functions, no
 * Blaze plan required.
 *
 * IMPORTANT: embedding a service account key inside the app is NOT something
 * you'd do for a real, distributed app — anyone who decompiled the APK could
 * pull this key out and send arbitrary notifications (or worse) through your
 * Firebase project. This is only acceptable because this is a school project
 * that never gets distributed beyond your own devices. Don't push this key
 * to a public GitHub repo.
 */
object FcmService {

    private const val PROJECT_ID = "com-avla-app"
    private const val FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"
    private const val SEND_URL = "https://fcm.googleapis.com/v1/projects/$PROJECT_ID/messages:send"

    private val client = OkHttpClient()

    private suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        val context = FirebaseApp.getInstance().applicationContext
        context.resources.openRawResource(R.raw.fcm_service_account).use { stream ->
            val credentials = GoogleCredentials.fromStream(stream).createScoped(listOf(FCM_SCOPE))
            credentials.refreshIfExpired()
            credentials.accessToken.tokenValue
        }
    }

    /**
     * Sends a data-only push to a single device token. Data-only (no
     * top-level "notification" key) so onMessageReceived always fires in
     * AvlaFirebaseMessagingService and we build the notification ourselves
     * consistently, whether the app is foregrounded or backgrounded.
     *
     * Best-effort: failures are swallowed so a notification problem never
     * crashes or blocks the admin action that triggered it.
     */
    suspend fun sendNotification(targetToken: String, title: String, body: String) {
        if (targetToken.isBlank()) return

        withContext(Dispatchers.IO) {
            try {
                val accessToken = getAccessToken()

                val payload = JSONObject().apply {
                    put("message", JSONObject().apply {
                        put("token", targetToken)
                        put("data", JSONObject().apply {
                            put("title", title)
                            put("body", body)
                        })
                    })
                }

                val requestBody = payload.toString().toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(SEND_URL)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}