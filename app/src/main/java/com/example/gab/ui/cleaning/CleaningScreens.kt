@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.gab.ui.cleaning

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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.navigation.Routes
import com.example.gab.ui.theme.*

private data class CleaningTask(
    val id: String,
    val area: String,
    val description: String,
    val priority: String,
    val time: String,
    var done: Boolean = false
)

@Composable
fun CleaningShell(user: AppUser, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val navItems = listOf(
        NavItem("Inicio",  Icons.Default.Home,             Routes.CLEANING_HOME),
        NavItem("Tareas",  Icons.Default.CleaningServices, Routes.CLEANING_TASKS),
    )
    AppShell(
        navController = navController,
        navItems = navItems,
        topBarTitle = "Limpieza",
        topBarActions = {
            IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }
        }
    ) {
        NavHost(navController = navController, startDestination = Routes.CLEANING_HOME) {
            composable(Routes.CLEANING_HOME)  { CleaningHomeScreen(user) }
            composable(Routes.CLEANING_TASKS) { CleaningTasksScreen() }
        }
    }
}

@Composable
fun CleaningHomeScreen(user: AppUser) {
    val allTasks = 8
    val doneTasks = 3
    val progress = doneTasks.toFloat() / allTasks

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Hola, ${user.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Turno mañana · Zona A–D", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item {
            GuardianCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Progreso del día", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("$doneTasks de $allTasks tareas completadas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = CleaningOrange)
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                        color = CleaningOrange,
                        trackColor = CleaningOrange.copy(alpha = 0.15f)
                    )
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Pendientes", "${allTasks - doneTasks}", Icons.Default.Pending,    StatusWarning,  Modifier.weight(1f))
                StatCard("Completadas", "$doneTasks",             Icons.Default.CheckCircle, StatusSuccess, Modifier.weight(1f))
            }
        }

        item { SectionHeader("Zonas asignadas hoy") }
        items(
            listOf(
                Triple("Vestíbulo principal",  "Hecho",      StatusSuccess),
                Triple("Escaleras Bloque A-B", "En proceso", StatusInfo),
                Triple("Parqueadero",           "Pendiente",  StatusWarning),
                Triple("Zona de reciclaje",     "Pendiente",  StatusWarning),
            )
        ) { (zona, status, color) ->
            GuardianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CleaningServices, null, tint = CleaningOrange, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(zona, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    StatusChip(status, color)
                }
            }
        }
    }
}

@Composable
fun CleaningTasksScreen() {
    val tasks = remember {
        mutableStateListOf(
            CleaningTask("T01", "Vestíbulo",   "Barrer y trapear entrada principal",    "Alta",  "07:00", done = true),
            CleaningTask("T02", "Escaleras A", "Limpieza completa escaleras Bloque A",  "Alta",  "07:30", done = true),
            CleaningTask("T03", "Ascensor",    "Desinfectar paredes y suelo ascensor",  "Media", "08:00", done = true),
            CleaningTask("T04", "Escaleras B", "Limpieza completa escaleras Bloque B",  "Alta",  "08:30"),
            CleaningTask("T05", "Parqueadero", "Barrida zona de parqueo",               "Media", "09:30"),
            CleaningTask("T06", "Zona verde",  "Recogida de basura zona jardines",      "Baja",  "10:30"),
            CleaningTask("T07", "Reciclaje",   "Organizar puntos de reciclaje",         "Media", "11:00"),
            CleaningTask("T08", "Piscina",     "Limpieza área piscina y vestidores",    "Baja",  "12:00"),
        )
    }
    var filterDone by remember { mutableStateOf<Boolean?>(null) }

    val filtered = when (filterDone) {
        true  -> tasks.filter { it.done }
        false -> tasks.filter { !it.done }
        null  -> tasks.toList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Lista de Tareas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = filterDone == null,  onClick = { filterDone = null  }, label = { Text("Todas") })
                FilterChip(selected = filterDone == false, onClick = { filterDone = false }, label = { Text("Pendientes") })
                FilterChip(selected = filterDone == true,  onClick = { filterDone = true  }, label = { Text("Hechas") })
            }
        }

        items(filtered, key = { it.id }) { task ->
            val idx = tasks.indexOfFirst { it.id == task.id }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = task.done,
                        onCheckedChange = { if (idx >= 0) tasks[idx] = tasks[idx].copy(done = it) },
                        colors = CheckboxDefaults.colors(checkedColor = CleaningOrange)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        val priorityColor = when (task.priority) { "Alta" -> StatusDanger; "Media" -> StatusWarning; else -> StatusInfo }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                task.area,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                                color = if (task.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            StatusChip(task.priority, priorityColor)
                        }
                        Text(
                            task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = if (task.done) TextDecoration.LineThrough else null
                        )
                        Text("Hora: ${task.time}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
