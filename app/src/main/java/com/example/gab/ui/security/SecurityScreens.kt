@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.gab.ui.security

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.navigation.Routes
import com.example.gab.ui.theme.*

private data class Visitor(val name: String, val apt: String, val time: String, val status: String)
private data class Incident(val id: String, val title: String, val location: String, val priority: String, val time: String)

@Composable
fun SecurityShell(user: AppUser, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val navItems = listOf(
        NavItem("Inicio",      Icons.Default.Home,     Routes.SECURITY_HOME),
        NavItem("Visitantes",  Icons.Default.PeopleAlt, Routes.SECURITY_VISITORS),
        NavItem("Incidentes",  Icons.Default.Warning,  Routes.SECURITY_INCIDENTS),
    )
    AppShell(
        navController = navController,
        navItems = navItems,
        topBarTitle = "Seguridad",
        topBarActions = {
            IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }
        }
    ) {
        NavHost(navController = navController, startDestination = Routes.SECURITY_HOME) {
            composable(Routes.SECURITY_HOME)      { SecurityHomeScreen(user) }
            composable(Routes.SECURITY_VISITORS)  { SecurityVisitorsScreen() }
            composable(Routes.SECURITY_INCIDENTS) { SecurityIncidentsScreen() }
        }
    }
}

@Composable
fun SecurityHomeScreen(user: AppUser) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Bienvenido, ${user.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Turno activo · Puesto principal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            GuardianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(12.dp).also {
                            androidx.compose.foundation.layout.Box(it) {}
                        }
                    )
                    Icon(Icons.Default.Circle, null, tint = StatusSuccess, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Estado del turno", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("ACTIVO – desde las 06:00", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = StatusSuccess)
                    }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)) {
                        Text("Fin turno")
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Visitantes hoy", "12", Icons.Default.PeopleAlt, SecurityGreen, Modifier.weight(1f))
                StatCard("Incidentes", "2",  Icons.Default.Warning,   StatusWarning,  Modifier.weight(1f))
            }
        }

        item {
            SectionHeader("Acciones rápidas")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionButton(Icons.Default.PersonAdd, "Registrar visita", SecurityGreen, {}, Modifier.weight(1f))
                QuickActionButton(Icons.Default.Report,    "Incidente",        StatusDanger,  {}, Modifier.weight(1f))
                QuickActionButton(Icons.AutoMirrored.Filled.DirectionsWalk, "Ronda",       StatusInfo,    {}, Modifier.weight(1f))
            }
        }

        item { SectionHeader("Últimos eventos") }
        items(
            listOf(
                Triple("Visitante autorizado: Luis Mora → Apto 2A", "09:45", "visitor"),
                Triple("Ronda completada – Sector Norte",           "08:30", "patrol"),
                Triple("Incidente: Persona sospechosa Bloque C",    "07:15", "incident"),
            )
        ) { (msg, time, type) ->
            GuardianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val (icon, color) = when (type) {
                        "visitor"  -> Icons.Default.PeopleAlt to SecurityGreen
                        "patrol"   -> Icons.AutoMirrored.Filled.DirectionsWalk to StatusInfo
                        else       -> Icons.Default.Warning to StatusDanger
                    }
                    Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(msg, style = MaterialTheme.typography.bodyMedium)
                        Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityVisitorsScreen() {
    val visitors = remember {
        listOf(
            Visitor("Luis Mora",     "2A", "09:45", "Dentro"),
            Visitor("Sara Gómez",    "5C", "09:20", "Salió"),
            Visitor("Pedro Ríos",    "1B", "08:55", "Dentro"),
            Visitor("Ana Castillo",  "3D", "08:30", "Salió"),
            Visitor("Miguel Torres", "4A", "07:50", "Salió"),
        )
    }
    var showRegisterDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Registro de Visitantes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            items(visitors) { visitor ->
                GuardianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarCircle(
                            initials = visitor.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
                            color = if (visitor.status == "Dentro") SecurityGreen else TextDisabled
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(visitor.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Apto ${visitor.apt} · Entrada: ${visitor.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusChip(visitor.status, if (visitor.status == "Dentro") SecurityGreen else TextDisabled)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showRegisterDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = SecurityGreen
        ) {
            Icon(Icons.Default.PersonAdd, null, tint = Color.White)
        }
    }

    if (showRegisterDialog) {
        RegisterVisitorDialog(onDismiss = { showRegisterDialog = false })
    }
}

@Composable
private fun RegisterVisitorDialog(onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var apt by remember { mutableStateOf("") }
    var doc by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar visitante") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre completo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = doc,  onValueChange = { doc = it },  label = { Text("Documento de identidad") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = apt,  onValueChange = { apt = it },  label = { Text("Apartamento a visitar") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = SecurityGreen)) { Text("Registrar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun SecurityIncidentsScreen() {
    val incidents = remember {
        listOf(
            Incident("INC-012", "Persona sospechosa en Bloque C", "Bloque C – P2", "Alta",  "07:15"),
            Incident("INC-011", "Daño a vehículo en parqueadero",  "Parqueadero B", "Media", "Ayer 22:40"),
            Incident("INC-010", "Ruido excesivo vecinos",          "Bloque A – P3", "Baja",  "Ayer 23:10"),
        )
    }
    var showNewIncident by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item { Text("Incidentes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
            items(incidents) { inc ->
                GuardianCard {
                    Row(verticalAlignment = Alignment.Top) {
                        val priorityColor = when (inc.priority) {
                            "Alta"  -> StatusDanger
                            "Media" -> StatusWarning
                            else    -> StatusInfo
                        }
                        Icon(Icons.Default.Warning, null, tint = priorityColor, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(inc.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                StatusChip(inc.priority, priorityColor)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(inc.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("${inc.location} · ${inc.time}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showNewIncident = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = StatusDanger
        ) {
            Icon(Icons.Default.Add, null, tint = Color.White)
        }
    }

    if (showNewIncident) {
        NewIncidentDialog(onDismiss = { showNewIncident = false })
    }
}

@Composable
private fun NewIncidentDialog(onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Media") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reportar incidente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title,    onValueChange = { title = it },    label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Ubicación") },   modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Baja", "Media", "Alta").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (p) { "Alta" -> StatusDanger; "Media" -> StatusWarning; else -> StatusInfo },
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)) { Text("Reportar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
