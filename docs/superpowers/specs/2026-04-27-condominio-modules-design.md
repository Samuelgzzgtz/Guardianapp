# Diseño: Módulos Completos App Condominio
**Fecha:** 2026-04-27
**Stack:** Android Kotlin + Jetpack Compose + Supabase (Auth, Postgrest, Realtime, Storage) + Firebase Messaging (FCM)
**Contexto:** App comercial de gestión de condominios con 4 roles: RESIDENTE, SEGURIDAD, ADMIN, LIMPIEZA

---

## Módulo 1 — AUTH: Verificación de Correo

### Problema actual
`AuthRepository.createUser()` pasa `email_confirm: true` en el body de la API de admin, saltándose la verificación siempre.

### Cambios
1. **AuthRepository.createUser()**: Cambiar `email_confirm:true` → `email_confirm:false`.
2. **AuthRepository.signIn()**: Después de obtener `supaUser`, verificar `supaUser.emailConfirmedAt`. Si es null → lanzar excepción `"Verifica tu correo antes de continuar"`.
3. **AuthRepository**: Agregar `suspend fun reenviarVerificacion(email: String)` que llame a `client.auth.resendEmail(OtpType.Email.EMAIL, email)`.
4. **LoginScreen**: Cuando el error contiene "Verifica", mostrar un segundo botón "Reenviar correo" que llame a `AuthViewModel.reenviarVerificacion(email)`.
5. **AuthViewModel**: Agregar `fun reenviarVerificacion(email: String)`.

### Criterio de aceptación
- Login bloqueado hasta que el email esté verificado.
- Botón "Reenviar" visible y funcional cuando el error es de verificación.
- Admin puede crear usuarios; esos usuarios reciben correo de verificación.

---

## Módulo 2 — REALTIME: Sincronización en Tiempo Real

### Problema actual
`Realtime` está instalado pero no se usa. Todas las pantallas cargan datos con `postgrest["tabla"].select()` (one-shot). No hay actualizaciones automáticas.

### Cambios
Crear `RealtimeRepository` (o extensiones en los repositorios existentes) que expongan `Flow<List<T>>` usando `supabase.channel().postgresChangeFlow()`.

**Pantallas críticas a migrar:**
- `ResidentAmenitiesScreen` → reservas en tiempo real
- `ResidentHomeScreen` → avisos en tiempo real
- `SecurityVisitorsScreen` → acceso log en tiempo real
- `CleaningTasksScreen` → tareas en tiempo real
- `AdminScreens` → reportes y usuarios en tiempo real

**Patrón a aplicar:**
```kotlin
fun observarReservas(userId: Int): Flow<List<Reserva>> = flow {
    val channel = supabase.channel("reservas-$userId")
    val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
        table = "reserva"
        filter = "fkusuario=eq.$userId"
    }
    channel.subscribe()
    // emitir carga inicial + cambios
    emit(fetchReservas(userId))
    changes.collect { emit(fetchReservas(userId)) }
}
```

ViewModels afectados: `ResidentViewModel`, `SecurityViewModel`, `CleaningViewModel`, `AdminViewModel`.

### Criterio de aceptación
- Acción en un dispositivo → visible en otro en < 3s sin recargar.
- Sin crash al perder conexión (el channel de Supabase maneja reconexión).

---

## Módulo 3 — LOCATION: Bloque / Piso / Departamento

### Problema actual
`Unidad` tiene `numero` (número de depto) y `torre` (bloque). No existe campo `piso`. La UI usa labels genéricos.

### Cambios DB (Supabase)
```sql
ALTER TABLE unidad ADD COLUMN IF NOT EXISTS piso integer DEFAULT 1;
```

### Cambios en Kotlin
- `Models.kt` → `Unidad`: agregar `@SerialName("piso") val piso: Int = 1`
- `Unidad.displayBloque`: property de extensión → `torre ?: "Sin torre"`
- `Unidad.displayDepartamento`: → `numero`

### Cambios UI
- **AdminScreens** (crear/editar unidad): Labels actualizados a "Bloque/Torre", "Piso", "N° Departamento". Agregar campo piso (NumberField).
- **ResidentProfileScreen**: Mostrar "Bloque X · Piso Y · Depto Z".
- **CleaningTasksScreen**: Mostrar ubicación de la tarea (bloque + piso + depto).
- **AdminScreens** (lista de unidades): Ordenar y mostrar con bloque/piso/depto.

### Criterio de aceptación
- Ninguna pantalla muestra "torre" en label; todo dice "Bloque".
- Campo piso guardado y recuperado correctamente de Supabase.
- Filtros de limpieza y admin usan bloque+piso+depto.

---

## Módulo 4 — RESERVAS: Bloqueo de Horario

### Problema actual
`crearReserva()` inserta directamente sin verificar solapamientos. Dos usuarios pueden reservar la misma amenidad en el mismo horario.

### Cambios en ResidentRepository
```kotlin
suspend fun slotOcupado(amenidadId: Int, fecha: String, slot: String): Boolean {
    val existing = client.postgrest["reserva"].select {
        filter {
            eq("fkamenidad", amenidadId)
            eq("fechareservacion", fecha)
            eq("horarioslot", slot)
            eq("estatus", "activa")
        }
    }.decodeList<Reserva>()
    return existing.isNotEmpty()
}

suspend fun getSlotsTomados(amenidadId: Int, fecha: String): List<String>
```

`crearReserva()` llama `slotOcupado()` primero → si true, lanza `Exception("Este horario ya está reservado")`.

### Cambios UI (ResidentAmenitiesScreen)
- `NewReservaDialog`: al seleccionar fecha, cargar slots tomados vía `vm.cargarSlotsTomados(amenidadId, fecha)`.
- Mostrar slots tomados con `enabled = false` y color grisáceo en el dropdown.
- Label "Ocupado" junto a slots no disponibles.

### Criterio de aceptación
- Reserva doble bloqueada con mensaje claro.
- Slots ocupados visibles en el selector antes de intentar reservar.

---

## Módulo 5 — VIGILANCIA: QR, INE y Placas

### Nuevas dependencias (app/build.gradle.kts)
```kotlin
implementation("com.journeyapps:zxing-android-embedded:4.3.0")
implementation("com.google.mlkit:text-recognition:16.0.1")
implementation("androidx.camera:camera-camera2:1.3.4")
implementation("androidx.camera:camera-view:1.3.4")
implementation("androidx.camera:camera-lifecycle:1.3.4")
```

### Nueva tabla Supabase
```sql
CREATE TABLE IF NOT EXISTS visita (
    id SERIAL PRIMARY KEY,
    nombre_visitante TEXT NOT NULL,
    fk_guardia INTEGER REFERENCES usuario(id),
    tipo TEXT DEFAULT 'INE',  -- 'INE' | 'PLACA' | 'QR'
    placa TEXT,
    foto_url TEXT,
    timestamp TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE IF NOT EXISTS vehiculo (
    id SERIAL PRIMARY KEY,
    fk_usuario INTEGER REFERENCES usuario(id),
    placa TEXT NOT NULL,
    descripcion TEXT,
    color TEXT
);
```

### Flujo 1 — Residente (QR)
- **ResidentProfileScreen**: Mostrar `QrCodeView` generado con la librería ZXing usando el `userId` como data. El QR se muestra en pantalla, el residente lo presenta al guardia.
- **SecurityScreens (nuevo tab "Accesos QR")**: `IntentIntegrator` de ZXing para escanear. Al decodificar el uid → buscar residente en Supabase → mostrar nombre, foto, unidad → registrar `AccesoLog`.

### Flujo 2 — Visitante (INE)
- **SecurityScreens**: Botón "Registrar visitante con INE". Abre `CameraPreviewScreen` con CameraX.
- Al capturar imagen → ML Kit `TextRecognizer.process(inputImage)` → extraer nombre (primera línea con mayúsculas y apellidos).
- Guardar en tabla `visita` con `tipo = "INE"` y foto subida a Supabase Storage bucket `fotos-visitas`.
- Mostrar resultado para confirmación antes de guardar.

### Flujo 3 — Vehículo (Placas)
- **SecurityScreens**: Botón "Verificar vehículo". Abre cámara.
- ML Kit lee texto → extrae cadena que coincida con patrón de placa mexicana (`[A-Z]{3}-\d{3}-[A-Z]{2}` o variantes).
- Consultar tabla `vehiculo` buscando esa placa. Si match → mostrar datos del propietario + acceso OK. Si no → alerta roja "Vehículo no registrado".
- **ResidentProfileScreen**: Sección "Mis vehículos" con formulario para agregar placa + descripción + color.

### Criterio de aceptación
- QR generado en perfil del residente, escaneable por guardia.
- ML Kit reconoce texto de INE con > 70% precisión en condiciones normales.
- Alerta visible cuando placa no está registrada.

---

## Módulo 6 — PAGOS: Recordatorio cada 7 días

### Problema actual
FCM token se guarda en `usuario.fcmtoken`. No hay scheduler ni banner en la app.

### Supabase Edge Function
Archivo: `supabase/functions/recordatorio-pago/index.ts`
```typescript
// Cron: cada 7 días (configurado en Supabase Dashboard → Edge Functions → Cron)
// Lee usuarios con cuotas pendientes → envía FCM via REST API
const FCM_SERVER_KEY = Deno.env.get("FCM_SERVER_KEY")!
// Para cada usuario con cuota pendiente y fcmToken:
//   POST https://fcm.googleapis.com/fcm/send con notification payload
```

### Cambios en la app
- **ResidentHomeScreen**: Leer `cuota.fechaVencimiento` → calcular días restantes → mostrar `Banner` de advertencia si ≤ 7 días o `Badge` en el ícono de cuota.
- `ResidentViewModel.diasParaPago(): Int` calculado localmente a partir de la fecha de vencimiento ya cargada.

### Criterio de aceptación
- Banner/badge visible en home del residente con días restantes.
- Edge Function deployada en Supabase y configurada con cron de 7 días.

---

## Módulo 7 — LIMPIEZA: Ubicación + Notas + Estados

### Problema actual
- `TareaLimpieza` no tiene `fkUnidad`, `notas`, ni estado de 3 valores.
- `estaCompletada: Boolean` es binario, no tiene "en proceso".
- No hay forma para el residente de solicitar limpieza con notas.

### Cambios DB (Supabase)
```sql
ALTER TABLE tarealimpieza
    ADD COLUMN IF NOT EXISTS fk_unidad INTEGER REFERENCES unidad(id),
    ADD COLUMN IF NOT EXISTS notas TEXT,
    ADD COLUMN IF NOT EXISTS estatus TEXT DEFAULT 'pendiente';
-- Migrar: UPDATE tarealimpieza SET estatus = CASE WHEN estacompletada THEN 'completado' ELSE 'pendiente' END;
```

### Cambios en Kotlin
- `TareaLimpieza`: agregar `fkUnidad: Int?`, `notas: String?`, `estatus: String = "pendiente"`.
- `CleaningRepository.setEstatus(tareaId, estatus)` → reemplaza `toggleTarea`.
- `CleaningRepository.getTareasConUnidad()` → join con tabla `unidad` para obtener bloque/piso/depto.

### Cambios UI
**CleaningTasksScreen**: 
- Cada tarea muestra "Bloque X · Piso Y · Depto Z".
- Campo "Notas del residente" expandible si `notas != null`.
- 3 botones de estado: Pendiente / En proceso / Completado (segmented button o chips).
- Lista ordenada por bloque → piso → departamento.

**ResidentHomeScreen** (nueva sección "Solicitar limpieza"):
- Botón que abre dialog con campo de texto "Notas adicionales".
- Al confirmar → inserta `TareaLimpieza` con `fkUnidad` del residente y `notas`.

### Criterio de aceptación
- Tarea muestra ubicación exacta del residente.
- Personal puede cambiar estado entre 3 valores.
- Residente puede agregar notas al solicitar.

---

## Orden de implementación

1. **DB migrations** (Supabase SQL editor): piso en unidad, fk_unidad/notas/estatus en tarealimpieza, tablas visita y vehiculo.
2. **Models.kt**: actualizar todos los modelos.
3. **AUTH** (módulo más crítico, sin dependencias).
4. **LOCATION** (base para LIMPIEZA y VIGILANCIA).
5. **RESERVAS** (lógica de negocio central).
6. **REALTIME** (mejora transversal, después de que los datos base estén correctos).
7. **LIMPIEZA** (depende de LOCATION).
8. **PAGOS** (Edge Function + banner en app).
9. **VIGILANCIA** (más complejo, requiere nuevas deps + CameraX + ML Kit).

## Dependencias nuevas a agregar
```kotlin
// ZXing QR
implementation("com.journeyapps:zxing-android-embedded:4.3.0")
// ML Kit
implementation("com.google.mlkit:text-recognition:16.0.1")
implementation("com.google.mlkit:text-recognition-latin:16.0.0")
// CameraX
implementation("androidx.camera:camera-camera2:1.3.4")
implementation("androidx.camera:camera-view:1.3.4")
implementation("androidx.camera:camera-lifecycle:1.3.4")
```

## Permisos (AndroidManifest.xml)
```xml
<uses-permission android:name="android.permission.CAMERA" />
```
