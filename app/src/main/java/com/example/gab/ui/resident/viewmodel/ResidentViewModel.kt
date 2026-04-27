package com.example.gab.ui.resident.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gab.data.model.*
import com.example.gab.data.repository.ResidentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.gab.util.calcularRecargo

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

    private val _loadingSlots = MutableStateFlow(false)
    val loadingSlots: StateFlow<Boolean> = _loadingSlots.asStateFlow()

    private val _vehiculos = MutableStateFlow<List<Vehiculo>>(emptyList())
    val vehiculos: StateFlow<List<Vehiculo>> = _vehiculos.asStateFlow()

    private val _isLoading  = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun loadAll(userId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            repo.getCuota(userId).onSuccess      { _cuota.value      = it }
            repo.getAvisos().onSuccess            { _avisos.value     = it }
            repo.getReportes(userId).onSuccess    { _reportes.value   = it }
            repo.getAmenidades().onSuccess        { _amenidades.value = it }
            repo.getReservas(userId).onSuccess        { _reservas.value       = it }
            repo.getHistorialCuotas(userId).onSuccess { _historialCuotas.value = it }
            repo.getUsuarioUnidad(userId).onSuccess   { _unidad.value         = it }
            _isLoading.value = false
        }
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

    fun cargarSlotsTomados(amenidadId: Int, fecha: String) {
        viewModelScope.launch {
            _loadingSlots.value = true
            repo.getSlotsTomados(amenidadId, fecha)
                .onSuccess { _slotsTomados.value = it }
                .onFailure { _slotsTomados.value = emptyList() }
            _loadingSlots.value = false
        }
    }

    fun limpiarSlotsTomados() { _slotsTomados.value = emptyList() }

    fun crearReserva(userId: Int, amenidadId: Int, fecha: String, slot: String) {
        viewModelScope.launch {
            repo.crearReservaConValidacion(userId, amenidadId, fecha, slot)
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

    fun clearToast() { _toastMessage.value = null }
}
