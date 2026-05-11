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
    val amenidades    by vm.amenidades.collectAsStateWithLifecycle()
    val reservas      by vm.reservas.collectAsStateWithLifecycle()
    val slotsTomados  by vm.slotsTomados.collectAsStateWithLifecycle()
    val conteoPorSlot by vm.conteoPorSlot.collectAsStateWithLifecycle()
    val loadingSlots  by vm.loadingSlots.collectAsStateWithLifecycle()
    val isLoading     by vm.isLoading.collectAsStateWithLifecycle()

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
                            Text("Fecha: ${reserva.fecha ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            reserva.slot?.let {
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
            amenidad      = amenidad,
            slotsTomados  = slotsTomados,
            conteoPorSlot = conteoPorSlot,
            loadingSlots  = loadingSlots,
            onFechaChange = { fecha -> vm.cargarSlotsTomados(amenidad, fecha) },
            onDismiss     = { selectedAmenidad = null; vm.limpiarSlotsTomados() },
            onSubmit      = { fecha, slot ->
                vm.crearReserva(user.id, amenidad, fecha, slot)
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
    conteoPorSlot: Map<String, Int>,
    loadingSlots: Boolean,
    onFechaChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (fecha: String, slot: String) -> Unit
) {
    var fecha by remember { mutableStateOf("") }
    var slot  by remember { mutableStateOf("08:00 - 10:00") }
    var sinDisponibilidad by remember { mutableStateOf(false) }
    val today = java.time.LocalDate.now().toString()
    val allSlots = listOf(
        "08:00 - 10:00", "10:00 - 12:00", "12:00 - 14:00",
        "14:00 - 16:00", "16:00 - 18:00", "18:00 - 20:00"
    )

    // When loaded slots change, auto-select first free slot to avoid silent disabled button
    LaunchedEffect(slotsTomados) {
        if (fecha.length < 10) return@LaunchedEffect
        val primerLibre = allSlots.firstOrNull { it !in slotsTomados }
        when {
            primerLibre == null  -> sinDisponibilidad = true
            slot in slotsTomados -> { slot = primerLibre; sinDisponibilidad = false }
            else                 -> sinDisponibilidad = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reservar ${amenidad.nombre}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fecha,
                    onValueChange = {
                        if (it.length == 10 && it >= today) {
                            fecha = it
                            sinDisponibilidad = false
                            onFechaChange(it)
                        } else if (it.length < 10) {
                            fecha = it
                            sinDisponibilidad = false
                        }
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
                    if (sinDisponibilidad) {
                        Text(
                            "Sin disponibilidad para esta fecha",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        Text("Selecciona horario:", style = MaterialTheme.typography.labelMedium)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            allSlots.forEach { s ->
                                val lleno      = s in slotsTomados
                                val isSelected = slot == s
                                val conteo     = conteoPorSlot[s] ?: 0
                                val cuposLibres = (amenidad.capacidad - conteo).coerceAtLeast(0)
                                Surface(
                                    onClick = { if (!lleno) slot = s },
                                    color = when {
                                        lleno      -> MaterialTheme.colorScheme.surfaceVariant
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
                                            color = if (lleno) MaterialTheme.colorScheme.onSurfaceVariant
                                                    else MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        when {
                                            lleno -> Text(
                                                if (amenidad.permiteConcurrencia) "Lleno" else "Ocupado",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            amenidad.permiteConcurrencia -> Text(
                                                "$cuposLibres/${amenidad.capacidad} cupos",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = SecurityGreen
                                            )
                                            isSelected -> Icon(
                                                Icons.Default.CheckCircle, null,
                                                tint = ResidentBlue, modifier = Modifier.size(16.dp)
                                            )
                                        }
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
                onClick  = { if (fecha.isNotBlank() && !sinDisponibilidad && slot !in slotsTomados) onSubmit(fecha, slot) },
                enabled  = fecha.length == 10 && fecha >= today && !sinDisponibilidad && slot !in slotsTomados,
                colors   = ButtonDefaults.buttonColors(containerColor = ResidentBlue)
            ) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
