@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.gab.ui.resident

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.navigation.Routes
import com.example.gab.ui.resident.viewmodel.ResidentViewModel
import com.example.gab.ui.theme.*
import com.example.gab.util.calcularRecargo
import com.example.gab.util.diasParaPago
import com.example.gab.util.toMoneda

@Composable
fun ResidentShell(user: AppUser, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val vm: ResidentViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(user.id) { vm.loadAll(user.id) }

    val toastMsg by vm.toastMessage.collectAsStateWithLifecycle()
    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearToast()
        }
    }

    val reportes by vm.reportes.collectAsStateWithLifecycle()
    val navItems = listOf(
        NavItem("Inicio",    Icons.Default.Home,                   Routes.RESIDENT_HOME),
        NavItem("Reportes",  Icons.AutoMirrored.Filled.Assignment, Routes.RESIDENT_REPORTS,
            badgeCount = reportes.count { it.estatus == "Pendiente" }.coerceAtMost(9)),
        NavItem("Reservas",  Icons.Default.EventAvailable,         Routes.RESIDENT_AMENITIES),
        NavItem("Cuenta",    Icons.Default.AccountBalance,         Routes.RESIDENT_ACCOUNT),
        NavItem("Perfil",    Icons.Default.Person,                 Routes.RESIDENT_PROFILE),
    )

    AppShell(
        navController = navController,
        navItems = navItems,
        topBarTitle = "GuardianApp",
        topBarActions = {
            IconButton(onClick = onLogout) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Salir")
            }
        }
    ) {
        NavHost(navController = navController, startDestination = Routes.RESIDENT_HOME) {
            composable(Routes.RESIDENT_HOME)      { ResidentHomeScreen(user, navController, vm) }
            composable(Routes.RESIDENT_REPORTS)   { ResidentReportsScreen(user, navController, vm) }
            composable(Routes.RESIDENT_AMENITIES) { ResidentAmenitiesScreen(user, vm) }
            composable(Routes.RESIDENT_ACCOUNT)   { ResidentAccountScreen(vm) }
            composable(Routes.RESIDENT_PROFILE)   { ResidentProfileScreen(user, vm, onLogout) }
        }
    }
}

@Composable
fun ResidentHomeScreen(user: AppUser, navController: NavController, vm: ResidentViewModel) {
    val avisos    by vm.avisos.collectAsStateWithLifecycle()
    val reportes  by vm.reportes.collectAsStateWithLifecycle()
    val cuota     by vm.cuota.collectAsStateWithLifecycle()
    val unidad    by vm.unidad.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

    var showPayDialog      by remember { mutableStateOf(false) }
    var showLimpiezaDialog by remember { mutableStateOf(false) }

    val reportesAbiertos = reportes.count { it.estatus == "Pendiente" || it.estatus == "En proceso" }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Column {
                Text("Buen día,", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(user.name.ifBlank { "Residente" }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (unidad != null) {
                    Text(
                        unidad!!.displayUbicacion(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (user.apartment.isNotBlank()) {
                    Text(user.apartment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (isLoading) {
            item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
        }

        cuota?.let { c ->
            val dias = c.diasParaPago()
            if (dias != null && dias <= 7) {
                item {
                    val (bannerColor, bannerIcon, bannerMsg) = when {
                        dias < 0  -> Triple(StatusDanger,  Icons.Default.Warning,       "Pago vencido hace ${-dias} día(s)")
                        dias == 0L -> Triple(StatusDanger,  Icons.Default.Warning,       "Pago vence HOY")
                        dias <= 3  -> Triple(StatusWarning, Icons.Default.Notifications, "Pago vence en $dias día(s)")
                        else       -> Triple(StatusInfo,    Icons.Default.Info,          "Pago vence en $dias días")
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = bannerColor.copy(alpha = 0.12f)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(bannerIcon, null, tint = bannerColor)
                            Text(
                                bannerMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = bannerColor,
                                modifier = Modifier.weight(1f)
                            )
                            if (c.estatus != "pagado") {
                                TextButton(onClick = { showPayDialog = true }) {
                                    Text("Pagar", color = bannerColor, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        cuota?.let { c ->
            val recargo = c.calcularRecargo()
            val totalAPagar = (c.monto ?: 0.0) + recargo
            item {
                GuardianCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Receipt, null,
                                tint = if (c.estatus == "pagado") StatusSuccess else StatusWarning,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Cuota ${c.periodo}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text("Vence: ${c.fechaVencimiento ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (recargo > 0) Text("Recargo: +${recargo.toMoneda()}", style = MaterialTheme.typography.labelSmall, color = StatusDanger)
                            }
                            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(totalAPagar.toMoneda(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                StatusChip(c.estatus, if (c.estatus == "pagado") StatusSuccess else StatusWarning)
                            }
                        }
                        if (c.estatus != "pagado") {
                            Button(
                                onClick = { showPayDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = StatusSuccess)
                            ) {
                                Icon(Icons.Default.Payment, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Pagar ${totalAPagar.toMoneda()}")
                            }
                        }
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Reportes abiertos", "$reportesAbiertos", Icons.AutoMirrored.Filled.Assignment, StatusWarning, Modifier.weight(1f))
                StatCard("Avisos",            "${avisos.size}",    Icons.Default.Notifications,           ResidentBlue,  Modifier.weight(1f))
            }
        }

        item {
            SectionHeader("Acciones rápidas")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickActionButton(Icons.Default.Add,              "Nuevo reporte", ResidentBlue,    { navController.navigate(Routes.RESIDENT_REPORTS)  }, Modifier.weight(1f))
                QuickActionButton(Icons.Default.EventAvailable,   "Reservas",      SecurityGreen,   { navController.navigate(Routes.RESIDENT_AMENITIES) }, Modifier.weight(1f))
                QuickActionButton(Icons.Default.CleaningServices, "Limpieza",      CleaningOrange,  { showLimpiezaDialog = true                         }, Modifier.weight(1f))
            }
        }

        if (avisos.isNotEmpty()) {
            item { SectionHeader("Avisos del condominio") }
            items(avisos) { aviso ->
                GuardianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, color) = when (aviso.tono) {
                            "warn"    -> Icons.Default.Warning     to StatusWarning
                            "success" -> Icons.Default.CheckCircle to StatusSuccess
                            else      -> Icons.Default.Info        to ResidentBlue
                        }
                        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(aviso.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            aviso.descripcion?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPayDialog) {
        AlertDialog(
            onDismissRequest = { showPayDialog = false },
            title = { Text("Confirmar pago") },
            text  = { Text("¿Confirmas el pago de la cuota mensual por ${((cuota?.monto ?: 0.0) + (cuota?.calcularRecargo() ?: 0.0)).toMoneda()}?") },
            confirmButton = {
                Button(
                    onClick = { vm.pagarCuota(user.id); showPayDialog = false },
                    colors  = ButtonDefaults.buttonColors(containerColor = StatusSuccess)
                ) { Text("Pagar") }
            },
            dismissButton = { TextButton(onClick = { showPayDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showLimpiezaDialog) {
        var notasLimpieza by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showLimpiezaDialog = false },
            title = { Text("Solicitar limpieza") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Se enviará una solicitud de limpieza para tu unidad.")
                    OutlinedTextField(
                        value = notasLimpieza,
                        onValueChange = { notasLimpieza = it },
                        label = { Text("Notas (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.solicitarLimpieza(user.id, notasLimpieza); showLimpiezaDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CleaningOrange)
                ) { Text("Solicitar") }
            },
            dismissButton = { TextButton(onClick = { showLimpiezaDialog = false }) { Text("Cancelar") } }
        )
    }
}
