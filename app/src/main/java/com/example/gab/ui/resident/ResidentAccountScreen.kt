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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gab.ui.common.*
import com.example.gab.ui.resident.viewmodel.ResidentViewModel
import com.example.gab.ui.theme.*
import com.example.gab.util.calcularRecargo
import com.example.gab.util.toMoneda

@Composable
fun ResidentAccountScreen(vm: ResidentViewModel) {
    val historial  by vm.historialCuotas.collectAsStateWithLifecycle()
    val isLoading  by vm.isLoading.collectAsStateWithLifecycle()
    val clipboard  = LocalClipboardManager.current

    val totalPagado   = historial.filter { it.estatus == "pagado" }.sumOf { it.monto ?: 0.0 }
    val totalPendiente = historial.filter { it.estatus != "pagado" }.sumOf { (it.monto ?: 0.0) + it.calcularRecargo() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Estado de Cuenta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                IconButton(onClick = {
                    val texto = buildString {
                        appendLine("ESTADO DE CUENTA")
                        appendLine("=".repeat(40))
                        historial.forEach { c ->
                            val rec = c.calcularRecargo()
                            val total = (c.monto ?: 0.0) + rec
                            appendLine("${c.periodo}  ${c.estatus.uppercase().padEnd(10)} ${total.toMoneda()}${if (rec > 0) " (recargo: ${rec.toMoneda()})" else ""}")
                        }
                        appendLine("─".repeat(40))
                        appendLine("Total pagado:    ${totalPagado.toMoneda()}")
                        appendLine("Total pendiente: ${totalPendiente.toMoneda()}")
                    }
                    clipboard.setText(AnnotatedString(texto))
                }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Copiar estado de cuenta")
                }
            }
            if (isLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Pagado",    totalPagado.toMoneda(),    Icons.Default.CheckCircle, StatusSuccess, Modifier.weight(1f))
                StatCard("Pendiente", totalPendiente.toMoneda(), Icons.Default.Warning,     StatusDanger,  Modifier.weight(1f))
            }
        }

        // Desglose de cuota mensual
        val cuotaActiva = historial.firstOrNull { it.estatus != "pagado" } ?: historial.firstOrNull()
        cuotaActiva?.monto?.let { monto ->
            if (monto > 0) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ResidentBlue.copy(alpha = 0.07f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("¿Qué incluye tu cuota mensual?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            HorizontalDivider()
                            val conceptos = listOf(
                                Triple("Mantenimiento general",      Icons.Default.Build,        0.50),
                                Triple("Áreas comunes y amenidades", Icons.Default.Pool,          0.25),
                                Triple("Seguridad 24/7",             Icons.Default.Security,     0.15),
                                Triple("Administración",             Icons.Default.AdminPanelSettings, 0.10)
                            )
                            conceptos.forEach { (label, icon, pct) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(icon, null, modifier = Modifier.size(16.dp), tint = ResidentBlue)
                                    Spacer(Modifier.width(8.dp))
                                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text((monto * pct).toMoneda(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                }
                            }
                            HorizontalDivider()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Total mensual", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(monto.toMoneda(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = ResidentBlue)
                            }
                        }
                    }
                }
            }
        }

        if (historial.isEmpty() && !isLoading) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Sin historial de cuotas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(historial) { c ->
            val recargo = c.calcularRecargo()
            val total   = (c.monto ?: 0.0) + recargo
            val statusColor = when (c.estatus) {
                "pagado"   -> StatusSuccess
                "vencido"  -> StatusDanger
                else       -> StatusWarning
            }
            GuardianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(c.periodo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text("Vence: ${c.fechaVencimiento ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (recargo > 0) {
                            Text("Recargo: +${recargo.toMoneda()}", style = MaterialTheme.typography.labelSmall, color = StatusDanger)
                        }
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        StatusChip(c.estatus, statusColor)
                        Text(total.toMoneda(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
