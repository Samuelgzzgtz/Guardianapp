@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.gab.ui.security

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.gab.data.model.Reporte
import com.example.gab.data.model.Usuario
import com.example.gab.data.model.Vehiculo
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.navigation.Routes
import com.example.gab.ui.security.viewmodel.QrScanResult
import com.example.gab.ui.security.viewmodel.SecurityViewModel
import com.example.gab.ui.theme.*
import com.google.zxing.integration.android.IntentIntegrator

@Composable
fun SecurityShell(user: AppUser, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val vm: SecurityViewModel = viewModel()
    val context = LocalContext.current

    LaunchedEffect(Unit) { vm.loadAll() }

    val toastMsg by vm.toastMessage.collectAsStateWithLifecycle()
    LaunchedEffect(toastMsg) {
        toastMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.clearToast()
        }
    }

    val navItems = listOf(
        NavItem("Inicio",     Icons.Default.Home,      Routes.SECURITY_HOME),
        NavItem("Accesos",    Icons.Default.PeopleAlt, Routes.SECURITY_VISITORS),
        NavItem("Incidentes", Icons.Default.Warning,   Routes.SECURITY_INCIDENTS),
    )
    AppShell(
        navController = navController,
        navItems = navItems,
        topBarTitle = "Seguridad",
        topBarActions = {
            IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }
        }
    ) {
        NavHost(navController = navController, startDestination = Routes.SECURITY_HOME) {
            composable(Routes.SECURITY_HOME)      { SecurityHomeScreen(user, vm, navController) }
            composable(Routes.SECURITY_VISITORS)  { SecurityVisitorsScreen(user, vm, navController) }
            composable(Routes.SECURITY_INCIDENTS) { SecurityIncidentsScreen(user, vm) }
            composable(Routes.SECURITY_QR)        { SecurityQrScreen(user, vm) }
            composable(Routes.SECURITY_INE)       { SecurityIneScreen(user, vm) }
            composable(Routes.SECURITY_PLACAS)    { SecurityPlacasScreen(user, vm) }
        }
    }
}

@Composable
fun SecurityHomeScreen(user: AppUser, vm: SecurityViewModel, navController: NavController) {
    val accesoLog  by vm.accesoLog.collectAsStateWithLifecycle()
    val residentes by vm.residentes.collectAsStateWithLifecycle()
    val isLoading  by vm.isLoading.collectAsStateWithLifecycle()

    val entradas       = accesoLog.count { it.direccion == "ENTRADA" }
    val salidas        = accesoLog.count { it.direccion == "SALIDA" }
    val ultimosEventos = accesoLog.takeLast(5).reversed()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Bienvenido, ${user.name.ifBlank { "Guardia" }}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Turno activo · Puesto principal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Entradas hoy", "$entradas", Icons.AutoMirrored.Filled.Login,  SecurityGreen, Modifier.weight(1f))
                StatCard("Salidas hoy",  "$salidas",  Icons.AutoMirrored.Filled.Logout, StatusWarning, Modifier.weight(1f))
            }
        }

        item {
            SectionHeader("Acciones rápidas")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickActionButton(Icons.Default.PersonAdd,     "Acceso",    SecurityGreen, { navController.navigate(Routes.SECURITY_VISITORS)  }, Modifier.weight(1f))
                QuickActionButton(Icons.Default.QrCodeScanner, "QR Scan",   ResidentBlue,  { navController.navigate(Routes.SECURITY_QR)        }, Modifier.weight(1f))
                QuickActionButton(Icons.Default.Report,        "Incidente", StatusDanger,  { navController.navigate(Routes.SECURITY_INCIDENTS) }, Modifier.weight(1f))
            }
        }

        if (ultimosEventos.isNotEmpty()) {
            item { SectionHeader("Últimos eventos") }
            items(ultimosEventos) { log ->
                val nombre    = residentes.find { it.id == log.fkResidente }?.nombre ?: "Residente #${log.fkResidente ?: "—"}"
                val isEntrada = log.direccion == "ENTRADA"
                GuardianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isEntrada) Icons.AutoMirrored.Filled.Login else Icons.AutoMirrored.Filled.Logout,
                            null,
                            tint = if (isEntrada) SecurityGreen else StatusWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("$nombre – ${log.direccion}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(log.horaRegistro ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    "Sin eventos recientes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun SecurityVisitorsScreen(user: AppUser, vm: SecurityViewModel, navController: NavController) {
    val accesoLog  by vm.accesoLog.collectAsStateWithLifecycle()
    val residentes by vm.residentes.collectAsStateWithLifecycle()
    val isLoading  by vm.isLoading.collectAsStateWithLifecycle()

    var showRegisterDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Registro de Accesos", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (isLoading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { navController.navigate(Routes.SECURITY_QR) }) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("QR")
                    }
                    OutlinedButton(onClick = { navController.navigate(Routes.SECURITY_INE) }) {
                        Icon(Icons.Default.Badge, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("INE")
                    }
                    OutlinedButton(onClick = { navController.navigate(Routes.SECURITY_PLACAS) }) {
                        Icon(Icons.Default.DirectionsCar, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Placa")
                    }
                }
            }

            if (accesoLog.isEmpty() && !isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Sin registros de acceso aún", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(accesoLog.reversed(), key = { "${it.id}_${it.horaRegistro}" }) { log ->
                val nombre    = residentes.find { it.id == log.fkResidente }?.nombre ?: "Residente #${log.fkResidente ?: "—"}"
                val isEntrada = log.direccion == "ENTRADA"
                GuardianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarCircle(
                            initials = nombre.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
                            color    = if (isEntrada) SecurityGreen else StatusWarning
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("${log.direccion} · ${log.horaRegistro ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusChip(log.direccion, if (isEntrada) SecurityGreen else StatusWarning)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showRegisterDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = SecurityGreen
        ) {
            Icon(Icons.Default.PersonAdd, null, tint = Color.White)
        }
    }

    if (showRegisterDialog) {
        RegisterAccessDialog(
            residentes = residentes,
            onSearch   = { vm.buscarResidente(it) },
            onDismiss  = { showRegisterDialog = false },
            onConfirm  = { residente, direccion ->
                vm.registrarAcceso(user.id, residente, direccion)
                showRegisterDialog = false
            }
        )
    }
}

@Composable
private fun RegisterAccessDialog(
    residentes: List<Usuario>,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (residente: Usuario, direccion: String) -> Unit
) {
    var query     by remember { mutableStateOf("") }
    var selected  by remember { mutableStateOf<Usuario?>(null) }
    var direccion by remember { mutableStateOf("ENTRADA") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar acceso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it; onSearch(it) },
                    label = { Text("Buscar residente") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (residentes.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                        items(residentes.take(5)) { r ->
                            val isSelected = selected?.id == r.id
                            Surface(
                                onClick = { selected = r },
                                color  = if (isSelected) SecurityGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                                shape  = MaterialTheme.shapes.small
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(r.nombre, modifier = Modifier.weight(1f))
                                    if (isSelected) Icon(Icons.Default.CheckCircle, null, tint = SecurityGreen, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                } else {
                    Text("Sin residentes encontrados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ENTRADA", "SALIDA").forEach { dir ->
                        FilterChip(
                            selected = direccion == dir,
                            onClick  = { direccion = dir },
                            label    = { Text(dir) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (dir == "ENTRADA") SecurityGreen else StatusWarning,
                                selectedLabelColor     = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { selected?.let { onConfirm(it, direccion) } },
                enabled  = selected != null,
                colors   = ButtonDefaults.buttonColors(containerColor = SecurityGreen)
            ) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun SecurityQrScreen(user: AppUser, vm: SecurityViewModel) {
    val residenteEscaneado by vm.residenteEscaneado.collectAsStateWithLifecycle()
    val qrScanResult by vm.qrScanResult.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val scanned = IntentIntegrator.parseActivityResult(result.resultCode, result.data)?.contents
        if (scanned != null) vm.onQrScanned(scanned, user.id)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(Modifier.height(16.dp))
        Icon(
            Icons.Default.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = SecurityGreen
        )
        Text(
            "Escanear QR de Residente",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Escanea el código QR del residente para registrar su acceso rápidamente.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Button(
            onClick = {
                val intent = IntentIntegrator(context as Activity).apply {
                    setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                    setPrompt("Escanea el código QR del residente")
                    setOrientationLocked(false)
                    setBeepEnabled(true)
                }.createScanIntent()
                scanLauncher.launch(intent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = SecurityGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.QrCodeScanner, null)
            Spacer(Modifier.width(8.dp))
            Text("Abrir escáner QR")
        }
    }

    residenteEscaneado?.let { residente ->
        QrAccessDialog(
            residente = residente,
            onDismiss = { vm.clearResidenteEscaneado() },
            onConfirm = { direccion -> vm.registrarAccesoQr(user.id, residente, direccion) }
        )
    }

    when (val result = qrScanResult) {
        is QrScanResult.PaseValido -> PaseValidoDialog(
            pase = result.pase,
            onConfirmar = { vm.confirmarAccesoPase(result.pase) },
            onDismiss   = { vm.clearQrScanResult() }
        )
        is QrScanResult.Error -> PaseErrorDialog(
            mensaje   = result.mensaje,
            onDismiss = { vm.clearQrScanResult() }
        )
        null -> Unit
    }
}

@Composable
private fun PaseValidoDialog(
    pase: com.example.gab.data.model.PaseVisita,
    onConfirmar: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = com.example.gab.ui.theme.StatusSuccess, modifier = Modifier.size(24.dp))
                Text("Pase válido", color = com.example.gab.ui.theme.StatusSuccess)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Visitante: ${pase.nombreVisitante}", style = MaterialTheme.typography.bodyMedium)
                pase.modeloVehiculo?.let { Text("Vehículo: $it ${pase.colorVehiculo ?: ""}", style = MaterialTheme.typography.bodySmall) }
                pase.placaVehiculo?.let { Text("Placa: ${pase.placaVehiculo}", style = MaterialTheme.typography.bodySmall) }
                Text("Usos restantes: ${pase.usosMaximos - pase.usosRealizados}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmar,
                colors = ButtonDefaults.buttonColors(containerColor = com.example.gab.ui.theme.SecurityGreen)
            ) { Text("Confirmar entrada") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun PaseErrorDialog(
    mensaje: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, null, tint = com.example.gab.ui.theme.StatusDanger, modifier = Modifier.size(24.dp))
                Text("Acceso denegado", color = com.example.gab.ui.theme.StatusDanger)
            }
        },
        text = { Text(mensaje) },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = com.example.gab.ui.theme.StatusDanger)
            ) { Text("Cerrar") }
        }
    )
}

@Composable
private fun QrAccessDialog(
    residente: Usuario,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var direccion by remember { mutableStateOf("ENTRADA") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Residente identificado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(residente.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ENTRADA", "SALIDA").forEach { dir ->
                        FilterChip(
                            selected = direccion == dir,
                            onClick  = { direccion = dir },
                            label    = { Text(dir) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = if (dir == "ENTRADA") SecurityGreen else StatusWarning,
                                selectedLabelColor     = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(direccion) },
                colors  = ButtonDefaults.buttonColors(containerColor = SecurityGreen)
            ) { Text("Registrar $direccion") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun SecurityIneScreen(user: AppUser, vm: SecurityViewModel) {
    val showCamera        by vm.showIneCamera.collectAsStateWithLifecycle()
    val nombreReconocido  by vm.ineTextoReconocido.collectAsStateWithLifecycle()
    val residentesMatch   by vm.residentesIneMatch.collectAsStateWithLifecycle()

    if (showCamera) {
        MlKitCameraScreen(
            hint = "Apunta la cámara a la INE del visitante o residente",
            onTextRecognized = { vm.onIneTextoReconocido(it) },
            onCancel = { vm.cerrarCamaraIne() }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 24.dp)
    ) {
        item {
            Text("Registro por INE", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Escanea la INE. Si es un residente, verás su perfil automáticamente. Si es visitante, regístralo manualmente.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Button(
                onClick = { vm.abrirCamaraIne() },
                colors  = ButtonDefaults.buttonColors(containerColor = SecurityGreen),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Escanear INE")
            }
        }

        if (nombreReconocido.isNotBlank()) {
            item {
                var nombreEditado by remember(nombreReconocido) { mutableStateOf(nombreReconocido) }

                // Residentes que coinciden con el nombre escaneado
                if (residentesMatch.isNotEmpty()) {
                    GuardianCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.ManageSearch, null, tint = SecurityGreen, modifier = Modifier.size(18.dp))
                            Text("Residente(s) encontrado(s)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = SecurityGreen)
                        }
                        Spacer(Modifier.height(10.dp))
                        residentesMatch.forEach { r ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (!r.ineUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model            = r.ineUrl,
                                        contentDescription = "INE de ${r.nombre}",
                                        contentScale     = ContentScale.Crop,
                                        modifier         = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    AvatarCircle(
                                        initials = r.nombre.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
                                        color    = SecurityGreen,
                                        size     = 48.dp
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(r.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    if (!r.ineUrl.isNullOrBlank()) {
                                        Text("INE registrado", style = MaterialTheme.typography.labelSmall, color = SecurityGreen)
                                    }
                                }
                                Icon(Icons.Default.CheckCircle, null, tint = SecurityGreen, modifier = Modifier.size(20.dp))
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }

                // Sección para visitante (no residente)
                Text(
                    if (residentesMatch.isEmpty()) "Nombre extraído de la INE:" else "Si es visitante, ajusta el nombre:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = nombreEditado,
                    onValueChange = { nombreEditado = it },
                    label = { Text("Nombre del visitante") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick  = { vm.confirmarVisitaIne(user.id, nombreEditado) },
                    enabled  = nombreEditado.isNotBlank(),
                    colors   = ButtonDefaults.buttonColors(containerColor = SecurityGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PersonAdd, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Registrar como visitante")
                }
            }
        }
    }
}

@Composable
fun SecurityIncidentsScreen(user: AppUser, vm: SecurityViewModel) {
    val incidentes by vm.incidentes.collectAsStateWithLifecycle()
    val isLoading  by vm.isLoading.collectAsStateWithLifecycle()

    var showNewIncident by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Incidentes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (isLoading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (incidentes.isEmpty() && !isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("Sin incidentes registrados", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            items(incidentes.reversed()) { inc ->
                IncidenteCard(inc)
            }
        }

        FloatingActionButton(
            onClick = { showNewIncident = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = StatusDanger
        ) {
            Icon(Icons.Default.Add, null, tint = Color.White)
        }
    }

    if (showNewIncident) {
        NewIncidentDialog(
            onDismiss = { showNewIncident = false },
            onConfirm = { titulo, ubicacion, esUrgente ->
                vm.reportarIncidente(user.id, titulo, ubicacion, esUrgente)
                showNewIncident = false
            }
        )
    }
}

@Composable
fun SecurityPlacasScreen(user: AppUser, vm: SecurityViewModel) {
    val showCamera            by vm.showPlacaCamera.collectAsStateWithLifecycle()
    val placaResult           by vm.placaResultado.collectAsStateWithLifecycle()
    val residentes            by vm.residentes.collectAsStateWithLifecycle()
    val residenteSeleccionado by vm.residenteParaPlaca.collectAsStateWithLifecycle()
    val vehiculosResidente    by vm.vehiculosResidente.collectAsStateWithLifecycle()
    val autoRegistrado        by vm.autoRegistrado.collectAsStateWithLifecycle()
    val residenteInfo         by vm.residentePlacaInfo.collectAsStateWithLifecycle()

    var busqueda by remember { mutableStateOf("") }
    var showResidenteDropdown by remember { mutableStateOf(false) }

    if (showCamera) {
        MlKitCameraScreen(
            hint = "Apunta la cámara a la placa del vehículo",
            onTextRecognized = { vm.onPlacaTextoReconocido(user.id, it) },
            onCancel = { vm.cerrarCamaraPlaca() }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Auto-registration confirmation banner with undo
        if (autoRegistrado) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SecurityGreen.copy(alpha = 0.12f)),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = SecurityGreen, modifier = Modifier.size(20.dp))
                        Text(
                            "Acceso registrado automáticamente",
                            modifier = Modifier.weight(1f),
                            color = SecurityGreen,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        TextButton(
                            onClick = { vm.cancelarUltimoAcceso() },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Anular", color = StatusDanger, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        item {
            Text("Verificar Placas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Paso 1: Identifica al residente. Paso 2: Confirma el vehículo. Paso 3: Escanea la placa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Step 1: Resident search
        item {
            GuardianCard {
                Text("1. Buscar residente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (residenteSeleccionado == null) {
                    OutlinedTextField(
                        value = busqueda,
                        onValueChange = { busqueda = it; vm.buscarResidente(it); showResidenteDropdown = it.isNotBlank() },
                        label = { Text("Nombre del residente") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (showResidenteDropdown) {
                        val filtrados = residentes.filter { it.nombre.contains(busqueda, ignoreCase = true) }.take(5)
                        filtrados.forEach { r ->
                            TextButton(
                                onClick = { vm.seleccionarResidenteParaPlaca(r); showResidenteDropdown = false; busqueda = "" },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(r.nombre, modifier = Modifier.weight(1f))
                            }
                        }
                        if (filtrados.isEmpty()) {
                            Text("Sin resultados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, tint = SecurityGreen)
                        Spacer(Modifier.width(8.dp))
                        Text(residenteSeleccionado!!.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.limpiarSeleccionResidentePlaca() }) { Text("Cambiar") }
                    }
                }
            }
        }

        // Step 2: Show registered vehicles for visual confirmation
        if (residenteSeleccionado != null) {
            item {
                GuardianCard {
                    Text("2. Confirmar vehículo visualmente", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    if (vehiculosResidente.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Warning, null, tint = StatusWarning, modifier = Modifier.size(20.dp))
                            Text("Sin vehículos registrados", style = MaterialTheme.typography.bodySmall, color = StatusWarning)
                        }
                    } else {
                        vehiculosResidente.forEach { v ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.DirectionsCar, null, tint = SecurityGreen, modifier = Modifier.size(20.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("Placa esperada: ${v.placa}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    v.descripcion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    v.color?.let { Text("Color: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                }
                            }
                        }
                    }
                }
            }

            // Step 3: Scan plate to confirm
            item {
                Button(
                    onClick = { vm.abrirCamaraPlaca() },
                    colors = ButtonDefaults.buttonColors(containerColor = SecurityGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CameraAlt, null)
                    Spacer(Modifier.width(8.dp))
                    Text("3. Escanear placa para confirmar")
                }
            }
        }

        // Result
        placaResult?.let { (placa, vehiculo) ->
            item {
                PlacaResultCard(
                    placa        = placa,
                    vehiculo     = vehiculo,
                    residente    = residenteInfo
                )
            }
        }
    }
}

@Composable
private fun PlacaResultCard(placa: String, vehiculo: Vehiculo?, residente: Usuario?) {
    val coincide = vehiculo != null
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (coincide) SecurityGreen.copy(alpha = 0.08f) else StatusDanger.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    if (coincide) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    null,
                    tint   = if (coincide) SecurityGreen else StatusDanger,
                    modifier = Modifier.size(28.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text("Placa: $placa", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (coincide) "✓ Vehículo registrado" else "⚠ Placa no registrada",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (coincide) SecurityGreen else StatusDanger
                    )
                    vehiculo?.descripcion?.let {
                        Text("Modelo: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    vehiculo?.color?.let {
                        Text("Color: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Resident identity card
            if (coincide && residente != null) {
                HorizontalDivider(color = SecurityGreen.copy(alpha = 0.3f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!residente.ineUrl.isNullOrBlank()) {
                        AsyncImage(
                            model            = residente.ineUrl,
                            contentDescription = "INE de ${residente.nombre}",
                            contentScale     = ContentScale.Crop,
                            modifier         = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        AvatarCircle(
                            initials = residente.nombre.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
                            color    = SecurityGreen,
                            size     = 56.dp
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(residente.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        if (!residente.ineUrl.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Badge, null, tint = SecurityGreen, modifier = Modifier.size(14.dp))
                                Text("INE verificado", style = MaterialTheme.typography.labelSmall, color = SecurityGreen)
                            }
                        } else {
                            Text("Sin foto de INE registrada", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IncidenteCard(inc: Reporte) {
    val priorityColor = if (inc.esUrgente) StatusDanger else StatusWarning
    val priorityLabel = if (inc.esUrgente) "Alta" else "Media"
    GuardianCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(Icons.Default.Warning, null, tint = priorityColor, modifier = Modifier.size(20.dp).padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("#${inc.id ?: "—"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusChip(priorityLabel, priorityColor)
                    StatusChip(inc.estatus, when (inc.estatus) { "Resuelto" -> StatusSuccess; "En proceso" -> StatusInfo; else -> StatusWarning })
                }
                Spacer(Modifier.height(2.dp))
                Text(inc.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inc.descripcion?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(inc.fechaCreacion ?: "—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NewIncidentDialog(
    onDismiss: () -> Unit,
    onConfirm: (titulo: String, ubicacion: String, esUrgente: Boolean) -> Unit
) {
    var title    by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("Media") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reportar incidente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Descripción del incidente") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2
                )
                OutlinedTextField(
                    value = location, onValueChange = { location = it },
                    label = { Text("Ubicación") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Baja", "Media", "Alta").forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick  = { priority = p },
                            label    = { Text(p) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = when (p) { "Alta" -> StatusDanger; "Media" -> StatusWarning; else -> StatusInfo },
                                selectedLabelColor     = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (title.isNotBlank()) onConfirm(title, location, priority == "Alta") },
                enabled  = title.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = StatusDanger)
            ) { Text("Reportar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
