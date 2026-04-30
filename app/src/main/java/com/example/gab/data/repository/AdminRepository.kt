package com.example.gab.data.repository

import com.example.gab.data.model.*
import com.example.gab.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

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

    suspend fun marcarCuotaPagada(cuotaId: Int): Result<Unit> = runCatching {
        client.postgrest["cuota"].update({ set("estatus", "pagado") }) {
            filter { eq("id", cuotaId) }
        }
    }

    suspend fun eliminarUsuarioCompleto(userId: Int, email: String): Result<Unit> = runCatching {
        val serviceKey = SupabaseClientProvider.SUPABASE_SERVICE_KEY
        val baseUrl    = SupabaseClientProvider.SUPABASE_URL

        withContext(Dispatchers.IO) {
            // 1. Buscar UUID del auth user por email para poder eliminarlo después
            val authUid = runCatching {
                val encoded = java.net.URLEncoder.encode(email.trim(), "UTF-8")
                val conn = URL("$baseUrl/auth/v1/admin/users?email=$encoded").openConnection() as HttpURLConnection
                conn.setRequestProperty("apikey", serviceKey)
                conn.setRequestProperty("Authorization", "Bearer $serviceKey")
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                Regex(""""id"\s*:\s*"([0-9a-f-]{36})"""").find(body)?.groupValues?.get(1)
            }.getOrNull()

            // Helper para DELETE via REST con service_role
            fun restDelete(tableAndFilter: String) = runCatching {
                val conn = URL("$baseUrl/rest/v1/$tableAndFilter").openConnection() as HttpURLConnection
                conn.requestMethod = "DELETE"
                conn.setRequestProperty("apikey", serviceKey)
                conn.setRequestProperty("Authorization", "Bearer $serviceKey")
                conn.responseCode
                conn.disconnect()
            }

            // 2. Eliminar registros hijo (evitar FK violations)
            restDelete("vehiculo?fk_usuario=eq.$userId")
            restDelete("notificacion?fkusuario=eq.$userId")
            restDelete("cuota?fkusuario=eq.$userId")
            restDelete("reporte?fkusuario=eq.$userId")
            restDelete("reserva?fkusuario=eq.$userId")
            restDelete("tarealimpieza?fkasignado=eq.$userId")
            restDelete("accesolog?fkresidente=eq.$userId")
            restDelete("accesolog?fkguardia=eq.$userId")

            // 3. Eliminar de tabla usuario
            restDelete("usuario?id=eq.$userId")

            // 4. Eliminar de auth.users
            authUid?.let { uid ->
                val conn = URL("$baseUrl/auth/v1/admin/users/$uid").openConnection() as HttpURLConnection
                conn.requestMethod = "DELETE"
                conn.setRequestProperty("apikey", serviceKey)
                conn.setRequestProperty("Authorization", "Bearer $serviceKey")
                conn.responseCode
                conn.disconnect()
            }
        }
    }

    suspend fun getTareasLimpieza(userId: Int): Result<List<TareaLimpieza>> = runCatching {
        client.postgrest["tarealimpieza"].select {
            filter { eq("fkasignado", userId) }
            order("fk_unidad", Order.ASCENDING)
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
