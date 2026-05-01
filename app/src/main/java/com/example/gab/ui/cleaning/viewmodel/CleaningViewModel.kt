package com.example.gab.ui.cleaning.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gab.data.model.AreaComun
import com.example.gab.data.model.TareaLimpieza
import com.example.gab.data.model.Unidad
import com.example.gab.data.remote.SupabaseClientProvider
import com.example.gab.data.repository.CleaningRepository
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class CleaningViewModel : ViewModel() {

    private val repo = CleaningRepository()
    private var realtimeJob: Job? = null
    private var realtimeChannel: io.github.jan.supabase.realtime.RealtimeChannel? = null

    private val _tareas = MutableStateFlow<List<TareaLimpieza>>(emptyList())
    val tareas: StateFlow<List<TareaLimpieza>> = _tareas.asStateFlow()

    private val _areas = MutableStateFlow<List<AreaComun>>(emptyList())
    val areas: StateFlow<List<AreaComun>> = _areas.asStateFlow()

    private val _unidades = MutableStateFlow<Map<Int, Unidad>>(emptyMap())
    val unidades: StateFlow<Map<Int, Unidad>> = _unidades.asStateFlow()

    private val _filtroArea = MutableStateFlow<String?>(null)
    val filtroArea: StateFlow<String?> = _filtroArea.asStateFlow()

    fun setFiltroArea(area: String?) { _filtroArea.value = area }

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    fun loadAll(userId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            val today = LocalDate.now().toString()
            repo.getTareas(userId, today).onSuccess { _tareas.value = it }
            repo.getAreas().onSuccess { _areas.value = it }
            repo.getAllUnidades().onSuccess { list -> _unidades.value = list.associateBy { it.id } }
            _isLoading.value = false
        }
        startRealtime(userId)
    }

    fun startRealtime(userId: Int) {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            val channel = SupabaseClientProvider.client.channel("cleaning-$userId")
            realtimeChannel = channel
            val tareaChanges = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "tarealimpieza" }
            val areaChanges  = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "areacomun" }
            channel.subscribe()
            launch {
                tareaChanges.collect {
                    val today = LocalDate.now().toString()
                    repo.getTareas(userId, today).onSuccess { _tareas.value = it }
                }
            }
            launch {
                areaChanges.collect {
                    repo.getAreas().onSuccess { _areas.value = it }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
        viewModelScope.launch {
            realtimeChannel?.let { runCatching { it.unsubscribe() } }
        }
    }

    fun toggleTarea(tareaId: Int, completada: Boolean) {
        viewModelScope.launch {
            // Optimistic update
            _tareas.value = _tareas.value.map {
                if (it.id == tareaId) it.copy(estaCompletada = completada) else it
            }
            repo.toggleTarea(tareaId, completada)
                .onFailure {
                    // Revert on error
                    _tareas.value = _tareas.value.map {
                        if (it.id == tareaId) it.copy(estaCompletada = !completada) else it
                    }
                    _toastMessage.value = "Error al actualizar tarea"
                }
        }
    }

    fun setEstatus(tareaId: Int, estatus: String, userId: Int) {
        val current = _tareas.value.find { it.id == tareaId }?.estatus ?: return
        val valid = when (current) {
            "pendiente"  -> estatus == "en_proceso"
            "en_proceso" -> estatus == "completada"
            else         -> false
        }
        if (!valid) return

        _tareas.value = _tareas.value.map {
            if (it.id == tareaId) it.copy(estatus = estatus) else it
        }
        viewModelScope.launch {
            repo.setEstatus(tareaId, estatus)
                .onFailure {
                    _toastMessage.value = "Error al actualizar estado"
                    val today = LocalDate.now().toString()
                    repo.getTareas(userId, today).onSuccess { _tareas.value = it }
                }
        }
    }

    fun actualizarArea(areaId: Int, estatus: String) {
        viewModelScope.launch {
            repo.actualizarEstatusArea(areaId, estatus)
                .onSuccess {
                    _areas.value = _areas.value.map {
                        if (it.id == areaId) it.copy(estatus = estatus) else it
                    }
                }
                .onFailure { _toastMessage.value = "Error al actualizar área" }
        }
    }

    fun clearToast() { _toastMessage.value = null }
}
