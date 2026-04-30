package com.example.gab.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gab.data.model.Usuario
import com.example.gab.ui.auth.LoginScreen
import com.example.gab.ui.auth.viewmodel.AuthState
import com.example.gab.ui.auth.viewmodel.AuthViewModel
import com.example.gab.ui.resident.ResidentShell
import com.example.gab.ui.security.SecurityShell
import com.example.gab.ui.admin.AdminShell
import com.example.gab.ui.cleaning.CleaningShell

enum class UserRole { RESIDENT, SECURITY, ADMIN, CLEANING }

data class AppUser(
    val id: Int = 0,
    val name: String = "",
    val role: UserRole = UserRole.RESIDENT,
    val apartment: String = ""
)

// Route constants used across all role-specific nav graphs
object Routes {
    // Resident
    const val RESIDENT_HOME      = "resident_home"
    const val RESIDENT_REPORTS   = "resident_reports"
    const val RESIDENT_PROFILE   = "resident_profile"
    const val RESIDENT_AMENITIES = "resident_amenities"
    // Security
    const val SECURITY_HOME      = "security_home"
    const val SECURITY_VISITORS  = "security_visitors"
    const val SECURITY_INCIDENTS = "security_incidents"
    const val SECURITY_QR        = "security_qr"
    const val SECURITY_INE       = "security_ine"
    const val SECURITY_PLACAS    = "security_placas"
    // Admin
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val ADMIN_USERS     = "admin_users"
    const val ADMIN_REPORTS   = "admin_reports"
    const val ADMIN_UNITS     = "admin_units"
    const val ADMIN_ACCOUNT   = "admin_account"
    // Resident account
    const val RESIDENT_ACCOUNT = "resident_account"
    // Cleaning
    const val CLEANING_HOME  = "cleaning_home"
    const val CLEANING_TASKS = "cleaning_tasks"
    // Shared
    const val NOTIFICATIONS = "notifications"
}

fun Usuario.toAppUser(): AppUser = AppUser(
    id = id,
    name = nombre,
    role = when (fkRolUsuario) {
        2 -> UserRole.SECURITY
        3 -> UserRole.ADMIN
        4 -> UserRole.CLEANING
        else -> UserRole.RESIDENT
    },
    apartment = ""
)

@Composable
fun GuardianApp(authViewModel: AuthViewModel = viewModel()) {
    val uiState by authViewModel.uiState.collectAsStateWithLifecycle()
    var currentUser by remember { mutableStateOf<AppUser?>(null) }

    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is AuthState.Success -> currentUser = s.usuario.toAppUser()
            is AuthState.SessionRestored -> {
                val role = when (s.role) {
                    2 -> UserRole.SECURITY
                    3 -> UserRole.ADMIN
                    4 -> UserRole.CLEANING
                    else -> UserRole.RESIDENT
                }
                currentUser = AppUser(id = s.userId, name = "", role = role)
            }
            is AuthState.Idle -> currentUser = null
            else -> {}
        }
    }

    when {
        uiState is AuthState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        currentUser == null -> {
            LoginScreen(
                viewModel = authViewModel,
                onLogin = { user -> currentUser = user }
            )
        }
        else -> {
            val user = currentUser ?: return@GuardianApp
            val onLogout: () -> Unit = {
                authViewModel.logout()
                currentUser = null
            }
            when (user.role) {
                UserRole.RESIDENT -> ResidentShell(user = user, onLogout = onLogout)
                UserRole.SECURITY -> SecurityShell(user = user, onLogout = onLogout)
                UserRole.ADMIN    -> AdminShell(user = user, onLogout = onLogout)
                UserRole.CLEANING -> CleaningShell(user = user, onLogout = onLogout)
            }
        }
    }
}
