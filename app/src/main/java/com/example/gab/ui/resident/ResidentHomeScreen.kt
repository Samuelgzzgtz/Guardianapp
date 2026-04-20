@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.gab.ui.resident

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.navigation.Routes
import com.example.gab.ui.theme.*

private data class ActivityItem(val title: String, val time: String, val type: String)

@Composable
fun ResidentShell(user: AppUser, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val navItems = listOf(
        NavItem("Inicio",    Icons.Default.Home,       Routes.RESIDENT_HOME),
        NavItem("Reportes",  Icons.AutoMirrored.Filled.Assignment, Routes.RESIDENT_REPORTS),
        NavItem("Perfil",    Icons.Default.Person,     Routes.RESIDENT_PROFILE),
    )

    AppShell(
        navController = navController,
        navItems = navItems,
        topBarTitle = "GuardianApp",
        topBarActions = {
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Salir")
            }
        }
    ) {
        NavHost(navController = navController, startDestination = Routes.RESIDENT_HOME) {
            composable(Routes.RESIDENT_HOME)    { ResidentHomeScreen(user, navController) }
            composable(Routes.RESIDENT_REPORTS) { ResidentReportsScreen(user, navController) }
            composable(Routes.RESIDENT_PROFILE) { ResidentProfileScreen(user, onLogout) }
        }
    }
}

@Composable
fun ResidentHomeScreen(user: AppUser, navController: NavController) {
    val recentActivity = remember {
        listOf(
            ActivityItem("Visitante registrado: Juan Pérez", "Hace 20 min", "visitor"),
            ActivityItem("Reporte #045 resuelto", "Hace 1h", "resolved"),
            ActivityItem("Mantenimiento programado Bloque A", "Hace 3h", "info"),
            ActivityItem("Alerta de ruido – Piso 3", "Ayer", "alert"),
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Column {
                Text("Buen día,", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(user.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (user.apartment.isNotBlank()) {
                    Text(user.apartment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    label = "Visitantes hoy",
                    value = "3",
                    icon = Icons.Default.PeopleAlt,
                    tint = ResidentBlue,
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    label = "Reportes abiertos",
                    value = "1",
                    icon = Icons.AutoMirrored.Filled.Assignment,
                    tint = StatusWarning,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            SectionHeader("Acciones rápidas")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionButton(
                    icon = Icons.Default.Add,
                    label = "Nuevo reporte",
                    tint = ResidentBlue,
                    onClick = { navController.navigate(Routes.RESIDENT_REPORTS) },
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.PersonAdd,
                    label = "Autorizar visita",
                    tint = SecurityGreen,
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
                QuickActionButton(
                    icon = Icons.Default.Notifications,
                    label = "Avisos",
                    tint = StatusWarning,
                    onClick = {},
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item { SectionHeader("Actividad reciente") }

        items(recentActivity) { item ->
            GuardianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, color) = when (item.type) {
                        "visitor"  -> Icons.Default.PeopleAlt to ResidentBlue
                        "resolved" -> Icons.Default.CheckCircle to StatusSuccess
                        "alert"    -> Icons.Default.Warning to StatusDanger
                        else       -> Icons.Default.Info to StatusInfo
                    }
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.bodyMedium)
                        Text(item.time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
