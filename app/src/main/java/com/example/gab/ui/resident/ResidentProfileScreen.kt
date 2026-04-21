package com.example.gab.ui.resident

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.gab.ui.common.AvatarCircle
import com.example.gab.ui.common.GuardianCard
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.theme.ResidentBlue
import com.example.gab.ui.theme.StatusDanger

@Composable
fun ResidentProfileScreen(user: AppUser, onLogout: () -> Unit) {
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
                InfoRow(Icons.Default.Home, "Apartamento", user.apartment.ifBlank { "—" })
                InfoRow(Icons.Default.Phone, "Teléfono", "+52 310 000 0000")
                InfoRow(Icons.Default.Email, "Correo", "residente@guardianapp.co")
            }
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
