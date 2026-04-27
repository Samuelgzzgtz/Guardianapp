package com.example.gab.ui.resident

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gab.ui.common.AvatarCircle
import com.example.gab.ui.common.GuardianCard
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.resident.viewmodel.ResidentViewModel
import com.example.gab.data.model.Vehiculo
import com.example.gab.ui.theme.ResidentBlue
import com.example.gab.ui.theme.StatusDanger
import com.example.gab.ui.theme.SecurityGreen
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

@Composable
fun ResidentProfileScreen(user: AppUser, vm: ResidentViewModel, onLogout: () -> Unit) {
    val unidad    by vm.unidad.collectAsStateWithLifecycle()
    val vehiculos by vm.vehiculos.collectAsStateWithLifecycle()
    var notificationsEnabled by remember { mutableStateOf(true) }
    var emailAlerts by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        item {
            // Header card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ResidentBlue)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AvatarCircle(
                        initials = user.name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.3f),
                        size = 72.dp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(user.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White)
                    Text(user.apartment, style = MaterialTheme.typography.bodyMedium, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.height(8.dp))
                    Surface(shape = MaterialTheme.shapes.small, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f)) {
                        Text("Residente activo", color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                    }
                }
            }
        }

        item {
            GuardianCard {
                Text("Información personal", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                InfoRow(Icons.Default.Person, "Nombre completo", user.name)
                InfoRow(Icons.Default.Home, "Ubicación", unidad?.displayUbicacion() ?: user.apartment.ifBlank { "—" })
                InfoRow(Icons.Default.Phone, "Teléfono", "+52 310 000 0000")
                InfoRow(Icons.Default.Email, "Correo", "residente@guardianapp.co")
            }
        }

        item {
            GuardianCard {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Mi código QR", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Muéstralo al guardia para acceso rápido",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    val qrBitmap = remember(user.id) {
                        try {
                            BarcodeEncoder().encodeBitmap(user.id.toString(), BarcodeFormat.QR_CODE, 400, 400)
                        } catch (_: Exception) { null }
                    }
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "Código QR de acceso",
                            modifier = Modifier.size(180.dp)
                        )
                    } else {
                        Text("No se pudo generar el QR", color = StatusDanger, style = MaterialTheme.typography.bodySmall)
                    }
                    Text("ID: ${user.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            VehicleManagementCard(
                vehiculos = vehiculos,
                onAdd = { placa, desc, color -> vm.agregarVehiculo(user.id, placa, desc, color) },
                onDelete = { id -> vm.eliminarVehiculo(user.id, id) }
            )
        }

        item {
            GuardianCard {
                Text("Notificaciones", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Notifications, null, tint = ResidentBlue)
                    Spacer(Modifier.width(12.dp))
                    Text("Notificaciones push", Modifier.weight(1f))
                    Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Email, null, tint = ResidentBlue)
                    Spacer(Modifier.width(12.dp))
                    Text("Alertas por correo", Modifier.weight(1f))
                    Switch(checked = emailAlerts, onCheckedChange = { emailAlerts = it })
                }
            }
        }

        item {
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, null)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar sesión")
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Cerrar sesión") },
            text = { Text("¿Estás seguro de que deseas salir?") },
            confirmButton = { Button(onClick = onLogout, colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)) { Text("Salir") } },
            dismissButton = { TextButton(onClick = { showLogoutDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun VehicleManagementCard(
    vehiculos: List<Vehiculo>,
    onAdd: (placa: String, descripcion: String, color: String) -> Unit,
    onDelete: (Int) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }

    GuardianCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mis Vehículos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Text("Agregar")
            }
        }

        if (vehiculos.isEmpty()) {
            Text(
                "No tienes vehículos registrados",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                vehiculos.forEach { v ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DirectionsCar, null, tint = SecurityGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.placa, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            val detail = listOfNotNull(v.descripcion, v.color).joinToString(" · ")
                            if (detail.isNotBlank()) {
                                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { v.id?.let { onDelete(it) } }) {
                            Icon(Icons.Default.Delete, null, tint = StatusDanger, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var placa by remember { mutableStateOf("") }
        var descripcion by remember { mutableStateOf("") }
        var color by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Registrar vehículo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = placa, onValueChange = { placa = it.uppercase() }, label = { Text("Placa *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = descripcion, onValueChange = { descripcion = it }, label = { Text("Marca / Modelo (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text("Color (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAdd(placa, descripcion, color); showAddDialog = false },
                    enabled = placa.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = ResidentBlue)
                ) { Text("Registrar") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = ResidentBlue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
