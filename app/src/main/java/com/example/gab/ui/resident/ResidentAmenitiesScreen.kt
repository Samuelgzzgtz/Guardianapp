@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.gab.ui.resident

import androidx.compose.foundation.BorderStroke
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gab.data.model.Amenidad
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.resident.viewmodel.ResidentViewModel
import com.example.gab.ui.theme.*

@Composable
fun ResidentAmenitiesScreen(user: AppUser, vm: ResidentViewModel) {
    val amenidades   by vm.amenidades.collectAsStateWithLifecycle()
    val reservas     by vm.reservas.collectAsStateWithLifecycle()
    val slotsTomados by vm.slotsTomados.collectAsStateWithLifecycle()
    val loadingSlots by vm.loadingSlots.collectAsStateWithLifecycle()
    val isLoading    by vm.isLoading.collectAsStateWithLifecycle()

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
            amenidad     = amenidad,
            slotsTomados = slotsTomados,
            loadingSlots = loadingSlots,
            onFechaChange = { fecha -> vm.cargarSlotsTomados(amenidad.id, fecha) },
            onDismiss = { selectedAmenidad = null; vm.limpiarSlotsTomados() },
            onSubmit = { fecha, slot ->
                vm.crearReserva(user.id, amenidad.id, fecha, slot)
                selectedAmenidad = null
                vm.limpiarSlotsTomados()
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
    slotsTomados: List<String>,
    loadingSlots: Boolean,
    onFechaChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (fecha: String, slot: String) -> Unit
) {
    var fecha by remember { mutableStateOf("") }
    var slot  by remember { mutableStateOf("08:00 - 10:00") }
    val allSlots = listOf(
        "08:00 - 10:00", "10:00 - 12:00", "12:00 - 14:00",
        "14:00 - 16:00", "16:00 - 18:00", "18:00 - 20:00"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reservar ${amenidad.nombre}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fecha,
                    onValueChange = {
                        fecha = it
                        if (it.length == 10) onFechaChange(it)
                    },
                    label = { Text("Fecha (YYYY-MM-DD)") },
                    placeholder = { Text("2026-05-01") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (loadingSlots) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (fecha.length == 10) {
                    Text("Selecciona horario:", style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        allSlots.forEach { s ->
                            val ocupado    = s in slotsTomados
                            val isSelected = slot == s
                            Surface(
                                onClick = { if (!ocupado) slot = s },
                                color = when {
                                    ocupado    -> MaterialTheme.colorScheme.surfaceVariant
                                    isSelected -> ResidentBlue.copy(alpha = 0.12f)
                                    else       -> MaterialTheme.colorScheme.surface
                                },
                                shape  = MaterialTheme.shapes.small,
                                border = if (isSelected) BorderStroke(1.dp, ResidentBlue) else null
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        s,
                                        modifier = Modifier.weight(1f),
                                        color = if (ocupado) MaterialTheme.colorScheme.onSurfaceVariant
                                                else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (ocupado) {
                                        Text("Ocupado", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, null, tint = ResidentBlue, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (fecha.isNotBlank() && slot !in slotsTomados) onSubmit(fecha, slot) },
                enabled  = fecha.length == 10 && slot !in slotsTomados,
                colors   = ButtonDefaults.buttonColors(containerColor = ResidentBlue)
            ) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
