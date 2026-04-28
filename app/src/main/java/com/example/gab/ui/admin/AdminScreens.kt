@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.gab.ui.admin

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
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
            IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }
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
    val stats     by vm.stats.collectAsStateWithLifecycle()
    val morosos   by vm.morosos.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

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
                StatCard("Reservas",          "${stats.reservasHoy}",       Icons.Default.EventAvailable, SecurityGreen, Modifier.weight(1f))
                StatCard("Morosos",           "${morosos.size}",            Icons.Default.Warning,        StatusDanger,  Modifier.weight(1f))
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
}

@Composable
fun AdminUsersScreen(vm: AdminViewModel) {
    val usuarios            by vm.usuarios.collectAsStateWithLifecycle()
    val unidadesConEstatus  by vm.unidadesConEstatus.collectAsStateWithLifecycle()
    val isLoading           by vm.isLoading.collectAsStateWithLifecycle()
    val createState  by vm.createState.collectAsStateWithLifecycle()

    var searchQuery     by remember { mutableStateOf("") }
    var rolFiltro       by remember { mutableStateOf<Int?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

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

            items(filtered) { u ->
                UserCard(
                    u,
                    onRoleChange  = { vm.actualizarRol(u.id, it) },
                    onDeactivate  = { vm.desactivarUsuario(u.id, u.nombre) }
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
private fun UserCard(u: Usuario, onRoleChange: (Int) -> Unit, onDeactivate: () -> Unit) {
    val (roleColor, roleName) = when (u.fkRolUsuario) {
        2    -> SecurityGreen  to "Seguridad"
        3    -> AdminPurple    to "Admin"
        4    -> CleaningOrange to "Limpieza"
        else -> ResidentBlue   to "Residente"
    }
    var showMenu    by remember { mutableStateOf(false) }
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
                            text = { Text("Desactivar", color = StatusDanger) },
                            leadingIcon = { Icon(Icons.Default.PersonOff, null, tint = StatusDanger) },
                            onClick = { showMenu = false; confirmDelete = true }
                        )
                    }
                }
            }
            if (confirmDelete) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("¿Desactivar a ${u.nombre}?", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { confirmDelete = false }) { Text("No") }
                    Button(
                        onClick = { onDeactivate(); confirmDelete = false },
                        colors = ButtonDefaults.buttonColors(containerColor = StatusDanger),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text("Sí", style = MaterialTheme.typography.labelMedium) }
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

        items(filtered) { r ->
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

            items(unidades) { u ->
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
    var numero  by remember { mutableStateOf("") }
    var torre   by remember { mutableStateOf("") }
    var pisoStr by remember { mutableStateOf("1") }
    var tipo    by remember { mutableStateOf("depto") }
    val tipos = listOf("depto", "casa")
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva Unidad") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = numero,  onValueChange = { numero = it  }, label = { Text("Número") },           modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = torre,   onValueChange = { torre = it   }, label = { Text("Torre (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = pisoStr, onValueChange = { pisoStr = it.filter(Char::isDigit) }, label = { Text("Piso") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = tipo, onValueChange = {}, readOnly = true,
                        label = { Text("Tipo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        tipos.forEach { t -> DropdownMenuItem(text = { Text(t) }, onClick = { tipo = t; expanded = false }) }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (numero.isNotBlank()) onSubmit(numero, torre.ifBlank { null }, pisoStr.toIntOrNull() ?: 1, tipo) },
                enabled = numero.isNotBlank()
            ) { Text("Crear") }
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
                items(residentes.filter { it.id != residenteActual?.id }) { r ->
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
    val clipboard    = LocalClipboardManager.current

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
                            clipboard.setText(AnnotatedString(texto))
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
