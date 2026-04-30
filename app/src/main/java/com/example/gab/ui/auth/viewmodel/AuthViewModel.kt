package com.example.gab.ui.auth.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gab.data.local.SessionDataStore
import com.example.gab.data.local.sessionDataStore
import com.example.gab.data.model.Usuario
import com.example.gab.data.repository.AuthRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AuthState {
    object Idle : AuthState()
    object Loading : AuthState()
    data class Success(val usuario: Usuario) : AuthState()
    data class Error(val message: String) : AuthState()
    data class SessionRestored(val userId: Int, val role: Int) : AuthState()
}

class AuthViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AuthRepository(application)
    private val sessionStore = SessionDataStore(application)

    private val _uiState = MutableStateFlow<AuthState>(AuthState.Loading)
    val uiState: StateFlow<AuthState> = _uiState.asStateFlow()

    init {
        checkSession()
    }

    fun checkSession() {
        viewModelScope.launch {
            try {
                val userId = sessionStore.userId.firstOrNull()
                val role = sessionStore.userRole.firstOrNull()
                val token = sessionStore.authToken.firstOrNull()
                if (userId != null && role != null && !token.isNullOrBlank()) {
                    _uiState.value = AuthState.SessionRestored(userId, role)
                } else {
                    _uiState.value = AuthState.Idle
                }
            } catch (e: Exception) {
                _uiState.value = AuthState.Idle
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthState.Loading
            repo.signIn(email, password)
                .onSuccess { user -> _uiState.value = AuthState.Success(user) }
                .onFailure { e -> _uiState.value = AuthState.Error(e.message ?: "Error de autenticación") }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.signOut()
            _uiState.value = AuthState.Idle
        }
    }

    fun reenviarVerificacion(email: String) {
        viewModelScope.launch {
            repo.reenviarVerificacion(email)
                .onSuccess { _uiState.value = AuthState.Error("Correo reenviado. Revisa tu bandeja de entrada.") }
                .onFailure { _uiState.value = AuthState.Error("No se pudo reenviar: ${it.message}") }
        }
    }
}
