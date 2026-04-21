package com.example.gab.ui.cleaning.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gab.data.model.AreaComun
import com.example.gab.data.model.TareaLimpieza
import com.example.gab.data.repository.CleaningRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class CleaningViewModel : ViewModel() {

    private val repo = CleaningRepository()

    private val _tareas = MutableStateFlow<List<TareaLimpieza>>(emptyList())
    val tareas: StateFlow<List<TareaLimpieza>> = _tareas.asStateFlow()

    private val _areas = MutableStateFlow<List<AreaComun>>(emptyList())
    val areas: StateFlow<List<AreaComun>> = _areas.asStateFlow()

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
            _isLoading.value = false
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
