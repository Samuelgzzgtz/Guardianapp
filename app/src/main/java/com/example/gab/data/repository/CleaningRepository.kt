package com.example.gab.data.repository

import com.example.gab.data.model.AreaComun
import com.example.gab.data.model.TareaLimpieza
import com.example.gab.data.model.Unidad
import com.example.gab.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

class CleaningRepository {
    private val client = SupabaseClientProvider.client

    suspend fun getTareas(asignadoId: Int, fecha: String): Result<List<TareaLimpieza>> = runCatching {
        // Fetch tasks for today, include both assigned to this user AND unassigned (resident requests)
        client.postgrest["tarealimpieza"].select {
            filter { eq("fecha", fecha) }
            order("fk_unidad", Order.ASCENDING)
        }.decodeList<TareaLimpieza>().filter { it.fkAsignado == asignadoId || it.fkAsignado == null }
    }

    suspend fun toggleTarea(tareaId: Int, completada: Boolean): Result<Unit> = runCatching {
        client.postgrest["tarealimpieza"].update({ set("estacompletada", completada) }) {
            filter { eq("id", tareaId) }
        }
    }

    suspend fun getAreas(): Result<List<AreaComun>> = runCatching {
        client.postgrest["areacomun"].select().decodeList()
    }

    suspend fun actualizarEstatusArea(areaId: Int, estatus: String): Result<Unit> = runCatching {
        client.postgrest["areacomun"].update({ set("estatus", estatus) }) {
            filter { eq("id", areaId) }
        }
    }

    suspend fun setEstatus(tareaId: Int, estatus: String): Result<Unit> = runCatching {
        client.postgrest["tarealimpieza"].update({ set("estatus", estatus) }) {
            filter { eq("id", tareaId) }
        }
    }

    suspend fun getAllUnidades(): Result<List<Unidad>> = runCatching {
        client.postgrest["unidad"].select().decodeList()
    }
}
