package com.example.gab.data.repository

import com.example.gab.data.model.Notificacion
import com.example.gab.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest

class NotificacionRepository {
    private val client = SupabaseClientProvider.client

    suspend fun getNotificaciones(userId: Int): Result<List<Notificacion>> = runCatching {
        client.postgrest["notificacion"].select {
            filter { eq("fkusuario", userId) }
        }.decodeList()
    }

    suspend fun marcarLeida(notifId: Int): Result<Unit> = runCatching {
        client.postgrest["notificacion"].update({ set("estaleida", true) }) {
            filter { eq("id", notifId) }
        }
    }
}
