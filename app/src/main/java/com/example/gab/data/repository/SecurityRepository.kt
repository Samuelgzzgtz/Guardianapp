package com.example.gab.data.repository

import com.example.gab.data.model.AccesoLog
import com.example.gab.data.model.Reporte
import com.example.gab.data.model.Usuario
import com.example.gab.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest

class SecurityRepository {
    private val client = SupabaseClientProvider.client

    suspend fun getResidentes(query: String = ""): Result<List<Usuario>> = runCatching {
        val all = client.postgrest["usuario"].select {
            filter { eq("fkrolusuario", 1) }
        }.decodeList<Usuario>()
        if (query.isBlank()) all
        else all.filter { it.nombre.contains(query, ignoreCase = true) }
    }

    suspend fun registrarAcceso(
        residenteId: Int,
        guardiaId: Int,
        direccion: String,
        hora: String
    ): Result<Unit> = runCatching {
        val log = AccesoLog(
            fkResidente  = residenteId,
            fkGuardia    = guardiaId,
            direccion    = direccion,
            horaRegistro = hora
        )
        client.postgrest["accesolog"].insert(log)
    }

    suspend fun getAccesoLog(): Result<List<AccesoLog>> = runCatching {
        client.postgrest["accesolog"].select().decodeList()
    }

    suspend fun reportarIncidente(
        guardiaId: Int,
        titulo: String,
        ubicacion: String,
        esUrgente: Boolean
    ): Result<Unit> = runCatching {
        val reporte = Reporte(
            fkUsuario   = guardiaId,
            titulo      = titulo,
            descripcion = ubicacion,
            categoria   = "Seguridad",
            esUrgente   = esUrgente
        )
        client.postgrest["reporte"].insert(reporte)
    }

    suspend fun getIncidentes(): Result<List<Reporte>> = runCatching {
        client.postgrest["reporte"].select {
            filter { eq("categoria", "Seguridad") }
        }.decodeList()
    }
}
