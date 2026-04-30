# Auth / Security / FCM Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix token expiry bug (ST-11), fix RLS-dependent vehicle query (B-19), and wire up FCM push notifications end-to-end.

**Architecture:** ST-11 adds refresh token persistence and REST-based session renewal in AuthRepository. B-19 moves client-side vehicle filtering to a server-side query. FCM registers device tokens via FirebaseMessaging, persists them in Supabase, and sends pushes via an Edge Function triggered on notificacion inserts.

**Tech Stack:** Kotlin, Supabase-kt 3.0.0, Firebase Messaging (BOM 33.x), Supabase Edge Functions (Deno/TypeScript)

---

## File Map

| File | Action | Purpose |
|------|--------|---------|
| `data/local/SessionDataStore.kt` | Modify | Add REFRESH_TOKEN key and parameter |
| `data/repository/AuthRepository.kt` | Modify | Save refresh token on login, add refreshSession + saveFcmToken |
| `ui/auth/viewmodel/AuthViewModel.kt` | Modify | Validate token against Supabase on session restore |
| `data/repository/SecurityRepository.kt` | Modify | Server-side plate filter |
| `gradle/libs.versions.toml` | Modify | Add firebase + google-services versions |
| `build.gradle.kts` (project root) | Modify | Add google-services plugin |
| `app/build.gradle.kts` | Modify | Add Firebase dependencies |
| `fcm/FcmService.kt` | Create | FirebaseMessagingService — token refresh + receive |
| `AndroidManifest.xml` | Modify | Register FcmService |
| `supabase/functions/send-push/index.ts` | Create | Edge Function that sends FCM push on notificacion insert |

---

## Task 1 — ST-11: Persist refresh token in SessionDataStore

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/local/SessionDataStore.kt`

- [ ] **Step 1: Add REFRESH_TOKEN key and update saveSession**

Replace the entire file with:

```kotlin
package com.example.gab.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "guardian_session")

object SessionKeys {
    val USER_ID       = intPreferencesKey("user_id")
    val USER_NAME     = stringPreferencesKey("user_name")
    val USER_ROLE     = intPreferencesKey("user_role")
    val USER_UNIT     = stringPreferencesKey("user_unit")
    val AUTH_TOKEN    = stringPreferencesKey("auth_token")
    val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
}

class SessionDataStore(private val context: Context) {

    val userId: Flow<Int?>     = context.sessionDataStore.data.map { it[SessionKeys.USER_ID] }
    val userName: Flow<String?> = context.sessionDataStore.data.map { it[SessionKeys.USER_NAME] }
    val userRole: Flow<Int?>   = context.sessionDataStore.data.map { it[SessionKeys.USER_ROLE] }
    val userUnit: Flow<String?> = context.sessionDataStore.data.map { it[SessionKeys.USER_UNIT] }
    val authToken: Flow<String?> = context.sessionDataStore.data.map { it[SessionKeys.AUTH_TOKEN] }
    val refreshToken: Flow<String?> = context.sessionDataStore.data.map { it[SessionKeys.REFRESH_TOKEN] }

    suspend fun saveSession(
        userId: Int,
        name: String,
        role: Int,
        unit: String,
        token: String,
        refreshToken: String = ""
    ) {
        context.sessionDataStore.edit { prefs ->
            prefs[SessionKeys.USER_ID]       = userId
            prefs[SessionKeys.USER_NAME]     = name
            prefs[SessionKeys.USER_ROLE]     = role
            prefs[SessionKeys.USER_UNIT]     = unit
            prefs[SessionKeys.AUTH_TOKEN]    = token
            prefs[SessionKeys.REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun clearSession() {
        context.sessionDataStore.edit { it.clear() }
    }
}
```

- [ ] **Step 2: Build — verify no compile errors**

```bash
./gradlew compileDebugKotlin
```

---

## Task 2 — ST-11: Save refresh token on login + add refreshSession in AuthRepository

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/repository/AuthRepository.kt`

- [ ] **Step 1: Update signIn to save refresh token and add refreshSession method**

In `signIn()`, replace the `saveSession(...)` call block (lines 38-44) with:

```kotlin
val supaSession = client.auth.currentSessionOrNull()
val accessToken  = supaSession?.accessToken  ?: ""
val refreshToken = supaSession?.refreshToken ?: ""
session.saveSession(
    userId       = usuario.id,
    name         = usuario.nombre,
    role         = usuario.fkRolUsuario ?: 1,
    unit         = "",
    token        = accessToken,
    refreshToken = refreshToken
)
```

- [ ] **Step 2: Add refreshSession() method to AuthRepository**

Add this method after `signOut()`:

```kotlin
// Returns new access token or null if refresh failed
suspend fun refreshSession(refreshToken: String): String? = runCatching {
    withContext(Dispatchers.IO) {
        val conn = URL("${SupabaseClientProvider.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("apikey", SupabaseClientProvider.SUPABASE_KEY)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.doInput  = true
        conn.outputStream.use { it.write("""{"refresh_token":"$refreshToken"}""".toByteArray()) }
        val code = conn.responseCode
        if (code !in 200..299) { conn.disconnect(); return@withContext null }
        val body = conn.inputStream.bufferedReader().readText()
        conn.disconnect()
        // Parse new tokens from JSON response
        val newAccess  = body.substringAfter("\"access_token\":\"").substringBefore("\"")
        val newRefresh = body.substringAfter("\"refresh_token\":\"").substringBefore("\"")
        val userId   = session.userId.firstOrNull()
        val name     = session.userName.firstOrNull() ?: ""
        val role     = session.userRole.firstOrNull() ?: 1
        if (userId != null && newAccess.isNotBlank()) {
            session.saveSession(
                userId = userId, name = name, role = role, unit = "",
                token = newAccess, refreshToken = newRefresh
            )
        }
        newAccess.takeIf { it.isNotBlank() }
    }
}.getOrNull()
```

- [ ] **Step 3: Build — verify no compile errors**

```bash
./gradlew compileDebugKotlin
```

---

## Task 3 — ST-11: Validate session on restore in AuthViewModel

**Files:**
- Modify: `app/src/main/java/com/example/gab/ui/auth/viewmodel/AuthViewModel.kt`

- [ ] **Step 1: Update checkSession() to call refreshSession when token exists**

Replace `checkSession()` body:

```kotlin
fun checkSession() {
    viewModelScope.launch {
        try {
            val userId  = sessionStore.userId.firstOrNull()
            val role    = sessionStore.userRole.firstOrNull()
            val token   = sessionStore.authToken.firstOrNull()
            val refresh = sessionStore.refreshToken.firstOrNull()

            if (userId != null && role != null && !token.isNullOrBlank()) {
                if (!refresh.isNullOrBlank()) {
                    val newToken = repo.refreshSession(refresh)
                    if (newToken != null) {
                        _uiState.value = AuthState.SessionRestored(userId, role)
                    } else {
                        sessionStore.clearSession()
                        _uiState.value = AuthState.Idle
                    }
                } else {
                    // Legacy session without refresh token — force re-login
                    sessionStore.clearSession()
                    _uiState.value = AuthState.Idle
                }
            } else {
                _uiState.value = AuthState.Idle
            }
        } catch (e: Exception) {
            _uiState.value = AuthState.Idle
        }
    }
}
```

Also expose `refreshToken` flow in AuthViewModel (needed by FcmService later):

```kotlin
fun refreshTokenFlow() = sessionStore.refreshToken
```

- [ ] **Step 2: Build**

```bash
./gradlew compileDebugKotlin
```

- [ ] **Step 3: Commit ST-11**

```bash
git add app/src/main/java/com/example/gab/data/local/SessionDataStore.kt
git add app/src/main/java/com/example/gab/data/repository/AuthRepository.kt
git add app/src/main/java/com/example/gab/ui/auth/viewmodel/AuthViewModel.kt
git commit -m "fix(auth): validate + refresh token on session restore (ST-11)"
```

---

## Task 4 — B-19: Server-side vehicle filter in SecurityRepository

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/repository/SecurityRepository.kt`

- [ ] **Step 1: Replace client-side filter with server-side ilike query**

Replace `getVehiculoPorPlaca()` (lines 95-101) with:

```kotlin
suspend fun getVehiculoPorPlaca(placa: String): Result<Vehiculo?> = runCatching {
    val placaNorm = placa.uppercase().replace(" ", "").replace("-", "")
    client.postgrest["vehiculo"].select {
        filter { ilike("placa", placaNorm) }
    }.decodeList<Vehiculo>().firstOrNull()
}
```

- [ ] **Step 2: Build + commit**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/gab/data/repository/SecurityRepository.kt
git commit -m "fix(security): server-side plate filter, remove full-table download (B-19)"
```

**MANUAL — RLS in Supabase Dashboard (required after code deploy):**

1. Go to: Supabase Dashboard → Table Editor → `accesolog` → RLS (top right toggle)
2. Enable RLS if not active
3. Click "New Policy" → "Create a policy from scratch":
   - **Name:** `security_read_all_logs`
   - **Allowed operation:** SELECT
   - **Using expression:**
     ```sql
     (SELECT fkrolusuario FROM usuario WHERE id = (auth.uid())::int) = 2
     ```
4. Save.
5. Repeat steps 2-4 for table `vehiculo`:
   - **Name:** `security_read_all_vehicles`
   - Same expression.
6. Add a second policy on `vehiculo` for residents to read their own:
   - **Name:** `resident_read_own_vehicles`
   - **Using expression:**
     ```sql
     fk_usuario = (auth.uid())::int
     ```

---

## Task 5 — FCM: Firebase project setup (MANUAL)

> This entire task is manual. Do it before Task 6.

- [ ] **Step 1: Create Firebase project**
  1. Go to https://console.firebase.google.com
  2. "Add project" → name it `GuardianApp` → disable Google Analytics if you want → Create
  3. Once created, click "Android" icon to add an Android app

- [ ] **Step 2: Register Android app in Firebase**
  - Package name: `com.guardianbuilding` (matches `applicationId` in `app/build.gradle.kts`)
  - App nickname: `GuardianApp Android`
  - Leave SHA-1 empty for now
  - Click "Register app"

- [ ] **Step 3: Download google-services.json**
  - Download the file from Firebase console
  - Place it at: `app/google-services.json` (same level as `app/build.gradle.kts`)

- [ ] **Step 4: Get Firebase Server Key for Edge Function**
  - In Firebase Console → Project Settings → Cloud Messaging tab
  - Copy the **Server key** (under "Cloud Messaging API (Legacy)") — needed for Task 8

- [ ] **Step 5: Add fcm_token column to Supabase**
  - Go to Supabase Dashboard → Table Editor → `usuario`
  - Add column: name `fcm_token`, type `text`, nullable ✓

---

## Task 6 — FCM: Gradle dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts` (project root)
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add versions to libs.versions.toml**

In `[versions]` section add:
```toml
firebaseBom = "33.1.0"
googleServices = "4.4.2"
```

In `[plugins]` section add:
```toml
google-services = { id = "com.google.gms.google-services", version.ref = "googleServices" }
```

- [ ] **Step 2: Add google-services plugin to project build.gradle.kts**

Replace content with:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.services) apply false
}
```

- [ ] **Step 3: Apply plugin + add Firebase deps in app/build.gradle.kts**

Add to `plugins {}` block:
```kotlin
alias(libs.plugins.google.services)
```

Add to `dependencies {}` block:
```kotlin
implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
implementation("com.google.firebase:firebase-messaging-ktx")
```

- [ ] **Step 4: Build — verify no compile errors**

```bash
./gradlew compileDebugKotlin
```

---

## Task 7 — FCM: FcmService + Manifest registration

**Files:**
- Create: `app/src/main/java/com/example/gab/fcm/FcmService.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Create FcmService**

```kotlin
package com.example.gab.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.gab.MainActivity
import com.example.gab.R
import com.example.gab.data.local.SessionDataStore
import com.example.gab.data.repository.AuthRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class FcmService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val userId = SessionDataStore(applicationContext).userId.firstOrNull()
            if (userId != null) {
                AuthRepository(applicationContext).saveFcmToken(userId, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body  = message.notification?.body  ?: message.data["body"]  ?: ""
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "guardian_alerts"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Alertas GuardianApp", NotificationManager.IMPORTANCE_HIGH)
        )
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        manager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
```

- [ ] **Step 2: Register FcmService in AndroidManifest.xml**

Inside `<application>` block, after the `<activity>` closing tag, add:

```xml
<service
    android:name=".fcm.FcmService"
    android:exported="false">
    <intent-filter>
        <action android:name="com.google.firebase.MESSAGING_EVENT" />
    </intent-filter>
</service>
```

- [ ] **Step 3: Build**

```bash
./gradlew compileDebugKotlin
```

---

## Task 8 — FCM: saveFcmToken in AuthRepository + call after login

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/repository/AuthRepository.kt`
- Modify: `app/src/main/java/com/example/gab/ui/auth/viewmodel/AuthViewModel.kt`

- [ ] **Step 1: Add saveFcmToken to AuthRepository**

Add this method after `refreshSession()`:

```kotlin
suspend fun saveFcmToken(userId: Int, fcmToken: String) = runCatching {
    withContext(Dispatchers.IO) {
        val conn = URL("${SupabaseClientProvider.SUPABASE_URL}/rest/v1/usuario?id=eq.$userId")
            .openConnection() as HttpURLConnection
        conn.requestMethod = "PATCH"
        conn.setRequestProperty("apikey", SupabaseClientProvider.SUPABASE_KEY)
        conn.setRequestProperty("Authorization", "Bearer ${session.authToken.firstOrNull() ?: ""}")
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Prefer", "return=minimal")
        conn.doOutput = true
        conn.outputStream.use { it.write("""{"fcm_token":"$fcmToken"}""".toByteArray()) }
        conn.responseCode
        conn.disconnect()
    }
}
```

- [ ] **Step 2: Call saveFcmToken after login in AuthViewModel**

Add import at the top of AuthViewModel:
```kotlin
import com.google.firebase.messaging.FirebaseMessaging
```

In `login()`, after `_uiState.value = AuthState.Success(user)`, add:

```kotlin
FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
    viewModelScope.launch {
        repo.saveFcmToken(user.id, fcmToken)
    }
}
```

- [ ] **Step 3: Build + commit FCM code**

```bash
./gradlew compileDebugKotlin
git add app/src/main/java/com/example/gab/data/repository/AuthRepository.kt
git add app/src/main/java/com/example/gab/ui/auth/viewmodel/AuthViewModel.kt
git add app/src/main/java/com/example/gab/fcm/FcmService.kt
git add app/src/main/AndroidManifest.xml
git add gradle/libs.versions.toml
git add build.gradle.kts
git add app/build.gradle.kts
git commit -m "feat(fcm): register device tokens and show local push notifications"
```

---

## Task 9 — FCM: Supabase Edge Function

**Files:**
- Create: `supabase/functions/send-push/index.ts`

- [ ] **Step 1: Create Edge Function file**

```typescript
// supabase/functions/send-push/index.ts
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const FCM_SERVER_KEY = Deno.env.get("FCM_SERVER_KEY") ?? "";
const SUPABASE_URL   = Deno.env.get("SUPABASE_URL") ?? "";
const SUPABASE_KEY   = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "";

serve(async (req) => {
  const { record } = await req.json();
  // record is the new notificacion row
  const destinatarioId: number = record.fkusuario;

  const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);
  const { data: user } = await supabase
    .from("usuario")
    .select("fcm_token, nombre")
    .eq("id", destinatarioId)
    .single();

  if (!user?.fcm_token) {
    return new Response("no token", { status: 200 });
  }

  const payload = {
    to: user.fcm_token,
    notification: {
      title: record.titulo ?? "GuardianApp",
      body:  record.mensaje ?? "",
    },
    data: {
      notificacion_id: String(record.id),
    },
  };

  const fcmRes = await fetch("https://fcm.googleapis.com/fcm/send", {
    method: "POST",
    headers: {
      "Authorization": `key=${FCM_SERVER_KEY}`,
      "Content-Type":  "application/json",
    },
    body: JSON.stringify(payload),
  });

  const result = await fcmRes.json();
  return new Response(JSON.stringify(result), { status: 200 });
});
```

- [ ] **Step 2: MANUAL — Deploy Edge Function and configure secrets**

  1. Install Supabase CLI if needed: `npm install -g supabase`
  2. Login: `supabase login`
  3. Link project: `supabase link --project-ref spbrzuxvlljowwjawmkv`
  4. Deploy:
     ```bash
     supabase functions deploy send-push
     ```
  5. Set Firebase Server Key secret in Supabase Dashboard:
     - Dashboard → Settings → Edge Functions → Secrets
     - Add secret: `FCM_SERVER_KEY` = (the server key from Firebase Console Task 5 Step 4)

- [ ] **Step 3: MANUAL — Create Database Webhook to trigger Edge Function**

  In Supabase Dashboard → Database → Webhooks → "Create a new webhook":
  - **Name:** `trigger_push_on_notificacion`
  - **Table:** `notificacion`
  - **Events:** INSERT
  - **Type:** Supabase Edge Functions
  - **Function:** `send-push`
  - Save

- [ ] **Step 4: Final commit**

```bash
git add supabase/functions/send-push/index.ts
git commit -m "feat(fcm): edge function to send push on notificacion insert"
```
