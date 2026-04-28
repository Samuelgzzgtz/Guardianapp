package com.example.gab.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.gab.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GuardianFirebaseService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        applicationContext.getSharedPreferences("fcm", MODE_PRIVATE)
            .edit().putString("token", token).apply()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = applicationContext.getSharedPreferences("guardian_simple_session", MODE_PRIVATE)
                val userId = prefs.getInt("user_id", -1)
                val accessToken = prefs.getString("auth_token", null)
                if (userId != -1 && !accessToken.isNullOrBlank()) {
                    val baseUrl = com.example.gab.data.remote.SupabaseClientProvider.SUPABASE_URL
                    val conn = java.net.URL("$baseUrl/rest/v1/usuario?id=eq.$userId").openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "PATCH"
                    conn.setRequestProperty("apikey", com.example.gab.data.remote.SupabaseClientProvider.SUPABASE_KEY)
                    conn.setRequestProperty("Authorization", "Bearer $accessToken")
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Prefer", "return=minimal")
                    conn.doOutput = true
                    conn.outputStream.use { it.write("""{"fcmtoken":"$token"}""".toByteArray()) }
                    conn.responseCode
                    conn.disconnect()
                }
            } catch (_: Exception) {
                // Best-effort — will sync at next login
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val title = message.notification?.title ?: message.data["title"] ?: "GuardianApp"
        val body  = message.notification?.body  ?: message.data["body"]  ?: ""
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "guardian_notifications"
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "GuardianApp",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
