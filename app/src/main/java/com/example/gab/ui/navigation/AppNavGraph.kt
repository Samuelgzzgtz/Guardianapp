package com.example.gab.ui.navigation

import androidx.compose.runtime.*
import com.example.gab.ui.auth.LoginScreen
import com.example.gab.ui.resident.ResidentShell
import com.example.gab.ui.security.SecurityShell
import com.example.gab.ui.admin.AdminShell
import com.example.gab.ui.cleaning.CleaningShell

enum class UserRole { RESIDENT, SECURITY, ADMIN, CLEANING }

data class AppUser(
    val name: String,
    val role: UserRole,
    val apartment: String = "",
    val id: String = ""
)

// Route constants used across all role-specific nav graphs
object Routes {
    // Resident
    const val RESIDENT_HOME     = "resident_home"
    const val RESIDENT_REPORTS  = "resident_reports"
    const val RESIDENT_PROFILE  = "resident_profile"
    // Security
    const val SECURITY_HOME      = "security_home"
    const val SECURITY_VISITORS  = "security_visitors"
    const val SECURITY_INCIDENTS = "security_incidents"
    // Admin
    const val ADMIN_DASHBOARD = "admin_dashboard"
    const val ADMIN_USERS     = "admin_users"
    const val ADMIN_REPORTS   = "admin_reports"
    // Cleaning
    const val CLEANING_HOME  = "cleaning_home"
    const val CLEANING_TASKS = "cleaning_tasks"
}

@Composable
fun GuardianApp() {
    var currentUser by remember { mutableStateOf<AppUser?>(null) }

    if (currentUser == null) {
        LoginScreen(onLogin = { user -> currentUser = user })
    } else {
        when (currentUser!!.role) {
            UserRole.RESIDENT -> ResidentShell(
                user = currentUser!!,
                onLogout = { currentUser = null }
            )
            UserRole.SECURITY -> SecurityShell(
                user = currentUser!!,
                onLogout = { currentUser = null }
            )
            UserRole.ADMIN -> AdminShell(
                user = currentUser!!,
                onLogout = { currentUser = null }
            )
            UserRole.CLEANING -> CleaningShell(
                user = currentUser!!,
                onLogout = { currentUser = null }
            )
        }
    }
}
