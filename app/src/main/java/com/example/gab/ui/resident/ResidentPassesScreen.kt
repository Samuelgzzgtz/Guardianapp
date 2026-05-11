@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.gab.ui.resident

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.resident.viewmodel.ResidentViewModel
import com.example.gab.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.journeyapps.barcodescanner.BarcodeEncoder

@Composable
fun ResidentPassesScreen(user: AppUser, vm: ResidentViewModel) {
    val pases     by vm.pases.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(user.id) { vm.loadPases(user.id) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = ResidentBlue
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nuevo pase")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Pases de Visita", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (isLoading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (pases.isEmpty() && !isLoading) {
                item {
                    EmptyState("No tienes pases activos. Crea uno con el botón +")
                }
            }

            items(pases) { pase ->
                PaseCard(
                    pase = pase,
                    onDesactivar = { vm.desactivarPase(user.id, pase.id!!) }
                )
            }
        }
    }

    if (showCreateDialog) {
        CrearPaseDialog(
            onDismiss = { showCreateDialog = false },
            onSubmit = { nombre, modelo, color, placa, vigencia, usos, expiracion ->
                vm.crearPase(user.id, nombre, modelo, color, placa, vigencia, usos, expiracion)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun PaseCard(pase: com.example.gab.data.model.PaseVisita, onDesactivar: () -> Unit) {
    val qrBitmap = remember(pase.id) {
        runCatching {
            val matrix = MultiFormatWriter().encode("pase:${pase.id}", BarcodeFormat.QR_CODE, 200, 200)
            BarcodeEncoder().createBitmap(matrix)
        }.getOrNull()
    }

    GuardianCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(pase.nombreVisitante, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    pase.modeloVehiculo?.let { Text("Vehículo: $it ${pase.colorVehiculo ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    pase.placaVehiculo?.let { Text("Placa: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text("Usos: ${pase.usosRealizados}/${pase.usosMaximos}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    pase.fechaExpiracion?.let { Text("Expira: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                StatusChip(
                    if (pase.activo) "Activo" else "Inactivo",
                    if (pase.activo) StatusSuccess else StatusDanger
                )
            }

            qrBitmap?.let { bmp ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "QR del pase",
                        modifier = Modifier.size(150.dp)
                    )
                }
            }

            if (pase.activo) {
                TextButton(
                    onClick = onDesactivar,
                    colors = ButtonDefaults.textButtonColors(contentColor = StatusDanger)
                ) {
                    Icon(Icons.Default.Cancel, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Desactivar pase")
                }
            }
        }
    }
}

@Composable
private fun CrearPaseDialog(
    onDismiss: () -> Unit,
    onSubmit: (nombre: String, modelo: String?, color: String?, placa: String?, vigencia: String, usos: Int, expiracion: String?) -> Unit
) {
    var nombre     by remember { mutableStateOf("") }
    var modelo     by remember { mutableStateOf("") }
    var color      by remember { mutableStateOf("") }
    var placa      by remember { mutableStateOf("") }
    var vigencia   by remember { mutableStateOf("hoy") }
    var usosStr    by remember { mutableStateOf("1") }
    var expiracion by remember { mutableStateOf("") }

    val vigenciaOptions = listOf("hoy", "semana", "mes")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo pase de visita") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = nombre, onValueChange = { nombre = it },
                    label = { Text("Nombre del visitante *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = modelo, onValueChange = { modelo = it },
                    label = { Text("Modelo de vehículo") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = color, onValueChange = { color = it },
                    label = { Text("Color de vehículo") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = placa, onValueChange = { placa = it },
                    label = { Text("Placa") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("Vigencia:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    vigenciaOptions.forEach { opt ->
                        FilterChip(
                            selected = vigencia == opt,
                            onClick = { vigencia = opt },
                            label = { Text(opt.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                OutlinedTextField(
                    value = usosStr, onValueChange = { usosStr = it.filter { c -> c.isDigit() } },
                    label = { Text("Número de usos") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = expiracion, onValueChange = { expiracion = it },
                    label = { Text("Fecha expiración (YYYY-MM-DD, opcional)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nombre.isNotBlank()) {
                        onSubmit(
                            nombre,
                            modelo.ifBlank { null },
                            color.ifBlank { null },
                            placa.ifBlank { null },
                            vigencia,
                            usosStr.toIntOrNull() ?: 1,
                            expiracion.ifBlank { null }
                        )
                    }
                },
                enabled = nombre.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ResidentBlue)
            ) { Text("Crear pase") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
