package com.example.gab.ui.notifications.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gab.data.model.Notificacion
import com.example.gab.data.remote.SupabaseClientProvider
import com.example.gab.data.repository.NotificacionRepository
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.Job
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
    private var realtimeJob: Job? = null

    fun init(userId: Int) {
        currentUserId = userId
        loadNotificaciones()
        startRealtime(userId)
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

    private fun startRealtime(userId: Int) {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            val channel = SupabaseClientProvider.client.channel("notif-$userId")
            val flow = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "notificacion"
                filter("fkusuario", FilterOperator.EQ, userId)
            }
            channel.subscribe()
            flow.collect { action ->
                val nueva = action.decodeRecord<Notificacion>()
                _notificaciones.value = listOf(nueva) + _notificaciones.value
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
    }
}
