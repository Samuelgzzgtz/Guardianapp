package com.example.gab.data.repository

import com.example.gab.data.model.Notificacion
import com.example.gab.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

    fun suscribirRealtime(userId: Int): Flow<Notificacion> {
        val channel = client.realtime.channel("notif-$userId")
        val flow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "notificacion"
            filter("fkusuario", FilterOperator.EQ, userId)
        }
        return flow.map { it.decodeRecord<Notificacion>() }
    }

    suspend fun subscribirCanal(userId: Int) {
        client.realtime.channel("notif-$userId").subscribe()
    }
}
