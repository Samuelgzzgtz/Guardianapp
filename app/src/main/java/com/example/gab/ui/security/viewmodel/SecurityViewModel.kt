package com.example.gab.ui.security.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gab.data.model.AccesoLog
import com.example.gab.data.model.Reporte
import com.example.gab.data.model.Usuario
import com.example.gab.data.repository.SecurityRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class SecurityViewModel : ViewModel() {

    private val repo = SecurityRepository()

    private val _residentes  = MutableStateFlow<List<Usuario>>(emptyList())
    val residentes: StateFlow<List<Usuario>> = _residentes.asStateFlow()

    private val _accesoLog   = MutableStateFlow<List<AccesoLog>>(emptyList())
    val accesoLog: StateFlow<List<AccesoLog>> = _accesoLog.asStateFlow()

    private val _incidentes  = MutableStateFlow<List<Reporte>>(emptyList())
    val incidentes: StateFlow<List<Reporte>> = _incidentes.asStateFlow()

    private val _isLoading   = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun loadAll() {
        viewModelScope.launch {
            _isLoading.value = true
            repo.getResidentes().onSuccess  { _residentes.value  = it }
            repo.getAccesoLog().onSuccess   { _accesoLog.value   = it }
            repo.getIncidentes().onSuccess  { _incidentes.value  = it }
            _isLoading.value = false
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

    fun clearToast() { _toastMessage.value = null }
}
