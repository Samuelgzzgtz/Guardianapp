@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.gab.ui.resident

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.gab.data.model.Reporte
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.resident.viewmodel.ResidentViewModel
import com.example.gab.ui.theme.*

@Composable
fun ResidentReportsScreen(user: AppUser, navController: NavController, vm: ResidentViewModel) {
    val reportes  by vm.reportes.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

    var selectedFilter  by rememberSaveable { mutableStateOf("Todos") }
    var showNewDialog   by remember { mutableStateOf(false) }
    var reportToClose   by remember { mutableStateOf<Reporte?>(null) }
    val filters = listOf("Todos", "Pendiente", "En proceso", "Resuelto")

    val filtered = if (selectedFilter == "Todos") reportes
    else reportes.filter { it.estatus == selectedFilter }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Mis Reportes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    filters.forEach { f ->
                        FilterChip(selected = selectedFilter == f, onClick = { selectedFilter = f }, label = { Text(f) })
                    }
                }
                if (isLoading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (reportes.isEmpty() && !isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No tienes reportes aún", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(filtered) { report ->
                val statusColor = when (report.estatus) {
                    "Resuelto"   -> StatusSuccess
                    "En proceso" -> StatusInfo
                    else         -> StatusWarning
                }
                GuardianCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("#${report.id ?: "—"}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    StatusChip(report.estatus, statusColor)
                                    if (report.esUrgente) StatusChip("Urgente", StatusDanger)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(report.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                report.descripcion?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                                }
                                Text(report.fechaCreacion ?: "—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (report.estatus != "Resuelto") {
                            OutlinedButton(
                                onClick = { reportToClose = report },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusSuccess)
                            ) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Marcar como resuelto")
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showNewDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = ResidentBlue
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nuevo reporte", tint = Color.White)
        }
    }

    if (showNewDialog) {
        NewReportDialog(
            onDismiss = { showNewDialog = false },
            onSubmit  = { titulo, desc, categoria ->
                vm.submitReporte(user.id, categoria, titulo, desc)
                showNewDialog = false
            }
        )
    }

    reportToClose?.let { r ->
        AlertDialog(
            onDismissRequest = { reportToClose = null },
            title = { Text("Marcar como resuelto") },
            text  = { Text("¿Confirmas que el reporte \"${r.titulo}\" ya fue resuelto?") },
            confirmButton = {
                Button(
                    onClick = { r.id?.let { vm.cerrarReporte(user.id, it) }; reportToClose = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = StatusSuccess)
                ) { Text("Confirmar") }
            },
            dismissButton = { TextButton(onClick = { reportToClose = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun NewReportDialog(
    onDismiss: () -> Unit,
    onSubmit: (titulo: String, desc: String, categoria: String) -> Unit
) {
    var title       by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category    by remember { mutableStateOf("Mantenimiento") }
    val categories = listOf("Mantenimiento", "Seguridad", "Ruido", "Limpieza", "Otro")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Reporte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = category, onValueChange = {}, readOnly = true,
                        label = { Text("Categoría") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { cat ->
                            DropdownMenuItem(text = { Text(cat) }, onClick = { category = cat; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { if (title.isNotBlank()) onSubmit(title, description, category) }, enabled = title.isNotBlank()) { Text("Enviar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
