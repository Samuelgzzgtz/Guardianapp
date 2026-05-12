@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.gab.ui.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import com.example.gab.data.model.*
import com.example.gab.ui.admin.viewmodel.AdminViewModel
import com.example.gab.ui.admin.viewmodel.CreateUserState
import com.example.gab.util.calcularRecargo
import com.example.gab.util.toMoneda
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.navigation.Routes
import com.example.gab.ui.navigation.UserRole
import com.example.gab.ui.theme.*

@Composable
fun AdminShell(user: AppUser, onLogout: () -> Unit) {
    val navController = rememberNavController()
    val vm: AdminViewModel = viewModel()
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
        NavItem("Dashboard", Icons.Default.Dashboard,          Routes.ADMIN_DASHBOARD),
        NavItem("Usuarios",  Icons.Default.Group,              Routes.ADMIN_USERS),
        NavItem("Reportes",  Icons.Default.Assessment,         Routes.ADMIN_REPORTS),
        NavItem("Unidades",  Icons.Default.Apartment,          Routes.ADMIN_UNITS),
        NavItem("Cuenta",    Icons.Default.AccountBalanceWallet, Routes.ADMIN_ACCOUNT),
    )
    AppShell(
        navController = navController,
        navItems = navItems,
        topBarTitle = "Administración",
        topBarActions = {
            IconButton(onClick = { vm.loadAll() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
            }
            var showMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Prueba push (este dispositivo)") },
                    leadingIcon = { Icon(Icons.Default.NotificationsActive, null) },
                    onClick = { vm.enviarPruebaPush(user.id); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Recordatorio push (todos)") },
                    leadingIcon = { Icon(Icons.Default.Notifications, null) },
                    onClick = { vm.dispararRecordatorioPago(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Recordatorio por correo") },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    onClick = { vm.dispararRecordatorioEmail(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Cerrar sesión") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                    onClick = { onLogout(); showMenu = false }
                )
            }
        }
    ) {
        NavHost(navController = navController, startDestination = Routes.ADMIN_DASHBOARD) {
            composable(Routes.ADMIN_DASHBOARD) { AdminDashboard(user, vm) }
            composable(Routes.ADMIN_USERS)     { AdminUsersScreen(vm) }
            composable(Routes.ADMIN_REPORTS)   { AdminReportsScreen(vm) }
            composable(Routes.ADMIN_UNITS)     { AdminUnitsScreen(vm) }
            composable(Routes.ADMIN_ACCOUNT)   { AdminAccountScreen(vm) }
        }
    }
}

@Composable
fun AdminDashboard(user: AppUser, vm: AdminViewModel) {
    val stats      by vm.stats.collectAsStateWithLifecycle()
    val morosos    by vm.morosos.collectAsStateWithLifecycle()
    val avisos     by vm.avisos.collectAsStateWithLifecycle()
    val reservas   by vm.reservas.collectAsStateWithLifecycle()
    val amenidades by vm.amenidades.collectAsStateWithLifecycle()
    val isLoading  by vm.isLoading.collectAsStateWithLifecycle()

    var showAvisoDialog    by remember { mutableStateOf(false) }
    var avisoToDelete      by remember { mutableStateOf<com.example.gab.data.model.Aviso?>(null) }
    var showReservasDialog by remember { mutableStateOf(false) }
    var showCobrarDialog   by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Panel de Control", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Bienvenido, ${user.name.ifBlank { "Administrador" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Usuarios",         "${stats.totalUsuarios}",      Icons.Default.Group,          ResidentBlue,  Modifier.weight(1f))
                StatCard("Reportes abiertos","${stats.reportesAbiertos}",   Icons.AutoMirrored.Filled.Assignment, StatusWarning, Modifier.weight(1f))
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Reservas", "${stats.reservasHoy}", Icons.Default.EventAvailable, SecurityGreen, Modifier.weight(1f),
                    onClick = { showReservasDialog = true })
                StatCard("Morosos", "${morosos.size}", Icons.Default.Warning, StatusDanger, Modifier.weight(1f))
            }
        }

        item {
            OutlinedButton(
                onClick = { showCobrarDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AttachMoney, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Cobrar mensualidad a todos")
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Avisos del condominio", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { showAvisoDialog = true }) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Nuevo aviso")
                }
            }
        }

        if (avisos.isEmpty()) {
            item {
                Text(
                    "Sin avisos publicados",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                )
            }
        }

        items(avisos, key = { it.id ?: 0 }) { aviso ->
            val (icon, color) = when (aviso.tono) {
                "warn"    -> Icons.Default.Warning     to StatusWarning
                "success" -> Icons.Default.CheckCircle to StatusSuccess
                else      -> Icons.Default.Info        to ResidentBlue
            }
            GuardianCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(aviso.titulo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        aviso.descripcion?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                        }
                    }
                    IconButton(onClick = { avisoToDelete = aviso }) {
                        Icon(Icons.Default.DeleteOutline, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (morosos.isNotEmpty()) {
            item { SectionHeader("Morosos (vencido > 30 días)") }
            items(morosos) { m ->
                val recargo = Cuota(monto = m.monto, fechaVencimiento = m.fechaVencimiento, estatus = "vencido").calcularRecargo()
                GuardianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text("Periodo ${m.periodo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Vencimiento: ${m.fechaVencimiento ?: "—"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text((m.monto + recargo).toMoneda(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = StatusDanger)
                            if (recargo > 0) Text("+${recargo.toMoneda()} recargo", style = MaterialTheme.typography.labelSmall, color = StatusWarning)
                        }
                    }
                }
            }
        }
    }

    if (showAvisoDialog) {
        NuevoAvisoDialog(
            onDismiss = { showAvisoDialog = false },
            onSubmit  = { titulo, descripcion, tono ->
                vm.crearAviso(titulo, descripcion, tono)
                showAvisoDialog = false
            }
        )
    }

    avisoToDelete?.let { aviso ->
        AlertDialog(
            onDismissRequest = { avisoToDelete = null },
            title = { Text("Eliminar aviso") },
            text  = { Text("¿Eliminar el aviso \"${aviso.titulo}\"? Los residentes ya no lo verán.") },
            confirmButton = {
                Button(
                    onClick = { aviso.id?.let { vm.eliminarAviso(it) }; avisoToDelete = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = StatusDanger)
                ) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { avisoToDelete = null }) { Text("Cancelar") } }
        )
    }

    if (showReservasDialog) {
        ReservasAdminDialog(
            reservas   = reservas,
            amenidades = amenidades,
            onDismiss  = { showReservasDialog = false }
        )
    }

    if (showCobrarDialog) {
        CobrarMensualidadDialog(
            onDismiss = { showCobrarDialog = false },
            onCobrar  = { monto -> vm.cobrarMensualidad(monto); showCobrarDialog = false }
        )
    }
}

@Composable
private fun NuevoAvisoDialog(
    onDismiss: () -> Unit,
    onSubmit: (titulo: String, descripcion: String?, tono: String) -> Unit
) {
    var titulo       by remember { mutableStateOf("") }
    var descripcion  by remember { mutableStateOf("") }
    var tono         by remember { mutableStateOf("primary") }
    var tonoExpanded by remember { mutableStateOf(false) }

    val tonos = listOf(
        "primary" to "Informativo",
        "warn"    to "Advertencia",
        "success" to "Positivo"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo aviso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = titulo, onValueChange = { titulo = it },
                    label = { Text("Título *") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                OutlinedTextField(
                    value = descripcion, onValueChange = { descripcion = it },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4
                )
                ExposedDropdownMenuBox(expanded = tonoExpanded, onExpandedChange = { tonoExpanded = it }) {
                    val tonoLabel = tonos.find { it.first == tono }?.second ?: "Informativo"
                    OutlinedTextField(
                        value = tonoLabel, onValueChange = {}, readOnly = true,
                        label = { Text("Tipo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(tonoExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = tonoExpanded, onDismissRequest = { tonoExpanded = false }) {
                        tonos.forEach { (key, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = { tono = key; tonoExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(titulo, descripcion.ifBlank { null }, tono) },
                enabled = titulo.isNotBlank(),
                colors  = ButtonDefaults.buttonColors(containerColor = AdminPurple)
            ) { Text("Publicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun AdminUsersScreen(vm: AdminViewModel) {
    val usuarios            by vm.usuarios.collectAsStateWithLifecycle()
    val unidadesConEstatus  by vm.unidadesConEstatus.collectAsStateWithLifecycle()
    val tareasLimpieza      by vm.tareasLimpieza.collectAsStateWithLifecycle()
    val isLoading           by vm.isLoading.collectAsStateWithLifecycle()
    val createState         by vm.createState.collectAsStateWithLifecycle()

    var searchQuery          by rememberSaveable { mutableStateOf("") }
    var rolFiltro            by rememberSaveable { mutableStateOf<Int?>(null) }
    var showBottomSheet      by remember { mutableStateOf(false) }
    var selectedLimpiezaUser by remember { mutableStateOf<Usuario?>(null) }
    var selectedResidente    by remember { mutableStateOf<Usuario?>(null) }
    val cuotasUsuario        by vm.cuotasUsuario.collectAsStateWithLifecycle()

    val roleFilters = listOf(null to "Todos", 1 to "Residentes", 2 to "Seguridad", 4 to "Limpieza")
    val filtered = usuarios.filter { u ->
        (rolFiltro == null || u.fkRolUsuario == rolFiltro) &&
        (searchQuery.isBlank() || u.nombre.contains(searchQuery, ignoreCase = true))
    }

    LaunchedEffect(createState) {
        if (createState is CreateUserState.Success) {
            showBottomSheet = false
            vm.resetCreateState()
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Gestión de Usuarios", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar usuario…") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    roleFilters.forEach { (rol, label) ->
                        FilterChip(selected = rolFiltro == rol, onClick = { rolFiltro = rol; vm.filtrarUsuarios(rol) }, label = { Text(label, fontSize = 11.sp) })
                    }
                }
                if (isLoading) { Spacer(Modifier.height(8.dp)); LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }

            if (usuarios.isEmpty() && !isLoading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("No hay usuarios", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }

            items(filtered, key = { it.id }) { u ->
                UserCard(
                    u,
                    onRoleChange   = { vm.actualizarRol(u.id, it) },
                    onDelete       = { vm.eliminarUsuario(u.id, u.email ?: "", u.nombre) },
                    onVerDetalle   = if (u.fkRolUsuario == 1) ({
                        selectedResidente = u
                        vm.loadCuotasUsuario(u.id)
                    }) else null,
                    onVerTareas    = if (u.fkRolUsuario == 4) ({
                        selectedLimpiezaUser = u
                        vm.loadTareasLimpieza(u.id)
                    }) else null
                )
            }
        }

        FloatingActionButton(
            onClick = { vm.resetCreateState(); showBottomSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = AdminPurple
        ) { Icon(Icons.Default.PersonAdd, contentDescription = "Nuevo usuario") }
    }

    if (showBottomSheet) {
        ModalBottomSheet(onDismissRequest = { showBottomSheet = false }) {
            CreateUserForm(
                createState        = createState,
                unidadesConEstatus = unidadesConEstatus,
                onSubmit    = { nombre, apellido, email, pass, rolId, unidadId ->
                    vm.crearUsuario(nombre, apellido, email, pass, rolId, unidadId)
                },
                onDismiss = { showBottomSheet = false }
            )
        }
    }

    selectedLimpiezaUser?.let { lUser ->
        CleaningTasksAdminDialog(
            usuario   = lUser,
            tareas    = tareasLimpieza,
            onDismiss = { selectedLimpiezaUser = null },
            onAddTarea = { titulo, area, prioridad, notas ->
                vm.crearTareaLimpieza(lUser.id, titulo, area, prioridad, notas)
            }
        )
    }

    selectedResidente?.let { res ->
        val unidad = unidadesConEstatus.firstOrNull { it.unidad.id == res.fkUnidad }?.unidad
        ResidenteDetalleDialog(
            usuario   = res,
            unidad    = unidad,
            cuotas    = cuotasUsuario,
            vm        = vm,
            onDismiss = { selectedResidente = null }
        )
    }
}

@Composable
private fun CreateUserForm(
    createState: CreateUserState,
    unidadesConEstatus: List<UnidadConEstatus>,
    onSubmit: (String, String, String, String, Int, Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var nombre    by remember { mutableStateOf("") }
    var apellido  by remember { mutableStateOf("") }
    var email     by remember { mutableStateOf("") }
    var password  by remember { mutableStateOf("") }
    var showPass  by remember { mutableStateOf(false) }
    var rolId     by remember { mutableStateOf(1) }
    var unidadId  by remember { mutableStateOf<Int?>(null) }
    var rolExpanded by remember { mutableStateOf(false) }
    var unidadExpanded by remember { mutableStateOf(false) }

    val roles = listOf(1 to "Residente", 2 to "Seguridad", 4 to "Limpieza")
    val rolNombre = roles.find { it.first == rolId }?.second ?: "Residente"
    val isLoading = createState is CreateUserState.Loading
    val errorMsg = (createState as? CreateUserState.Error)?.message

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Nuevo Usuario", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = nombre, onValueChange = { nombre = it }, label = { Text("Nombre") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(value = apellido, onValueChange = { apellido = it }, label = { Text("Apellido") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(
            value = password, onValueChange = { password = it },
            label = { Text("Contraseña") },
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPass = !showPass }) {
                    Icon(if (showPass) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                }
            },
            modifier = Modifier.fillMaxWidth(), singleLine = true
        )

        ExposedDropdownMenuBox(expanded = rolExpanded, onExpandedChange = { rolExpanded = it }) {
            OutlinedTextField(
                value = rolNombre, onValueChange = {}, readOnly = true, label = { Text("Rol") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(rolExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
            )
            ExposedDropdownMenu(expanded = rolExpanded, onDismissRequest = { rolExpanded = false }) {
                roles.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { rolId = id; unidadId = null; rolExpanded = false })
                }
            }
        }

        if (rolId == 1) {
            val selectedLabel = unidadesConEstatus.find { it.unidad.id == unidadId }
                ?.unidad?.displayUbicacion()
                ?: "Selecciona unidad"
            ExposedDropdownMenuBox(expanded = unidadExpanded, onExpandedChange = { unidadExpanded = it }) {
                OutlinedTextField(
                    value = selectedLabel, onValueChange = {}, readOnly = true, label = { Text("Unidad *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unidadExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(expanded = unidadExpanded, onDismissRequest = { unidadExpanded = false }) {
                    unidadesConEstatus.forEach { (u, ocupada) ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        u.displayUbicacion(),
                                        modifier = Modifier.weight(1f),
                                        color = if (ocupada) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (ocupada) {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = StatusWarning.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                "Ocupada",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = StatusWarning
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = { if (!ocupada) { unidadId = u.id; unidadExpanded = false } },
                            enabled = !ocupada
                        )
                    }
                }
            }
        }

        if (errorMsg != null) {
            Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        val enabled = nombre.isNotBlank() && email.isNotBlank() && password.length >= 6 &&
                      (rolId != 1 || unidadId != null) && !isLoading
        Button(
            onClick = { onSubmit(nombre, apellido, email, password, rolId, unidadId) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = AdminPurple)
        ) {
            if (isLoading) { CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) }
            else { Text("Crear usuario") }
        }
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun UserCard(u: Usuario, onRoleChange: (Int) -> Unit, onDelete: () -> Unit, onVerDetalle: (() -> Unit)? = null, onVerTareas: (() -> Unit)? = null) {
    val (roleColor, roleName) = when (u.fkRolUsuario) {
        2    -> SecurityGreen  to "Seguridad"
        3    -> AdminPurple    to "Admin"
        4    -> CleaningOrange to "Limpieza"
        else -> ResidentBlue   to "Residente"
    }
    var showMenu      by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    GuardianCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AvatarCircle(
                    initials = u.nombre.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
                    color = roleColor
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(u.email ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { showMenu = true }) { RoleBadge(roleName, roleColor) }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        listOf(1 to "Residente", 2 to "Seguridad", 3 to "Admin", 4 to "Limpieza").forEach { (id, name) ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { onRoleChange(id); showMenu = false })
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("Eliminar usuario", color = StatusDanger) },
                            leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = StatusDanger) },
                            onClick = { showMenu = false; confirmDelete = true }
                        )
                    }
                }
            }
            if (confirmDelete) {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "¿Eliminar a ${u.nombre}? Se borrarán todos sus datos y podrá volver a registrarse con el mismo correo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusDanger
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = { confirmDelete = false }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                        Button(
                            onClick = { onDelete(); confirmDelete = false },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DeleteForever, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Eliminar", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (!confirmDelete) {
                onVerDetalle?.let {
                    TextButton(
                        onClick = it,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Info, null, modifier = Modifier.size(14.dp), tint = ResidentBlue)
                        Spacer(Modifier.width(4.dp))
                        Text("Ver detalle y cuota", style = MaterialTheme.typography.labelSmall, color = ResidentBlue)
                    }
                }
                onVerTareas?.let {
                    TextButton(
                        onClick = it,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.CleaningServices, null, modifier = Modifier.size(14.dp), tint = CleaningOrange)
                        Spacer(Modifier.width(4.dp))
                        Text("Gestionar tareas del día", style = MaterialTheme.typography.labelSmall, color = CleaningOrange)
                    }
                }
            }
        }
    }
}

@Composable
fun AdminReportsScreen(vm: AdminViewModel) {
    val reportes  by vm.reportes.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

    var filter by remember { mutableStateOf("Todos") }
    val filters = listOf("Todos", "Pendiente", "En proceso", "Resuelto")
    val filtered = if (filter == "Todos") reportes else reportes.filter { it.estatus == filter }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Todos los Reportes", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
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
                    Text("No hay reportes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        items(filtered, key = { it.id ?: it.titulo }) { r ->
            AdminReportCard(r, onStatusChange = { newStatus -> vm.actualizarEstatusReporte(r.id ?: return@AdminReportCard, newStatus) })
        }
    }
}

@Composable
private fun AdminReportCard(r: Reporte, onStatusChange: (String) -> Unit) {
    val statusColor = when (r.estatus) {
        "Resuelto"   -> StatusSuccess
        "En proceso" -> StatusInfo
        else         -> StatusWarning
    }
    var showMenu by remember { mutableStateOf(false) }

    GuardianCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("#${r.id ?: "—"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    StatusChip(r.estatus, statusColor)
                    if (r.esUrgente) StatusChip("Urgente", StatusDanger)
                }
                Spacer(Modifier.height(2.dp))
                Text(r.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("Usuario #${r.fkUsuario ?: "—"} · ${r.fechaCreacion ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    listOf("Pendiente", "En proceso", "Resuelto").forEach { s ->
                        DropdownMenuItem(text = { Text(s) }, onClick = { onStatusChange(s); showMenu = false })
                    }
                }
            }
        }
    }
}

// ── Admin: Gestión de Unidades ────────────────────────────────────────────────

@Composable
fun AdminUnitsScreen(vm: AdminViewModel) {
    val unidades  by vm.unidades.collectAsStateWithLifecycle()
    val usuarios  by vm.usuarios.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

    var showNewDialog    by remember { mutableStateOf(false) }
    var unidadToAssign   by remember { mutableStateOf<Unidad?>(null) }
    var unidadToDeactivate by remember { mutableStateOf<Unidad?>(null) }

    val residentes = usuarios.filter { it.fkRolUsuario == 1 || it.fkRolUsuario == null }

    val unidadesOrdenadas = remember(unidades) {
        unidades.sortedWith(compareBy({ it.torre }, { it.piso }, { it.numero }))
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Gestión de Unidades", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (isLoading) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            if (unidades.isEmpty() && !isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No hay unidades registradas", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            itemsIndexed(unidadesOrdenadas) { index, u ->
                val bloque = u.torre
                val bloqueAnterior = unidadesOrdenadas.getOrNull(index - 1)?.torre
                if (bloque != null && bloque != bloqueAnterior) {
                    Column {
                        if (index > 0) Spacer(Modifier.height(8.dp))
                        Text(
                            "Bloque $bloque",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = AdminPurple,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        HorizontalDivider(color = AdminPurple.copy(alpha = 0.3f))
                        Spacer(Modifier.height(4.dp))
                    }
                }
                val residenteAsignado = usuarios.find { it.fkUnidad == u.id }
                GuardianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Apartment, null, tint = AdminPurple, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.displayUbicacion(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(u.tipo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (residenteAsignado != null) {
                                Text(residenteAsignado.nombre, style = MaterialTheme.typography.labelSmall, color = SecurityGreen)
                            } else {
                                Text("Sin asignar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { unidadToAssign = u }, contentPadding = PaddingValues(4.dp)) {
                                Text("Asignar", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(
                                onClick = { unidadToDeactivate = u },
                                contentPadding = PaddingValues(4.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = StatusDanger)
                            ) {
                                Text("Baja", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showNewDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = AdminPurple
        ) {
            Icon(Icons.Default.Add, null)
        }
    }

    if (showNewDialog) {
        NewUnidadDialog(
            onDismiss = { showNewDialog = false },
            onSubmit  = { num, torre, piso, tipo -> vm.crearUnidad(num, torre, piso, tipo); showNewDialog = false }
        )
    }

    unidadToAssign?.let { u ->
        AsignarResidenteDialog(
            unidad = u,
            residentes = residentes,
            residenteActual = usuarios.find { it.fkUnidad == u.id },
            onDismiss = { unidadToAssign = null },
            onAssign  = { userId -> vm.asignarResidente(userId, u.id); unidadToAssign = null },
            onRemove  = { userId -> vm.asignarResidente(userId, null); unidadToAssign = null }
        )
    }

    unidadToDeactivate?.let { u ->
        AlertDialog(
            onDismissRequest = { unidadToDeactivate = null },
            title = { Text("Dar de baja unidad ${u.numero}") },
            text  = { Text("Se desactivará la unidad. No es posible si tiene cuotas pendientes.") },
            confirmButton = {
                Button(
                    onClick = { vm.darDeBajaUnidad(u.id, u.numero); unidadToDeactivate = null },
                    colors  = ButtonDefaults.buttonColors(containerColor = StatusDanger)
                ) { Text("Dar de baja") }
            },
            dismissButton = { TextButton(onClick = { unidadToDeactivate = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun NewUnidadDialog(onDismiss: () -> Unit, onSubmit: (String, String?, Int, String) -> Unit) {
    val bloques = listOf("A", "B", "C")
    val pisos   = listOf(1, 2, 3, 4)
    val deptos  = listOf(1, 2, 3, 4, 5)

    var bloque         by remember { mutableStateOf("A") }
    var piso           by remember { mutableStateOf(1) }
    var depto          by remember { mutableStateOf(1) }
    var bloqueExpanded by remember { mutableStateOf(false) }
    var pisoExpanded   by remember { mutableStateOf(false) }
    var deptoExpanded  by remember { mutableStateOf(false) }

    val numero = depto.toString().padStart(2, '0')

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva Unidad") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = AdminPurple.copy(alpha = 0.10f))) {
                    Text(
                        "Bloque $bloque · Piso $piso · Depto $numero",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AdminPurple
                    )
                }
                ExposedDropdownMenuBox(expanded = bloqueExpanded, onExpandedChange = { bloqueExpanded = it }) {
                    OutlinedTextField(
                        value = "Bloque $bloque", onValueChange = {}, readOnly = true,
                        label = { Text("Bloque") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(bloqueExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = bloqueExpanded, onDismissRequest = { bloqueExpanded = false }) {
                        bloques.forEach { b -> DropdownMenuItem(text = { Text("Bloque $b") }, onClick = { bloque = b; bloqueExpanded = false }) }
                    }
                }
                ExposedDropdownMenuBox(expanded = pisoExpanded, onExpandedChange = { pisoExpanded = it }) {
                    OutlinedTextField(
                        value = "Piso $piso", onValueChange = {}, readOnly = true,
                        label = { Text("Piso") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(pisoExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = pisoExpanded, onDismissRequest = { pisoExpanded = false }) {
                        pisos.forEach { p -> DropdownMenuItem(text = { Text("Piso $p") }, onClick = { piso = p; pisoExpanded = false }) }
                    }
                }
                ExposedDropdownMenuBox(expanded = deptoExpanded, onExpandedChange = { deptoExpanded = it }) {
                    OutlinedTextField(
                        value = "Departamento $numero", onValueChange = {}, readOnly = true,
                        label = { Text("Departamento") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(deptoExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = deptoExpanded, onDismissRequest = { deptoExpanded = false }) {
                        deptos.forEach { d ->
                            val n = d.toString().padStart(2, '0')
                            DropdownMenuItem(text = { Text("Departamento $n") }, onClick = { depto = d; deptoExpanded = false })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(numero, bloque, piso, "depto") }) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun AsignarResidenteDialog(
    unidad: Unidad,
    residentes: List<Usuario>,
    residenteActual: Usuario?,
    onDismiss: () -> Unit,
    onAssign: (Int) -> Unit,
    onRemove: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Asignar residente · Unidad ${unidad.numero}") },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (residenteActual != null) {
                    item {
                        Text("Actual: ${residenteActual.nombre}", style = MaterialTheme.typography.labelMedium, color = SecurityGreen)
                        Spacer(Modifier.height(4.dp))
                        OutlinedButton(
                            onClick = { onRemove(residenteActual.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDanger)
                        ) { Text("Quitar asignación") }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
                items(residentes.filter { it.id != residenteActual?.id }, key = { it.id }) { r ->
                    OutlinedButton(onClick = { onAssign(r.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(r.nombre)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

// ── Admin: Estado de Cuenta ───────────────────────────────────────────────────

@Composable
fun AdminAccountScreen(vm: AdminViewModel) {
    val usuarios     by vm.usuarios.collectAsStateWithLifecycle()
    val cuotasUsuario by vm.cuotasUsuario.collectAsStateWithLifecycle()
    val morosos      by vm.morosos.collectAsStateWithLifecycle()
    val clipboard    = LocalClipboard.current
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    val residentes = usuarios.filter { it.fkRolUsuario == 1 }
    var selectedUsuario by remember { mutableStateOf<Usuario?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Estado de Cuenta", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = activeTab == 0, onClick = { activeTab = 0 }, label = { Text("Por residente") })
                FilterChip(selected = activeTab == 1, onClick = { activeTab = 1 }, label = { Text("Morosos") })
            }
        }

        if (activeTab == 0) {
            item {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedUsuario?.nombre ?: "Selecciona residente",
                        onValueChange = {}, readOnly = true,
                        label = { Text("Residente") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        residentes.forEach { r ->
                            DropdownMenuItem(text = { Text(r.nombre) }, onClick = {
                                selectedUsuario = r
                                vm.loadCuotasUsuario(r.id)
                                expanded = false
                            })
                        }
                    }
                }
            }

            if (selectedUsuario != null && cuotasUsuario.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Historial de cuotas", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = {
                            val texto = buildString {
                                appendLine("Estado de cuenta: ${selectedUsuario?.nombre}")
                                appendLine("─".repeat(40))
                                cuotasUsuario.forEach { c ->
                                    val rec = c.calcularRecargo()
                                    appendLine("${c.periodo}  ${c.estatus.uppercase().padEnd(12)} ${"%.2f".format(c.monto ?: 0.0)}${if (rec > 0) " + recargo ${"%.2f".format(rec)}" else ""}")
                                }
                            }
                            coroutineScope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("cuenta", texto))) }
                        }) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Copiar")
                        }
                    }
                }

                items(cuotasUsuario) { c ->
                    val recargo = c.calcularRecargo()
                    val statusColor = when (c.estatus) {
                        "pagado"   -> StatusSuccess
                        "vencido"  -> StatusDanger
                        else       -> StatusWarning
                    }
                    GuardianCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.periodo, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("Vence: ${c.fechaVencimiento ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                StatusChip(c.estatus, statusColor)
                                Text((c.monto ?: 0.0).toMoneda(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                if (recargo > 0) Text("+${recargo.toMoneda()} recargo", style = MaterialTheme.typography.labelSmall, color = StatusDanger)
                            }
                        }
                    }
                }
            } else if (selectedUsuario != null) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { Text("Sin cuotas registradas") } }
            }
        } else {
            // Tab morosos
            if (morosos.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("Sin morosos", color = StatusSuccess) } }
            }
            items(morosos) { m ->
                val recargo = Cuota(monto = m.monto, fechaVencimiento = m.fechaVencimiento, estatus = "vencido").calcularRecargo()
                GuardianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.nombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(m.email ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Periodo ${m.periodo} · Vence ${m.fechaVencimiento ?: "—"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text((m.monto + recargo).toMoneda(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = StatusDanger)
                            if (recargo > 0) Text("+${recargo.toMoneda()} recargo", style = MaterialTheme.typography.labelSmall, color = StatusWarning)
                        }
                    }
                }
            }
        }
    }
}

// ── Admin: Gestión de tareas de limpieza ─────────────────────────────────────

@Composable
private fun CleaningTasksAdminDialog(
    usuario: Usuario,
    tareas: List<TareaLimpieza>,
    onDismiss: () -> Unit,
    onAddTarea: (titulo: String, area: String?, prioridad: String, notas: String?) -> Unit
) {
    var showAddForm  by remember { mutableStateOf(false) }
    var titulo       by remember { mutableStateOf("") }
    var area         by remember { mutableStateOf("") }
    var notas        by remember { mutableStateOf("") }
    var prioridad    by remember { mutableStateOf("normal") }
    var prioExpanded by remember { mutableStateOf(false) }
    val prioridades  = listOf("baja", "normal", "alta")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CleaningServices, null, tint = CleaningOrange, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tareas de limpieza", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(usuario.nombre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }

                HorizontalDivider()

                val pendientes  = tareas.count { it.estatus == "pendiente" }
                val enProceso   = tareas.count { it.estatus == "en_proceso" }
                val completadas = tareas.count { it.estatus == "completada" }
                if (tareas.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (pendientes  > 0) StatusChip("$pendientes pendiente${if (pendientes > 1) "s" else ""}",  StatusWarning)
                        if (enProceso   > 0) StatusChip("$enProceso en proceso",                                     StatusInfo)
                        if (completadas > 0) StatusChip("$completadas hecha${if (completadas > 1) "s" else ""}",     StatusSuccess)
                    }
                }

                // Plain Column replaces LazyColumn — avoids nested-scroll conflict with outer verticalScroll
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (tareas.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                            Text("Sin tareas asignadas hoy", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        tareas.forEach { t ->
                            val statusColor = when (t.estatus) {
                                "completada" -> StatusSuccess
                                "en_proceso" -> StatusInfo
                                else         -> StatusWarning
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.08f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                t.titulo,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                textDecoration = if (t.estatus == "completada") androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                                color = if (t.estatus == "completada") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                            )
                                            t.area?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                        StatusChip(t.estatus.replace("_", " "), statusColor)
                                    }
                                    t.notas?.takeIf { it.isNotBlank() }?.let { n ->
                                        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.AutoMirrored.Filled.Notes, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(n, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                    val prioColor = when (t.prioridad) { "alta" -> StatusDanger; "normal" -> StatusWarning; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                                    StatusChip(t.prioridad, prioColor)
                                }
                            }
                        }
                    }
                }

                TextButton(onClick = { showAddForm = !showAddForm }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (showAddForm) Icons.Default.ExpandLess else Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if (showAddForm) "Cancelar" else "Agregar tarea")
                }

                if (showAddForm) {
                    HorizontalDivider()
                    OutlinedTextField(value = titulo, onValueChange = { titulo = it }, label = { Text("Titulo *") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = area, onValueChange = { area = it }, label = { Text("Area (opcional)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = notas, onValueChange = { notas = it }, label = { Text("Notas (opcional)") },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3)
                    ExposedDropdownMenuBox(expanded = prioExpanded, onExpandedChange = { prioExpanded = it }) {
                        OutlinedTextField(
                            value = prioridad, onValueChange = {}, readOnly = true,
                            label = { Text("Prioridad") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(prioExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(expanded = prioExpanded, onDismissRequest = { prioExpanded = false }) {
                            prioridades.forEach { p ->
                                DropdownMenuItem(text = { Text(p) }, onClick = { prioridad = p; prioExpanded = false })
                            }
                        }
                    }
                    Button(
                        onClick = {
                            onAddTarea(titulo, area.ifBlank { null }, prioridad, notas.ifBlank { null })
                            titulo = ""; area = ""; notas = ""; prioridad = "normal"; showAddForm = false
                        },
                        enabled = titulo.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CleaningOrange)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Asignar tarea")
                    }
                }
            }
        }
    }
}

// ── Admin: Detalle de residente ───────────────────────────────────────────────

@Composable
private fun ResidenteDetalleDialog(
    usuario: Usuario,
    unidad: Unidad?,
    cuotas: List<Cuota>,
    vm: AdminViewModel,
    onDismiss: () -> Unit
) {
    val cuotaActiva = cuotas.firstOrNull { it.estatus != "pagado" } ?: cuotas.firstOrNull()

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle(
                        initials = usuario.nombre.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
                        color = ResidentBlue
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(usuario.nombre, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(usuario.email ?: "—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
                }

                HorizontalDivider()

                // Ubicacion
                if (unidad != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = ResidentBlue.copy(alpha = 0.08f)), shape = RoundedCornerShape(8.dp)) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            unidad.torre?.let {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("BLOQUE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ResidentBlue)
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("PISO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${unidad.piso}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ResidentBlue)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("DEPTO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(unidad.numero, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = ResidentBlue)
                            }
                        }
                    }
                } else {
                    Text("Sin unidad asignada", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                // Cuota
                Text("Estado de cuenta", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                if (cuotaActiva != null) {
                    val statusColor = when (cuotaActiva.estatus) {
                        "pagado"  -> StatusSuccess
                        "vencido" -> StatusDanger
                        else      -> StatusWarning
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.08f)), shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(cuotaActiva.periodo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(cuotaActiva.monto?.let { "${"%.2f".format(it)}" } ?: "—",
                                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = statusColor)
                                }
                                StatusChip(cuotaActiva.estatus, statusColor)
                            }
                            cuotaActiva.fechaVencimiento?.let {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(Icons.Default.CalendarToday, null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Vence: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                    if (cuotas.size > 1) {
                        Text("${cuotas.size} cuotas en total · ${cuotas.count { it.estatus == "pagado" }} pagadas",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (cuotaActiva.estatus != "pagado") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                vm.marcarCuotaPagada(cuotaActiva.id)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Marcar como pagado")
                        }
                    }
                } else if (cuotas.isEmpty()) {
                    Text("Sin cuotas registradas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
            }
        }
    }
}

@Composable
private fun ReservasAdminDialog(
    reservas: List<Reserva>,
    amenidades: List<Amenidad>,
    onDismiss: () -> Unit
) {
    val amenidadMap = amenidades.associateBy { it.id }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Reservas activas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                if (reservas.isEmpty()) {
                    EmptyState("Sin reservas registradas")
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(reservas, key = { it.id ?: 0 }) { r ->
                            val amenidadNombre = amenidadMap[r.fkAmenidad]?.nombre ?: "Amenidad #${r.fkAmenidad}"
                            val (chipColor, chipText) = when (r.estatus) {
                                "activa"    -> SecurityGreen to "Activa"
                                "cancelada" -> StatusDanger  to "Cancelada"
                                else        -> StatusWarning to r.estatus
                            }
                            GuardianCard {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(amenidadNombre, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            "${r.fecha ?: "—"}  ·  ${r.slot ?: "—"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StatusChip(chipText, chipColor)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cerrar") }
            }
        }
    }
}

@Composable
private fun CobrarMensualidadDialog(
    onDismiss: () -> Unit,
    onCobrar: (Double) -> Unit
) {
    var montoText by remember { mutableStateOf("1500.00") }
    val monto = montoText.toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cobrar mensualidad") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Genera una cuota pendiente para todos los residentes activos que no tengan cuota del mes actual.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = montoText,
                    onValueChange = { montoText = it },
                    label = { Text("Monto ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = monto == null || monto <= 0
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { monto?.let { onCobrar(it); onDismiss() } },
                enabled = monto != null && monto > 0,
                colors = ButtonDefaults.buttonColors(containerColor = AdminPurple)
            ) { Text("Cobrar a todos") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
