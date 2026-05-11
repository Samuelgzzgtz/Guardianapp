package com.example.gab.data.repository

import android.content.Context
import android.net.Uri
import com.example.gab.data.model.*
import com.example.gab.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage

class ResidentRepository(private val context: Context) {
    private val client = SupabaseClientProvider.client

    suspend fun getCuota(userId: Int): Result<Cuota?> = runCatching {
        client.postgrest["cuota"].select {
            filter { eq("fkusuario", userId) }
        }.decodeList<Cuota>().firstOrNull()
    }

    suspend fun actualizarEstatusCuota(cuotaId: Int, estatus: String): Result<Unit> = runCatching {
        client.postgrest["cuota"].update({ set("estatus", estatus) }) {
            filter { eq("id", cuotaId) }
        }
    }

    suspend fun getAvisos(): Result<List<Aviso>> = runCatching {
        client.postgrest["aviso"].select().decodeList()
    }

    suspend fun getReportes(userId: Int): Result<List<Reporte>> = runCatching {
        client.postgrest["reporte"].select {
            filter { eq("fkusuario", userId) }
        }.decodeList()
    }

    suspend fun actualizarEstatusReporte(reporteId: Int, estatus: String): Result<Unit> = runCatching {
        client.postgrest["reporte"].update({ set("estatus", estatus) }) {
            filter { eq("id", reporteId) }
        }
    }

    suspend fun submitReporte(
        userId: Int,
        categoria: String,
        titulo: String,
        descripcion: String,
        fotoUri: Uri? = null
    ): Result<Unit> = runCatching {
        var fotoUrl: String? = null
        if (fotoUri != null) {
            val bmp = android.graphics.BitmapFactory.decodeStream(
                context.contentResolver.openInputStream(fotoUri)
            )
            val bytes = if (bmp != null) {
                var quality = 85
                var out: java.io.ByteArrayOutputStream
                do {
                    out = java.io.ByteArrayOutputStream()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, out)
                    quality -= 10
                } while (out.size() > 500_000 && quality > 20)
                out.toByteArray()
            } else {
                context.contentResolver.openInputStream(fotoUri)?.readBytes()
            }
            if (bytes != null) {
                val fileName = "reporte_${System.currentTimeMillis()}.jpg"
                client.storage["fotos-reportes"].upload(fileName, bytes) { upsert = true }
                fotoUrl = client.storage["fotos-reportes"].publicUrl(fileName)
            }
        }
        client.postgrest["reporte"].insert(
            Reporte(fkUsuario = userId, titulo = titulo, descripcion = descripcion, categoria = categoria, fotoUrl = fotoUrl)
        )
    }

    suspend fun getAmenidades(): Result<List<Amenidad>> = runCatching {
        client.postgrest["amenidad"].select().decodeList<Amenidad>().distinctBy { it.nombre }
    }

    suspend fun getReservas(userId: Int): Result<List<Reserva>> = runCatching {
        client.postgrest["reserva"].select {
            filter { eq("fkusuario", userId) }
        }.decodeList()
    }

    suspend fun crearReserva(userId: Int, amenidadId: Int, fecha: String, slot: String): Result<Unit> = runCatching {
        client.postgrest["reserva"].insert(
            Reserva(fkUsuario = userId, fkAmenidad = amenidadId, fecha = fecha, slot = slot)
        )
    }

    suspend fun cancelarReserva(reservaId: Int): Result<Unit> = runCatching {
        client.postgrest["reserva"].update({ set("estatus", "cancelada") }) {
            filter { eq("id", reservaId) }
        }
    }

    suspend fun getSlotsTomados(
        amenidadId: Int, fecha: String, capacidad: Int, permiteConcurrencia: Boolean
    ): Result<List<String>> = runCatching {
        val reservas = client.postgrest["reserva"].select {
            filter {
                eq("fkamenidad", amenidadId)
                eq("fechareservacion", fecha)
                eq("estatus", "activa")
            }
        }.decodeList<Reserva>()
        val limite = if (permiteConcurrencia) capacidad else 1
        reservas.groupBy { it.slot ?: "" }
            .filter { (_, list) -> list.size >= limite }
            .keys.toList()
    }

    suspend fun getConteoPorSlot(amenidadId: Int, fecha: String): Result<Map<String, Int>> = runCatching {
        val reservas = client.postgrest["reserva"].select {
            filter {
                eq("fkamenidad", amenidadId)
                eq("fechareservacion", fecha)
                eq("estatus", "activa")
            }
        }.decodeList<Reserva>()
        reservas.groupBy { it.slot ?: "" }.mapValues { it.value.size }
    }

    suspend fun crearReservaConValidacion(
        userId: Int, amenidadId: Int, fecha: String, slot: String,
        capacidad: Int = 1, permiteConcurrencia: Boolean = false
    ): Result<Unit> = runCatching {
        val tomados = client.postgrest["reserva"].select {
            filter {
                eq("fkamenidad", amenidadId)
                eq("fechareservacion", fecha)
                eq("horarioslot", slot)
                eq("estatus", "activa")
            }
        }.decodeList<Reserva>()
        val limite = if (permiteConcurrencia) capacidad else 1
        if (tomados.size >= limite) error("Este horario ya está lleno. Elige otro.")
        client.postgrest["reserva"].insert(
            Reserva(fkUsuario = userId, fkAmenidad = amenidadId, fecha = fecha, slot = slot)
        )
    }

    suspend fun getHistorialCuotas(userId: Int): Result<List<Cuota>> = runCatching {
        client.postgrest["cuota"].select {
            filter { eq("fkusuario", userId) }
        }.decodeList<Cuota>().sortedByDescending { it.periodo }
    }

    suspend fun getUnidad(unidadId: Int): Result<Unidad?> = runCatching {
        client.postgrest["unidad"].select {
            filter { eq("id", unidadId) }
        }.decodeList<Unidad>().firstOrNull()
    }

    suspend fun getUsuarioUnidad(userId: Int): Result<Unidad?> = runCatching {
        val usuario = client.postgrest["usuario"].select {
            filter { eq("id", userId) }
        }.decodeList<Usuario>().firstOrNull()
        val unidadId = usuario?.fkUnidad ?: return@runCatching null
        client.postgrest["unidad"].select {
            filter { eq("id", unidadId) }
        }.decodeList<Unidad>().firstOrNull()
    }

    suspend fun solicitarLimpieza(userId: Int, fkUnidad: Int, notas: String): Result<Unit> = runCatching {
        val fecha = java.time.LocalDate.now().toString()
        client.postgrest["tarealimpieza"].insert(
            TareaLimpieza(
                fkAsignado = null,
                fkUnidad   = fkUnidad,
                titulo     = "Solicitud de limpieza",
                fecha      = fecha,
                prioridad  = "normal",
                notas      = notas.ifBlank { null },
                estatus    = "pendiente"
            )
        )
    }

    suspend fun getVehiculos(userId: Int): Result<List<Vehiculo>> = runCatching {
        client.postgrest["vehiculo"].select {
            filter { eq("fk_usuario", userId) }
        }.decodeList()
    }

    suspend fun agregarVehiculo(userId: Int, placa: String, descripcion: String, color: String): Result<Unit> = runCatching {
        client.postgrest["vehiculo"].insert(
            Vehiculo(
                fkUsuario   = userId,
                placa       = placa.uppercase().trim(),
                descripcion = descripcion.ifBlank { null },
                color       = color.ifBlank { null }
            )
        )
    }

    suspend fun eliminarVehiculo(vehiculoId: Int): Result<Unit> = runCatching {
        client.postgrest["vehiculo"].delete { filter { eq("id", vehiculoId) } }
    }
}
