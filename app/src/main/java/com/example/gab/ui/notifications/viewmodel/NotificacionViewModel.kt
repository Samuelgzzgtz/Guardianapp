package com.example.gab.ui.notifications.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gab.data.model.Notificacion
import com.example.gab.data.repository.NotificacionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NotificacionViewModel : ViewModel() {

    private val repo = NotificacionRepository()

    private val _notificaciones = MutableStateFlow<List<Notificacion>>(emptyList())
    val notificaciones: StateFlow<List<Notificacion>> = _notificaciones.asStateFlow()

    val unreadCount: StateFlow<Int> = _notificaciones
        .map { it.count { n -> !n.estaLeida } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentUserId: Int = 0

    fun init(userId: Int) {
        currentUserId = userId
        loadNotificaciones()
        suscribirRealtime(userId)
    }

    private fun loadNotificaciones() {
        viewModelScope.launch {
            _isLoading.value = true
            repo.getNotificaciones(currentUserId).onSuccess { _notificaciones.value = it }
            _isLoading.value = false
        }
    }

    fun marcarLeida(notifId: Int) {
        viewModelScope.launch {
            _notificaciones.value = _notificaciones.value.map {
                if (it.id == notifId) it.copy(estaLeida = true) else it
            }
            repo.marcarLeida(notifId)
        }
    }

    private fun suscribirRealtime(userId: Int) {
        viewModelScope.launch {
            repo.subscribirCanal(userId)
            repo.suscribirRealtime(userId).collect { nueva ->
                _notificaciones.value = listOf(nueva) + _notificaciones.value
            }
        }
    }
}
