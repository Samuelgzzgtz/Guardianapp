# Management Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Four independent fixes: (1) enforce 3-vehicle limit per resident, (2) block reservations for residents with overdue payments > 7 days, (3) auto-expire reservation slots 15 min after scheduled start time via a Supabase edge function, (4) ensure user deletion also cleans `pase_visita` rows.

**Architecture:** All Android-side fixes touch existing files with minimal diff. The slot auto-release is a new Supabase Edge Function (`liberar-slots-expirados`) deployed with a 15-minute cron trigger using pg_cron / Supabase Dashboard scheduled jobs. Cascading delete uses ON DELETE CASCADE already in the DB FK; the REST chain in `eliminarUsuarioCompleto` is updated defensively.

**Tech Stack:** Kotlin/Compose, Supabase Postgrest SDK, Supabase Edge Functions (Deno/TypeScript), pg_cron (Supabase built-in).

---

## File Map

| Action | File |
|---|---|
| Modify | `app/.../ui/resident/viewmodel/ResidentViewModel.kt` |
| Modify | `app/.../ui/resident/ResidentAmenitiesScreen.kt` |
| Create | `supabase/functions/liberar-slots-expirados/index.ts` |
| Modify | `app/.../data/repository/AdminRepository.kt` |

---

## Task 1: Vehicle limit enforcement — max 3 per resident

**Files:**
- Modify: `app/src/main/java/com/example/gab/ui/resident/viewmodel/ResidentViewModel.kt`

- [ ] **Step 1: Add limit check in `agregarVehiculo` (around line 199)**

Replace the existing `agregarVehiculo` function:

```kotlin
fun agregarVehiculo(userId: Int, placa: String, descripcion: String, color: String) {
    if (_vehiculos.value.size >= 3) {
        _toastMessage.value = "Límite de 3 vehículos por residente alcanzado"
        return
    }
    viewModelScope.launch {
        repo.agregarVehiculo(userId, placa, descripcion, color)
            .onSuccess {
                _toastMessage.value = "Vehículo registrado"
                repo.getVehiculos(userId).onSuccess { _vehiculos.value = it }
            }
            .onFailure { _toastMessage.value = "Error: ${it.message}" }
    }
}
```

- [ ] **Step 2: Also update the UI button to show limit state**

In `ResidentProfileScreen.kt` in `VehicleManagementCard`, the "Agregar" `TextButton` should show count:

Replace:
```kotlin
TextButton(onClick = { showAddDialog = true }) {
    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
    Text("Agregar")
}
```
With:
```kotlin
TextButton(
    onClick  = { if (vehiculos.size < 3) showAddDialog = true },
    enabled  = vehiculos.size < 3
) {
    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
    Text(if (vehiculos.size < 3) "Agregar (${vehiculos.size}/3)" else "Límite alcanzado (3/3)")
}
```

- [ ] **Step 3: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Manual test**

1. Open app as resident with 0 vehicles → "Agregar (0/3)" is enabled
2. Add 3 vehicles → button shows "Límite alcanzado (3/3)" and is disabled
3. Try adding a 4th via ViewModel directly (shouldn't happen with UI guard, but VM also blocks it) → toast "Límite de 3 vehículos"

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/example/gab/ui/resident/viewmodel/ResidentViewModel.kt
git add app/src/main/java/com/example/gab/ui/resident/ResidentProfileScreen.kt
git commit -m "feat: enforce 3-vehicle limit per resident with visual feedback"
```

---

## Task 2: Morosidad blocking — disable reservations when overdue > 7 days

**Files:**
- Modify: `app/src/main/java/com/example/gab/ui/resident/ResidentAmenitiesScreen.kt`

- [ ] **Step 1: Add cuota collection + morosidad computation in `ResidentAmenitiesScreen`**

After the existing state collections at the top of `ResidentAmenitiesScreen` (around line 31), add:

```kotlin
val cuota by vm.cuota.collectAsStateWithLifecycle()
```

Then define a `esMoroso` derived variable:

```kotlin
val esMoroso = remember(cuota) {
    val c = cuota ?: return@remember false
    if (c.estatus == "pagado") return@remember false
    val fechaStr = c.fechaVencimiento ?: return@remember false
    val vencimiento = runCatching { java.time.LocalDate.parse(fechaStr) }.getOrNull()
        ?: return@remember false
    java.time.LocalDate.now().isAfter(vencimiento.plusDays(7))
}
```

- [ ] **Step 2: Add morosidad warning banner as a LazyColumn item after the header item**

After the `isLoading` item block (around line 46), add a new item:

```kotlin
if (esMoroso) {
    item {
        Card(
            colors = CardDefaults.cardColors(containerColor = StatusDanger.copy(alpha = 0.10f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Warning, null, tint = StatusDanger, modifier = Modifier.size(24.dp))
                Column(Modifier.weight(1f)) {
                    Text("Cuota vencida", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = StatusDanger)
                    Text(
                        "Tu pago lleva más de 7 días vencido. Realiza el pago para desbloquear las reservaciones.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusDanger
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Disable "Reservar" button when moroso**

In the `items(amenidades)` block, change the `Button`:

```kotlin
Button(
    onClick  = { if (!esMoroso) selectedAmenidad = amenidad },
    enabled  = !esMoroso,
    colors   = ButtonDefaults.buttonColors(
        containerColor = if (esMoroso) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                         else ResidentBlue
    )
) { Text(if (esMoroso) "Bloqueado" else "Reservar") }
```

- [ ] **Step 4: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Manual test**

1. As resident with paid cuota → buttons show "Reservar" and are enabled
2. Change a test resident's cuota `fechavencimiento` in Supabase to 10 days ago with `estatus = 'pendiente'` → reload app → warning banner appears, buttons show "Bloqueado"

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/gab/ui/resident/ResidentAmenitiesScreen.kt
git commit -m "feat: block amenity reservations for residents with overdue payment > 7 days"
```

---

## Task 3: Slot auto-release — expire reservation slots 15 min after start

**Files:**
- Create: `supabase/functions/liberar-slots-expirados/index.ts`

- [ ] **Step 1: Create the SQL function in Supabase**

Run this via Supabase MCP `execute_sql` or Dashboard SQL Editor:

```sql
-- Function to expire active reservations for today where the slot start time
-- has passed by more than 15 minutes.
CREATE OR REPLACE FUNCTION liberar_slots_expirados()
RETURNS INT AS $$
DECLARE
  filas_afectadas INT;
BEGIN
  UPDATE reserva
  SET estatus = 'expirada'
  WHERE estatus = 'activa'
    AND fechareservacion = CURRENT_DATE
    AND (
      -- horarioslot is like "08:00 - 10:00"; extract start part before " - "
      CURRENT_TIME > (
        TRIM(split_part(horarioslot, '-', 1))::TIME + INTERVAL '15 minutes'
      )
    );
  GET DIAGNOSTICS filas_afectadas = ROW_COUNT;
  RETURN filas_afectadas;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
```

Expected: "CREATE FUNCTION"

- [ ] **Step 2: Test the SQL function manually**

```sql
SELECT liberar_slots_expirados();
```

Expected: Returns an integer (0 if no slots expired right now, which is expected during testing).

- [ ] **Step 3: Create the edge function file**

```typescript
// supabase/functions/liberar-slots-expirados/index.ts
import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL         = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

serve(async (_req) => {
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_KEY);

  const { data, error } = await supabase.rpc("liberar_slots_expirados");

  if (error) {
    console.error("Error liberando slots:", error.message);
    return new Response(
      JSON.stringify({ error: error.message }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }

  console.log(`Slots liberados: ${data}`);
  return new Response(
    JSON.stringify({ liberados: data, timestamp: new Date().toISOString() }),
    { status: 200, headers: { "Content-Type": "application/json" } }
  );
});
```

- [ ] **Step 4: Deploy edge function**

```bash
supabase functions deploy liberar-slots-expirados
```

Or use MCP `deploy_edge_function` with name `liberar-slots-expirados` and the file content above.

- [ ] **Step 5: Set up cron schedule in Supabase**

Option A — Supabase Dashboard:
1. Go to Dashboard → Edge Functions → `liberar-slots-expirados` → Schedule
2. Set cron: `*/15 * * * *` (every 15 minutes)

Option B — pg_cron SQL (if pg_cron extension is enabled):
```sql
SELECT cron.schedule(
  'liberar-slots-expirados',
  '*/15 * * * *',
  $$
    SELECT net.http_post(
      url := current_setting('app.supabase_url') || '/functions/v1/liberar-slots-expirados',
      headers := jsonb_build_object(
        'Authorization', 'Bearer ' || current_setting('app.service_role_key'),
        'Content-Type', 'application/json'
      ),
      body := '{}'::jsonb
    );
  $$
);
```

- [ ] **Step 6: Commit**

```bash
git add supabase/functions/liberar-slots-expirados/index.ts
git commit -m "feat: edge function + SQL to auto-expire reservation slots 15 min after start"
```

---

## Task 4: Cascading delete — add `pase_visita` to user deletion

**Files:**
- Modify: `app/src/main/java/com/example/gab/data/repository/AdminRepository.kt`

**Context:** `pase_visita` has `ON DELETE CASCADE` on `fk_residente`, so Postgres already handles this when the `usuario` row is deleted. However, the existing `eliminarUsuarioCompleto` function deletes children via REST before deleting `usuario`. We add `pase_visita` to that chain for explicitness and to avoid any race conditions.

- [ ] **Step 1: Add `pase_visita` deletion to `eliminarUsuarioCompleto` in AdminRepository.kt**

In `eliminarUsuarioCompleto`, after `restDelete("accesolog?fkguardia=eq.$userId")` and before deleting `usuario`, add:

```kotlin
restDelete("pase_visita?fk_residente=eq.$userId")
```

The full delete block after this change:
```kotlin
// 2. Eliminar registros hijo (evitar FK violations)
restDelete("vehiculo?fk_usuario=eq.$userId")
restDelete("notificacion?fkusuario=eq.$userId")
restDelete("cuota?fkusuario=eq.$userId")
restDelete("reporte?fkusuario=eq.$userId")
restDelete("reserva?fkusuario=eq.$userId")
restDelete("tarealimpieza?fkasignado=eq.$userId")
restDelete("accesolog?fkresidente=eq.$userId")
restDelete("accesolog?fkguardia=eq.$userId")
restDelete("pase_visita?fk_residente=eq.$userId")  // ← add this line

// 3. Eliminar de tabla usuario
restDelete("usuario?id=eq.$userId")
```

- [ ] **Step 2: Build**

```bash
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Manual test**

1. As admin, create a test resident with a few vehicles and passes
2. Delete the resident → verify in Supabase that `vehiculo`, `pase_visita`, and `usuario` rows are all gone
3. Try re-registering with the same email → should succeed (auth.users cleared)

- [ ] **Step 4: Commit + push**

```bash
git add app/src/main/java/com/example/gab/data/repository/AdminRepository.kt
git commit -m "fix: add pase_visita to user cascade delete chain"
git push origin master
```

---

## Verification Checklist

- [ ] Resident with 3 vehicles sees "Límite alcanzado (3/3)" and cannot add more
- [ ] Resident with cuota vencida > 7 days sees red banner and "Bloqueado" buttons in amenidades
- [ ] Resident with cuota al día sees normal "Reservar" buttons
- [ ] `liberar-slots-expirados` edge function deploys without error
- [ ] Calling the function via Dashboard returns `{ "liberados": N }` 
- [ ] Deleting a user in admin panel removes their `pase_visita` rows (verify in Supabase Table Editor)
- [ ] Deleted user can re-register with the same email
