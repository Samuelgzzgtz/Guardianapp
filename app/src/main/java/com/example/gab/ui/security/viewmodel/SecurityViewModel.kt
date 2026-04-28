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
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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

    private val _isLoading         = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage      = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

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

    fun startRealtime() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            val client  = SupabaseClientProvider.client
            val channel = client.channel("security-acceso-live")
            val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "accesolog"
            }
            channel.subscribe()
            changes.collect {
                repo.getAccesoLog().onSuccess { _accesoLog.value = it }
            }
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
    fun onQrScanned(rawUid: String, guardiaId: Int) {
        viewModelScope.launch {
            val uid = rawUid.trim().toIntOrNull() ?: run {
                _toastMessage.value = "QR inválido"
                return@launch
            }
            repo.getResidentePorId(uid)
                .onSuccess { residente ->
                    if (residente == null) _toastMessage.value = "Residente no encontrado"
                    else _residenteEscaneado.value = residente
                }
                .onFailure { _toastMessage.value = "Error al leer QR: ${it.message}" }
        }
    }

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

    fun clearResidenteEscaneado() { _residenteEscaneado.value = null }

    // ── INE ─────────────────────────────────────────────────────────────────
    fun abrirCamaraIne()  { _showIneCamera.value = true }
    fun cerrarCamaraIne() { _showIneCamera.value = false; _ineTextoReconocido.value = "" }

    fun onIneTextoReconocido(texto: String) {
        _showIneCamera.value = false
        val lineas = texto.lines().map { it.trim() }.filter { it.isNotBlank() }
        val nombreEstimado = lineas
            .filter { line -> line.all { it.isLetter() || it.isWhitespace() } && line.length > 4 }
            .maxByOrNull { it.length } ?: lineas.firstOrNull() ?: texto.take(50)
        _ineTextoReconocido.value = nombreEstimado
    }

    fun confirmarVisitaIne(guardiaId: Int, nombre: String) {
        viewModelScope.launch {
            repo.guardarVisita(nombre, guardiaId, "INE")
                .onSuccess {
                    _toastMessage.value = "Visitante registrado: $nombre"
                    _ineTextoReconocido.value = ""
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
    }

    fun abrirCamaraPlaca()  { _showPlacaCamera.value = true }
    fun cerrarCamaraPlaca() { _showPlacaCamera.value = false; _placaResultado.value = null }

    fun onPlacaTextoReconocido(guardiaId: Int, texto: String) {
        _showPlacaCamera.value = false
        viewModelScope.launch {
            val placaEncontrada = PLACA_REGEX.find(texto.uppercase())?.value
            if (placaEncontrada == null) {
                _toastMessage.value = "No se detectó placa. Intenta con mejor iluminación."
                return@launch
            }
            // If a resident is pre-selected, verify the plate against their registered vehicles
            val residente = _residenteParaPlaca.value
            val vehiculoCorrecto = if (residente != null) {
                _vehiculosResidente.value.firstOrNull { v ->
                    v.placa.uppercase().replace(" ", "").replace("-", "") ==
                        placaEncontrada.uppercase().replace(" ", "").replace("-", "")
                }
            } else {
                repo.getVehiculoPorPlaca(placaEncontrada).getOrNull()
            }
            _placaResultado.value = Pair(placaEncontrada, vehiculoCorrecto)
            if (vehiculoCorrecto != null) {
                repo.guardarVisita("Vehículo ${vehiculoCorrecto.placa} (${vehiculoCorrecto.descripcion ?: ""})", guardiaId, "PLACA")
                    .onSuccess { repo.getVisitas().onSuccess { _visitas.value = it } }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
    }

    fun clearToast() { _toastMessage.value = null }
}
