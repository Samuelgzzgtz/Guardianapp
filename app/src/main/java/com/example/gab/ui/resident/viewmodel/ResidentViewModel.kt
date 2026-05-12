package com.example.gab.ui.resident.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gab.data.model.*
import com.example.gab.data.remote.SupabaseClientProvider
import com.example.gab.data.repository.ResidentRepository
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.gab.util.calcularRecargo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ResidentViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ResidentRepository(application)

    private val _cuota      = MutableStateFlow<Cuota?>(null)
    val cuota: StateFlow<Cuota?> = _cuota.asStateFlow()

    private val _avisos     = MutableStateFlow<List<Aviso>>(emptyList())
    val avisos: StateFlow<List<Aviso>> = _avisos.asStateFlow()

    private val _reportes   = MutableStateFlow<List<Reporte>>(emptyList())
    val reportes: StateFlow<List<Reporte>> = _reportes.asStateFlow()

    private val _amenidades = MutableStateFlow<List<Amenidad>>(emptyList())
    val amenidades: StateFlow<List<Amenidad>> = _amenidades.asStateFlow()

    private val _reservas   = MutableStateFlow<List<Reserva>>(emptyList())
    val reservas: StateFlow<List<Reserva>> = _reservas.asStateFlow()

    private val _historialCuotas = MutableStateFlow<List<Cuota>>(emptyList())
    val historialCuotas: StateFlow<List<Cuota>> = _historialCuotas.asStateFlow()

    private val _unidad = MutableStateFlow<Unidad?>(null)
    val unidad: StateFlow<Unidad?> = _unidad.asStateFlow()

    private val _slotsTomados = MutableStateFlow<List<String>>(emptyList())
    val slotsTomados: StateFlow<List<String>> = _slotsTomados.asStateFlow()

    private val _conteoPorSlot = MutableStateFlow<Map<String, Int>>(emptyMap())
    val conteoPorSlot: StateFlow<Map<String, Int>> = _conteoPorSlot.asStateFlow()

    private val _loadingSlots = MutableStateFlow(false)
    val loadingSlots: StateFlow<Boolean> = _loadingSlots.asStateFlow()

    private val _vehiculos = MutableStateFlow<List<Vehiculo>>(emptyList())
    val vehiculos: StateFlow<List<Vehiculo>> = _vehiculos.asStateFlow()

    private val _ineUrl = MutableStateFlow<String?>(null)
    val ineUrl: StateFlow<String?> = _ineUrl.asStateFlow()

    private val _pases = MutableStateFlow<List<PaseVisita>>(emptyList())
    val pases: StateFlow<List<PaseVisita>> = _pases.asStateFlow()

    private val _isLoading  = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun loadAll(userId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            repo.getCuota(userId).onSuccess           { _cuota.value          = it }
            repo.getAvisos().onSuccess                { _avisos.value         = it }
            repo.getReportes(userId).onSuccess        { _reportes.value       = it }
            repo.getAmenidades().onSuccess            { _amenidades.value     = it }
            repo.getReservas(userId).onSuccess        { _reservas.value       = it }
            repo.getHistorialCuotas(userId).onSuccess { _historialCuotas.value = it }
            repo.getUsuarioUnidad(userId).onSuccess   { _unidad.value         = it }
            repo.getVehiculos(userId).onSuccess       { _vehiculos.value      = it }
            repo.getPases(userId).onSuccess           { _pases.value          = it }
            repo.getUsuario(userId).onSuccess         { _ineUrl.value         = it?.ineUrl }
            _isLoading.value = false
        }
        startRealtime(userId)
    }

    fun pagarCuota(userId: Int) {
        viewModelScope.launch {
            val current = _cuota.value ?: return@launch
            _isLoading.value = true
            repo.actualizarEstatusCuota(current.id, "pagado")
                .onSuccess {
                    _cuota.value = current.copy(estatus = "pagado")
                    _toastMessage.value = "Pago procesado correctamente"
                }
                .onFailure { _toastMessage.value = "Error al procesar pago: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun submitReporte(userId: Int, categoria: String, titulo: String, desc: String, fotoUri: Uri? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            repo.submitReporte(userId, categoria, titulo, desc, fotoUri)
                .onSuccess {
                    _toastMessage.value = "Reporte enviado correctamente"
                    repo.getReportes(userId).onSuccess { _reportes.value = it }
                }
                .onFailure { _toastMessage.value = "Error al enviar reporte: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun cerrarReporte(userId: Int, reporteId: Int) {
        viewModelScope.launch {
            repo.actualizarEstatusReporte(reporteId, "Resuelto")
                .onSuccess {
                    _toastMessage.value = "Reporte marcado como resuelto"
                    repo.getReportes(userId).onSuccess { _reportes.value = it }
                }
                .onFailure { _toastMessage.value = "Error al actualizar reporte: ${it.message}" }
        }
    }

    fun cargarSlotsTomados(amenidad: Amenidad, fecha: String) {
        viewModelScope.launch {
            _loadingSlots.value = true
            repo.getSlotsTomados(amenidad.id, fecha, amenidad.capacidad, amenidad.permiteConcurrencia)
                .onSuccess { _slotsTomados.value = it }
                .onFailure { _slotsTomados.value = emptyList() }
            repo.getConteoPorSlot(amenidad.id, fecha)
                .onSuccess { _conteoPorSlot.value = it }
                .onFailure { _conteoPorSlot.value = emptyMap() }
            _loadingSlots.value = false
        }
    }

    fun limpiarSlotsTomados() {
        _slotsTomados.value = emptyList()
        _conteoPorSlot.value = emptyMap()
    }

    fun crearReserva(userId: Int, amenidad: Amenidad, fecha: String, slot: String) {
        viewModelScope.launch {
            repo.crearReservaConValidacion(userId, amenidad.id, fecha, slot, amenidad.capacidad, amenidad.permiteConcurrencia)
                .onSuccess {
                    _toastMessage.value = "Reserva creada exitosamente"
                    repo.getReservas(userId).onSuccess { _reservas.value = it }
                }
                .onFailure { _toastMessage.value = it.message ?: "Error al reservar" }
        }
    }

    fun cancelarReserva(userId: Int, reservaId: Int) {
        viewModelScope.launch {
            repo.cancelarReserva(reservaId)
                .onSuccess {
                    _toastMessage.value = "Reserva cancelada"
                    repo.getReservas(userId).onSuccess { _reservas.value = it }
                }
                .onFailure { _toastMessage.value = "Error al cancelar: ${it.message}" }
        }
    }

    private var realtimeJob: Job? = null
    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    fun startRealtime(userId: Int) {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            val channel = SupabaseClientProvider.client.channel("resident-live-$userId")
            realtimeChannel = channel
            val reservaChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "reserva" }
            val avisoChanges   = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "aviso" }
            val reporteChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "reporte" }
            channel.subscribe()
            launch { reservaChanges.collect { repo.getReservas(userId).onSuccess { _reservas.value = it } } }
            launch { avisoChanges.collect   { repo.getAvisos().onSuccess          { _avisos.value   = it } } }
            launch { reporteChanges.collect { repo.getReportes(userId).onSuccess  { _reportes.value = it } } }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
        viewModelScope.launch {
            realtimeChannel?.let { runCatching { it.unsubscribe() } }
        }
    }

    fun solicitarLimpieza(userId: Int, notas: String) {
        viewModelScope.launch {
            val fkUnidad = _unidad.value?.id ?: run {
                _toastMessage.value = "No tienes una unidad asignada"
                return@launch
            }
            repo.solicitarLimpieza(userId, fkUnidad, notas)
                .onSuccess { _toastMessage.value = "Solicitud de limpieza enviada" }
                .onFailure { _toastMessage.value = "Error al enviar solicitud: ${it.message}" }
        }
    }

    fun cargarVehiculos(userId: Int) {
        viewModelScope.launch {
            repo.getVehiculos(userId).onSuccess { _vehiculos.value = it }
        }
    }

    fun agregarVehiculo(userId: Int, placa: String, descripcion: String, color: String) {
        if (_vehiculos.value.size >= 3) {
            _toastMessage.value = "Límite de 3 vehículos por residente alcanzado"
            return
        }
        viewModelScope.launch {
            repo.agregarVehiculo(userId, placa, descripcion, color)
                .onSuccess {
                    _toastMessage.value = "Vehículo registrado"
                    repo.getVehiculos(userId).onSuccess { _vehiculos.value = it }
                }
                .onFailure { _toastMessage.value = "Error: ${it.message}" }
        }
    }

    fun eliminarVehiculo(userId: Int, vehiculoId: Int) {
        viewModelScope.launch {
            repo.eliminarVehiculo(vehiculoId)
                .onSuccess {
                    _toastMessage.value = "Vehículo eliminado"
                    _vehiculos.value = _vehiculos.value.filter { it.id != vehiculoId }
                }
                .onFailure { _toastMessage.value = "Error al eliminar: ${it.message}" }
        }
    }

    fun loadPases(userId: Int) {
        viewModelScope.launch {
            repo.getPases(userId).onSuccess { _pases.value = it }
        }
    }

    fun crearPase(
        userId: Int,
        nombreVisitante: String,
        modeloVehiculo: String?,
        colorVehiculo: String?,
        placaVehiculo: String?,
        vigencia: String,
        usosMaximos: Int,
        fechaExpiracion: String?
    ) {
        viewModelScope.launch {
            repo.crearPase(userId, nombreVisitante, modeloVehiculo, colorVehiculo, placaVehiculo, vigencia, usosMaximos, fechaExpiracion)
                .onSuccess {
                    _toastMessage.value = "Pase creado"
                    repo.getPases(userId).onSuccess { _pases.value = it }
                }
                .onFailure { _toastMessage.value = "Error al crear pase: ${it.message}" }
        }
    }

    fun desactivarPase(userId: Int, paseId: Int) {
        viewModelScope.launch {
            repo.desactivarPase(paseId)
                .onSuccess {
                    _toastMessage.value = "Pase desactivado"
                    repo.getPases(userId).onSuccess { _pases.value = it }
                }
                .onFailure { _toastMessage.value = "Error al desactivar pase: ${it.message}" }
        }
    }

    fun subirFotoIne(userId: Int, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            repo.subirFotoIne(userId, uri)
                .onSuccess { url ->
                    _ineUrl.value = url
                    _toastMessage.value = "Foto de INE guardada"
                }
                .onFailure { _toastMessage.value = "Error al subir INE: ${it.message}" }
            _isLoading.value = false
        }
    }

    fun clearToast() { _toastMessage.value = null }
}
