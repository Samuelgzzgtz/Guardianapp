@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.gab.ui.cleaning

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gab.ui.cleaning.viewmodel.CleaningViewModel
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.navigation.Routes
import com.example.gab.ui.theme.*

@Composable
fun CleaningShell(user: AppUser, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val vm: CleaningViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(user.id) { vm.loadAll(user.id) }

    val toastMsg by vm.toastMessage.collectAsStateWithLifecycle()
    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearToast()
        }
    }

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
            composable(Routes.CLEANING_HOME)  { CleaningHomeScreen(user, vm) }
            composable(Routes.CLEANING_TASKS) { CleaningTasksScreen(vm) }
        }
    }
}

@Composable
fun CleaningHomeScreen(user: AppUser, vm: CleaningViewModel) {
    val tareas    by vm.tareas.collectAsStateWithLifecycle()
    val areas     by vm.areas.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

    val totalTasks = tareas.size
    val doneTasks  = tareas.count { it.estaCompletada }
    val progress   = if (totalTasks > 0) doneTasks.toFloat() / totalTasks else 0f

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Hola, ${user.name.ifBlank { "Equipo" }}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Turno activo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            GuardianCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Progreso del día", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("$doneTasks de $totalTasks tareas completadas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                StatCard("Pendientes",  "${totalTasks - doneTasks}", Icons.Default.Pending,     StatusWarning, Modifier.weight(1f))
                StatCard("Completadas", "$doneTasks",                Icons.Default.CheckCircle,  StatusSuccess, Modifier.weight(1f))
            }
        }

        if (areas.isNotEmpty()) {
            item { SectionHeader("Zonas asignadas") }
            items(areas) { area ->
                val (statusLabel, statusColor) = when (area.estatus) {
                    "listo"    -> "Hecho"      to StatusSuccess
                    "en_curso" -> "En proceso" to StatusInfo
                    else       -> "Pendiente"  to StatusWarning
                }
                GuardianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CleaningServices, null, tint = CleaningOrange, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(area.nombre, style = MaterialTheme.typography.bodyMedium)
                            area.sector?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        StatusChip(statusLabel, statusColor)
                    }
                }
            }
        }
    }
}

@Composable
fun CleaningTasksScreen(vm: CleaningViewModel) {
    val tareas    by vm.tareas.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

    var filterDone by remember { mutableStateOf<Boolean?>(null) }

    val filtered = when (filterDone) {
        true  -> tareas.filter { it.estaCompletada }
        false -> tareas.filter { !it.estaCompletada }
        null  -> tareas
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
            if (isLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        if (tareas.isEmpty() && !isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No hay tareas asignadas para hoy", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(filtered, key = { it.id ?: it.titulo }) { tarea ->
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
                        checked = tarea.estaCompletada,
                        onCheckedChange = { checked ->
                            tarea.id?.let { vm.toggleTarea(it, checked) }
                        },
                        colors = CheckboxDefaults.colors(checkedColor = CleaningOrange)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        val priorityColor = when (tarea.prioridad) { "alta" -> StatusDanger; else -> StatusWarning }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                tarea.titulo,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                textDecoration = if (tarea.estaCompletada) TextDecoration.LineThrough else null,
                                color = if (tarea.estaCompletada) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                            StatusChip(tarea.prioridad, priorityColor)
                        }
                        tarea.area?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = if (tarea.estaCompletada) TextDecoration.LineThrough else null
                            )
                        }
                        tarea.horarioSlot?.let {
                            Text("Hora: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
