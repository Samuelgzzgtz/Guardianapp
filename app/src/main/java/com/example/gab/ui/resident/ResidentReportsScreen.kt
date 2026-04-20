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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.theme.*

private data class Report(
    val id: String,
    val title: String,
    val description: String,
    val date: String,
    val status: String
)

@Composable
fun ResidentReportsScreen(user: AppUser, navController: NavController) {
    val allReports = remember {
        listOf(
            Report("045", "Filtración en techo",       "Hay una gotera en el baño principal.",          "15/04/2024", "Pendiente"),
            Report("038", "Ruido nocturno",             "Vecinos con música alta después de medianoche.","10/04/2024", "En proceso"),
            Report("031", "Iluminación pasillo roto",  "La luz del pasillo del piso 4 no funciona.",    "02/04/2024", "Resuelto"),
            Report("024", "Daño en jardín comunitario","Zona de plantas dañada por obras.",              "20/03/2024", "Resuelto"),
        )
    }

    var selectedFilter by remember { mutableStateOf("Todos") }
    var showNewDialog by remember { mutableStateOf(false) }
    val filters = listOf("Todos", "Pendiente", "En proceso", "Resuelto")

    val filtered = if (selectedFilter == "Todos") allReports
    else allReports.filter { it.status == selectedFilter }

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
                        FilterChip(
                            selected = selectedFilter == f,
                            onClick = { selectedFilter = f },
                            label = { Text(f) }
                        )
                    }
                }
            }

            items(filtered) { report ->
                GuardianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("#${report.id}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val statusColor = when (report.status) {
                                    "Resuelto"   -> StatusSuccess
                                    "En proceso" -> StatusInfo
                                    else         -> StatusWarning
                                }
                                StatusChip(report.status, statusColor)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(report.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(report.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                            Text(report.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showNewDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = ResidentBlue
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nuevo reporte", tint = androidx.compose.ui.graphics.Color.White)
        }
    }

    if (showNewDialog) {
        NewReportDialog(onDismiss = { showNewDialog = false })
    }
}

@Composable
private fun NewReportDialog(onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Mantenimiento") }
    val categories = listOf("Mantenimiento", "Seguridad", "Ruido", "Limpieza", "Otro")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo Reporte") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = {},
                        readOnly = true,
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
            Button(onClick = onDismiss) { Text("Enviar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
