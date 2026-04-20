@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.gab.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.navigation.Routes
import com.example.gab.ui.navigation.UserRole
import com.example.gab.ui.theme.*

private data class ManagedUser(val name: String, val role: UserRole, val unit: String, val active: Boolean)
private data class AdminReport(val id: String, val title: String, val reporter: String, val status: String, val date: String)

@Composable
fun AdminShell(user: AppUser, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val navItems = listOf(
        NavItem("Dashboard", Icons.Default.Dashboard, Routes.ADMIN_DASHBOARD),
        NavItem("Usuarios",  Icons.Default.Group,     Routes.ADMIN_USERS),
        NavItem("Reportes",  Icons.Default.Assessment, Routes.ADMIN_REPORTS),
    )
    AppShell(
        navController = navController,
        navItems = navItems,
        topBarTitle = "Administración",
        topBarActions = {
            IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }
        }
    ) {
        NavHost(navController = navController, startDestination = Routes.ADMIN_DASHBOARD) {
            composable(Routes.ADMIN_DASHBOARD) { AdminDashboard(user) }
            composable(Routes.ADMIN_USERS)     { AdminUsersScreen() }
            composable(Routes.ADMIN_REPORTS)   { AdminReportsScreen() }
        }
    }
}

@Composable
fun AdminDashboard(user: AppUser) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Panel de Control", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Bienvenido, ${user.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Residentes", "48",  Icons.Default.Home,       ResidentBlue,  Modifier.weight(1f))
                StatCard("Personal",   "12",  Icons.Default.ManageAccounts, AdminPurple, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Reportes abiertos", "7", Icons.AutoMirrored.Filled.Assignment, StatusWarning, Modifier.weight(1f))
                StatCard("Visitantes hoy",    "23", Icons.Default.PeopleAlt,  SecurityGreen, Modifier.weight(1f))
            }
        }

        item {
            SectionHeader("Ocupación por bloque")
            GuardianCard {
                val blocks = listOf("A" to 0.9f, "B" to 0.75f, "C" to 0.6f, "D" to 0.85f)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    blocks.forEach { (bloque, pct) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Bloque $bloque", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(pct)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(AdminPurple)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("${(pct * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Alertas del sistema")
            listOf(
                Triple("Mantenimiento preventivo – Bloque A", StatusWarning, "Programado"),
                Triple("Renovación de contrato de vigilancia", StatusInfo, "30 días"),
                Triple("Licencia de operación vence en 60 días", StatusDanger, "Urgente"),
            ).forEach { (msg, color, tag) ->
                Spacer(Modifier.height(6.dp))
                GuardianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, null, tint = color, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(msg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        StatusChip(tag, color)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminUsersScreen() {
    val users = remember {
        listOf(
            ManagedUser("Carlos García",   UserRole.RESIDENT, "Apto 4B", true),
            ManagedUser("María López",     UserRole.CLEANING, "Turno M", true),
            ManagedUser("Roberto Pérez",   UserRole.SECURITY, "Puesto 1", true),
            ManagedUser("Laura Suárez",    UserRole.RESIDENT, "Apto 2A", true),
            ManagedUser("Diego Herrera",   UserRole.RESIDENT, "Apto 7C", false),
            ManagedUser("Patricia Vega",   UserRole.CLEANING, "Turno T", true),
            ManagedUser("Andrés Montoya",  UserRole.SECURITY, "Puesto 2", true),
        )
    }
    var searchQuery by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf<UserRole?>(null) }

    val roleFilters = listOf(null to "Todos", UserRole.RESIDENT to "Residentes", UserRole.SECURITY to "Seguridad", UserRole.CLEANING to "Limpieza")

    val filtered = users.filter {
        (roleFilter == null || it.role == roleFilter) &&
                (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true))
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Gestión de Usuarios", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar usuario…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                roleFilters.forEach { (role, label) ->
                    FilterChip(
                        selected = roleFilter == role,
                        onClick = { roleFilter = role },
                        label = { Text(label, fontSize = 11.sp) }
                    )
                }
            }
        }

        items(filtered) { u ->
            GuardianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val roleColor = when (u.role) {
                        UserRole.RESIDENT -> ResidentBlue
                        UserRole.SECURITY -> SecurityGreen
                        UserRole.ADMIN    -> AdminPurple
                        UserRole.CLEANING -> CleaningOrange
                    }
                    AvatarCircle(
                        initials = u.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
                        color = roleColor
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(u.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(u.unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val roleName = when (u.role) {
                            UserRole.RESIDENT -> "Residente"
                            UserRole.SECURITY -> "Seguridad"
                            UserRole.ADMIN    -> "Admin"
                            UserRole.CLEANING -> "Limpieza"
                        }
                        RoleBadge(roleName, roleColor)
                        StatusChip(if (u.active) "Activo" else "Inactivo", if (u.active) StatusSuccess else TextDisabled)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminReportsScreen() {
    val reports = remember {
        listOf(
            AdminReport("045", "Filtración en techo Bloque B",      "Carlos García",  "Pendiente",  "15/04"),
            AdminReport("044", "Daño en ascensor",                   "Laura Suárez",   "En proceso", "14/04"),
            AdminReport("043", "Robo de correspondencia",            "Diego Herrera",  "Pendiente",  "12/04"),
            AdminReport("042", "Iluminación pasillo roto – P4",      "Carlos García",  "Resuelto",   "10/04"),
            AdminReport("041", "Ruido nocturno recurrente",          "Patricia Vega",  "Resuelto",   "08/04"),
        )
    }
    var filter by remember { mutableStateOf("Todos") }
    val filters = listOf("Todos", "Pendiente", "En proceso", "Resuelto")
    val filtered = if (filter == "Todos") reports else reports.filter { it.status == filter }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Todos los Reportes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
                }
            }
        }
        items(filtered) { r ->
            GuardianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("#${r.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val sc = when (r.status) { "Resuelto" -> StatusSuccess; "En proceso" -> StatusInfo; else -> StatusWarning }
                            StatusChip(r.status, sc)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(r.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Por: ${r.reporter} · ${r.date}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
