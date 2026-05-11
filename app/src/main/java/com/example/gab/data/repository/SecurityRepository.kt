package com.example.gab.data.repository

import com.example.gab.data.model.*
import com.example.gab.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

class SecurityRepository {
    private val client = SupabaseClientProvider.client

    suspend fun getResidentes(query: String = ""): Result<List<Usuario>> = runCatching {
        val all = client.postgrest["usuario"].select {
            filter { eq("fkrolusuario", 1) }
        }.decodeList<Usuario>()
        if (query.isBlank()) all
        else all.filter { it.nombre.contains(query, ignoreCase = true) }
    }

    suspend fun getResidentePorId(userId: Int): Result<Usuario?> = runCatching {
        client.postgrest["usuario"].select {
            filter { eq("id", userId) }
        }.decodeList<Usuario>().firstOrNull()
    }

    suspend fun registrarAcceso(
        residenteId: Int,
        guardiaId: Int,
        direccion: String,
        hora: String
    ): Result<Unit> = runCatching {
        client.postgrest["accesolog"].insert(
            AccesoLog(
                fkResidente  = residenteId,
                fkGuardia    = guardiaId,
                direccion    = direccion,
                horaRegistro = hora
            )
        )
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
        client.postgrest["reporte"].insert(
            Reporte(
                fkUsuario   = guardiaId,
                titulo      = titulo,
                descripcion = ubicacion,
                categoria   = "Seguridad",
                esUrgente   = esUrgente
            )
        )
    }

    suspend fun getIncidentes(): Result<List<Reporte>> = runCatching {
        client.postgrest["reporte"].select {
            filter { eq("categoria", "Seguridad") }
        }.decodeList()
    }

    suspend fun guardarVisita(
        nombreVisitante: String,
        guardiaId: Int,
        tipo: String
    ): Result<Unit> = runCatching {
        client.postgrest["visita"].insert(
            Visita(
                nombreVisitante = nombreVisitante,
                fkGuardia       = guardiaId,
                tipo            = tipo
            )
        )
    }

    suspend fun getVisitas(): Result<List<Visita>> = runCatching {
        client.postgrest["visita"].select {
            order("timestamp", Order.DESCENDING)
            limit(50)
        }.decodeList()
    }

    suspend fun getVehiculosPorResidente(userId: Int): Result<List<Vehiculo>> = runCatching {
        client.postgrest["vehiculo"].select {
            filter { eq("fk_usuario", userId) }
        }.decodeList()
    }

    suspend fun getVehiculoPorPlaca(placa: String): Result<Vehiculo?> = runCatching {
        val placaNorm = placa.uppercase().replace(" ", "").replace("-", "")
        client.postgrest["vehiculo"].select {
            filter { ilike("placa", placaNorm) }
        }.decodeList<Vehiculo>().firstOrNull()
    }

    suspend fun registrarAccesoConId(
        residenteId: Int,
        guardiaId: Int,
        direccion: String,
        hora: String
    ): Result<Int> = runCatching {
        client.postgrest["accesolog"].insert(
            AccesoLog(fkResidente = residenteId, fkGuardia = guardiaId, direccion = direccion, horaRegistro = hora)
        )
        client.postgrest["accesolog"].select {
            filter {
                eq("fkresidente", residenteId)
                eq("fkguardia", guardiaId)
            }
            order("id", Order.DESCENDING)
            limit(1)
        }.decodeList<AccesoLog>().firstOrNull()?.id ?: 0
    }

    suspend fun cancelarAcceso(accesoId: Int): Result<Unit> = runCatching {
        client.postgrest["accesolog"].delete {
            filter { eq("id", accesoId) }
        }
    }
}
