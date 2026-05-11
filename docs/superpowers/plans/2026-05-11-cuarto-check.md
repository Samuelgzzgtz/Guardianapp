# Cuarto Check Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 5 independent bugs/features identified in QA pass 4 — cleaning modal scroll, reservations UX, auth confirm edge function, vehicle auto-registration, payment admin trigger.

**Architecture:** All changes are contained within the existing Android app (Kotlin/Compose) and Supabase Edge Functions (Deno). No new files, no new dependencies. Areas are fully independent.

**Tech Stack:** Kotlin/Compose, Supabase Kotlin SDK, Deno/TypeScript Edge Functions, HttpURLConnection (already in AdminRepository)

---

### Task 1: Cleaning Modal Scroll Fix

**Files:**
- Modify: `app/src/main/java/com/example/gab/ui/admin/AdminScreens.kt` (CleaningTasksAdminDialog)

- [ ] **Step 1: Add missing scroll imports at top of AdminScreens.kt**

The file already has `foundation.layout.*` but needs explicit scroll imports. Add after existing foundation imports:
```kotlin
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
```

- [ ] **Step 2: Replace CleaningTasksAdminDialog with scrollable version**

Find and replace the entire `CleaningTasksAdminDialog` function (lines ~1058–1194). The fix: wrap outer Column in `verticalScroll`, replace inner `LazyColumn` with `Column` + `forEach`.

Replace this function:
```kotlin
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
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

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

                LazyColumn(
                    modifier = Modifier.heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (tareas.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                Text("Sin tareas asignadas hoy", color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        items(tareas, key = { it.id ?: it.titulo }) { t ->
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
```

With this fixed version (Column with verticalScroll, forEach instead of LazyColumn):
```kotlin
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

                // Task list as regular Column (no LazyColumn — avoids nested scroll conflict)
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
```

- [ ] **Step 3: Compile check**
```
./gradlew compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/example/gab/ui/admin/AdminScreens.kt
git commit -m "fix: modal tareas limpieza scrollable — reemplaza LazyColumn por Column+forEach"
```

---

### Task 2: Reservations Confirm Button UX Fix

**Files:**
- Modify: `app/src/main/java/com/example/gab/ui/resident/ResidentAmenitiesScreen.kt` (NewReservaDialog)

- [ ] **Step 1: Update NewReservaDialog to add auto-slot-selection and sin-disponibilidad feedback**

Replace the entire `NewReservaDialog` composable:
```kotlin
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
    var sinDisponibilidad by remember { mutableStateOf(false) }
    val today = java.time.LocalDate.now().toString()
    val allSlots = listOf(
        "08:00 - 10:00", "10:00 - 12:00", "12:00 - 14:00",
        "14:00 - 16:00", "16:00 - 18:00", "18:00 - 20:00"
    )

    // Auto-select first available slot when loaded slots change
    LaunchedEffect(slotsTomados) {
        if (fecha.length < 10) return@LaunchedEffect
        val primerLibre = allSlots.firstOrNull { it !in slotsTomados }
        when {
            primerLibre == null           -> sinDisponibilidad = true
            slot in slotsTomados          -> { slot = primerLibre; sinDisponibilidad = false }
            else                          -> sinDisponibilidad = false
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
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (fecha.isNotBlank() && slot !in slotsTomados) onSubmit(fecha, slot) },
                enabled  = fecha.length == 10 && fecha >= today && !sinDisponibilidad && slot !in slotsTomados,
                colors   = ButtonDefaults.buttonColors(containerColor = ResidentBlue)
            ) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
```

- [ ] **Step 2: Compile check**
```
./gradlew compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**
```bash
git add app/src/main/java/com/example/gab/ui/resident/ResidentAmenitiesScreen.kt
git commit -m "fix: reserva — auto-selecciona primer slot libre, muestra sin-disponibilidad"
```

---

### Task 3: Auth Confirm Edge Function

**Files:**
- Modify: `supabase/functions/auth-confirm/index.ts`

- [ ] **Step 1: Rewrite auth-confirm/index.ts with error detection**

The Supabase redirect after email verification lands on this function. If verification failed, Supabase adds `?error=xxx&error_description=yyy` to the URL. On success, no error params are present. The JS in the HTML also checks the hash for errors from implicit flow.

```typescript
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

serve(async (req) => {
  const url = new URL(req.url)
  const error = url.searchParams.get("error")
  const errorDesc = url.searchParams.get("error_description")

  if (error) {
    const msg = errorDesc
      ? decodeURIComponent(errorDesc.replace(/\+/g, " "))
      : "Link de verificación inválido o expirado."
    return new Response(errorPage(msg), {
      headers: { "Content-Type": "text/html; charset=utf-8" },
    })
  }

  return new Response(successPage, {
    headers: { "Content-Type": "text/html; charset=utf-8" },
  })
})

// ── Success page ───────────────────────────────────────────────────────────────
const successPage = `<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Cuenta Verificada — GuardianApp</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    background: linear-gradient(135deg, #1A1A2E 0%, #16213E 50%, #0F3460 100%);
    min-height: 100vh;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 24px;
  }
  .card {
    background: #FFFFFF;
    border-radius: 24px;
    padding: 56px 48px;
    max-width: 480px;
    width: 100%;
    text-align: center;
    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
  }
  .logo-wrap {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 10px;
    margin-bottom: 32px;
  }
  .logo-icon { width: 36px; height: 36px; }
  .logo-text { font-size: 18px; font-weight: 700; letter-spacing: 1px; color: #1A1A2E; }
  .icon-wrap {
    width: 88px; height: 88px;
    background: #E8F5E9;
    border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    margin: 0 auto 28px;
  }
  h1 { font-size: 24px; font-weight: 700; color: #1A1A2E; margin-bottom: 14px; line-height: 1.3; }
  p { font-size: 15px; color: #6B7280; line-height: 1.7; margin-bottom: 32px; }
  .btn {
    display: inline-block;
    background: linear-gradient(135deg, #1A73E8, #0F3460);
    color: #FFFFFF;
    font-size: 15px; font-weight: 600;
    padding: 15px 40px;
    border-radius: 14px;
    text-decoration: none;
    letter-spacing: 0.3px;
    transition: opacity 0.2s;
    margin-bottom: 8px;
  }
  .btn:hover { opacity: 0.88; }
  .divider { height: 1px; background: #F0F0F0; margin: 28px 0; }
  .footer { font-size: 13px; color: #9CA3AF; }
  .footer a { color: #1A73E8; text-decoration: none; }
  @media (max-width: 480px) { .card { padding: 36px 24px; } h1 { font-size: 20px; } }
</style>
</head>
<body>
<div class="card">
  <div class="logo-wrap">
    <svg class="logo-icon" viewBox="0 0 36 36" fill="none">
      <circle cx="18" cy="18" r="18" fill="#1A1A2E"/>
      <path d="M18 8L10 12v8c0 4.4 3.4 8.5 8 9.5 4.6-1 8-5.1 8-9.5v-8l-8-4z" fill="#1A73E8" opacity="0.9"/>
      <path d="M14 18l2.5 2.5L22 15" stroke="white" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>
    <span class="logo-text">GuardianApp</span>
  </div>
  <div class="icon-wrap">
    <svg width="44" height="44" viewBox="0 0 44 44" fill="none">
      <circle cx="22" cy="22" r="22" fill="#4CAF50"/>
      <path d="M12 22.5L18.5 29L32 16" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>
  </div>
  <h1>¡Cuenta verificada exitosamente!</h1>
  <p>Tu correo electrónico ha sido confirmado.<br>Ya puedes acceder a GuardianApp.</p>
  <a href="guardianapp://home" class="btn">Abrir la aplicación</a>
  <div class="divider"></div>
  <div class="footer">¿Problemas para acceder? <a href="mailto:soporte@guardianapp.com">Contáctanos</a></div>
</div>
<script>
  // Handle error delivered via URL hash (implicit flow)
  const hash = new URLSearchParams(window.location.hash.replace('#',''));
  if (hash.get('error')) {
    document.querySelector('.icon-wrap').innerHTML = '<svg width="44" height="44" viewBox="0 0 44 44" fill="none"><circle cx="22" cy="22" r="22" fill="#EF5350"/><path d="M15 15l14 14M29 15l-14 14" stroke="white" stroke-width="3" stroke-linecap="round"/></svg>';
    document.querySelector('h1').textContent = 'Link de verificación inválido';
    document.querySelector('p').textContent = hash.get('error_description') || 'El enlace expiró o ya fue usado. Solicita un nuevo correo de confirmación.';
    document.querySelector('.btn').style.display = 'none';
  }
</script>
</body>
</html>`

// ── Error page ─────────────────────────────────────────────────────────────────
function errorPage(message: string): string {
  return `<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Error de verificación — GuardianApp</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    background: linear-gradient(135deg, #1A1A2E 0%, #16213E 50%, #0F3460 100%);
    min-height: 100vh;
    display: flex; align-items: center; justify-content: center;
    padding: 24px;
  }
  .card {
    background: #FFFFFF; border-radius: 24px; padding: 56px 48px;
    max-width: 480px; width: 100%; text-align: center;
    box-shadow: 0 20px 60px rgba(0,0,0,0.3);
  }
  .logo-wrap { display: flex; align-items: center; justify-content: center; gap: 10px; margin-bottom: 32px; }
  .logo-text { font-size: 18px; font-weight: 700; letter-spacing: 1px; color: #1A1A2E; }
  .icon-wrap {
    width: 88px; height: 88px; background: #FFEBEE;
    border-radius: 50%; display: flex; align-items: center; justify-content: center;
    margin: 0 auto 28px;
  }
  h1 { font-size: 24px; font-weight: 700; color: #1A1A2E; margin-bottom: 14px; }
  p { font-size: 15px; color: #6B7280; line-height: 1.7; margin-bottom: 32px; }
  .footer { font-size: 13px; color: #9CA3AF; }
  .footer a { color: #1A73E8; text-decoration: none; }
  @media (max-width: 480px) { .card { padding: 36px 24px; } }
</style>
</head>
<body>
<div class="card">
  <div class="logo-wrap">
    <svg width="36" height="36" viewBox="0 0 36 36" fill="none">
      <circle cx="18" cy="18" r="18" fill="#1A1A2E"/>
      <path d="M18 8L10 12v8c0 4.4 3.4 8.5 8 9.5 4.6-1 8-5.1 8-9.5v-8l-8-4z" fill="#1A73E8" opacity="0.9"/>
    </svg>
    <span class="logo-text">GuardianApp</span>
  </div>
  <div class="icon-wrap">
    <svg width="44" height="44" viewBox="0 0 44 44" fill="none">
      <circle cx="22" cy="22" r="22" fill="#EF5350"/>
      <path d="M15 15l14 14M29 15l-14 14" stroke="white" stroke-width="3" stroke-linecap="round"/>
    </svg>
  </div>
  <h1>Link de verificación inválido</h1>
  <p>${message}</p>
  <div class="footer">¿Necesitas ayuda? <a href="mailto:soporte@guardianapp.com">Contáctanos</a></div>
</div>
</body>
</html>`
}
```

- [ ] **Step 2: Commit**
```bash
git add supabase/functions/auth-confirm/index.ts
git commit -m "fix: auth-confirm — detecta errores de Supabase, muestra página de error correcta"
```

---

### Task 4: Vehicle Plate Auto-Registration

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/repository/SecurityRepository.kt`
- Modify: `app/src/main/java/com/example/gab/ui/security/viewmodel/SecurityViewModel.kt`
- Modify: `app/src/main/java/com/example/gab/ui/security/SecurityScreens.kt`

- [ ] **Step 1: Add registrarAccesoConId and cancelarAcceso to SecurityRepository**

Add at the end of `SecurityRepository`, before closing `}`:
```kotlin
    suspend fun registrarAccesoConId(
        residenteId: Int,
        guardiaId: Int,
        direccion: String,
        hora: String
    ): Result<Int> = runCatching {
        client.postgrest["accesolog"].insert(
            AccesoLog(fkResidente = residenteId, fkGuardia = guardiaId, direccion = direccion, horaRegistro = hora)
        )
        client.postgrest["accesolog"].select {
            filter {
                eq("fkresidente", residenteId)
                eq("fkguardia", guardiaId)
            }
            order("id", Order.DESCENDING)
            limit(1)
        }.decodeList<AccesoLog>().firstOrNull()?.id ?: 0
    }

    suspend fun cancelarAcceso(accesoId: Int): Result<Unit> = runCatching {
        client.postgrest["accesolog"].delete {
            filter { eq("id", accesoId) }
        }
    }
```

- [ ] **Step 2: Add autoRegistrado state and undo logic to SecurityViewModel**

Add two new state fields after `_vehiculosResidente`:
```kotlin
    private val _autoRegistrado = MutableStateFlow(false)
    val autoRegistrado: StateFlow<Boolean> = _autoRegistrado.asStateFlow()

    private val _ultimoAccesoId = MutableStateFlow(0)
```

Replace the existing `onPlacaTextoReconocido` function:
```kotlin
    fun onPlacaTextoReconocido(guardiaId: Int, texto: String) {
        _showPlacaCamera.value = false
        viewModelScope.launch {
            val placaEncontrada = PLACA_REGEX.find(texto.uppercase())?.value
            if (placaEncontrada == null) {
                _toastMessage.value = "No se detectó placa. Intenta con mejor iluminación."
                return@launch
            }
            val placaNormalizada = placaEncontrada.trim().replace(" ", "").replace("-", "").uppercase()
            val residente = _residenteParaPlaca.value
            val vehiculoCorrecto = if (residente != null) {
                _vehiculosResidente.value.firstOrNull { v ->
                    v.placa.uppercase().replace(" ", "").replace("-", "") == placaNormalizada
                }
            } else {
                repo.getVehiculoPorPlaca(placaNormalizada).getOrNull()
            }
            _placaResultado.value = Pair(placaNormalizada, vehiculoCorrecto)

            if (vehiculoCorrecto != null) {
                repo.guardarVisita(
                    "Vehículo ${vehiculoCorrecto.placa} (${vehiculoCorrecto.descripcion ?: ""})",
                    guardiaId, "PLACA"
                ).onSuccess { repo.getVisitas().onSuccess { _visitas.value = it } }

                if (residente != null) {
                    val hora = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                    repo.registrarAccesoConId(residente.id, guardiaId, "ENTRADA", hora)
                        .onSuccess { accesoId ->
                            _ultimoAccesoId.value = accesoId
                            _autoRegistrado.value = true
                            repo.getAccesoLog().onSuccess { _accesoLog.value = it }
                        }
                        .onFailure { _toastMessage.value = "Error al registrar acceso: ${it.message}" }
                }
            }
        }
    }
```

Add two new functions after `clearToast()`:
```kotlin
    fun cancelarUltimoAcceso() {
        val accesoId = _ultimoAccesoId.value
        if (accesoId == 0) return
        viewModelScope.launch {
            repo.cancelarAcceso(accesoId)
                .onSuccess {
                    _autoRegistrado.value = false
                    _ultimoAccesoId.value = 0
                    _toastMessage.value = "Acceso anulado"
                    repo.getAccesoLog().onSuccess { _accesoLog.value = it }
                }
                .onFailure { _toastMessage.value = "Error al anular: ${it.message}" }
        }
    }

    fun clearAutoRegistrado() { _autoRegistrado.value = false }
```

- [ ] **Step 3: Add auto-registration banner to SecurityPlacasScreen**

At the top of `SecurityPlacasScreen`, add state collection after existing `collectAsStateWithLifecycle()` calls:
```kotlin
    val autoRegistrado by vm.autoRegistrado.collectAsStateWithLifecycle()
```

In the `LazyColumn`, add this as the FIRST `item` block (before the title item):
```kotlin
        if (autoRegistrado) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SecurityGreen.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
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
```

- [ ] **Step 4: Compile check**
```
./gradlew compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/example/gab/data/repository/SecurityRepository.kt
git add app/src/main/java/com/example/gab/ui/security/viewmodel/SecurityViewModel.kt
git add app/src/main/java/com/example/gab/ui/security/SecurityScreens.kt
git commit -m "feat: auto-registro acceso al detectar placa coincidente con residente seleccionado"
```

---

### Task 5: Payment Admin Trigger + cron SQL

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/repository/AdminRepository.kt`
- Modify: `app/src/main/java/com/example/gab/ui/admin/viewmodel/AdminViewModel.kt`
- Modify: `app/src/main/java/com/example/gab/ui/admin/AdminScreens.kt`
- Create: `supabase/recordatorio_pago_cron.sql`

- [ ] **Step 1: Add dispararRecordatorioPago to AdminRepository**

Add import at top of AdminRepository.kt (already has `java.net.HttpURLConnection` and `java.net.URL`):
```kotlin
import org.json.JSONObject
```

Add at end of `AdminRepository` class before closing `}`:
```kotlin
    suspend fun dispararRecordatorioPago(): Result<Int> = runCatching {
        withContext(Dispatchers.IO) {
            val serviceKey = SupabaseClientProvider.SUPABASE_SERVICE_KEY
            val url = "${SupabaseClientProvider.SUPABASE_URL}/functions/v1/recordatorio-pago"
            val conn = java.net.URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $serviceKey")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.outputStream.bufferedWriter().use { it.write("{}") }
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            Regex(""""processed"\s*:\s*(\d+)""").find(response)?.groupValues?.get(1)?.toInt() ?: 0
        }
    }
```

Note: no new import needed — `withContext`, `Dispatchers`, `HttpURLConnection`, `URL` are already imported.

- [ ] **Step 2: Add dispararRecordatorioPago to AdminViewModel**

Add at end of `AdminViewModel` class before closing `}`:
```kotlin
    fun dispararRecordatorioPago() {
        viewModelScope.launch {
            _isLoading.value = true
            repo.dispararRecordatorioPago()
                .onSuccess { count -> _toastMessage.value = "Recordatorios enviados: $count usuarios" }
                .onFailure { _toastMessage.value = "Error al enviar recordatorios: ${it.message}" }
            _isLoading.value = false
        }
    }
```

- [ ] **Step 3: Add overflow menu to AdminShell topBarActions**

Replace the `topBarActions` block in `AdminShell`:
```kotlin
        topBarActions = {
            IconButton(onClick = onLogout) { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }
        }
```
With:
```kotlin
        topBarActions = {
            var showMenu by remember { mutableStateOf(false) }
            IconButton(onClick = { showMenu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Más opciones")
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Enviar recordatorios de pago") },
                    leadingIcon = { Icon(Icons.Default.Notifications, null) },
                    onClick = { vm.dispararRecordatorioPago(); showMenu = false }
                )
                DropdownMenuItem(
                    text = { Text("Cerrar sesión") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                    onClick = { onLogout(); showMenu = false }
                )
            }
        }
```

- [ ] **Step 4: Create cron SQL script**

Create `supabase/recordatorio_pago_cron.sql`:
```sql
-- Enable pg_cron and pg_net extensions (run once if not already enabled)
-- In Supabase Dashboard: Database → Extensions → enable pg_cron and pg_net

-- Schedule payment reminders every 7 days at 9:00 AM
-- Replace <SERVICE_ROLE_KEY> with your actual service role key from Supabase Dashboard → Settings → API
select cron.schedule(
  'recordatorio-pago-semanal',
  '0 9 */7 * *',
  $$
  select net.http_post(
    url     := 'https://spbrzuxvlljowwjawmkv.supabase.co/functions/v1/recordatorio-pago',
    headers := jsonb_build_object(
      'Authorization', 'Bearer <SERVICE_ROLE_KEY>',
      'Content-Type', 'application/json'
    ),
    body    := '{}'::jsonb
  )
  $$
);

-- To verify the schedule was created:
-- select * from cron.job;

-- To remove the schedule:
-- select cron.unschedule('recordatorio-pago-semanal');
```

- [ ] **Step 5: Compile check**
```
./gradlew compileDebugKotlin
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**
```bash
git add app/src/main/java/com/example/gab/data/repository/AdminRepository.kt
git add app/src/main/java/com/example/gab/ui/admin/viewmodel/AdminViewModel.kt
git add app/src/main/java/com/example/gab/ui/admin/AdminScreens.kt
git add supabase/recordatorio_pago_cron.sql
git commit -m "feat: botón admin para enviar recordatorios de pago + script cron pg_cron"
```

---

### Task 6: Final Build Verification

- [ ] **Step 1: Full debug build**
```
./gradlew assembleDebug
```
Expected: BUILD SUCCESSFUL, APK generated at `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 2: Commit plan**
```bash
git add docs/superpowers/plans/2026-05-11-cuarto-check.md
git commit -m "docs: plan implementación cuarto check"
```
