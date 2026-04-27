package com.example.gab.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RolUsuario(
    @SerialName("id") val id: Int,
    @SerialName("nombre") val nombre: String
)

@Serializable
data class Unidad(
    @SerialName("id") val id: Int = 0,
    @SerialName("numero") val numero: String = "",
    @SerialName("torre") val torre: String? = null,
    @SerialName("piso") val piso: Int = 1,
    @SerialName("tipo") val tipo: String = "depto",
    @SerialName("estaactivo") val estaActivo: Boolean = true
) {
    fun displayUbicacion(): String = buildString {
        torre?.let { append("Bloque $it · ") }
        append("Piso $piso · Depto $numero")
    }
}

@Serializable
data class MorosoRow(
    @SerialName("userid") val userId: Int = 0,
    @SerialName("nombre") val nombre: String = "",
    @SerialName("email") val email: String? = null,
    @SerialName("cuotaid") val cuotaId: Int = 0,
    @SerialName("periodo") val periodo: String = "",
    @SerialName("monto") val monto: Double = 0.0,
    @SerialName("fechavencimiento") val fechaVencimiento: String? = null
)

@Serializable
data class Usuario(
    @SerialName("id") val id: Int = 0,
    @SerialName("nombre") val nombre: String = "",
    @SerialName("email") val email: String? = null,
    @SerialName("fkrolusuario") val fkRolUsuario: Int? = null,
    @SerialName("fkunidad") val fkUnidad: Int? = null,
    @SerialName("fcmtoken") val fcmToken: String? = null,
    @SerialName("fotourl") val fotoUrl: String? = null,
    @SerialName("estaactivo") val estaActivo: Boolean = true
)

@Serializable
data class Cuota(
    @SerialName("id") val id: Int = 0,
    @SerialName("fkusuario") val fkUsuario: Int? = null,
    @SerialName("monto") val monto: Double? = null,
    @SerialName("estatus") val estatus: String = "pendiente",
    @SerialName("fechavencimiento") val fechaVencimiento: String? = null,
    @SerialName("periodo") val periodo: String = "mensual"
)

@Serializable
data class Reporte(
    @SerialName("id") val id: Int? = null,
    @SerialName("fkusuario") val fkUsuario: Int? = null,
    @SerialName("titulo") val titulo: String = "",
    @SerialName("descripcion") val descripcion: String? = null,
    @SerialName("categoria") val categoria: String = "General",
    @SerialName("esurgente") val esUrgente: Boolean = false,
    @SerialName("fotourl") val fotoUrl: String? = null,
    @SerialName("estatus") val estatus: String = "Pendiente",
    @SerialName("fechacreacion") val fechaCreacion: String? = null
)

@Serializable
data class Aviso(
    @SerialName("id") val id: Int = 0,
    @SerialName("titulo") val titulo: String = "",
    @SerialName("descripcion") val descripcion: String? = null,
    @SerialName("tono") val tono: String = "primary"
)

@Serializable
data class Amenidad(
    @SerialName("id") val id: Int = 0,
    @SerialName("nombre") val nombre: String = "",
    @SerialName("horario") val horario: String? = null,
    @SerialName("capacidad") val capacidad: Int = 10
)

@Serializable
data class Reserva(
    @SerialName("id") val id: Int? = null,
    @SerialName("fkusuario") val fkUsuario: Int? = null,
    @SerialName("fkamenidad") val fkAmenidad: Int? = null,
    @SerialName("fechareservacion") val fechaReservacion: String? = null,
    @SerialName("horarioslot") val horarioSlot: String? = null,
    @SerialName("estatus") val estatus: String = "activa"
)

@Serializable
data class AccesoLog(
    @SerialName("id") val id: Int? = null,
    @SerialName("fkresidente") val fkResidente: Int? = null,
    @SerialName("fkguardia") val fkGuardia: Int? = null,
    @SerialName("direccion") val direccion: String = "ENTRADA",
    @SerialName("horaregistro") val horaRegistro: String? = null,
    @SerialName("timestamp") val timestamp: String? = null
)

@Serializable
data class TareaLimpieza(
    @SerialName("id") val id: Int? = null,
    @SerialName("fkasignado") val fkAsignado: Int? = null,
    @SerialName("fk_unidad") val fkUnidad: Int? = null,
    @SerialName("titulo") val titulo: String = "",
    @SerialName("area") val area: String? = null,
    @SerialName("horarioslot") val horarioSlot: String? = null,
    @SerialName("prioridad") val prioridad: String = "normal",
    @SerialName("estacompletada") val estaCompletada: Boolean = false,
    @SerialName("fecha") val fecha: String? = null,
    @SerialName("notas") val notas: String? = null,
    @SerialName("estatus") val estatus: String = "pendiente"
)

@Serializable
data class AreaComun(
    @SerialName("id") val id: Int = 0,
    @SerialName("nombre") val nombre: String = "",
    @SerialName("sector") val sector: String? = null,
    @SerialName("estatus") val estatus: String = "pendiente",
    @SerialName("ultimalimpieza") val ultimaLimpieza: String? = null
)

@Serializable
data class Notificacion(
    @SerialName("id") val id: Int? = null,
    @SerialName("fkusuario") val fkUsuario: Int? = null,
    @SerialName("titulo") val titulo: String = "",
    @SerialName("cuerpo") val cuerpo: String? = null,
    @SerialName("estaleida") val estaLeida: Boolean = false,
    @SerialName("fechacreacion") val fechaCreacion: String? = null
)

@Serializable
data class Vehiculo(
    @SerialName("id") val id: Int? = null,
    @SerialName("fk_usuario") val fkUsuario: Int? = null,
    @SerialName("placa") val placa: String = "",
    @SerialName("descripcion") val descripcion: String? = null,
    @SerialName("color") val color: String? = null
)

@Serializable
data class Visita(
    @SerialName("id") val id: Int? = null,
    @SerialName("nombre_visitante") val nombreVisitante: String = "",
    @SerialName("fk_guardia") val fkGuardia: Int? = null,
    @SerialName("tipo") val tipo: String = "INE",
    @SerialName("placa") val placa: String? = null,
    @SerialName("foto_url") val fotoUrl: String? = null,
    @SerialName("timestamp") val timestamp: String? = null
)

data class UnidadConEstatus(val unidad: Unidad, val ocupada: Boolean)

data class DashboardStats(
    val totalUsuarios: Int = 0,
    val reportesAbiertos: Int = 0,
    val reservasHoy: Int = 0,
    val cuotasPendientes: Int = 0
)
