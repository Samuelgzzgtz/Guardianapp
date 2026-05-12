package com.example.gab.ui.security.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gab.data.model.*
import com.example.gab.data.remote.SupabaseClientProvider
import com.example.gab.data.repository.SecurityRepository
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

sealed class QrScanResult {
    data class Error(val mensaje: String) : QrScanResult()
    data class PaseValido(val pase: PaseVisita) : QrScanResult()
}

class SecurityViewModel : ViewModel() {

    private val repo = SecurityRepository()

    private val _residentes        = MutableStateFlow<List<Usuario>>(emptyList())
    val residentes: StateFlow<List<Usuario>> = _residentes.asStateFlow()

    private val _accesoLog         = MutableStateFlow<List<AccesoLog>>(emptyList())
    val accesoLog: StateFlow<List<AccesoLog>> = _accesoLog.asStateFlow()

    private val _incidentes        = MutableStateFlow<List<Reporte>>(emptyList())
    val incidentes: StateFlow<List<Reporte>> = _incidentes.asStateFlow()

    private val _visitas           = MutableStateFlow<List<Visita>>(emptyList())
    val visitas: StateFlow<List<Visita>> = _visitas.asStateFlow()

    private val _residenteEscaneado = MutableStateFlow<Usuario?>(null)
    val residenteEscaneado: StateFlow<Usuario?> = _residenteEscaneado.asStateFlow()

    private val _vehiculosEscaneado = MutableStateFlow<List<Vehiculo>>(emptyList())
    val vehiculosEscaneado: StateFlow<List<Vehiculo>> = _vehiculosEscaneado.asStateFlow()

    private val _unidadEscaneado = MutableStateFlow<Unidad?>(null)
    val unidadEscaneado: StateFlow<Unidad?> = _unidadEscaneado.asStateFlow()

    private val _unidadPlacaInfo = MutableStateFlow<Unidad?>(null)
    val unidadPlacaInfo: StateFlow<Unidad?> = _unidadPlacaInfo.asStateFlow()

    private val _showIneCamera     = MutableStateFlow(false)
    val showIneCamera: StateFlow<Boolean> = _showIneCamera.asStateFlow()

    private val _ineTextoReconocido = MutableStateFlow("")
    val ineTextoReconocido: StateFlow<String> = _ineTextoReconocido.asStateFlow()

    private val _showPlacaCamera   = MutableStateFlow(false)
    val showPlacaCamera: StateFlow<Boolean> = _showPlacaCamera.asStateFlow()

    private val _placaResultado    = MutableStateFlow<Pair<String, Vehiculo?>?>(null)
    val placaResultado: StateFlow<Pair<String, Vehiculo?>?> = _placaResultado.asStateFlow()

    // Resident-first vehicle flow
    private val _residenteParaPlaca       = MutableStateFlow<Usuario?>(null)
    val residenteParaPlaca: StateFlow<Usuario?> = _residenteParaPlaca.asStateFlow()

    private val _vehiculosResidente       = MutableStateFlow<List<Vehiculo>>(emptyList())
    val vehiculosResidente: StateFlow<List<Vehiculo>> = _vehiculosResidente.asStateFlow()

    private val _autoRegistrado    = MutableStateFlow(false)
    val autoRegistrado: StateFlow<Boolean> = _autoRegistrado.asStateFlow()

    // Residente cuya placa fue escaneada (con su foto de INE)
    private val _residentePlacaInfo  = MutableStateFlow<Usuario?>(null)
    val residentePlacaInfo: StateFlow<Usuario?> = _residentePlacaInfo.asStateFlow()

    // Residentes que coinciden con el texto de INE escaneado
    private val _residentesIneMatch  = MutableStateFlow<List<Usuario>>(emptyList())
    val residentesIneMatch: StateFlow<List<Usuario>> = _residentesIneMatch.asStateFlow()

    private val _ultimoAccesoId    = MutableStateFlow(0)

    private val _isLoading         = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage      = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private val _qrScanResult      = MutableStateFlow<QrScanResult?>(null)
    val qrScanResult: StateFlow<QrScanResult?> = _qrScanResult.asStateFlow()

    private var realtimeJob: Job? = null
    private val PLACA_REGEX = Regex("[A-Z]{2,3}[-\\s]?\\d{2,4}[-\\s]?[A-Z0-9]{0,3}")

    fun loadAll() {
        viewModelScope.launch {
            _isLoading.value = true
            repo.getResidentes().onSuccess  { _residentes.value  = it }
            repo.getAccesoLog().onSuccess   { _accesoLog.value   = it }
            repo.getIncidentes().onSuccess  { _incidentes.value  = it }
            repo.getVisitas().onSuccess     { _visitas.value     = it }
            _isLoading.value = false
        }
        startRealtime()
    }

    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    fun startRealtime() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            val channel = SupabaseClientProvider.client.channel("security-acceso-live")
            realtimeChannel = channel
            val accesoChanges    = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "accesolog" }
            val incidenteChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "reporte" }
            val residenteChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "usuario" }
            channel.subscribe()
            launch { accesoChanges.collect    { repo.getAccesoLog().onSuccess  { _accesoLog.value  = it } } }
            launch { incidenteChanges.collect { repo.getIncidentes().onSuccess { _incidentes.value = it } } }
            launch { residenteChanges.collect { repo.getResidentes().onSuccess { _residentes.value = it } } }
        }
    }

    fun buscarResidente(query: String) {
        viewModelScope.launch {
            repo.getResidentes(query).onSuccess { _residentes.value = it }
        }
    }

    fun registrarAcceso(guardiaId: Int, residente: Usuario, direccion: String) {
        viewModelScope.launch {
            val hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            repo.registrarAcceso(residente.id, guardiaId, direccion, hora)
                .onSuccess {
                    _toastMessage.value = "${direccion.lowercase().replaceFirstChar { it.uppercase() }} registrada para ${residente.nombre}"
                    repo.getAccesoLog().onSuccess { _accesoLog.value = it }
                }
                .onFailure { _toastMessage.value = "Error al registrar: ${it.message}" }
        }
    }

    fun reportarIncidente(guardiaId: Int, titulo: String, ubicacion: String, esUrgente: Boolean) {
        viewModelScope.launch {
            repo.reportarIncidente(guardiaId, titulo, ubicacion, esUrgente)
                .onSuccess {
                    _toastMessage.value = "Incidente reportado"
                    repo.getIncidentes().onSuccess { _incidentes.value = it }
                }
                .onFailure { _toastMessage.value = "Error al reportar: ${it.message}" }
        }
    }

    // ── QR ──────────────────────────────────────────────────────────────────
    fun onQrScanned(qrContent: String, guardiaId: Int) {
        if (qrContent.startsWith("pase:")) {
            val paseId = qrContent.removePrefix("pase:").toIntOrNull()
            if (paseId == null) {
                _qrScanResult.value = QrScanResult.Error("QR inválido")
                return
            }
            resolverPaseQr(paseId)
        } else {
            val userId = qrContent.trim().toIntOrNull()
            if (userId == null) {
                _toastMessage.value = "QR no reconocido"
                return
            }
            handleResidentQr(userId)
        }
    }

    private fun handleResidentQr(userId: Int) {
        viewModelScope.launch {
            repo.getResidentePorId(userId)
                .onSuccess { residente ->
                    if (residente == null) {
                        _toastMessage.value = "Residente no encontrado"
                    } else {
                        _residenteEscaneado.value = residente
                        repo.getVehiculosPorResidente(userId)
                            .onSuccess { _vehiculosEscaneado.value = it }
                            .onFailure { _vehiculosEscaneado.value = emptyList() }
                        residente.fkUnidad?.let { uid ->
                            repo.getUnidadPorId(uid)
                                .onSuccess { _unidadEscaneado.value = it }
                                .onFailure { _unidadEscaneado.value = null }
                        }
                    }
                }
                .onFailure { _toastMessage.value = "Error al leer QR: ${it.message}" }
        }
    }

    private fun resolverPaseQr(paseId: Int) {
        viewModelScope.launch {
            repo.getPaseById(paseId)
                .onSuccess { pase ->
                    when {
                        !pase.activo -> _qrScanResult.value = QrScanResult.Error("Este pase ya no está activo")
                        pase.fechaExpiracion != null && LocalDate.now().isAfter(
                            LocalDate.parse(pase.fechaExpiracion)
                        ) -> _qrScanResult.value = QrScanResult.Error("Pase expirado")
                        else -> _qrScanResult.value = QrScanResult.PaseValido(pase)
                    }
                }
                .onFailure { _qrScanResult.value = QrScanResult.Error("Pase no encontrado") }
        }
    }

    fun confirmarAccesoPase(pase: PaseVisita) {
        viewModelScope.launch {
            repo.registrarAccesoPase(pase)
                .onSuccess {
                    repo.notificarLlegadaVisitante(pase.fkResidente!!, pase.nombreVisitante)
                    _toastMessage.value = "Acceso registrado para ${pase.nombreVisitante}"
                    _qrScanResult.value = null
                }
                .onFailure { _toastMessage.value = "Error al registrar acceso: ${it.message}" }
        }
    }

    fun clearQrScanResult() { _qrScanResult.value = null }

    fun registrarAccesoQr(guardiaId: Int, residente: Usuario, direccion: String) {
        val hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        viewModelScope.launch {
            repo.registrarAcceso(residente.id, guardiaId, direccion, hora)
                .onSuccess {
                    _toastMessage.value = "${direccion.lowercase().replaceFirstChar { it.uppercase() }} QR: ${residente.nombre}"
                    _residenteEscaneado.value = null
                    repo.getAccesoLog().onSuccess { _accesoLog.value = it }
                }
                .onFailure { _toastMessage.value = "Error al registrar acceso" }
        }
    }

    fun clearResidenteEscaneado() {
        _residenteEscaneado.value = null
        _vehiculosEscaneado.value = emptyList()
        _unidadEscaneado.value = null
    }

    // ── INE ─────────────────────────────────────────────────────────────────
    fun abrirCamaraIne()  { _showIneCamera.value = true }
    fun cerrarCamaraIne() { _showIneCamera.value = false; _ineTextoReconocido.value = "" }

    fun onIneTextoReconocido(texto: String) {
        _showIneCamera.value = false
        val lineas = texto.lines().map { it.trim() }.filter { it.isNotBlank() }
        // Lines that look like names: only letters/spaces, no numbers, length > 3
        val soloLetras = lineas.filter { line ->
            line.all { it.isLetter() || it.isWhitespace() } && line.length > 3
        }
        // Concatenate all name-like lines for broader matching (INE splits apellidos across lines)
        val nombreEstimado = if (soloLetras.isNotEmpty())
            soloLetras.joinToString(" ")
        else
            lineas.firstOrNull() ?: texto.take(80)
        _ineTextoReconocido.value = nombreEstimado
        if (nombreEstimado.length >= 3) {
            viewModelScope.launch {
                repo.buscarResidentePorNombre(nombreEstimado)
                    .onSuccess { _residentesIneMatch.value = it }
                    .onFailure { _residentesIneMatch.value = emptyList() }
            }
        }
    }

    fun clearIneMatch() { _residentesIneMatch.value = emptyList() }

    fun confirmarVisitaIne(guardiaId: Int, nombre: String) {
        viewModelScope.launch {
            repo.guardarVisita(nombre, guardiaId, "INE")
                .onSuccess {
                    _toastMessage.value = "Visitante registrado: $nombre"
                    _ineTextoReconocido.value = ""
                    _residentesIneMatch.value = emptyList()
                    repo.getVisitas().onSuccess { _visitas.value = it }
                }
                .onFailure { _toastMessage.value = "Error al registrar visitante: ${it.message}" }
        }
    }

    // ── PLACAS — Flujo correcto: residente primero → descripción visible → escanear placa ──────────
    fun seleccionarResidenteParaPlaca(residente: Usuario) {
        _residenteParaPlaca.value = residente
        _placaResultado.value = null
        viewModelScope.launch {
            repo.getVehiculosPorResidente(residente.id)
                .onSuccess { _vehiculosResidente.value = it }
                .onFailure { _vehiculosResidente.value = emptyList() }
        }
    }

    fun limpiarSeleccionResidentePlaca() {
        _residenteParaPlaca.value = null
        _vehiculosResidente.value = emptyList()
        _placaResultado.value = null
        _residentePlacaInfo.value = null
        _unidadPlacaInfo.value = null
    }

    fun abrirCamaraPlaca()  { _showPlacaCamera.value = true }
    fun cerrarCamaraPlaca() { _showPlacaCamera.value = false; _placaResultado.value = null }

    fun confirmarPlacaManual(guardiaId: Int, placaTexto: String) {
        viewModelScope.launch {
            val placaNormalizada = placaTexto.trim().replace(" ", "").replace("-", "").uppercase()
            if (placaNormalizada.length < 4) {
                _toastMessage.value = "Ingresa una placa válida"
                return@launch
            }
            val residente = _residenteParaPlaca.value
            val vehiculoCorrecto = if (residente != null) {
                _vehiculosResidente.value.firstOrNull { v ->
                    v.placa.uppercase().replace(" ", "").replace("-", "") == placaNormalizada
                }
            } else {
                repo.getVehiculoPorPlaca(placaNormalizada).getOrNull()
            }
            _placaResultado.value = Pair(placaNormalizada, vehiculoCorrecto)

            if (vehiculoCorrecto != null) {
                repo.guardarVisita(
                    "Vehículo ${vehiculoCorrecto.placa} (${vehiculoCorrecto.descripcion ?: ""})",
                    guardiaId, "PLACA"
                ).onSuccess { repo.getVisitas().onSuccess { _visitas.value = it } }

                val ownerUserId = vehiculoCorrecto.fkUsuario ?: residente?.id
                if (ownerUserId != null) {
                    repo.getResidentePorId(ownerUserId)
                        .onSuccess { owner ->
                            _residentePlacaInfo.value = owner
                            owner?.fkUnidad?.let { uid ->
                                repo.getUnidadPorId(uid)
                                    .onSuccess { _unidadPlacaInfo.value = it }
                                    .onFailure { _unidadPlacaInfo.value = null }
                            }
                        }
                }

                if (residente != null) {
                    val hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    repo.registrarAccesoConId(residente.id, guardiaId, "ENTRADA", hora)
                        .onSuccess { accesoId ->
                            _ultimoAccesoId.value = accesoId
                            _autoRegistrado.value = true
                            repo.getAccesoLog().onSuccess { _accesoLog.value = it }
                        }
                        .onFailure { _toastMessage.value = "Error al registrar acceso: ${it.message}" }
                }
            } else {
                _residentePlacaInfo.value = null
                _unidadPlacaInfo.value = null
            }
        }
    }

    fun onPlacaTextoReconocido(guardiaId: Int, texto: String) {
        _showPlacaCamera.value = false
        viewModelScope.launch {
            val placaEncontrada = PLACA_REGEX.find(texto.uppercase())?.value
            if (placaEncontrada == null) {
                _toastMessage.value = "No se detectó placa. Intenta con mejor iluminación."
                return@launch
            }
            val placaNormalizada = placaEncontrada.trim().replace(" ", "").replace("-", "").uppercase()
            val residente = _residenteParaPlaca.value
            val vehiculoCorrecto = if (residente != null) {
                _vehiculosResidente.value.firstOrNull { v ->
                    v.placa.uppercase().replace(" ", "").replace("-", "") == placaNormalizada
                }
            } else {
                repo.getVehiculoPorPlaca(placaNormalizada).getOrNull()
            }
            _placaResultado.value = Pair(placaNormalizada, vehiculoCorrecto)

            if (vehiculoCorrecto != null) {
                repo.guardarVisita(
                    "Vehículo ${vehiculoCorrecto.placa} (${vehiculoCorrecto.descripcion ?: ""})",
                    guardiaId, "PLACA"
                ).onSuccess { repo.getVisitas().onSuccess { _visitas.value = it } }

                // Cargar datos del residente (nombre, foto INE, unidad) para mostrar en UI
                val ownerUserId = vehiculoCorrecto.fkUsuario ?: residente?.id
                if (ownerUserId != null) {
                    repo.getResidentePorId(ownerUserId)
                        .onSuccess { owner ->
                            _residentePlacaInfo.value = owner
                            owner?.fkUnidad?.let { uid ->
                                repo.getUnidadPorId(uid)
                                    .onSuccess { _unidadPlacaInfo.value = it }
                                    .onFailure { _unidadPlacaInfo.value = null }
                            }
                        }
                }

                // Auto-register building access when resident was pre-selected and plate matches
                if (residente != null) {
                    val hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    repo.registrarAccesoConId(residente.id, guardiaId, "ENTRADA", hora)
                        .onSuccess { accesoId ->
                            _ultimoAccesoId.value = accesoId
                            _autoRegistrado.value = true
                            repo.getAccesoLog().onSuccess { _accesoLog.value = it }
                        }
                        .onFailure { _toastMessage.value = "Error al registrar acceso: ${it.message}" }
                }
            } else {
                _residentePlacaInfo.value = null
            }
        }
    }

    fun cancelarUltimoAcceso() {
        val accesoId = _ultimoAccesoId.value
        if (accesoId == 0) return
        viewModelScope.launch {
            repo.cancelarAcceso(accesoId)
                .onSuccess {
                    _autoRegistrado.value = false
                    _ultimoAccesoId.value = 0
                    _toastMessage.value = "Acceso anulado"
                    repo.getAccesoLog().onSuccess { _accesoLog.value = it }
                }
                .onFailure { _toastMessage.value = "Error al anular: ${it.message}" }
        }
    }

    fun clearAutoRegistrado() { _autoRegistrado.value = false }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
        viewModelScope.launch {
            realtimeChannel?.let { runCatching { it.unsubscribe() } }
        }
    }

    fun clearToast() { _toastMessage.value = null }
}
