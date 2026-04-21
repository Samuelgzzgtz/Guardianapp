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
            val bytes = context.contentResolver.openInputStream(fotoUri)?.readBytes()
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
        client.postgrest["amenidad"].select().decodeList()
    }

    suspend fun getReservas(userId: Int): Result<List<Reserva>> = runCatching {
        client.postgrest["reserva"].select {
            filter { eq("fkusuario", userId) }
        }.decodeList()
    }

    suspend fun crearReserva(userId: Int, amenidadId: Int, fecha: String, slot: String): Result<Unit> = runCatching {
        client.postgrest["reserva"].insert(
            Reserva(fkUsuario = userId, fkAmenidad = amenidadId, fechaReservacion = fecha, horarioSlot = slot)
        )
    }

    suspend fun cancelarReserva(reservaId: Int): Result<Unit> = runCatching {
        client.postgrest["reserva"].update({ set("estatus", "cancelada") }) {
            filter { eq("id", reservaId) }
        }
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
}
