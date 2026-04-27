# Módulos Condominio — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar los 7 módulos (Auth, Realtime, Location, Reservas, Vigilancia, Pagos, Limpieza) en el proyecto Android Kotlin + Supabase existente para convertirlo en una app comercial de condominios.

**Architecture:** MVVM existente se mantiene. Cada módulo extiende sus Repository + ViewModel + Screen correspondientes. Supabase Realtime v3 se usa via `postgresChangeFlow` para sincronización en vivo. CameraX + ML Kit manejan los flujos visuales del guardia.

**Tech Stack:** Kotlin 2.0.21, Jetpack Compose, Supabase 3.0.0 (Auth/Postgrest/Realtime/Storage), Firebase Messaging, ZXing 4.3.0 (QR), CameraX 1.3.4, ML Kit Text Recognition 16.0.1.

---

## Archivos que se modifican/crean

| Archivo | Acción |
|---|---|
| `app/build.gradle.kts` | Modificar — agregar deps ZXing + CameraX + MLKit |
| `app/src/main/AndroidManifest.xml` | Modificar — permiso CAMERA |
| `app/src/main/java/com/example/gab/data/model/Models.kt` | Modificar — Unidad.piso, TareaLimpieza.notas/estatus/fkUnidad, Vehiculo, Visita |
| `app/src/main/java/com/example/gab/util/Extensions.kt` | Modificar — diasParaPago() |
| `app/src/main/java/com/example/gab/data/repository/AuthRepository.kt` | Modificar — email verification, reenviar |
| `app/src/main/java/com/example/gab/ui/auth/viewmodel/AuthViewModel.kt` | Modificar — reenviarVerificacion() |
| `app/src/main/java/com/example/gab/ui/auth/LoginScreen.kt` | Modificar — botón reenviar correo |
| `app/src/main/java/com/example/gab/data/repository/ResidentRepository.kt` | Modificar — slotOcupado, getSlotsTomados, solicitudLimpieza, observeAvisos, observeReservas |
| `app/src/main/java/com/example/gab/ui/resident/viewmodel/ResidentViewModel.kt` | Modificar — slotsTomados, diasParaPago, startRealtime, solicitudLimpieza |
| `app/src/main/java/com/example/gab/ui/resident/ResidentAmenitiesScreen.kt` | Modificar — slots ocupados grises |
| `app/src/main/java/com/example/gab/ui/resident/ResidentHomeScreen.kt` | Modificar — banner de pago, botón solicitar limpieza |
| `app/src/main/java/com/example/gab/ui/resident/ResidentProfileScreen.kt` | Modificar — QR code, sección vehículos, info real de unidad |
| `app/src/main/java/com/example/gab/data/repository/CleaningRepository.kt` | Modificar — setEstatus, getTareasConUnidad, observeTareas |
| `app/src/main/java/com/example/gab/ui/cleaning/viewmodel/CleaningViewModel.kt` | Modificar — setEstatus, startRealtime |
| `app/src/main/java/com/example/gab/ui/cleaning/CleaningScreens.kt` | Modificar — 3 estados, ubicación, notas |
| `app/src/main/java/com/example/gab/data/repository/SecurityRepository.kt` | Modificar — guardarVisita, getVehiculos, registrarVehiculo, observeAccesoLog |
| `app/src/main/java/com/example/gab/ui/security/viewmodel/SecurityViewModel.kt` | Modificar — startRealtime, flujos INE/placas, vehículos |
| `app/src/main/java/com/example/gab/ui/security/SecurityScreens.kt` | Modificar — tabs QR/INE/Placas |
| `app/src/main/java/com/example/gab/ui/security/CameraScreen.kt` | **Crear** — CameraX preview composable |
| `app/src/main/java/com/example/gab/ui/admin/AdminScreens.kt` | Modificar — campo Piso en crear unidad |
| `supabase/functions/recordatorio-pago/index.ts` | **Crear** — Edge Function FCM scheduler |

---

## Task 1: DB Migrations en Supabase

**Files:**
- No files — SQL ejecutado en Supabase Dashboard → SQL Editor

- [ ] **Step 1: Abrir Supabase Dashboard → SQL Editor y ejecutar este script completo**

```sql
-- 1. Agregar piso a unidad
ALTER TABLE unidad ADD COLUMN IF NOT EXISTS piso integer DEFAULT 1;

-- 2. Actualizar TareaLimpieza
ALTER TABLE tarealimpieza
    ADD COLUMN IF NOT EXISTS fk_unidad integer REFERENCES unidad(id),
    ADD COLUMN IF NOT EXISTS notas text,
    ADD COLUMN IF NOT EXISTS estatus text DEFAULT 'pendiente';

-- Migrar estado booleano a string
UPDATE tarealimpieza 
SET estatus = CASE WHEN estacompletada THEN 'completado' ELSE 'pendiente' END
WHERE estatus = 'pendiente' AND estacompletada = true;

-- 3. Tabla visita (registro de visitantes del guardia)
CREATE TABLE IF NOT EXISTS visita (
    id SERIAL PRIMARY KEY,
    nombre_visitante TEXT NOT NULL,
    fk_guardia INTEGER REFERENCES usuario(id),
    tipo TEXT DEFAULT 'INE',
    placa TEXT,
    foto_url TEXT,
    timestamp TIMESTAMPTZ DEFAULT now()
);

-- 4. Tabla vehiculo (vehículos registrados por residentes)
CREATE TABLE IF NOT EXISTS vehiculo (
    id SERIAL PRIMARY KEY,
    fk_usuario INTEGER REFERENCES usuario(id),
    placa TEXT NOT NULL,
    descripcion TEXT,
    color TEXT
);

-- 5. Habilitar Realtime en tablas críticas
-- (En Supabase Dashboard → Database → Replication → Tables: activar reserva, aviso, tarealimpieza, accesolog)
```

- [ ] **Step 2: Verificar en Table Editor que las columnas nuevas existen**
  - `unidad` → columna `piso`
  - `tarealimpieza` → columnas `fk_unidad`, `notas`, `estatus`
  - Tablas `visita` y `vehiculo` creadas

- [ ] **Step 3: En Supabase Dashboard → Database → Replication**
  - Activar `reserva`, `aviso`, `tarealimpieza`, `accesolog` en la lista de tablas replicadas para Realtime

---

## Task 2: Actualizar Models.kt

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/model/Models.kt`

- [ ] **Step 1: Reemplazar el contenido completo de Models.kt**

```kotlin
package com.example.gab.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RolUsuario(
    @SerialName("id") val id: Int,
    @SerialName("nombre") val nombre: String
)

@Serializable
data class Unidad(
    @SerialName("id") val id: Int = 0,
    @SerialName("numero") val numero: String = "",
    @SerialName("torre") val torre: String? = null,
    @SerialName("piso") val piso: Int = 1,
    @SerialName("tipo") val tipo: String = "depto",
    @SerialName("estaactivo") val estaActivo: Boolean = true
) {
    fun displayUbicacion(): String = buildString {
        torre?.let { append("Bloque $it · ") }
        append("Piso $piso · Depto $numero")
    }
}

@Serializable
data class MorosoRow(
    @SerialName("userid") val userId: Int = 0,
    @SerialName("nombre") val nombre: String = "",
    @SerialName("email") val email: String? = null,
    @SerialName("cuotaid") val cuotaId: Int = 0,
    @SerialName("periodo") val periodo: String = "",
    @SerialName("monto") val monto: Double = 0.0,
    @SerialName("fechavencimiento") val fechaVencimiento: String? = null
)

@Serializable
data class Usuario(
    @SerialName("id") val id: Int = 0,
    @SerialName("nombre") val nombre: String = "",
    @SerialName("email") val email: String? = null,
    @SerialName("fkrolusuario") val fkRolUsuario: Int? = null,
    @SerialName("fkunidad") val fkUnidad: Int? = null,
    @SerialName("fcmtoken") val fcmToken: String? = null,
    @SerialName("fotourl") val fotoUrl: String? = null,
    @SerialName("estaactivo") val estaActivo: Boolean = true
)

@Serializable
data class Cuota(
    @SerialName("id") val id: Int = 0,
    @SerialName("fkusuario") val fkUsuario: Int? = null,
    @SerialName("monto") val monto: Double? = null,
    @SerialName("estatus") val estatus: String = "pendiente",
    @SerialName("fechavencimiento") val fechaVencimiento: String? = null,
    @SerialName("periodo") val periodo: String = "mensual"
)

@Serializable
data class Reporte(
    @SerialName("id") val id: Int? = null,
    @SerialName("fkusuario") val fkUsuario: Int? = null,
    @SerialName("titulo") val titulo: String = "",
    @SerialName("descripcion") val descripcion: String? = null,
    @SerialName("categoria") val categoria: String = "General",
    @SerialName("esurgente") val esUrgente: Boolean = false,
    @SerialName("fotourl") val fotoUrl: String? = null,
    @SerialName("estatus") val estatus: String = "Pendiente",
    @SerialName("fechacreacion") val fechaCreacion: String? = null
)

@Serializable
data class Aviso(
    @SerialName("id") val id: Int = 0,
    @SerialName("titulo") val titulo: String = "",
    @SerialName("descripcion") val descripcion: String? = null,
    @SerialName("tono") val tono: String = "primary"
)

@Serializable
data class Amenidad(
    @SerialName("id") val id: Int = 0,
    @SerialName("nombre") val nombre: String = "",
    @SerialName("horario") val horario: String? = null,
    @SerialName("capacidad") val capacidad: Int = 10
)

@Serializable
data class Reserva(
    @SerialName("id") val id: Int? = null,
    @SerialName("fkusuario") val fkUsuario: Int? = null,
    @SerialName("fkamenidad") val fkAmenidad: Int? = null,
    @SerialName("fechareservacion") val fechaReservacion: String? = null,
    @SerialName("horarioslot") val horarioSlot: String? = null,
    @SerialName("estatus") val estatus: String = "activa"
)

@Serializable
data class AccesoLog(
    @SerialName("id") val id: Int? = null,
    @SerialName("fkresidente") val fkResidente: Int? = null,
    @SerialName("fkguardia") val fkGuardia: Int? = null,
    @SerialName("direccion") val direccion: String = "ENTRADA",
    @SerialName("horaregistro") val horaRegistro: String? = null,
    @SerialName("timestamp") val timestamp: String? = null
)

@Serializable
data class TareaLimpieza(
    @SerialName("id") val id: Int? = null,
    @SerialName("fkasignado") val fkAsignado: Int? = null,
    @SerialName("fk_unidad") val fkUnidad: Int? = null,
    @SerialName("titulo") val titulo: String = "",
    @SerialName("area") val area: String? = null,
    @SerialName("horarioslot") val horarioSlot: String? = null,
    @SerialName("prioridad") val prioridad: String = "normal",
    @SerialName("estacompletada") val estaCompletada: Boolean = false,
    @SerialName("fecha") val fecha: String? = null,
    @SerialName("notas") val notas: String? = null,
    @SerialName("estatus") val estatus: String = "pendiente"
)

@Serializable
data class AreaComun(
    @SerialName("id") val id: Int = 0,
    @SerialName("nombre") val nombre: String = "",
    @SerialName("sector") val sector: String? = null,
    @SerialName("estatus") val estatus: String = "pendiente",
    @SerialName("ultimalimpieza") val ultimaLimpieza: String? = null
)

@Serializable
data class Notificacion(
    @SerialName("id") val id: Int? = null,
    @SerialName("fkusuario") val fkUsuario: Int? = null,
    @SerialName("titulo") val titulo: String = "",
    @SerialName("cuerpo") val cuerpo: String? = null,
    @SerialName("estaleida") val estaLeida: Boolean = false,
    @SerialName("fechacreacion") val fechaCreacion: String? = null
)

@Serializable
data class Vehiculo(
    @SerialName("id") val id: Int? = null,
    @SerialName("fk_usuario") val fkUsuario: Int? = null,
    @SerialName("placa") val placa: String = "",
    @SerialName("descripcion") val descripcion: String? = null,
    @SerialName("color") val color: String? = null
)

@Serializable
data class Visita(
    @SerialName("id") val id: Int? = null,
    @SerialName("nombre_visitante") val nombreVisitante: String = "",
    @SerialName("fk_guardia") val fkGuardia: Int? = null,
    @SerialName("tipo") val tipo: String = "INE",
    @SerialName("placa") val placa: String? = null,
    @SerialName("foto_url") val fotoUrl: String? = null,
    @SerialName("timestamp") val timestamp: String? = null
)

data class UnidadConEstatus(val unidad: Unidad, val ocupada: Boolean)

data class DashboardStats(
    val totalUsuarios: Int = 0,
    val reportesAbiertos: Int = 0,
    val reservasHoy: Int = 0,
    val cuotasPendientes: Int = 0
)
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/gab/data/model/Models.kt
git commit -m "feat: add piso to Unidad, notas/estatus/fkUnidad to TareaLimpieza, Vehiculo and Visita models"
```

---

## Task 3: Dependencias y Permisos

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Reemplazar el bloque `dependencies` en app/build.gradle.kts**

```kotlin
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.supabase.auth.kt)
    implementation(libs.supabase.realtime.kt)
    implementation(libs.supabase.storage.kt)
    implementation(libs.ktor.client.android)
    implementation(libs.firebase.messaging.ktx)
    implementation(libs.kotlinx.serialization.json)

    // QR Code generation + scanning
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    // CameraX
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")

    // ML Kit Text Recognition
    implementation("com.google.mlkit:text-recognition:16.0.1")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
```

- [ ] **Step 2: Agregar permiso CAMERA en AndroidManifest.xml**

Agregar después de `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`:
```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

- [ ] **Step 3: Sync Gradle y verificar que compila sin errores**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "feat: add ZXing, CameraX, ML Kit dependencies and CAMERA permission"
```

---

## Task 4: AUTH — Verificación de Email

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/repository/AuthRepository.kt`
- Modify: `app/src/main/java/com/example/gab/ui/auth/viewmodel/AuthViewModel.kt`
- Modify: `app/src/main/java/com/example/gab/ui/auth/LoginScreen.kt`

- [ ] **Step 1: Modificar AuthRepository — bloquear login sin verificar y agregar reenvío**

En `AuthRepository.kt`, reemplazar el método `signIn` y agregar `reenviarVerificacion`:

```kotlin
suspend fun signIn(email: String, password: String): Result<Usuario> = runCatching {
    client.auth.signInWith(Email) {
        this.email = email
        this.password = password
    }
    val supaUser = client.auth.currentUserOrNull() ?: error("Login failed")

    // Bloquear si el email no está verificado
    if (supaUser.emailConfirmedAt == null) {
        client.auth.signOut()
        error("Verifica tu correo antes de continuar")
    }

    val usuario = client.postgrest["usuario"].select {
        filter { eq("email", email) }
    }.decodeSingle<Usuario>()

    val token = client.auth.currentSessionOrNull()?.accessToken ?: ""
    session.saveSession(
        userId = usuario.id,
        name = usuario.nombre,
        role = usuario.fkRolUsuario ?: 1,
        unit = "",
        token = token
    )
    usuario
}

suspend fun reenviarVerificacion(email: String): Result<Unit> = runCatching {
    val safeEmail = email.trim().replace("\"", "")
    val baseUrl = SupabaseClientProvider.SUPABASE_URL
    val anonKey = SupabaseClientProvider.SUPABASE_KEY
    withContext(Dispatchers.IO) {
        val conn = URL("$baseUrl/auth/v1/resend").openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("apikey", anonKey)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        val body = """{"type":"signup","email":"$safeEmail"}"""
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        conn.disconnect()
        if (code !in 200..299) error("No se pudo reenviar el correo")
    }
}
```

También cambiar en `createUser()` la línea:
```kotlin
// ANTES:
val body = """{"email":"$safeEmail","password":"$password","email_confirm":true}"""
// DESPUÉS:
val body = """{"email":"$safeEmail","password":"$password","email_confirm":false}"""
```

- [ ] **Step 2: Agregar `reenviarVerificacion` en AuthViewModel**

En `AuthViewModel.kt`, agregar después de `fun logout()`:

```kotlin
fun reenviarVerificacion(email: String) {
    viewModelScope.launch {
        repo.reenviarVerificacion(email)
            .onSuccess { _uiState.value = AuthState.Error("Correo reenviado. Revisa tu bandeja de entrada.") }
            .onFailure { _uiState.value = AuthState.Error("No se pudo reenviar: ${it.message}") }
    }
}
```

- [ ] **Step 3: Actualizar LoginScreen — mostrar botón de reenvío cuando el error es de verificación**

En `LoginScreen.kt`, reemplazar el bloque del botón de login con el siguiente bloque completo (después del campo de contraseña y antes del cierre de la Column del Card):

```kotlin
Spacer(Modifier.height(24.dp))

Button(
    onClick = { viewModel.login(email, password) },
    enabled = !isLoading && email.isNotBlank() && password.isNotBlank(),
    modifier = Modifier.fillMaxWidth().height(52.dp),
    shape = RoundedCornerShape(12.dp),
    colors = ButtonDefaults.buttonColors(containerColor = GuardianBlue)
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Color.White,
            strokeWidth = 2.dp
        )
    } else {
        Text("Ingresar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

// Mostrar botón de reenvío solo cuando el error es de verificación
val errorMsg = (uiState as? AuthState.Error)?.message ?: ""
if (errorMsg.contains("Verifica") || errorMsg.contains("verifica")) {
    Spacer(Modifier.height(12.dp))
    OutlinedButton(
        onClick = { viewModel.reenviarVerificacion(email) },
        enabled = email.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(Icons.Default.Email, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Reenviar correo de verificación")
    }
}
```

- [ ] **Step 4: Compilar y verificar que no hay errores de importación**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/gab/data/repository/AuthRepository.kt \
        app/src/main/java/com/example/gab/ui/auth/viewmodel/AuthViewModel.kt \
        app/src/main/java/com/example/gab/ui/auth/LoginScreen.kt
git commit -m "feat: email verification on login + resend button"
```

---

## Task 5: LOCATION — Labels Bloque/Piso/Depto + Admin Piso Field

**Files:**
- Modify: `app/src/main/java/com/example/gab/ui/resident/ResidentHomeScreen.kt`
- Modify: `app/src/main/java/com/example/gab/ui/resident/ResidentProfileScreen.kt`
- Modify: `app/src/main/java/com/example/gab/ui/admin/AdminScreens.kt`

- [ ] **Step 1: Actualizar label de ubicación en ResidentHomeScreen**

En `ResidentHomeScreen.kt`, reemplazar el bloque de texto de la unidad (líneas 99-110):

```kotlin
if (unidad != null) {
    Text(
        unidad!!.displayUbicacion(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
} else if (user.apartment.isNotBlank()) {
    Text(user.apartment, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
```

- [ ] **Step 2: Actualizar ResidentProfileScreen — mostrar ubicación real**

En el bloque `InfoRow(Icons.Default.Home, "Apartamento", user.apartment.ifBlank { "—" })`, necesitamos pasar la `unidad`. Cambiar la firma de `ResidentProfileScreen` para recibir el ViewModel:

```kotlin
@Composable
fun ResidentProfileScreen(user: AppUser, vm: ResidentViewModel, onLogout: () -> Unit) {
    val unidad by vm.unidad.collectAsStateWithLifecycle()
    // ... resto del código existente ...
    
    // Reemplazar la InfoRow de apartamento:
    InfoRow(
        icon = Icons.Default.Home,
        label = "Ubicación",
        value = unidad?.displayUbicacion() ?: user.apartment.ifBlank { "—" }
    )
```

En `ResidentHomeScreen.kt`, actualizar la llamada en el NavHost de `ResidentShell`:
```kotlin
composable(Routes.RESIDENT_PROFILE) { ResidentProfileScreen(user, vm, onLogout) }
```

- [ ] **Step 3: Agregar campo Piso en el formulario de crear unidad en AdminScreens**

Buscar en `AdminScreens.kt` el dialog de crear unidad. Agregar campo `piso` entre `torre` y `tipo`. El campo nuevo es:

```kotlin
// Después del campo "Torre/Bloque":
var piso by remember { mutableStateOf("1") }

OutlinedTextField(
    value = piso,
    onValueChange = { if (it.all { c -> c.isDigit() }) piso = it },
    label = { Text("Piso") },
    leadingIcon = { Icon(Icons.Default.Layers, null) },
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
)
```

Y pasar `piso = piso.toIntOrNull() ?: 1` al llamar `AdminRepository.createUnidad()` (o el método equivalente que inserte en la tabla). En el DTO del insert, incluir el campo piso:

```kotlin
// En AdminRepository o donde se inserta la unidad:
client.postgrest["unidad"].insert(
    mapOf(
        "numero" to numero,
        "torre" to torre.ifBlank { null },
        "piso" to (piso.toIntOrNull() ?: 1),
        "tipo" to tipo,
        "estaactivo" to true
    )
)
```

- [ ] **Step 4: Actualizar la lista de unidades en admin para mostrar Bloque · Piso · Depto**

En `AdminScreens.kt`, en la pantalla `AdminUnitsScreen`, donde se muestra la unidad en cada card, reemplazar el texto de la unidad por:

```kotlin
Text(unidad.displayUbicacion(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/gab/ui/resident/ResidentHomeScreen.kt \
        app/src/main/java/com/example/gab/ui/resident/ResidentProfileScreen.kt \
        app/src/main/java/com/example/gab/ui/admin/AdminScreens.kt
git commit -m "feat: show Bloque/Piso/Depto labels throughout UI, add piso field to admin unit form"
```

---

## Task 6: RESERVAS — Validación de Solapamiento de Horarios

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/repository/ResidentRepository.kt`
- Modify: `app/src/main/java/com/example/gab/ui/resident/viewmodel/ResidentViewModel.kt`
- Modify: `app/src/main/java/com/example/gab/ui/resident/ResidentAmenitiesScreen.kt`

- [ ] **Step 1: Agregar validación de slot en ResidentRepository**

Agregar dos funciones al final de `ResidentRepository`:

```kotlin
suspend fun getSlotsTomados(amenidadId: Int, fecha: String): Result<List<String>> = runCatching {
    client.postgrest["reserva"].select {
        filter {
            eq("fkamenidad", amenidadId)
            eq("fechareservacion", fecha)
            eq("estatus", "activa")
        }
    }.decodeList<Reserva>().mapNotNull { it.horarioSlot }
}

suspend fun crearReservaConValidacion(
    userId: Int, amenidadId: Int, fecha: String, slot: String
): Result<Unit> = runCatching {
    val tomados = client.postgrest["reserva"].select {
        filter {
            eq("fkamenidad", amenidadId)
            eq("fechareservacion", fecha)
            eq("horarioslot", slot)
            eq("estatus", "activa")
        }
    }.decodeList<Reserva>()
    if (tomados.isNotEmpty()) error("Este horario ya está reservado. Elige otro.")
    client.postgrest["reserva"].insert(
        Reserva(fkUsuario = userId, fkAmenidad = amenidadId, fechaReservacion = fecha, horarioSlot = slot)
    )
}
```

- [ ] **Step 2: Actualizar ResidentViewModel**

Agregar dos estados y dos funciones nuevas:

```kotlin
private val _slotsTomados = MutableStateFlow<List<String>>(emptyList())
val slotsTomados: StateFlow<List<String>> = _slotsTomados.asStateFlow()

private val _loadingSlots = MutableStateFlow(false)
val loadingSlots: StateFlow<Boolean> = _loadingSlots.asStateFlow()

fun cargarSlotsTomados(amenidadId: Int, fecha: String) {
    viewModelScope.launch {
        _loadingSlots.value = true
        repo.getSlotsTomados(amenidadId, fecha)
            .onSuccess { _slotsTomados.value = it }
            .onFailure { _slotsTomados.value = emptyList() }
        _loadingSlots.value = false
    }
}

fun limpiarSlotsTomados() {
    _slotsTomados.value = emptyList()
}
```

También reemplazar la llamada a `crearReserva` en `fun crearReserva(...)`:

```kotlin
fun crearReserva(userId: Int, amenidadId: Int, fecha: String, slot: String) {
    viewModelScope.launch {
        repo.crearReservaConValidacion(userId, amenidadId, fecha, slot)
            .onSuccess {
                _toastMessage.value = "Reserva creada exitosamente"
                repo.getReservas(userId).onSuccess { _reservas.value = it }
            }
            .onFailure { _toastMessage.value = it.message ?: "Error al reservar" }
    }
}
```

- [ ] **Step 3: Actualizar NewReservaDialog en ResidentAmenitiesScreen para mostrar slots ocupados**

Reemplazar `NewReservaDialog` con esta versión actualizada:

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
    var slot by remember { mutableStateOf("08:00 - 10:00") }
    val allSlots = listOf(
        "08:00 - 10:00", "10:00 - 12:00", "12:00 - 14:00",
        "14:00 - 16:00", "16:00 - 18:00", "18:00 - 20:00"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reservar ${amenidad.nombre}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = fecha,
                    onValueChange = {
                        fecha = it
                        if (it.length == 10) onFechaChange(it)
                    },
                    label = { Text("Fecha (YYYY-MM-DD)") },
                    placeholder = { Text("2026-05-01") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (loadingSlots) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text("Selecciona horario:", style = MaterialTheme.typography.labelMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    allSlots.forEach { s ->
                        val ocupado = s in slotsTomados
                        val isSelected = slot == s
                        Surface(
                            onClick = { if (!ocupado) slot = s },
                            color = when {
                                ocupado    -> MaterialTheme.colorScheme.surfaceVariant
                                isSelected -> ResidentBlue.copy(alpha = 0.15f)
                                else       -> MaterialTheme.colorScheme.surface
                            },
                            shape = MaterialTheme.shapes.small,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, ResidentBlue) else null
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    s,
                                    modifier = Modifier.weight(1f),
                                    color = if (ocupado) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
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
        },
        confirmButton = {
            Button(
                onClick = { if (fecha.isNotBlank() && slot !in slotsTomados) onSubmit(fecha, slot) },
                enabled = fecha.isNotBlank() && slot !in slotsTomados,
                colors = ButtonDefaults.buttonColors(containerColor = ResidentBlue)
            ) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
```

Y en `ResidentAmenitiesScreen`, colectar los nuevos estados y actualizar la llamada al dialog:

```kotlin
val slotsTomados  by vm.slotsTomados.collectAsStateWithLifecycle()
val loadingSlots  by vm.loadingSlots.collectAsStateWithLifecycle()

// Reemplazar la llamada a NewReservaDialog:
selectedAmenidad?.let { amenidad ->
    NewReservaDialog(
        amenidad = amenidad,
        slotsTomados = slotsTomados,
        loadingSlots = loadingSlots,
        onFechaChange = { fecha -> vm.cargarSlotsTomados(amenidad.id, fecha) },
        onDismiss = { selectedAmenidad = null; vm.limpiarSlotsTomados() },
        onSubmit = { fecha, slot ->
            vm.crearReserva(user.id, amenidad.id, fecha, slot)
            selectedAmenidad = null
            vm.limpiarSlotsTomados()
        }
    )
}
```

- [ ] **Step 4: Compilar**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/gab/data/repository/ResidentRepository.kt \
        app/src/main/java/com/example/gab/ui/resident/viewmodel/ResidentViewModel.kt \
        app/src/main/java/com/example/gab/ui/resident/ResidentAmenitiesScreen.kt
git commit -m "feat: slot conflict validation and occupied slots UI in reservations"
```

---

## Task 7: REALTIME — Sincronización en Tiempo Real

**Files:**
- Modify: `app/src/main/java/com/example/gab/ui/resident/viewmodel/ResidentViewModel.kt`
- Modify: `app/src/main/java/com/example/gab/ui/security/viewmodel/SecurityViewModel.kt`
- Modify: `app/src/main/java/com/example/gab/ui/cleaning/viewmodel/CleaningViewModel.kt`

- [ ] **Step 1: Agregar watcher de Realtime en ResidentViewModel**

Al final del bloque de propiedades del ViewModel, agregar:

```kotlin
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import com.example.gab.data.remote.SupabaseClientProvider

// En la clase ResidentViewModel, agregar:
private var realtimeJob: kotlinx.coroutines.Job? = null

fun startRealtime(userId: Int) {
    realtimeJob?.cancel()
    realtimeJob = viewModelScope.launch {
        val client = SupabaseClientProvider.client
        val channel = client.channel("resident-$userId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "reserva"
        }
        channel.subscribe()
        changes.collect {
            repo.getReservas(userId).onSuccess { _reservas.value = it }
            repo.getAvisos().onSuccess { _avisos.value = it }
        }
    }
}

override fun onCleared() {
    super.onCleared()
    realtimeJob?.cancel()
}
```

En `loadAll(userId)`, agregar al final: `startRealtime(userId)`.

- [ ] **Step 2: Agregar watcher en SecurityViewModel**

```kotlin
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import com.example.gab.data.remote.SupabaseClientProvider

// Agregar en SecurityViewModel:
private var realtimeJob: kotlinx.coroutines.Job? = null

fun startRealtime() {
    realtimeJob?.cancel()
    realtimeJob = viewModelScope.launch {
        val client = SupabaseClientProvider.client
        val channel = client.channel("security-acceso")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "accesolog"
        }
        channel.subscribe()
        changes.collect {
            repo.getAccesoLog().onSuccess { _accesoLog.value = it }
        }
    }
}

override fun onCleared() {
    super.onCleared()
    realtimeJob?.cancel()
}
```

En `loadAll()`, agregar al final: `startRealtime()`.

- [ ] **Step 3: Agregar watcher en CleaningViewModel**

```kotlin
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import com.example.gab.data.remote.SupabaseClientProvider

// Agregar en CleaningViewModel:
private var realtimeJob: kotlinx.coroutines.Job? = null
private var currentUserId: Int = 0

fun startRealtime(userId: Int) {
    currentUserId = userId
    realtimeJob?.cancel()
    realtimeJob = viewModelScope.launch {
        val client = SupabaseClientProvider.client
        val channel = client.channel("cleaning-$userId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "tarealimpieza"
        }
        channel.subscribe()
        changes.collect {
            val today = java.time.LocalDate.now().toString()
            repo.getTareas(userId, today).onSuccess { _tareas.value = it }
        }
    }
}

override fun onCleared() {
    super.onCleared()
    realtimeJob?.cancel()
}
```

En `loadAll(userId)`, agregar al final: `startRealtime(userId)`.

- [ ] **Step 4: Compilar**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/gab/ui/resident/viewmodel/ResidentViewModel.kt \
        app/src/main/java/com/example/gab/ui/security/viewmodel/SecurityViewModel.kt \
        app/src/main/java/com/example/gab/ui/cleaning/viewmodel/CleaningViewModel.kt
git commit -m "feat: Supabase Realtime live subscriptions for reservations, access log, and cleaning tasks"
```

---

## Task 8: LIMPIEZA — Notas + Estados + Ubicación

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/repository/CleaningRepository.kt`
- Modify: `app/src/main/java/com/example/gab/ui/cleaning/viewmodel/CleaningViewModel.kt`
- Modify: `app/src/main/java/com/example/gab/ui/cleaning/CleaningScreens.kt`
- Modify: `app/src/main/java/com/example/gab/data/repository/ResidentRepository.kt`
- Modify: `app/src/main/java/com/example/gab/ui/resident/viewmodel/ResidentViewModel.kt`
- Modify: `app/src/main/java/com/example/gab/ui/resident/ResidentHomeScreen.kt`

- [ ] **Step 1: Actualizar CleaningRepository**

Reemplazar el contenido de `CleaningRepository.kt`:

```kotlin
package com.example.gab.data.repository

import com.example.gab.data.model.AreaComun
import com.example.gab.data.model.TareaLimpieza
import com.example.gab.data.model.Unidad
import com.example.gab.data.remote.SupabaseClientProvider
import io.github.jan.supabase.postgrest.postgrest

class CleaningRepository {
    private val client = SupabaseClientProvider.client

    suspend fun getTareas(asignadoId: Int, fecha: String): Result<List<TareaLimpieza>> = runCatching {
        client.postgrest["tarealimpieza"].select {
            filter {
                eq("fkasignado", asignadoId)
                eq("fecha", fecha)
            }
        }.decodeList<TareaLimpieza>()
            .sortedWith(compareBy({ it.fkUnidad ?: Int.MAX_VALUE }, { it.id ?: 0 }))
    }

    suspend fun setEstatus(tareaId: Int, estatus: String): Result<Unit> = runCatching {
        val completada = estatus == "completado"
        client.postgrest["tarealimpieza"].update({
            set("estatus", estatus)
            set("estacompletada", completada)
        }) { filter { eq("id", tareaId) } }
    }

    // Mantener toggleTarea para compatibilidad
    suspend fun toggleTarea(tareaId: Int, completada: Boolean): Result<Unit> =
        setEstatus(tareaId, if (completada) "completado" else "pendiente")

    suspend fun getAreas(): Result<List<AreaComun>> = runCatching {
        client.postgrest["areacomun"].select().decodeList()
    }

    suspend fun actualizarEstatusArea(areaId: Int, estatus: String): Result<Unit> = runCatching {
        client.postgrest["areacomun"].update({ set("estatus", estatus) }) {
            filter { eq("id", areaId) }
        }
    }

    suspend fun getUnidadDeTarea(fkUnidad: Int): Result<Unidad?> = runCatching {
        client.postgrest["unidad"].select {
            filter { eq("id", fkUnidad) }
        }.decodeList<Unidad>().firstOrNull()
    }
}
```

- [ ] **Step 2: Actualizar CleaningViewModel — agregar setEstatus y caché de unidades**

Reemplazar `CleaningViewModel.kt` completo:

```kotlin
package com.example.gab.ui.cleaning.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.gab.data.model.AreaComun
import com.example.gab.data.model.TareaLimpieza
import com.example.gab.data.model.Unidad
import com.example.gab.data.remote.SupabaseClientProvider
import com.example.gab.data.repository.CleaningRepository
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

class CleaningViewModel : ViewModel() {

    private val repo = CleaningRepository()

    private val _tareas      = MutableStateFlow<List<TareaLimpieza>>(emptyList())
    val tareas: StateFlow<List<TareaLimpieza>> = _tareas.asStateFlow()

    private val _areas       = MutableStateFlow<List<AreaComun>>(emptyList())
    val areas: StateFlow<List<AreaComun>> = _areas.asStateFlow()

    private val _unidades    = MutableStateFlow<Map<Int, Unidad>>(emptyMap())
    val unidades: StateFlow<Map<Int, Unidad>> = _unidades.asStateFlow()

    private val _isLoading   = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    private var realtimeJob: Job? = null
    private var currentUserId: Int = 0

    fun loadAll(userId: Int) {
        currentUserId = userId
        viewModelScope.launch {
            _isLoading.value = true
            val today = LocalDate.now().toString()
            repo.getTareas(userId, today).onSuccess {
                _tareas.value = it
                preloadUnidades(it)
            }
            repo.getAreas().onSuccess { _areas.value = it }
            _isLoading.value = false
        }
        startRealtime(userId)
    }

    private fun preloadUnidades(tareas: List<TareaLimpieza>) {
        viewModelScope.launch {
            val ids = tareas.mapNotNull { it.fkUnidad }.distinct()
            val map = _unidades.value.toMutableMap()
            ids.forEach { id ->
                if (!map.containsKey(id)) {
                    repo.getUnidadDeTarea(id).onSuccess { it?.let { u -> map[id] = u } }
                }
            }
            _unidades.value = map
        }
    }

    fun setEstatus(tareaId: Int, estatus: String) {
        viewModelScope.launch {
            _tareas.value = _tareas.value.map {
                if (it.id == tareaId) it.copy(estatus = estatus, estaCompletada = estatus == "completado") else it
            }
            repo.setEstatus(tareaId, estatus).onFailure {
                _toastMessage.value = "Error al actualizar"
                val today = LocalDate.now().toString()
                repo.getTareas(currentUserId, today).onSuccess { _tareas.value = it }
            }
        }
    }

    fun toggleTarea(tareaId: Int, completada: Boolean) =
        setEstatus(tareaId, if (completada) "completado" else "pendiente")

    fun actualizarArea(areaId: Int, estatus: String) {
        viewModelScope.launch {
            repo.actualizarEstatusArea(areaId, estatus)
                .onSuccess { _areas.value = _areas.value.map { if (it.id == areaId) it.copy(estatus = estatus) else it } }
                .onFailure { _toastMessage.value = "Error al actualizar área" }
        }
    }

    fun startRealtime(userId: Int) {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            val channel = SupabaseClientProvider.client.channel("cleaning-$userId")
            val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = "tarealimpieza"
            }
            channel.subscribe()
            changes.collect {
                val today = LocalDate.now().toString()
                repo.getTareas(userId, today).onSuccess {
                    _tareas.value = it
                    preloadUnidades(it)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        realtimeJob?.cancel()
    }

    fun clearToast() { _toastMessage.value = null }
}
```

- [ ] **Step 3: Actualizar CleaningTasksScreen para mostrar estados de 3 niveles, notas y ubicación**

En `CleaningTasksScreen`, reemplazar el `items(filtered, ...)` block con:

```kotlin
items(filtered, key = { it.id ?: it.titulo }) { tarea ->
    val unidad = unidades[tarea.fkUnidad]
    val estatusColor = when (tarea.estatus) {
        "completado"  -> StatusSuccess
        "en_proceso"  -> StatusInfo
        else          -> StatusWarning
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            tarea.titulo,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (tarea.estatus == "completado") TextDecoration.LineThrough else null,
                            color = if (tarea.estatus == "completado") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                        val priorityColor = when (tarea.prioridad) { "alta" -> StatusDanger; else -> StatusWarning }
                        StatusChip(tarea.prioridad, priorityColor)
                    }
                    unidad?.let {
                        Text(
                            it.displayUbicacion(),
                            style = MaterialTheme.typography.labelSmall,
                            color = CleaningOrange
                        )
                    } ?: tarea.area?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    tarea.horarioSlot?.let {
                        Text("Hora: $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Notas del residente
            tarea.notas?.let { nota ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(Icons.Default.Notes, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Nota: $nota",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            // Selector de 3 estados
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("pendiente" to "Pendiente", "en_proceso" to "En proceso", "completado" to "Hecho").forEach { (valor, label) ->
                    FilterChip(
                        selected = tarea.estatus == valor,
                        onClick = { tarea.id?.let { vm.setEstatus(it, valor) } },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when (valor) {
                                "completado" -> StatusSuccess; "en_proceso" -> StatusInfo; else -> StatusWarning
                            },
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}
```

También agregar `val unidades by vm.unidades.collectAsStateWithLifecycle()` al inicio de `CleaningTasksScreen`.

- [ ] **Step 4: Agregar solicitud de limpieza en ResidentRepository**

Al final de `ResidentRepository.kt`, agregar:

```kotlin
suspend fun solicitarLimpieza(userId: Int, fkUnidad: Int, notas: String): Result<Unit> = runCatching {
    val fecha = java.time.LocalDate.now().toString()
    client.postgrest["tarealimpieza"].insert(
        TareaLimpieza(
            fkAsignado  = null,
            fkUnidad    = fkUnidad,
            titulo      = "Solicitud de limpieza",
            fecha       = fecha,
            prioridad   = "normal",
            notas       = notas.ifBlank { null },
            estatus     = "pendiente"
        )
    )
}
```

- [ ] **Step 5: Agregar solicitud en ResidentViewModel**

```kotlin
fun solicitarLimpieza(userId: Int, notas: String) {
    viewModelScope.launch {
        val fkUnidad = _unidad.value?.id ?: run {
            _toastMessage.value = "No tienes una unidad asignada"
            return@launch
        }
        repo.solicitarLimpieza(userId, fkUnidad, notas)
            .onSuccess { _toastMessage.value = "Solicitud de limpieza enviada" }
            .onFailure { _toastMessage.value = "Error al enviar solicitud: ${it.message}" }
    }
}
```

- [ ] **Step 6: Agregar botón de solicitud en ResidentHomeScreen**

Agregar un estado y un dialog al inicio de `ResidentHomeScreen`:

```kotlin
var showLimpiezaDialog by remember { mutableStateOf(false) }
var notasLimpieza      by remember { mutableStateOf("") }
```

Agregar en el bloque `SectionHeader("Acciones rápidas")`, después del botón de Avisos:

```kotlin
QuickActionButton(
    Icons.Default.CleaningServices,
    "Limpieza",
    CleaningOrange,
    { showLimpiezaDialog = true },
    Modifier.weight(1f)
)
```

Al final del composable, antes de `if (showPayDialog)`:

```kotlin
if (showLimpiezaDialog) {
    AlertDialog(
        onDismissRequest = { showLimpiezaDialog = false; notasLimpieza = "" },
        title = { Text("Solicitar limpieza") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se creará una tarea de limpieza para tu unidad.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = notasLimpieza,
                    onValueChange = { notasLimpieza = it },
                    label = { Text("Notas adicionales (opcional)") },
                    placeholder = { Text("Ej: Revisar área del lavado") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    vm.solicitarLimpieza(user.id, notasLimpieza)
                    showLimpiezaDialog = false
                    notasLimpieza = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = CleaningOrange)
            ) { Text("Enviar solicitud") }
        },
        dismissButton = { TextButton(onClick = { showLimpiezaDialog = false; notasLimpieza = "" }) { Text("Cancelar") } }
    )
}
```

- [ ] **Step 7: Compilar**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/example/gab/data/repository/CleaningRepository.kt \
        app/src/main/java/com/example/gab/ui/cleaning/viewmodel/CleaningViewModel.kt \
        app/src/main/java/com/example/gab/ui/cleaning/CleaningScreens.kt \
        app/src/main/java/com/example/gab/data/repository/ResidentRepository.kt \
        app/src/main/java/com/example/gab/ui/resident/viewmodel/ResidentViewModel.kt \
        app/src/main/java/com/example/gab/ui/resident/ResidentHomeScreen.kt
git commit -m "feat: cleaning module - 3-state status, unit location, resident notes, and request from home"
```

---

## Task 9: PAGOS — Banner de Vencimiento + Edge Function FCM

**Files:**
- Modify: `app/src/main/java/com/example/gab/util/Extensions.kt`
- Modify: `app/src/main/java/com/example/gab/ui/resident/ResidentHomeScreen.kt`
- Create: `supabase/functions/recordatorio-pago/index.ts`

- [ ] **Step 1: Agregar diasParaPago() en Extensions.kt**

Al final de `Extensions.kt`, agregar:

```kotlin
fun Cuota.diasParaPago(): Int {
    if (fechaVencimiento == null || estatus == "pagado") return Int.MAX_VALUE
    return try {
        val venc = LocalDate.parse(fechaVencimiento.take(10))
        ChronoUnit.DAYS.between(LocalDate.now(), venc).toInt()
    } catch (_: Exception) { Int.MAX_VALUE }
}
```

- [ ] **Step 2: Agregar banner de pago en ResidentHomeScreen**

Dentro del `cuota?.let { c ->` block, ANTES del `GuardianCard` existente de la cuota, agregar:

```kotlin
val diasRestantes = c.diasParaPago()
if (diasRestantes in 0..7 && c.estatus != "pagado") {
    val (bgColor, mensaje) = when {
        diasRestantes <= 0 -> StatusDanger to "¡Tu cuota está VENCIDA!"
        diasRestantes <= 3 -> StatusDanger.copy(alpha = 0.85f) to "Tu cuota vence en $diasRestantes días"
        else               -> StatusWarning to "Tu cuota vence en $diasRestantes días"
    }
    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.Warning, null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(mensaje, color = Color.White, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}
```

Agregar import al inicio del archivo: `import com.example.gab.util.diasParaPago`

- [ ] **Step 3: Crear la Edge Function de Supabase**

Crear la carpeta y el archivo:

```bash
mkdir -p supabase/functions/recordatorio-pago
```

Crear `supabase/functions/recordatorio-pago/index.ts`:

```typescript
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

serve(async (_req) => {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")!
  const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
  const fcmServerKey = Deno.env.get("FCM_SERVER_KEY")!

  const supabase = createClient(supabaseUrl, supabaseKey)

  // Obtener cuotas pendientes con datos del usuario
  const { data: cuotas, error } = await supabase
    .from("cuota")
    .select("id, monto, fechavencimiento, fkusuario, usuario!inner(nombre, fcmtoken, email)")
    .eq("estatus", "pendiente")

  if (error) {
    return new Response(JSON.stringify({ error: error.message }), { status: 500 })
  }

  let enviados = 0
  let errores = 0

  for (const cuota of cuotas ?? []) {
    const usuario = cuota.usuario as { nombre: string; fcmtoken: string | null; email: string | null }
    if (!usuario?.fcmtoken) continue

    const monto = cuota.monto ?? 0
    const vencimiento = cuota.fechavencimiento ?? "próximamente"

    try {
      const res = await fetch("https://fcm.googleapis.com/fcm/send", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `key=${fcmServerKey}`,
        },
        body: JSON.stringify({
          to: usuario.fcmtoken,
          notification: {
            title: "Recordatorio de pago",
            body: `Hola ${usuario.nombre}, tienes una cuota de $${monto.toFixed(2)} que vence el ${vencimiento}.`,
          },
          data: {
            type: "pago",
            cuotaId: cuota.id.toString(),
          },
        }),
      })

      if (res.ok) {
        enviados++
      } else {
        errores++
      }
    } catch (_e) {
      errores++
    }
  }

  return new Response(
    JSON.stringify({ enviados, errores, total: cuotas?.length ?? 0 }),
    { headers: { "Content-Type": "application/json" } }
  )
})
```

- [ ] **Step 4: Configurar el cron en Supabase Dashboard**

En Supabase Dashboard → Edge Functions → `recordatorio-pago`:
1. Hacer deploy manual la primera vez (o via CLI: `supabase functions deploy recordatorio-pago`)
2. Ir a Database → Extensions → habilitar `pg_cron`
3. Ejecutar en SQL Editor:

```sql
-- Ejecutar cada 7 días (domingo a las 9am UTC)
SELECT cron.schedule(
  'recordatorio-pago-semanal',
  '0 9 * * 0',
  $$
  SELECT net.http_post(
    url := current_setting('app.settings.supabase_url') || '/functions/v1/recordatorio-pago',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'Authorization', 'Bearer ' || current_setting('app.settings.service_role_key')
    ),
    body := '{}'::jsonb
  )
  $$
);
```

Alternativamente, configurar cron desde Supabase Dashboard → Database → Cron Jobs (disponible en proyectos con pg_cron habilitado).

- [ ] **Step 5: Agregar FCM_SERVER_KEY en Supabase Secrets**

En Supabase Dashboard → Edge Functions → Secrets, agregar:
- `FCM_SERVER_KEY` = tu Server Key de Firebase Console → Project Settings → Cloud Messaging

- [ ] **Step 6: Compilar app**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/example/gab/util/Extensions.kt \
        app/src/main/java/com/example/gab/ui/resident/ResidentHomeScreen.kt \
        supabase/functions/recordatorio-pago/index.ts
git commit -m "feat: payment due banner in resident home + Supabase Edge Function for 7-day FCM reminders"
```

---

## Task 10: VIGILANCIA — QR Code (Generación + Escaneo)

**Files:**
- Modify: `app/src/main/java/com/example/gab/ui/resident/ResidentProfileScreen.kt`
- Modify: `app/src/main/java/com/example/gab/ui/security/SecurityScreens.kt`
- Modify: `app/src/main/java/com/example/gab/ui/security/viewmodel/SecurityViewModel.kt`
- Modify: `app/src/main/java/com/example/gab/data/repository/SecurityRepository.kt`

- [ ] **Step 1: Agregar QR display en ResidentProfileScreen**

En `ResidentProfileScreen.kt`, agregar imports al inicio del archivo:

```kotlin
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
```

Agregar un `item` nuevo DESPUÉS del card de "Información personal" y ANTES del card de "Notificaciones":

```kotlin
item {
    GuardianCard {
        Text("Mi código QR de acceso", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Preséntalo al guardia para registrar tu entrada/salida",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        val qrBitmap: Bitmap? = remember(user.id) {
            runCatching {
                BarcodeEncoder().encodeBitmap(user.id.toString(), BarcodeFormat.QR_CODE, 400, 400)
            }.getOrNull()
        }
        qrBitmap?.let { bmp ->
            androidx.compose.foundation.Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Código QR",
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.CenterHorizontally)
            )
        } ?: Text("No se pudo generar el QR", color = MaterialTheme.colorScheme.error)
    }
}
```

Nota: `ResidentProfileScreen` ahora recibe `vm: ResidentViewModel` (Task 5). `user.id` debe estar disponible.

- [ ] **Step 2: Agregar función de búsqueda de residente por ID en SecurityRepository**

Al final de `SecurityRepository.kt`, agregar:

```kotlin
suspend fun getResidentePorId(userId: Int): Result<Usuario?> = runCatching {
    client.postgrest["usuario"].select {
        filter { eq("id", userId) }
    }.decodeList<Usuario>().firstOrNull()
}
```

- [ ] **Step 3: Agregar estado y función para QR scan en SecurityViewModel**

Agregar al final de los estados:

```kotlin
private val _residenteEscaneado = MutableStateFlow<Usuario?>(null)
val residenteEscaneado: StateFlow<Usuario?> = _residenteEscaneado.asStateFlow()
```

Agregar función nueva:

```kotlin
fun onQrScanned(rawUid: String, guardiaId: Int) {
    viewModelScope.launch {
        val uid = rawUid.trim().toIntOrNull() ?: run {
            _toastMessage.value = "QR inválido"
            return@launch
        }
        repo.getResidentePorId(uid)
            .onSuccess { residente ->
                if (residente == null) {
                    _toastMessage.value = "Residente no encontrado"
                } else {
                    _residenteEscaneado.value = residente
                }
            }
            .onFailure { _toastMessage.value = "Error al leer QR: ${it.message}" }
    }
}

fun registrarAccesoQr(guardiaId: Int, residente: Usuario, direccion: String) {
    val hora = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    viewModelScope.launch {
        repo.registrarAcceso(residente.id, guardiaId, direccion, hora)
            .onSuccess {
                _toastMessage.value = "${direccion.lowercase().replaceFirstChar { it.uppercase() }} QR: ${residente.nombre}"
                _residenteEscaneado.value = null
                repo.getAccesoLog().onSuccess { _accesoLog.value = it }
            }
            .onFailure { _toastMessage.value = "Error al registrar acceso" }
    }
}

fun clearResidenteEscaneado() { _residenteEscaneado.value = null }
```

- [ ] **Step 4: Agregar tab "QR" en SecurityScreens y el scanner composable**

En `SecurityShell`, agregar un item a `navItems` y la nueva constante de route. Primero en `Routes.kt` (AppNavGraph.kt), agregar:

```kotlin
const val SECURITY_QR = "security_qr"
```

En `SecurityShell`, actualizar navItems:

```kotlin
val navItems = listOf(
    NavItem("Inicio",     Icons.Default.Home,      Routes.SECURITY_HOME),
    NavItem("Accesos",    Icons.Default.PeopleAlt, Routes.SECURITY_VISITORS),
    NavItem("QR Scan",    Icons.Default.QrCodeScanner, Routes.SECURITY_QR),
    NavItem("Incidentes", Icons.Default.Warning,   Routes.SECURITY_INCIDENTS),
)
```

En el `NavHost` agregar:

```kotlin
composable(Routes.SECURITY_QR) { SecurityQrScreen(user, vm) }
```

Agregar el composable `SecurityQrScreen` al final de `SecurityScreens.kt`:

```kotlin
@Composable
fun SecurityQrScreen(user: AppUser, vm: SecurityViewModel) {
    val residenteEscaneado by vm.residenteEscaneado.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? androidx.activity.ComponentActivity

    val scanLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val scanResult = com.journeyapps.barcodescanner.ScanIntentResult.parseActivityResult(
            result.resultCode, result.data
        )
        scanResult.contents?.let { vm.onQrScanned(it, user.id) }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Verificar Residente por QR", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Escanea el código QR del residente para registrar su acceso.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Button(
                onClick = {
                    activity?.let { act ->
                        val integrator = com.journeyapps.barcodescanner.IntentIntegrator(act)
                        integrator.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.IntentIntegrator.QR_CODE)
                        integrator.setOrientationLocked(false)
                        integrator.setPrompt("Apunta al QR del residente")
                        scanLauncher.launch(integrator.createScanIntent())
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SecurityGreen)
            ) {
                Icon(Icons.Default.QrCodeScanner, null)
                Spacer(Modifier.width(8.dp))
                Text("Abrir escáner QR", fontWeight = FontWeight.Bold)
            }
        }
        residenteEscaneado?.let { residente ->
            item {
                GuardianCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Residente identificado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = SecurityGreen)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarCircle(
                                initials = residente.nombre.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""),
                                color = SecurityGreen
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(residente.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("ID: ${residente.id}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.registrarAccesoQr(user.id, residente, "ENTRADA") },
                                colors = ButtonDefaults.buttonColors(containerColor = SecurityGreen),
                                modifier = Modifier.weight(1f)
                            ) { Text("Entrada") }
                            Button(
                                onClick = { vm.registrarAccesoQr(user.id, residente, "SALIDA") },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusWarning),
                                modifier = Modifier.weight(1f)
                            ) { Text("Salida") }
                        }
                        TextButton(onClick = { vm.clearResidenteEscaneado() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Cancelar")
                        }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 5: Compilar**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/gab/ui/resident/ResidentProfileScreen.kt \
        app/src/main/java/com/example/gab/ui/security/SecurityScreens.kt \
        app/src/main/java/com/example/gab/ui/security/viewmodel/SecurityViewModel.kt \
        app/src/main/java/com/example/gab/data/repository/SecurityRepository.kt \
        app/src/main/java/com/example/gab/ui/navigation/AppNavGraph.kt
git commit -m "feat: QR code display for residents and QR scanner tab for security guard"
```

---

## Task 11: VIGILANCIA — CameraX Composable Compartido

**Files:**
- Create: `app/src/main/java/com/example/gab/ui/security/CameraScreen.kt`

Este composable es la base que usan tanto el flujo de INE como el de Placas.

- [ ] **Step 1: Crear CameraScreen.kt**

```kotlin
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.gab.ui.security

import android.Manifest
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    hint: String = "Apunta la cámara al documento",
    onTextRecognized: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context       = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageCapture  = remember { ImageCapture.Builder().build() }
    var isCapturing   by remember { mutableStateOf(false) }
    var errorMsg      by remember { mutableStateOf<String?>(null) }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    if (!cameraPermission.status.isGranted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Se necesita permiso de cámara", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = { cameraPermission.launchPermissionRequest() }) { Text("Conceder permiso") }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                    } catch (_: Exception) { }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay hint
        Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp),
            color = Color.Black.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(hint, color = Color.White, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }

        // Error message
        errorMsg?.let {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        // Buttons
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionButton(
                onClick = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cancelar")
            }
            FloatingActionButton(
                onClick = {
                    if (isCapturing) return@FloatingActionButton
                    isCapturing = true
                    errorMsg = null
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val mediaImage = image.image
                                if (mediaImage == null) {
                                    image.close()
                                    isCapturing = false
                                    return
                                }
                                val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                                TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                    .process(inputImage)
                                    .addOnSuccessListener { visionText ->
                                        image.close()
                                        isCapturing = false
                                        if (visionText.text.isBlank()) {
                                            errorMsg = "No se detectó texto. Intenta nuevamente."
                                        } else {
                                            onTextRecognized(visionText.text)
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        image.close()
                                        isCapturing = false
                                        errorMsg = "Error de reconocimiento: ${e.message}"
                                    }
                            }
                            override fun onError(exception: ImageCaptureException) {
                                isCapturing = false
                                errorMsg = "Error de cámara: ${exception.message}"
                            }
                        }
                    )
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                if (isCapturing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Camera, contentDescription = "Capturar", tint = Color.White)
                }
            }
        }
    }
}
```

Nota: `com.google.accompanist.permissions` requiere una dependencia adicional. Agregar en `app/build.gradle.kts`:
```kotlin
implementation("com.google.accompanist:accompanist-permissions:0.36.0")
```

- [ ] **Step 2: Compilar**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/example/gab/ui/security/CameraScreen.kt app/build.gradle.kts
git commit -m "feat: CameraX + ML Kit shared composable for text recognition"
```

---

## Task 12: VIGILANCIA — INE de Visitantes

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/repository/SecurityRepository.kt`
- Modify: `app/src/main/java/com/example/gab/ui/security/viewmodel/SecurityViewModel.kt`
- Modify: `app/src/main/java/com/example/gab/ui/security/SecurityScreens.kt`

- [ ] **Step 1: Agregar guardarVisita en SecurityRepository**

Al final de `SecurityRepository.kt`, agregar:

```kotlin
suspend fun guardarVisita(
    nombreVisitante: String,
    guardiaId: Int,
    tipo: String
): Result<Unit> = runCatching {
    client.postgrest["visita"].insert(
        Visita(
            nombreVisitante = nombreVisitante,
            fkGuardia = guardiaId,
            tipo = tipo
        )
    )
}

suspend fun getVisitas(): Result<List<Visita>> = runCatching {
    client.postgrest["visita"].select {
        order("timestamp", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
        limit(50)
    }.decodeList()
}
```

- [ ] **Step 2: Agregar estados y funciones de INE en SecurityViewModel**

Agregar al bloque de estados:

```kotlin
private val _visitas = MutableStateFlow<List<Visita>>(emptyList())
val visitas: StateFlow<List<Visita>> = _visitas.asStateFlow()

private val _showIneCamera = MutableStateFlow(false)
val showIneCamera: StateFlow<Boolean> = _showIneCamera.asStateFlow()

private val _ineTextoReconocido = MutableStateFlow("")
val ineTextoReconocido: StateFlow<String> = _ineTextoReconocido.asStateFlow()
```

Agregar funciones:

```kotlin
fun abrirCamaraIne()  { _showIneCamera.value = true }
fun cerrarCamaraIne() { _showIneCamera.value = false; _ineTextoReconocido.value = "" }

fun onIneTextoReconocido(texto: String) {
    _showIneCamera.value = false
    // Extraer nombre: primera línea en mayúsculas o la de mayor longitud con solo letras/espacios
    val lineas = texto.lines().map { it.trim() }.filter { it.isNotBlank() }
    val nombreEstimado = lineas
        .filter { line -> line.all { it.isLetter() || it.isWhitespace() } && line.length > 4 }
        .maxByOrNull { it.length } ?: lineas.firstOrNull() ?: texto.take(50)
    _ineTextoReconocido.value = nombreEstimado
}

fun confirmarVisitaIne(guardiaId: Int, nombreConfirmado: String) {
    viewModelScope.launch {
        repo.guardarVisita(nombreConfirmado, guardiaId, "INE")
            .onSuccess {
                _toastMessage.value = "Visitante registrado: $nombreConfirmado"
                _ineTextoReconocido.value = ""
                repo.getVisitas().onSuccess { _visitas.value = it }
            }
            .onFailure { _toastMessage.value = "Error al registrar visitante: ${it.message}" }
    }
}
```

Actualizar `loadAll()` para cargar visitas: `repo.getVisitas().onSuccess { _visitas.value = it }`.

- [ ] **Step 3: Agregar flujo de INE en SecurityScreens**

Agregar constante en `Routes`:

```kotlin
const val SECURITY_VISITORS_INE = "security_visitors_ine"
```

En `SecurityShell` NavHost agregar:

```kotlin
composable(Routes.SECURITY_VISITORS_INE) { SecurityIneScreen(user, vm) }
```

Agregar composable al final de `SecurityScreens.kt`:

```kotlin
@Composable
fun SecurityIneScreen(user: AppUser, vm: SecurityViewModel) {
    val showCamera          by vm.showIneCamera.collectAsStateWithLifecycle()
    val ineTextoReconocido  by vm.ineTextoReconocido.collectAsStateWithLifecycle()
    val visitas             by vm.visitas.collectAsStateWithLifecycle()
    var nombreEditable      by remember(ineTextoReconocido) { mutableStateOf(ineTextoReconocido) }

    if (showCamera) {
        CameraScreen(
            hint = "Apunta al nombre en la INE del visitante",
            onTextRecognized = { vm.onIneTextoReconocido(it) },
            onDismiss = { vm.cerrarCamaraIne() }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Registro de Visitantes (INE)", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Button(
                onClick = { vm.abrirCamaraIne() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SecurityGreen)
            ) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("Fotografiar INE del visitante")
            }
        }
        if (ineTextoReconocido.isNotBlank()) {
            item {
                LaunchedEffect(ineTextoReconocido) { nombreEditable = ineTextoReconocido }
                GuardianCard {
                    Text("Nombre detectado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = nombreEditable,
                        onValueChange = { nombreEditable = it },
                        label = { Text("Nombre del visitante") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.cerrarCamaraIne() }, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                        Button(
                            onClick = { if (nombreEditable.isNotBlank()) vm.confirmarVisitaIne(user.id, nombreEditable) },
                            enabled = nombreEditable.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = SecurityGreen),
                            modifier = Modifier.weight(1f)
                        ) { Text("Registrar") }
                    }
                }
            }
        }
        if (visitas.isNotEmpty()) {
            item { SectionHeader("Últimas visitas") }
            items(visitas.take(20)) { visita ->
                GuardianCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AvatarCircle(initials = visita.nombreVisitante.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString(""), color = SecurityGreen)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(visita.nombreVisitante, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("${visita.tipo} · ${visita.timestamp?.take(16) ?: "—"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusChip("INE", SecurityGreen)
                    }
                }
            }
        }
    }
}
```

En `SecurityVisitorsScreen`, agregar botón "Registrar visitante con INE":

```kotlin
// Agregar al inicio del LazyColumn, después del header:
item {
    Button(
        onClick = { navController.navigate(Routes.SECURITY_VISITORS_INE) },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = SecurityGreen)
    ) {
        Icon(Icons.Default.CameraAlt, null)
        Spacer(Modifier.width(8.dp))
        Text("Registrar visitante con INE")
    }
}
```

`SecurityVisitorsScreen` necesita recibir `navController` como parámetro. Actualizar en `SecurityShell`:
```kotlin
composable(Routes.SECURITY_VISITORS) { SecurityVisitorsScreen(user, vm, navController) }
```

Y la firma: `fun SecurityVisitorsScreen(user: AppUser, vm: SecurityViewModel, navController: NavController)`

- [ ] **Step 4: Compilar**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/gab/data/repository/SecurityRepository.kt \
        app/src/main/java/com/example/gab/ui/security/viewmodel/SecurityViewModel.kt \
        app/src/main/java/com/example/gab/ui/security/SecurityScreens.kt \
        app/src/main/java/com/example/gab/ui/navigation/AppNavGraph.kt
git commit -m "feat: INE visitor registration with ML Kit text recognition"
```

---

## Task 13: VIGILANCIA — Vehículos y Reconocimiento de Placas

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/repository/SecurityRepository.kt`
- Modify: `app/src/main/java/com/example/gab/data/repository/ResidentRepository.kt`
- Modify: `app/src/main/java/com/example/gab/ui/security/viewmodel/SecurityViewModel.kt`
- Modify: `app/src/main/java/com/example/gab/ui/resident/viewmodel/ResidentViewModel.kt`
- Modify: `app/src/main/java/com/example/gab/ui/security/SecurityScreens.kt`
- Modify: `app/src/main/java/com/example/gab/ui/resident/ResidentProfileScreen.kt`

- [ ] **Step 1: Agregar CRUD de vehículos en SecurityRepository**

Al final de `SecurityRepository.kt`, agregar:

```kotlin
suspend fun getVehiculoPorPlaca(placa: String): Result<Vehiculo?> = runCatching {
    val placaNormalizada = placa.uppercase().replace(" ", "").replace("-", "")
    client.postgrest["vehiculo"].select().decodeList<Vehiculo>()
        .firstOrNull { v ->
            val vNorm = v.placa.uppercase().replace(" ", "").replace("-", "")
            vNorm == placaNormalizada
        }
}
```

- [ ] **Step 2: Agregar CRUD de vehículos en ResidentRepository**

Al final de `ResidentRepository.kt`, agregar:

```kotlin
suspend fun getVehiculos(userId: Int): Result<List<Vehiculo>> = runCatching {
    client.postgrest["vehiculo"].select {
        filter { eq("fk_usuario", userId) }
    }.decodeList()
}

suspend fun agregarVehiculo(userId: Int, placa: String, descripcion: String, color: String): Result<Unit> = runCatching {
    client.postgrest["vehiculo"].insert(
        Vehiculo(fkUsuario = userId, placa = placa.uppercase().trim(), descripcion = descripcion.ifBlank { null }, color = color.ifBlank { null })
    )
}

suspend fun eliminarVehiculo(vehiculoId: Int): Result<Unit> = runCatching {
    client.postgrest["vehiculo"].delete { filter { eq("id", vehiculoId) } }
}
```

- [ ] **Step 3: Agregar flujo de placas en SecurityViewModel**

Agregar estados:

```kotlin
private val _showPlacaCamera     = MutableStateFlow(false)
val showPlacaCamera: StateFlow<Boolean> = _showPlacaCamera.asStateFlow()

private val _placaResultado      = MutableStateFlow<Pair<String, Vehiculo?>?>(null)
val placaResultado: StateFlow<Pair<String, Vehiculo?>?> = _placaResultado.asStateFlow()
```

Agregar funciones:

```kotlin
fun abrirCamaraPlaca()  { _showPlacaCamera.value = true }
fun cerrarCamaraPlaca() { _showPlacaCamera.value = false; _placaResultado.value = null }

private val PLACA_REGEX = Regex("[A-Z]{2,3}[-\\s]?\\d{2,4}[-\\s]?[A-Z0-9]{0,3}")

fun onPlacaTextoReconocido(guardiaId: Int, texto: String) {
    _showPlacaCamera.value = false
    viewModelScope.launch {
        val placaEncontrada = PLACA_REGEX.find(texto.uppercase())?.value
        if (placaEncontrada == null) {
            _toastMessage.value = "No se detectó placa. Intenta con mejor iluminación."
            return@launch
        }
        repo.getVehiculoPorPlaca(placaEncontrada)
            .onSuccess { vehiculo ->
                _placaResultado.value = Pair(placaEncontrada, vehiculo)
                if (vehiculo != null) {
                    // Registrar visita de vehículo autorizado
                    repo.guardarVisita(
                        nombreVisitante = "Vehículo ${vehiculo.placa} (${vehiculo.descripcion ?: ""})",
                        guardiaId = guardiaId,
                        tipo = "PLACA"
                    )
                }
            }
            .onFailure { _toastMessage.value = "Error al consultar placa: ${it.message}" }
    }
}
```

- [ ] **Step 4: Agregar pantalla de verificación de placas en SecurityScreens**

Agregar constante: `const val SECURITY_PLACAS = "security_placas"`

En `SecurityShell` NavHost: `composable(Routes.SECURITY_PLACAS) { SecurityPlacasScreen(user, vm) }`

Agregar composable:

```kotlin
@Composable
fun SecurityPlacasScreen(user: AppUser, vm: SecurityViewModel) {
    val showCamera    by vm.showPlacaCamera.collectAsStateWithLifecycle()
    val placaResult   by vm.placaResultado.collectAsStateWithLifecycle()

    if (showCamera) {
        CameraScreen(
            hint = "Apunta a la placa del vehículo",
            onTextRecognized = { vm.onPlacaTextoReconocido(user.id, it) },
            onDismiss = { vm.cerrarCamaraPlaca() }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Text("Verificar Vehículo", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            Button(
                onClick = { vm.abrirCamaraPlaca() },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SecurityGreen)
            ) {
                Icon(Icons.Default.DirectionsCar, null)
                Spacer(Modifier.width(8.dp))
                Text("Fotografiar placa")
            }
        }
        placaResult?.let { (placa, vehiculo) ->
            item {
                val (cardColor, titulo, subtitulo) = if (vehiculo != null) {
                    Triple(StatusSuccess, "Vehículo AUTORIZADO", "Propietario ID: ${vehiculo.fkUsuario}")
                } else {
                    Triple(StatusDanger, "Vehículo NO REGISTRADO", "La placa $placa no está en el sistema")
                }
                Surface(
                    color = cardColor.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, cardColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (vehiculo != null) Icons.Default.CheckCircle else Icons.Default.Warning,
                                null, tint = cardColor, modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(titulo, fontWeight = FontWeight.Bold, color = cardColor, style = MaterialTheme.typography.bodyLarge)
                                Text("Placa: $placa", style = MaterialTheme.typography.bodyMedium)
                                vehiculo?.let {
                                    it.descripcion?.let { d -> Text(d, style = MaterialTheme.typography.bodySmall) }
                                    it.color?.let { c -> Text("Color: $c", style = MaterialTheme.typography.bodySmall) }
                                }
                                Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        TextButton(onClick = { vm.cerrarCamaraPlaca() }, modifier = Modifier.fillMaxWidth()) { Text("Nueva verificación") }
                    }
                }
            }
        }
    }
}
```

En `SecurityShell`, agregar a `navItems`:
```kotlin
NavItem("Vehículos", Icons.Default.DirectionsCar, Routes.SECURITY_PLACAS),
```

- [ ] **Step 5: Agregar sección "Mis vehículos" en ResidentProfileScreen**

Agregar estados en `ResidentProfileScreen` (que ya recibe `vm`):

```kotlin
val vehiculos by vm.vehiculos.collectAsStateWithLifecycle()
var showAddVehiculo by remember { mutableStateOf(false) }
```

Agregar estos estados en `ResidentViewModel`:
```kotlin
private val _vehiculos = MutableStateFlow<List<Vehiculo>>(emptyList())
val vehiculos: StateFlow<List<Vehiculo>> = _vehiculos.asStateFlow()

fun cargarVehiculos(userId: Int) {
    viewModelScope.launch {
        repo.getVehiculos(userId).onSuccess { _vehiculos.value = it }
    }
}

fun agregarVehiculo(userId: Int, placa: String, descripcion: String, color: String) {
    viewModelScope.launch {
        repo.agregarVehiculo(userId, placa, descripcion, color)
            .onSuccess {
                _toastMessage.value = "Vehículo registrado"
                repo.getVehiculos(userId).onSuccess { _vehiculos.value = it }
            }
            .onFailure { _toastMessage.value = "Error: ${it.message}" }
    }
}

fun eliminarVehiculo(userId: Int, vehiculoId: Int) {
    viewModelScope.launch {
        repo.eliminarVehiculo(vehiculoId)
            .onSuccess {
                _toastMessage.value = "Vehículo eliminado"
                _vehiculos.value = _vehiculos.value.filter { it.id != vehiculoId }
            }
            .onFailure { _toastMessage.value = "Error al eliminar: ${it.message}" }
    }
}
```

En `loadAll(userId)` agregar: `repo.getVehiculos(userId).onSuccess { _vehiculos.value = it }`

Agregar `item` de vehículos en `ResidentProfileScreen` ANTES del card de notificaciones:

```kotlin
item {
    LaunchedEffect(user.id) { vm.cargarVehiculos(user.id) }
    GuardianCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mis vehículos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = { showAddVehiculo = true }) {
                Icon(Icons.Default.Add, null, tint = ResidentBlue)
            }
        }
        if (vehiculos.isEmpty()) {
            Text("Sin vehículos registrados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        vehiculos.forEach { v ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.DirectionsCar, null, tint = ResidentBlue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.placa, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    listOfNotNull(v.descripcion, v.color?.let { "Color: $it" }).joinToString(" · ").let {
                        if (it.isNotBlank()) Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = { v.id?.let { vm.eliminarVehiculo(user.id, it) } }) {
                    Icon(Icons.Default.Delete, null, tint = StatusDanger, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
```

Dialog para agregar vehículo (al final del composable, antes del dialog de logout):

```kotlin
if (showAddVehiculo) {
    var placa       by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var color       by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { showAddVehiculo = false },
        title = { Text("Registrar vehículo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = placa, onValueChange = { placa = it.uppercase() }, label = { Text("Placa (ej. ABC-1234)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = descripcion, onValueChange = { descripcion = it }, label = { Text("Descripción (ej. Sedan gris)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = color, onValueChange = { color = it }, label = { Text("Color") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (placa.isNotBlank()) {
                        vm.agregarVehiculo(user.id, placa, descripcion, color)
                        showAddVehiculo = false
                    }
                },
                enabled = placa.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ResidentBlue)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = { showAddVehiculo = false }) { Text("Cancelar") } }
    )
}
```

- [ ] **Step 6: Compilar final completo**

```bash
./gradlew assembleDebug
```
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 7: Commit final**

```bash
git add -A
git commit -m "feat: vehicle registration for residents and license plate recognition for security guard"
```

---

## Checklist de QA Manual

Antes de entregar, verificar manualmente en emulador o dispositivo:

### Auth
- [ ] Crear usuario desde Admin → usuario recibe email de verificación
- [ ] Login sin verificar → mensaje claro + botón "Reenviar"
- [ ] Login con email verificado → acceso exitoso

### Realtime
- [ ] Crear aviso desde Admin en dispositivo A → aparece en residente en dispositivo B sin recargar
- [ ] Crear reserva → visible en admin en tiempo real

### Location
- [ ] ResidentHomeScreen muestra "Bloque X · Piso Y · Depto Z"
- [ ] Admin puede crear unidad con campo Piso

### Reservas
- [ ] Intentar reservar slot ya ocupado → mensaje de error claro
- [ ] Slots ocupados aparecen en gris al abrir el dialog

### Limpieza
- [ ] Residente puede solicitar limpieza con notas desde Home
- [ ] Personal ve las 3 opciones de estado (Pendiente / En proceso / Hecho)
- [ ] Tarea muestra ubicación (Bloque · Piso · Depto) si tiene unidad asignada

### Pagos
- [ ] Si cuota vence en ≤ 7 días → banner visible en ResidentHome
- [ ] Si cuota está vencida → banner rojo

### Vigilancia - QR
- [ ] ResidentProfileScreen muestra código QR del userId
- [ ] Guardia puede escanear y ver datos del residente
- [ ] Botones Entrada / Salida registran en acceso log

### Vigilancia - INE
- [ ] Cámara abre correctamente con permiso
- [ ] ML Kit extrae nombre de texto capturado
- [ ] Nombre editable antes de confirmar
- [ ] Visita guardada en tabla `visita`

### Vigilancia - Placas
- [ ] Cámara detecta texto de placa
- [ ] Placa registrada → alerta verde con datos del propietario
- [ ] Placa no registrada → alerta roja visible

---

## Notas Importantes para el Implementador

1. **Supabase Realtime requiere** que las tablas estén habilitadas en la configuración de Replication. Ver Task 1 Step 3.
2. **La Edge Function** necesita `FCM_SERVER_KEY` configurada en Supabase Secrets antes de funcionar.
3. **ML Kit + CameraX** requieren dispositivo físico o emulador con cámara configurada para probar correctamente.
4. **Accompanist permissions** (`com.google.accompanist:accompanist-permissions:0.36.0`) debe agregarse en Task 11 Step 1.
5. El extractor de placas usa regex genérico. Las placas mexicanas tienen varios formatos; el regex `[A-Z]{2,3}[-\s]?\d{2,4}[-\s]?[A-Z0-9]{0,3}` cubre la mayoría pero puede necesitar ajuste.
