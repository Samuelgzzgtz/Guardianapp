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
import com.example.gab.data.model.Amenidad
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.resident.viewmodel.ResidentViewModel
import com.example.gab.ui.theme.*

@Composable
fun ResidentAmenitiesScreen(user: AppUser, vm: ResidentViewModel) {
    val amenidades by vm.amenidades.collectAsStateWithLifecycle()
    val reservas   by vm.reservas.collectAsStateWithLifecycle()
    val isLoading  by vm.isLoading.collectAsStateWithLifecycle()

    var selectedAmenidad by remember { mutableStateOf<Amenidad?>(null) }
    var reservaToCancel  by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Áreas Comunes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            if (isLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        if (amenidades.isEmpty() && !isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No hay áreas disponibles", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(amenidades) { amenidad ->
            GuardianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Place, null, tint = ResidentBlue, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(amenidad.nombre, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        amenidad.horario?.let {
                            Text("Horario: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Capacidad: ${amenidad.capacidad} personas", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = { selectedAmenidad = amenidad },
                        colors = ButtonDefaults.buttonColors(containerColor = ResidentBlue)
                    ) { Text("Reservar") }
                }
            }
        }

        if (reservas.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader("Mis Reservas")
            }
            items(reservas) { reserva ->
                GuardianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EventAvailable, null, tint = SecurityGreen, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Reserva #${reserva.id ?: "—"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Fecha: ${reserva.fechaReservacion ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            reserva.horarioSlot?.let {
                                Text("Horario: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusChip(reserva.estatus,
                                if (reserva.estatus == "activa") StatusSuccess else StatusWarning)
                        }
                        IconButton(onClick = { reservaToCancel = reserva.id }) {
                            Icon(Icons.Default.Cancel, contentDescription = "Cancelar", tint = StatusDanger)
                        }
                    }
                }
            }
        }
    }

    selectedAmenidad?.let { amenidad ->
        NewReservaDialog(
            amenidad = amenidad,
            onDismiss = { selectedAmenidad = null },
            onSubmit = { fecha, slot ->
                vm.crearReserva(user.id, amenidad.id, fecha, slot)
                selectedAmenidad = null
            }
        )
    }

    reservaToCancel?.let { reservaId ->
        AlertDialog(
            onDismissRequest = { reservaToCancel = null },
            title = { Text("Cancelar reserva") },
            text  = { Text("¿Confirmas que deseas cancelar esta reserva?") },
            confirmButton = {
                Button(
                    onClick = { vm.cancelarReserva(user.id, reservaId); reservaToCancel = null },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)
                ) { Text("Cancelar reserva") }
            },
            dismissButton = { TextButton(onClick = { reservaToCancel = null }) { Text("Volver") } }
        )
    }
}

@Composable
private fun NewReservaDialog(
    amenidad: Amenidad,
    onDismiss: () -> Unit,
    onSubmit: (fecha: String, slot: String) -> Unit
) {
    var fecha by remember { mutableStateOf("") }
    var slot  by remember { mutableStateOf("08:00 - 10:00") }
    val slots = listOf("08:00 - 10:00", "10:00 - 12:00", "12:00 - 14:00", "14:00 - 16:00", "16:00 - 18:00", "18:00 - 20:00")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reservar ${amenidad.nombre}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fecha,
                    onValueChange = { fecha = it },
                    label = { Text("Fecha (YYYY-MM-DD)") },
                    placeholder = { Text("2026-05-01") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = slot, onValueChange = {}, readOnly = true,
                        label = { Text("Horario") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        slots.forEach { s ->
                            DropdownMenuItem(text = { Text(s) }, onClick = { slot = s; expanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (fecha.isNotBlank()) onSubmit(fecha, slot) },
                enabled = fecha.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ResidentBlue)
            ) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
