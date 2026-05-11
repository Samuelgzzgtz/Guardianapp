package com.example.gab.data.repository

import android.content.Context
import com.example.gab.data.local.SessionDataStore
import com.example.gab.data.model.Usuario
import com.example.gab.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class AuthRepository(private val context: Context) {
    private val client = SupabaseClientProvider.client
    private val session = SessionDataStore(context)

    suspend fun signIn(email: String, password: String): Result<Usuario> = runCatching {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        val supaUser = client.auth.currentUserOrNull()
            ?: error("Login failed")

        // Bloquear si el email no está verificado
        if (supaUser.emailConfirmedAt == null) {
            client.auth.signOut()
            error("Verifica tu correo antes de continuar")
        }

        val usuario = client.postgrest["usuario"].select {
            filter { eq("email", email) }
        }.decodeSingleOrNull<Usuario>()
            ?: error("Perfil de usuario no encontrado. Contacta al administrador.")
        val supaSession  = client.auth.currentSessionOrNull()
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
        usuario
    }

    suspend fun reenviarVerificacion(email: String): Result<Unit> = runCatching {
        val safeEmail = email.trim().replace("\"", "")
        val baseUrl = SupabaseClientProvider.SUPABASE_URL
        val anonKey = SupabaseClientProvider.SUPABASE_KEY
        withContext(Dispatchers.IO) {
            val conn = URL("$baseUrl/auth/v1/resend").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", anonKey)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val body = """{"type":"signup","email":"$safeEmail"}"""
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            if (code !in 200..299) error("No se pudo reenviar el correo ($code)")
        }
    }

    suspend fun signOut() {
        runCatching { client.auth.signOut() }
        session.clearSession()
    }

    suspend fun createUser(
        nombre: String,
        email: String,
        password: String,
        rolId: Int,
        unidadId: Int?
    ): Result<Unit> = runCatching {
        val safeEmail   = email.trim().replace("\"", "")
        val safeNombre  = nombre.replace("\"", "").replace("\\", "")
        val serviceKey  = SupabaseClientProvider.SUPABASE_SERVICE_KEY
        val anonKey     = SupabaseClientProvider.SUPABASE_KEY
        val baseUrl     = SupabaseClientProvider.SUPABASE_URL

        // 0. Verificar que la unidad no esté ocupada
        if (unidadId != null) {
            withContext(Dispatchers.IO) {
                val checkConn = URL("$baseUrl/rest/v1/usuario?fkunidad=eq.$unidadId&estaactivo=eq.true&select=id")
                    .openConnection() as HttpURLConnection
                checkConn.setRequestProperty("apikey", serviceKey)
                checkConn.setRequestProperty("Authorization", "Bearer $serviceKey")
                val body = checkConn.inputStream.bufferedReader().readText()
                checkConn.disconnect()
                if (body.trim() != "[]") error("Esta unidad ya está ocupada")
            }
        }

        // 1. Crear usuario en Supabase Auth (service_role → no afecta sesión actual)
        val authUserId = withContext(Dispatchers.IO) {
            val conn = URL("$baseUrl/auth/v1/admin/users").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", serviceKey)
            conn.setRequestProperty("Authorization", "Bearer $serviceKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.doInput  = true
            val safePass = password.replace("\\", "\\\\").replace("\"", "\\\"")
            val body = """{"email":"$safeEmail","password":"$safePass","email_confirm":false}"""
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                val msg = when {
                    err.contains("already registered") || err.contains("already been registered") ->
                        "El email ya está registrado"
                    err.contains("\"message\"") ->
                        err.substringAfter("\"message\":\"").substringBefore("\"")
                    err.contains("\"msg\"") ->
                        err.substringAfter("\"msg\":\"").substringBefore("\"")
                    else -> "Error al crear cuenta ($code)"
                }
                conn.disconnect()
                error(msg)
            }
            val resp = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            // Extraer id del auth user para rollback si el insert falla
            resp.substringAfter("\"id\":\"").substringBefore("\"").takeIf { it.length == 36 }
        }

        // 2. Insertar en tabla usuario via REST con service_role (bypass RLS)
        try {
            withContext(Dispatchers.IO) {
                val conn = URL("$baseUrl/rest/v1/usuario").openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", serviceKey)
                conn.setRequestProperty("Authorization", "Bearer $serviceKey")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Prefer", "return=minimal")
                conn.doOutput = true
                val unidadPart = if (unidadId != null) "\"fkunidad\":$unidadId," else ""
                val body = """{"nombre":"$safeNombre","email":"$safeEmail","fkrolusuario":$rolId,${unidadPart}"estaactivo":true}"""
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                    conn.disconnect()
                    val msg = when {
                        err.contains("unique_unidad") || err.contains("idx_unique_unidad") ->
                            "Esta unidad ya está ocupada"
                        else -> "Error al registrar perfil: $err"
                    }
                    error(msg)
                }
                conn.disconnect()
            }
            // 3. Enviar email de confirmación al usuario recién creado
            withContext(Dispatchers.IO) {
                runCatching {
                    val conn = URL("$baseUrl/auth/v1/resend").openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("apikey", anonKey)
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.doOutput = true
                    conn.outputStream.use { it.write("""{"type":"signup","email":"$safeEmail"}""".toByteArray()) }
                    conn.responseCode
                    conn.disconnect()
                }
            }
        } catch (e: Exception) {
            // Rollback: eliminar el auth user para no dejar inconsistencia
            authUserId?.let { uid ->
                runCatching {
                    withContext(Dispatchers.IO) {
                        val conn = URL("$baseUrl/auth/v1/admin/users/$uid").openConnection() as HttpURLConnection
                        conn.requestMethod = "DELETE"
                        conn.setRequestProperty("apikey", serviceKey)
                        conn.setRequestProperty("Authorization", "Bearer $serviceKey")
                        conn.responseCode
                        conn.disconnect()
                    }
                }
            }
            throw e
        }
    }

    // Returns new access token, or null if refresh failed (token expired / invalid)
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
            val body       = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            val newAccess  = body.substringAfter("\"access_token\":\"").substringBefore("\"")
            val newRefresh = body.substringAfter("\"refresh_token\":\"").substringBefore("\"")
            val userId = session.userId.firstOrNull()
            val name   = session.userName.firstOrNull() ?: ""
            val role   = session.userRole.firstOrNull() ?: 1
            if (userId != null && newAccess.isNotBlank()) {
                session.saveSession(
                    userId = userId, name = name, role = role, unit = "",
                    token = newAccess, refreshToken = newRefresh
                )
            }
            newAccess.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    suspend fun saveFcmToken(userId: Int, fcmToken: String) = runCatching {
        withContext(Dispatchers.IO) {
            val token = session.authToken.firstOrNull() ?: ""
            val conn = URL("${SupabaseClientProvider.SUPABASE_URL}/rest/v1/usuario?id=eq.$userId")
                .openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("apikey", SupabaseClientProvider.SUPABASE_KEY)
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Prefer", "return=minimal")
            conn.doOutput = true
            conn.outputStream.use { it.write("""{"fcmtoken":"$fcmToken"}""".toByteArray()) }
            conn.responseCode
            conn.disconnect()
        }
    }

    fun currentUserFlow() = session.userId
    fun currentRoleFlow() = session.userRole
    fun authTokenFlow()   = session.authToken
    fun refreshTokenFlow() = session.refreshToken
}
