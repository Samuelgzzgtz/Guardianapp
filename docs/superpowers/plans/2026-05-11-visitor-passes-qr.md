# Visitor Passes & QR Identity System — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow residents to create time-limited visitor passes (QR codes), guards to scan and validate them with a vivid success/error UI, and automatically notify the resident when their visitor arrives.

**Architecture:** New `pase_visita` DB table stores passes created by residents. Each pass is encoded as `pase:<id>` in a QR. The guard's existing ZXing scanner parses the prefix and routes to a new validation flow: check expiry + uses, register access log, insert into `notificacion` (which triggers the FCM push webhook). The resident has a new "Mis Accesos" screen accessible from the Home quick-actions grid.

**Tech Stack:** Kotlin/Compose, Supabase Postgrest (Kotlin SDK), ZXing (`BarcodeEncoder`/`IntentIntegrator`), Supabase Realtime (existing), FCM v1 via existing `send-push` edge function (webhook-triggered by `notificacion` INSERT).

---

## File Map

| Action | File |
|---|---|
| Create | `supabase/migrations/20260511_pase_visita.sql` |
| Modify | `app/.../data/model/Models.kt` |
| Modify | `app/.../data/repository/ResidentRepository.kt` |
| Modify | `app/.../data/repository/SecurityRepository.kt` |
| Modify | `app/.../ui/resident/viewmodel/ResidentViewModel.kt` |
| Modify | `app/.../ui/security/viewmodel/SecurityViewModel.kt` |
| Create | `app/.../ui/resident/ResidentPassesScreen.kt` |
| Modify | `app/.../ui/navigation/AppNavGraph.kt` |
| Modify | `app/.../ui/resident/ResidentHomeScreen.kt` |
| Modify | `app/.../ui/security/SecurityScreens.kt` |
| Modify | `supabase/functions/send-push/index.ts` |

---

## Task 1: DB Migration — `pase_visita` table

**Files:**
- Create: `supabase/migrations/20260511_pase_visita.sql`

- [ ] **Step 1: Write the migration file**

```sql
-- supabase/migrations/20260511_pase_visita.sql

CREATE TABLE IF NOT EXISTS pase_visita (
  id                SERIAL PRIMARY KEY,
  fk_residente      INT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  nombre_visitante  TEXT NOT NULL,
  modelo_vehiculo   TEXT,
  color_vehiculo    TEXT,
  placa_vehiculo    TEXT,
  vigencia          TEXT NOT NULL DEFAULT 'hoy',   -- 'hoy' | 'semanal' | 'indefinido'
  usos_maximos      INT NOT NULL DEFAULT 1,
  usos_realizados   INT NOT NULL DEFAULT 0,
  fecha_creacion    TIMESTAMPTZ DEFAULT now(),
  fecha_expiracion  DATE,
  activo            BOOLEAN DEFAULT true
);

-- Allow residents to manage only their own passes
ALTER TABLE pase_visita ENABLE ROW LEVEL SECURITY;

CREATE POLICY "residente_gestiona_sus_pases" ON pase_visita
  FOR ALL
  USING (
    fk_residente = (SELECT id FROM usuario WHERE email = auth.email())
  );

-- Allow security staff (role 2) to read and update uses
CREATE POLICY "guardia_puede_leer_y_actualizar_pases" ON pase_visita
  FOR ALL
  USING (
    (SELECT fkrolusuario FROM usuario WHERE email = auth.email()) = 2
  )
  WITH CHECK (
    (SELECT fkrolusuario FROM usuario WHERE email = auth.email()) = 2
  );

-- Also add foto_ine_url to visita for guard-captured visitor photos
ALTER TABLE visita ADD COLUMN IF NOT EXISTS foto_ine_url TEXT;
```

- [ ] **Step 2: Apply migration via Supabase MCP tool**

Use `mcp__claude_ai_Supabase__execute_sql` with the full SQL above against the project. Alternatively run via Supabase Dashboard > SQL Editor.

Expected: Table `pase_visita` created, no errors.

- [ ] **Step 3: Verify table exists**

```sql
SELECT column_name, data_type FROM information_schema.columns
WHERE table_name = 'pase_visita' ORDER BY ordinal_position;
```

Expected: 11 rows (id, fk_residente, nombre_visitante, modelo_vehiculo, color_vehiculo, placa_vehiculo, vigencia, usos_maximos, usos_realizados, fecha_creacion, fecha_expiracion, activo).

- [ ] **Step 4: Commit**

```bash
git add supabase/migrations/20260511_pase_visita.sql
git commit -m "feat: DB migration — pase_visita table with RLS"
```

---

## Task 2: `PaseVisita` model + fix `send-push` body field

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/model/Models.kt`
- Modify: `supabase/functions/send-push/index.ts`

- [ ] **Step 1: Add `PaseVisita` data class to Models.kt**

Append after the `Visita` data class (around line 162):

```kotlin
@Serializable
data class PaseVisita(
    @SerialName("id") val id: Int? = null,
    @SerialName("fk_residente") val fkResidente: Int? = null,
    @SerialName("nombre_visitante") val nombreVisitante: String = "",
    @SerialName("modelo_vehiculo") val modeloVehiculo: String? = null,
    @SerialName("color_vehiculo") val colorVehiculo: String? = null,
    @SerialName("placa_vehiculo") val placaVehiculo: String? = null,
    @SerialName("vigencia") val vigencia: String = "hoy",
    @SerialName("usos_maximos") val usosMaximos: Int = 1,
    @SerialName("usos_realizados") val usosRealizados: Int = 0,
    @SerialName("fecha_creacion") val fechaCreacion: String? = null,
    @SerialName("fecha_expiracion") val fechaExpiracion: String? = null,
    @SerialName("activo") val activo: Boolean = true
)
```

- [ ] **Step 2: Fix `send-push` to read `cuerpo` not `mensaje`**

The DB column in `notificacion` is `cuerpo` but the edge function reads `record.mensaje`. Fix `supabase/functions/send-push/index.ts` line 121:

Replace:
```typescript
  const { record } = await req.json();
  const destinatarioId: number = record.fkusuario;
```
and later:
```typescript
    await sendFcmNotification(
      accessToken,
      user.fcmtoken,
      record.titulo  ?? "GuardianApp",
      record.mensaje ?? "",
      { notificacion_id: String(record.id) }
    );
```

With:
```typescript
  // Support both direct call ({ userId, title, body }) and webhook ({ record: {...} })
  const payload = await req.json();
  const record = payload.record ?? payload;
  const destinatarioId: number = record.fkusuario ?? record.userId;
```
and:
```typescript
    await sendFcmNotification(
      accessToken,
      user.fcmtoken,
      record.titulo  ?? record.title  ?? "GuardianApp",
      record.cuerpo  ?? record.body   ?? record.mensaje ?? "",
      { notificacion_id: String(record.id ?? "") }
    );
```

- [ ] **Step 3: Build project to confirm no compile errors**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Deploy updated edge function**

```bash
supabase functions deploy send-push
```

Or use `mcp__claude_ai_Supabase__deploy_edge_function` with function name `send-push`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/gab/data/model/Models.kt
git add supabase/functions/send-push/index.ts
git commit -m "feat: add PaseVisita model; fix send-push to read cuerpo field"
```

---

## Task 3: `ResidentRepository` — visitor pass CRUD

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/repository/ResidentRepository.kt`

- [ ] **Step 1: Add import at top of ResidentRepository.kt**

Ensure `Reserva` import path already covers models. The file already imports `com.example.gab.data.model.*`, so `PaseVisita` is included. No import change needed.

- [ ] **Step 2: Add three methods at the end of ResidentRepository (before the closing `}`)**

```kotlin
suspend fun crearPaseVisita(
    residenteId: Int,
    nombre: String,
    modelo: String?,
    color: String?,
    placa: String?,
    vigencia: String
): Result<Unit> = runCatching {
    val today = java.time.LocalDate.now()
    val expiracion: String? = when (vigencia) {
        "hoy"     -> today.toString()
        "semanal" -> today.plusDays(7).toString()
        else      -> null
    }
    val usosMax = if (vigencia == "indefinido") 9999 else 1
    client.postgrest["pase_visita"].insert(
        PaseVisita(
            fkResidente      = residenteId,
            nombreVisitante  = nombre.trim(),
            modeloVehiculo   = modelo?.trim()?.ifBlank { null },
            colorVehiculo    = color?.trim()?.ifBlank { null },
            placaVehiculo    = placa?.trim()?.uppercase()?.ifBlank { null },
            vigencia         = vigencia,
            usosMaximos      = usosMax,
            fechaExpiracion  = expiracion
        )
    )
}

suspend fun getPasesVisita(residenteId: Int): Result<List<PaseVisita>> = runCatching {
    client.postgrest["pase_visita"].select {
        filter { eq("fk_residente", residenteId) }
    }.decodeList<PaseVisita>().sortedByDescending { it.fechaCreacion }
}

suspend fun desactivarPase(paseId: Int): Result<Unit> = runCatching {
    client.postgrest["pase_visita"].update({ set("activo", false) }) {
        filter { eq("id", paseId) }
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/gab/data/repository/ResidentRepository.kt
git commit -m "feat: ResidentRepository — visitor pass CRUD methods"
```

---

## Task 4: `SecurityRepository` — pass lookup + arrival notification

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/repository/SecurityRepository.kt`

- [ ] **Step 1: Add three methods at the end of SecurityRepository (before closing `}`)**

```kotlin
suspend fun getPaseVisita(paseId: Int): Result<PaseVisita?> = runCatching {
    client.postgrest["pase_visita"].select {
        filter { eq("id", paseId) }
    }.decodeList<PaseVisita>().firstOrNull()
}

suspend fun registrarUsoPase(paseId: Int, nuevosUsos: Int): Result<Unit> = runCatching {
    client.postgrest["pase_visita"].update({ set("usos_realizados", nuevosUsos) }) {
        filter { eq("id", paseId) }
    }
}

// Inserts a notificacion row so the DB webhook fires send-push to the resident
suspend fun notificarLlegadaVisitante(residenteId: Int, nombreVisitante: String): Result<Unit> = runCatching {
    client.postgrest["notificacion"].insert(
        Notificacion(
            fkUsuario = residenteId,
            titulo    = "Tu visitante ha llegado",
            cuerpo    = "$nombreVisitante ha ingresado al complejo"
        )
    )
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/gab/data/repository/SecurityRepository.kt
git commit -m "feat: SecurityRepository — pass lookup, use registration, arrival notification"
```

---

## Task 5: `ResidentViewModel` — pases state + actions

**Files:**
- Modify: `app/src/main/java/com/example/gab/ui/resident/viewmodel/ResidentViewModel.kt`

- [ ] **Step 1: Add pases StateFlow after `_vehiculos` declaration (around line 53)**

```kotlin
private val _pasesVisita = MutableStateFlow<List<PaseVisita>>(emptyList())
val pasesVisita: StateFlow<List<PaseVisita>> = _pasesVisita.asStateFlow()
```

- [ ] **Step 2: Add three action functions before `clearToast()` (end of class)**

```kotlin
fun cargarPases(userId: Int) {
    viewModelScope.launch {
        repo.getPasesVisita(userId).onSuccess { _pasesVisita.value = it }
    }
}

fun crearPase(userId: Int, nombre: String, modelo: String, color: String, placa: String, vigencia: String) {
    viewModelScope.launch {
        repo.crearPaseVisita(userId, nombre, modelo, color, placa, vigencia)
            .onSuccess {
                _toastMessage.value = "Pase creado para $nombre"
                repo.getPasesVisita(userId).onSuccess { _pasesVisita.value = it }
            }
            .onFailure { _toastMessage.value = "Error al crear pase: ${it.message}" }
    }
}

fun desactivarPase(userId: Int, paseId: Int) {
    viewModelScope.launch {
        repo.desactivarPase(paseId)
            .onSuccess {
                _toastMessage.value = "Pase cancelado"
                repo.getPasesVisita(userId).onSuccess { _pasesVisita.value = it }
            }
            .onFailure { _toastMessage.value = "Error: ${it.message}" }
    }
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/gab/ui/resident/viewmodel/ResidentViewModel.kt
git commit -m "feat: ResidentViewModel — visitor pass state flows and CRUD actions"
```

---

## Task 6: `SecurityViewModel` — `QrScanResult` + enhanced QR handler

**Files:**
- Modify: `app/src/main/java/com/example/gab/ui/security/viewmodel/SecurityViewModel.kt`

- [ ] **Step 1: Add `QrScanResult` sealed class at top of file (after package declaration, before `class SecurityViewModel`)**

```kotlin
sealed class QrScanResult {
    data class Error(val mensaje: String) : QrScanResult()
    data class PaseValido(val pase: PaseVisita) : QrScanResult()
}
```

- [ ] **Step 2: Add `_qrScanResult` StateFlow after `_toastMessage` declaration**

```kotlin
private val _qrScanResult = MutableStateFlow<QrScanResult?>(null)
val qrScanResult: StateFlow<QrScanResult?> = _qrScanResult.asStateFlow()
```

- [ ] **Step 3: Replace `onQrScanned()` entirely**

```kotlin
fun onQrScanned(rawContent: String, guardiaId: Int) {
    viewModelScope.launch {
        when {
            rawContent.trim().startsWith("pase:") -> {
                val paseId = rawContent.trim().removePrefix("pase:").toIntOrNull() ?: run {
                    _qrScanResult.value = QrScanResult.Error("QR de pase inválido")
                    return@launch
                }
                resolverPaseQr(paseId)
            }
            else -> {
                val uid = rawContent.trim().toIntOrNull() ?: run {
                    _qrScanResult.value = QrScanResult.Error("QR no reconocido")
                    return@launch
                }
                repo.getResidentePorId(uid)
                    .onSuccess { residente ->
                        if (residente == null)
                            _qrScanResult.value = QrScanResult.Error("Residente no encontrado")
                        else
                            _residenteEscaneado.value = residente
                    }
                    .onFailure { _qrScanResult.value = QrScanResult.Error("Error al leer QR: ${it.message}") }
            }
        }
    }
}

private suspend fun resolverPaseQr(paseId: Int) {
    val pase = repo.getPaseVisita(paseId).getOrNull()
    if (pase == null) {
        _qrScanResult.value = QrScanResult.Error("Pase #$paseId no encontrado")
        return
    }
    val today = java.time.LocalDate.now().toString()
    _qrScanResult.value = when {
        !pase.activo ->
            QrScanResult.Error("Este pase fue cancelado por el residente")
        pase.fechaExpiracion != null && pase.fechaExpiracion < today ->
            QrScanResult.Error("Pase expirado el ${pase.fechaExpiracion}")
        pase.usosRealizados >= pase.usosMaximos && pase.usosMaximos < 9999 ->
            QrScanResult.Error("Pase agotado (${pase.usosRealizados}/${pase.usosMaximos} usos realizados)")
        else ->
            QrScanResult.PaseValido(pase)
    }
}

fun confirmarAccesoPase(guardiaId: Int, pase: PaseVisita) {
    viewModelScope.launch {
        val hora = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        pase.fkResidente?.let { residenteId ->
            repo.registrarAcceso(residenteId, guardiaId, "ENTRADA", hora)
            repo.registrarUsoPase(pase.id!!, pase.usosRealizados + 1)
            repo.notificarLlegadaVisitante(residenteId, pase.nombreVisitante)
        }
        _toastMessage.value = "Acceso registrado: ${pase.nombreVisitante}"
        _qrScanResult.value = null
        repo.getAccesoLog().onSuccess { _accesoLog.value = it }
    }
}

fun clearQrScanResult() { _qrScanResult.value = null }
```

- [ ] **Step 4: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/gab/ui/security/viewmodel/SecurityViewModel.kt
git commit -m "feat: SecurityViewModel — QrScanResult sealed class + visitor pass validation flow"
```

---

## Task 7: `ResidentPassesScreen.kt` — create & view visitor passes

**Files:**
- Create: `app/src/main/java/com/example/gab/ui/resident/ResidentPassesScreen.kt`

- [ ] **Step 1: Create the file with full content**

```kotlin
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.gab.data.model.PaseVisita
import com.example.gab.ui.common.*
import com.example.gab.ui.navigation.AppUser
import com.example.gab.ui.resident.viewmodel.ResidentViewModel
import com.example.gab.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

@Composable
fun ResidentPassesScreen(user: AppUser, vm: ResidentViewModel) {
    val pases by vm.pasesVisita.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var paseToDelete by remember { mutableStateOf<PaseVisita?>(null) }

    LaunchedEffect(user.id) { vm.cargarPases(user.id) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Text("Mis Accesos de Visitante", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Genera un QR para que tus visitantes entren al complejo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            if (pases.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.QrCode2, null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("No tienes pases activos", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Toca + para crear uno", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            items(pases, key = { it.id ?: it.nombreVisitante }) { pase ->
                PaseCard(pase, onDelete = { paseToDelete = pase })
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = ResidentBlue
        ) {
            Icon(Icons.Default.Add, null, tint = Color.White)
        }
    }

    if (showCreateDialog) {
        CrearPaseDialog(
            onDismiss = { showCreateDialog = false },
            onSubmit  = { nombre, modelo, color, placa, vigencia ->
                vm.crearPase(user.id, nombre, modelo, color, placa, vigencia)
                showCreateDialog = false
            }
        )
    }

    paseToDelete?.let { pase ->
        AlertDialog(
            onDismissRequest = { paseToDelete = null },
            title = { Text("Cancelar pase") },
            text  = { Text("¿Cancelar el pase de ${pase.nombreVisitante}? El QR dejará de funcionar.") },
            confirmButton = {
                Button(
                    onClick = { vm.desactivarPase(user.id, pase.id!!); paseToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusDanger)
                ) { Text("Cancelar pase") }
            },
            dismissButton = { TextButton(onClick = { paseToDelete = null }) { Text("Volver") } }
        )
    }
}

@Composable
private fun PaseCard(pase: PaseVisita, onDelete: () -> Unit) {
    val today = java.time.LocalDate.now().toString()
    val expirado = pase.fechaExpiracion != null && pase.fechaExpiracion < today
    val agotado  = pase.usosRealizados >= pase.usosMaximos && pase.usosMaximos < 9999
    val valido   = pase.activo && !expirado && !agotado

    val qrBitmap = remember(pase.id) {
        runCatching { BarcodeEncoder().encodeBitmap("pase:${pase.id}", BarcodeFormat.QR_CODE, 300, 300) }.getOrNull()
    }

    GuardianCard {
        Row(verticalAlignment = Alignment.Top) {
            if (qrBitmap != null && valido) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR pase",
                    modifier = Modifier.size(96.dp)
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(pase.nombreVisitante, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                pase.placaVehiculo?.let { Text("Placa: $it", style = MaterialTheme.typography.bodySmall) }
                val vehiculoInfo = listOfNotNull(pase.modeloVehiculo, pase.colorVehiculo).joinToString(" · ")
                if (vehiculoInfo.isNotBlank()) Text(vehiculoInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                val vigenciaLabel = when (pase.vigencia) {
                    "hoy"     -> "Válido hoy"
                    "semanal" -> "Válido hasta ${pase.fechaExpiracion ?: "—"}"
                    else      -> "Vigencia indefinida"
                }
                Text(vigenciaLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                if (pase.usosMaximos < 9999) {
                    Text("Usos: ${pase.usosRealizados}/${pase.usosMaximos}", style = MaterialTheme.typography.labelSmall)
                }

                StatusChip(
                    text = when {
                        !pase.activo -> "Cancelado"
                        expirado     -> "Expirado"
                        agotado      -> "Agotado"
                        else         -> "Activo"
                    },
                    color = when {
                        valido -> SecurityGreen
                        else   -> StatusDanger
                    }
                )
            }
            if (pase.activo) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Cancel, null, tint = StatusDanger)
                }
            }
        }
    }
}

@Composable
private fun CrearPaseDialog(
    onDismiss: () -> Unit,
    onSubmit: (nombre: String, modelo: String, color: String, placa: String, vigencia: String) -> Unit
) {
    var nombre   by remember { mutableStateOf("") }
    var modelo   by remember { mutableStateOf("") }
    var color    by remember { mutableStateOf("") }
    var placa    by remember { mutableStateOf("") }
    var vigencia by remember { mutableStateOf("hoy") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo acceso de visitante") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = nombre,  onValueChange = { nombre  = it }, label = { Text("Nombre del visitante *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = modelo,  onValueChange = { modelo  = it }, label = { Text("Marca/modelo del vehículo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = color,   onValueChange = { color   = it }, label = { Text("Color del vehículo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = placa,   onValueChange = { placa   = it.uppercase() }, label = { Text("Placas del vehículo") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Text("Vigencia:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("hoy" to "Solo hoy", "semanal" to "1 semana", "indefinido" to "Sin límite").forEach { (key, label) ->
                        FilterChip(
                            selected = vigencia == key,
                            onClick  = { vigencia = key },
                            label    = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick  = { if (nombre.isNotBlank()) onSubmit(nombre, modelo, color, placa, vigencia) },
                enabled  = nombre.isNotBlank(),
                colors   = ButtonDefaults.buttonColors(containerColor = ResidentBlue)
            ) { Text("Crear pase") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/gab/ui/resident/ResidentPassesScreen.kt
git commit -m "feat: ResidentPassesScreen — create and view visitor passes with QR"
```

---

## Task 8: Route + navigation wiring

**Files:**
- Modify: `app/src/main/java/com/example/gab/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/example/gab/ui/resident/ResidentHomeScreen.kt`

- [ ] **Step 1: Add `RESIDENT_PASSES` constant to `Routes` object in `AppNavGraph.kt`**

In the `Routes` object after `RESIDENT_PROFILE`:

```kotlin
const val RESIDENT_PASSES = "resident_passes"
```

- [ ] **Step 2: Wire route in `ResidentShell` in `ResidentHomeScreen.kt`**

In `ResidentShell`, add to the `NavHost`:

```kotlin
composable(Routes.RESIDENT_PASSES) { ResidentPassesScreen(user = user, vm = vm) }
```

- [ ] **Step 3: Add quick-action button in `ResidentHomeScreen`**

In `ResidentHomeScreen`, find the existing quick-actions `Row` with `QuickActionButton` items. Add a new button:

```kotlin
QuickActionButton(
    icon    = Icons.Default.QrCode2,
    label   = "Mis Accesos",
    color   = ResidentBlue,
    onClick = { navController.navigate(Routes.RESIDENT_PASSES) },
    modifier = Modifier.weight(1f)
)
```

(Add it alongside the existing quick-action buttons. If already 3 items in the row and no room, split into two rows or replace a less-used one.)

- [ ] **Step 4: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/gab/ui/navigation/AppNavGraph.kt
git add app/src/main/java/com/example/gab/ui/resident/ResidentHomeScreen.kt
git commit -m "feat: wire ResidentPassesScreen route and quick-action button"
```

---

## Task 9: `SecurityQrScreen` — pass result dialogs (success / error)

**Files:**
- Modify: `app/src/main/java/com/example/gab/ui/security/SecurityScreens.kt`

- [ ] **Step 1: Add import for `QrScanResult` at top of SecurityScreens.kt**

```kotlin
import com.example.gab.ui.security.viewmodel.QrScanResult
```

- [ ] **Step 2: Replace the entire `SecurityQrScreen` composable**

```kotlin
@Composable
fun SecurityQrScreen(user: AppUser, vm: SecurityViewModel) {
    val residenteEscaneado by vm.residenteEscaneado.collectAsStateWithLifecycle()
    val qrScanResult       by vm.qrScanResult.collectAsStateWithLifecycle()
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
        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(96.dp), tint = SecurityGreen)
        Text("Escanear QR de Acceso", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Escanea el QR personal del residente o el pase QR de un visitante.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Button(
            onClick = {
                val intent = IntentIntegrator(context as android.app.Activity).apply {
                    setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
                    setPrompt("Escanea el QR del residente o del pase de visitante")
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

    // Resident QR (existing flow)
    residenteEscaneado?.let { residente ->
        QrAccessDialog(
            residente = residente,
            onDismiss = { vm.clearResidenteEscaneado() },
            onConfirm = { direccion -> vm.registrarAccesoQr(user.id, residente, direccion) }
        )
    }

    // Visitor pass QR (new flows)
    when (val result = qrScanResult) {
        is QrScanResult.PaseValido -> {
            PaseValidoDialog(
                pase      = result.pase,
                onDismiss = { vm.clearQrScanResult() },
                onConfirm = { vm.confirmarAccesoPase(user.id, result.pase) }
            )
        }
        is QrScanResult.Error -> {
            PaseErrorDialog(
                mensaje   = result.mensaje,
                onDismiss = { vm.clearQrScanResult() }
            )
        }
        null -> {}
    }
}

@Composable
private fun PaseValidoDialog(pase: PaseVisita, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = SecurityGreen)
                Text("Pase válido — Permitir acceso")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = SecurityGreen.copy(alpha = 0.08f))) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Visitante:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(pase.nombreVisitante, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        pase.placaVehiculo?.let {
                            Text("Placa registrada: $it", style = MaterialTheme.typography.bodySmall)
                        }
                        val vehiculo = listOfNotNull(pase.modeloVehiculo, pase.colorVehiculo).joinToString(" · ")
                        if (vehiculo.isNotBlank()) Text(vehiculo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val usosLabel = if (pase.usosMaximos >= 9999) "Sin límite de usos"
                                        else "Uso ${pase.usosRealizados + 1} de ${pase.usosMaximos}"
                        Text(usosLabel, style = MaterialTheme.typography.labelSmall, color = SecurityGreen)
                    }
                }
                Text(
                    "El residente será notificado inmediatamente cuando confirmes el acceso.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors  = ButtonDefaults.buttonColors(containerColor = SecurityGreen)
            ) {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Confirmar acceso")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PaseErrorDialog(mensaje: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Cancel, null, tint = StatusDanger)
                Text("Acceso denegado", color = StatusDanger)
            }
        },
        text = {
            Card(colors = CardDefaults.cardColors(containerColor = StatusDanger.copy(alpha = 0.08f))) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Block, null, tint = StatusDanger, modifier = Modifier.size(32.dp))
                    Text(mensaje, style = MaterialTheme.typography.bodyMedium, color = StatusDanger, fontWeight = FontWeight.Medium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors  = ButtonDefaults.buttonColors(containerColor = StatusDanger)
            ) { Text("Cerrar") }
        }
    )
}
```

Note: `PaseVisita` is imported via `com.example.gab.data.model.*` which SecurityScreens.kt already imports implicitly since it uses other models. Add explicit import if needed:
```kotlin
import com.example.gab.data.model.PaseVisita
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit + push**

```bash
git add app/src/main/java/com/example/gab/ui/security/SecurityScreens.kt
git commit -m "feat: SecurityQrScreen — visitor pass success/error dialogs with guard confirmation"
git push origin master
```

---

## Verification Checklist

- [ ] Resident can navigate to "Mis Accesos" from Home and create a pass → QR displays on the card
- [ ] Guard scans resident's personal QR → existing `QrAccessDialog` appears (unchanged flow)
- [ ] Guard scans visitor pass QR → `PaseValidoDialog` shows visitor name, plate, uses remaining
- [ ] Guard scans expired/cancelled/used-up pass → `PaseErrorDialog` shows red error message
- [ ] After guard confirms → `accesolog` row inserted, `usos_realizados` incremented, resident gets push notification
- [ ] Resident can cancel (deactivate) a pass → guard sees "Pase fue cancelado"
