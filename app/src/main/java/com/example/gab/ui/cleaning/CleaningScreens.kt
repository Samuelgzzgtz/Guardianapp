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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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
            composable(Routes.CLEANING_HOME)  { CleaningHomeScreen(user, navController, vm) }
            composable(Routes.CLEANING_TASKS) { CleaningTasksScreen(user.id, vm) }
        }
    }
}

@Composable
fun CleaningHomeScreen(user: AppUser, navController: androidx.navigation.NavController, vm: CleaningViewModel) {
    val tareas    by vm.tareas.collectAsStateWithLifecycle()
    val areas     by vm.areas.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

    val totalTasks = tareas.size
    val doneTasks  = tareas.count { it.estatus == "completada" }
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
                val enProceso = tareas.count { it.estatus == "en_proceso" }
                StatCard("En proceso",   "$enProceso",                   Icons.Default.Pending,    StatusInfo,    Modifier.weight(1f))
                StatCard("Completadas",  "$doneTasks",                   Icons.Default.CheckCircle, StatusSuccess, Modifier.weight(1f))
            }
        }

        if (areas.isNotEmpty()) {
            item { SectionHeader("Zonas asignadas") }
            items(areas) { area ->
                val nextEstatus = when (area.estatus) {
                    "pendiente" -> "en_curso"
                    "en_curso"  -> "listo"
                    else        -> "pendiente"
                }
                val (statusLabel, statusColor) = when (area.estatus) {
                    "listo"    -> "Hecho"      to StatusSuccess
                    "en_curso" -> "En proceso" to StatusInfo
                    else       -> "Pendiente"  to StatusWarning
                }
                GuardianCard(onClick = { vm.actualizarArea(area.id, nextEstatus) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CleaningServices, null, tint = CleaningOrange, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(area.nombre, style = MaterialTheme.typography.bodyMedium)
                            area.sector?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text("Toca para cambiar estado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            StatusChip(statusLabel, statusColor)
                            TextButton(
                                onClick = {
                                    vm.setFiltroArea(area.nombre)
                                    navController.navigate(Routes.CLEANING_TASKS)
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                            ) {
                                Icon(Icons.Default.FilterList, null, modifier = Modifier.size(14.dp))
                                Text("Ver tareas", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    "Sin zonas asignadas",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun CleaningTasksScreen(userId: Int, vm: CleaningViewModel) {
    val tareas     by vm.tareas.collectAsStateWithLifecycle()
    val unidades   by vm.unidades.collectAsStateWithLifecycle()
    val isLoading  by vm.isLoading.collectAsStateWithLifecycle()
    val filtroArea by vm.filtroArea.collectAsStateWithLifecycle()

    var filterEstatus by rememberSaveable { mutableStateOf<String?>(null) }

    val filtered = when {
        filterEstatus != null -> tareas.filter { it.estatus == filterEstatus }
        filtroArea    != null -> tareas.filter { it.area == filtroArea }
        else                  -> tareas
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
                FilterChip(selected = filterEstatus == null,         onClick = { filterEstatus = null          }, label = { Text("Todas") })
                FilterChip(selected = filterEstatus == "pendiente",  onClick = { filterEstatus = "pendiente"  }, label = { Text("Pendientes") })
                FilterChip(selected = filterEstatus == "en_proceso", onClick = { filterEstatus = "en_proceso" }, label = { Text("En proceso") })
                FilterChip(selected = filterEstatus == "completada", onClick = { filterEstatus = "completada" }, label = { Text("Hechas") })
            }
            if (filtroArea != null) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = CleaningOrange.copy(alpha = 0.10f)),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FilterList, null, modifier = Modifier.size(16.dp), tint = CleaningOrange)
                        Spacer(Modifier.width(6.dp))
                        Text("Zona: $filtroArea", style = MaterialTheme.typography.labelMedium, color = CleaningOrange, modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { vm.setFiltroArea(null) },
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                            Text("Quitar", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
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
            val isCompletada = tarea.estatus == "completada"
            val ubicacion = tarea.fkUnidad?.let { unidades[it]?.displayUbicacion() }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    tarea.titulo,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = if (isCompletada) TextDecoration.LineThrough else null,
                                    color = if (isCompletada) MaterialTheme.colorScheme.onSurfaceVariant
                                            else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val priorityColor = when (tarea.prioridad) {
                                    "alta"   -> StatusDanger
                                    "normal" -> StatusWarning
                                    else     -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                                StatusChip(tarea.prioridad, priorityColor)
                            }
                            tarea.area?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            ubicacion?.let {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.LocationOn, null, modifier = Modifier.size(12.dp), tint = CleaningOrange)
                                    Text(it, style = MaterialTheme.typography.labelSmall, color = CleaningOrange)
                                }
                            }
                            tarea.horarioSlot?.let {
                                Text("Hora: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        EstatusCycleButton(
                            estatus = tarea.estatus,
                            onNext = { nextEstatus ->
                                tarea.id?.let { vm.setEstatus(it, nextEstatus, userId) }
                            }
                        )
                    }
                    tarea.notas?.let { notas ->
                        if (notas.isNotBlank()) {
                            HorizontalDivider()
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.AutoMirrored.Filled.Notes, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    notas,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EstatusCycleButton(estatus: String, onNext: (String) -> Unit) {
    when (estatus) {
        "completada" -> {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = StatusSuccess.copy(alpha = 0.12f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(14.dp), tint = StatusSuccess)
                    Text("Hecho", style = MaterialTheme.typography.labelSmall, color = StatusSuccess)
                }
            }
        }
        else -> {
            val (label, color) = when (estatus) {
                "en_proceso" -> "En proceso" to StatusInfo
                else         -> "Pendiente"  to StatusWarning
            }
            val next = when (estatus) {
                "pendiente"  -> "en_proceso"
                else         -> "completada"
            }
            AssistChip(
                onClick = { onNext(next) },
                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = color.copy(alpha = 0.12f),
                    labelColor = color
                )
            )
        }
    }
}
