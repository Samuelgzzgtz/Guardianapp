package com.example.gab.data.repository

import com.example.gab.data.model.*
import com.example.gab.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order

class AdminRepository {
    private val client = SupabaseClientProvider.client

    suspend fun getEstadisticas(): Result<DashboardStats> = runCatching {
        val usuarios = client.postgrest["usuario"].select().decodeList<Usuario>()
        val reportes = client.postgrest["reporte"].select().decodeList<Reporte>()
        val reservas = client.postgrest["reserva"].select().decodeList<Reserva>()
        val cuotas   = client.postgrest["cuota"].select().decodeList<Cuota>()
        DashboardStats(
            totalUsuarios     = usuarios.size,
            reportesAbiertos  = reportes.count { it.estatus == "Pendiente" || it.estatus == "En proceso" },
            reservasHoy       = reservas.size,
            cuotasPendientes  = cuotas.count { it.estatus == "pendiente" }
        )
    }

    suspend fun getUsuarios(rolFiltro: Int? = null): Result<List<Usuario>> = runCatching {
        client.postgrest["usuario"].select {
            filter {
                eq("estaactivo", true)
                if (rolFiltro != null) eq("fkrolusuario", rolFiltro)
            }
        }.decodeList()
    }

    suspend fun actualizarRolUsuario(userId: Int, rolId: Int): Result<Unit> = runCatching {
        client.postgrest["usuario"].update({ set("fkrolusuario", rolId) }) {
            filter { eq("id", userId) }
        }
    }

    suspend fun getReportes(estatusFiltro: String? = null): Result<List<Reporte>> = runCatching {
        if (estatusFiltro != null) {
            client.postgrest["reporte"].select {
                filter { eq("estatus", estatusFiltro) }
            }.decodeList()
        } else {
            client.postgrest["reporte"].select().decodeList()
        }
    }

    suspend fun actualizarEstatusReporte(reporteId: Int, estatus: String): Result<Unit> = runCatching {
        client.postgrest["reporte"].update({ set("estatus", estatus) }) {
            filter { eq("id", reporteId) }
        }
    }

    suspend fun getReservas(): Result<List<Reserva>> = runCatching {
        client.postgrest["reserva"].select().decodeList()
    }

    suspend fun getUnidades(): Result<List<Unidad>> = runCatching {
        client.postgrest["unidad"].select {
            filter { eq("estaactivo", true) }
        }.decodeList()
    }

    suspend fun getUnidadesConEstatus(): Result<List<UnidadConEstatus>> = runCatching {
        val unidades = client.postgrest["unidad"].select {
            filter { eq("estaactivo", true) }
        }.decodeList<Unidad>()
        val ocupadas = client.postgrest["usuario"].select {
            filter { eq("estaactivo", true) }
        }.decodeList<Usuario>().mapNotNull { it.fkUnidad }.toSet()
        unidades.map { u -> UnidadConEstatus(u, u.id in ocupadas) }
    }

    suspend fun crearUnidad(numero: String, torre: String?, piso: Int, tipo: String): Result<Unit> = runCatching {
        client.postgrest["unidad"].insert(
            Unidad(numero = numero, torre = torre, piso = piso, tipo = tipo)
        )
    }

    suspend fun darDeBajaUnidad(unidadId: Int): Result<Unit> = runCatching {
        client.postgrest["unidad"].update({ set("estaactivo", false) }) {
            filter { eq("id", unidadId) }
        }
    }

    suspend fun asignarResidente(userId: Int, unidadId: Int?): Result<Unit> = runCatching {
        client.postgrest["usuario"].update({ set("fkunidad", unidadId) }) {
            filter { eq("id", userId) }
        }
    }

    suspend fun getMorosos(): Result<List<MorosoRow>> = runCatching {
        client.postgrest["morosos"].select().decodeList()
    }

    suspend fun getCuotasPorUsuario(userId: Int): Result<List<Cuota>> = runCatching {
        client.postgrest["cuota"].select {
            filter { eq("fkusuario", userId) }
            order("periodo", Order.DESCENDING)
        }.decodeList()
    }

    suspend fun deleteUsuario(userId: Int): Result<Unit> = runCatching {
        client.postgrest["usuario"].update({ set("estaactivo", false) }) {
            filter { eq("id", userId) }
        }
    }

    suspend fun getTareasLimpieza(userId: Int): Result<List<TareaLimpieza>> = runCatching {
        client.postgrest["tarealimpieza"].select {
            filter { eq("fkasignado", userId) }
            order("id", Order.ASCENDING)
        }.decodeList()
    }

    suspend fun crearTareaLimpieza(
        asignadoId: Int, titulo: String, area: String?,
        prioridad: String, fecha: String, notas: String?
    ): Result<Unit> = runCatching {
        client.postgrest["tarealimpieza"].insert(
            TareaLimpieza(
                fkAsignado = asignadoId,
                titulo     = titulo,
                area       = area?.takeIf { it.isNotBlank() },
                prioridad  = prioridad,
                fecha      = fecha,
                notas      = notas?.takeIf { it.isNotBlank() },
                estatus    = "pendiente"
            )
        )
    }

    suspend fun tieneCuotasPendientes(unidadId: Int): Result<Boolean> = runCatching {
        val residentes = client.postgrest["usuario"].select {
            filter { eq("fkunidad", unidadId) }
        }.decodeList<Usuario>()
        if (residentes.isEmpty()) return@runCatching false
        val ids = residentes.map { it.id }
        val cuotas = client.postgrest["cuota"].select {
            filter {
                isIn("fkusuario", ids)
                neq("estatus", "pagado")
            }
        }.decodeList<Cuota>()
        cuotas.isNotEmpty()
    }
}
