# Cuarto Check — Design Spec
**Date:** 2026-05-11  
**Stack:** Android (Kotlin/Compose) + Supabase (Auth, DB, Realtime, Edge Functions) + Firebase FCM  
**Scope:** 5 independent bug fixes and feature improvements identified in QA pass #4

---

## Context

GuardianApp is a residential management Android app with 4 roles (Resident, Security, Admin, Cleaning). Backend is Supabase. Realtime is already implemented in all ViewModels. Firebase FCM is integrated (FcmService.kt). All 5 areas below are independent and can be implemented in parallel.

---

## Area 1 — Auth Confirm Token Exchange

### Problem
`supabase/functions/auth-confirm/index.ts` serves a static HTML page unconditionally. Supabase confirmation emails contain `?token_hash=xxx&type=signup` (or `email`). The function ignores these params, so clicking the email link never actually verifies the account — it just shows the success page regardless.

### Solution
The function will:
1. Read `token_hash` and `type` from the URL query string
2. Call Supabase Admin REST API (`POST /auth/v1/verify`) with the token
3. On success → serve the existing success HTML page
4. On failure (invalid/expired token) → serve a minimal error page with a retry message

### Files Changed
- `supabase/functions/auth-confirm/index.ts` — add token exchange logic before serving HTML

### Manual Step (not automatable via code)
In Supabase Dashboard → Authentication → URL Configuration:
- **Site URL:** `https://spbrzuxvlljowwjawmkv.supabase.co/functions/v1/auth-confirm`
- **Redirect URLs:** add `https://spbrzuxvlljowwjawmkv.supabase.co/functions/v1/auth-confirm`

### Error States
- Missing `token_hash` → show generic "link inválido" error page
- Expired token → show "link expirado, solicita un nuevo correo" error page
- Network error → show "error de servidor, intenta más tarde" error page

---

## Area 2 — Reservations Confirm Button UX Fix

### Problem
`NewReservaDialog` initializes `slot = "08:00 - 10:00"`. The Confirm button is `enabled = fecha.length == 10 && fecha >= today && slot !in slotsTomados`. When `slotsTomados` loads and includes the default slot, the button becomes disabled with no explanation. The user doesn't know they need to pick a different slot.

### Solution
Add a `LaunchedEffect(slotsTomados)` that:
- Finds the first slot NOT in `slotsTomados`
- Auto-selects it (updates `slot` state)
- If all slots are taken, sets a `sinDisponibilidad = true` flag

UI changes:
- When `sinDisponibilidad = true`: show `"Sin disponibilidad para esta fecha"` text in red below the slot list; Confirm button disabled with reason
- No backend changes — `crearReservaConValidacion` already handles race conditions

### Files Changed
- `app/src/main/java/com/example/gab/ui/resident/ResidentAmenitiesScreen.kt` — `NewReservaDialog` composable

---

## Area 3 — Cleaning Tasks Admin Modal Scroll Fix

### Problem
`CleaningTasksAdminDialog` in `AdminScreens.kt` has a `Column` with a `LazyColumn` (max 260dp) for the task list, followed by a collapsible add-form. When `showAddForm = true`, the form fields + "Asignar tarea" button overflow the dialog and are unreachable. Compose does not allow `verticalScroll` + `LazyColumn` in the same scroll direction.

### Solution
- Replace the task list `LazyColumn` with a plain `Column` using `forEach` (task count is always small — daily tasks per worker)
- Wrap the entire dialog `Column` content in `Column(modifier = Modifier.verticalScroll(rememberScrollState()))`
- Add `modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)` on the outer `Card` to cap dialog height on small screens

### Files Changed
- `app/src/main/java/com/example/gab/ui/admin/AdminScreens.kt` — `CleaningTasksAdminDialog` composable

---

## Area 4 — Payment Reminder Cron + Admin Manual Trigger

### Problem
`supabase/functions/recordatorio-pago/index.ts` exists but is not deployed. No cron schedule exists. Admin has no way to send a manual reminder or test the system.

### Solution

#### 4a — Edge Function
The function already has correct logic (finds unpaid cuotas due within 7 days, sends FCM push). No code changes needed — it just needs deployment.

**Deploy command:**
```bash
supabase functions deploy recordatorio-pago --project-ref spbrzuxvlljowwjawmkv
```

**Required env secrets** (set via Supabase Dashboard → Edge Functions → Secrets):
- `SUPABASE_URL` (auto-set)
- `SUPABASE_SERVICE_ROLE_KEY` (auto-set)
- `FCM_SERVER_KEY` — must be added manually from Firebase Console → Project Settings → Cloud Messaging

#### 4b — pg_cron Schedule
SQL to run in Supabase SQL Editor to schedule every 7 days:
```sql
select cron.schedule(
  'recordatorio-pago-semanal',
  '0 9 */7 * *',
  $$
  select net.http_post(
    url := 'https://spbrzuxvlljowwjawmkv.supabase.co/functions/v1/recordatorio-pago',
    headers := '{"Authorization": "Bearer <SERVICE_ROLE_KEY>", "Content-Type": "application/json"}'::jsonb,
    body := '{}'::jsonb
  )
  $$
);
```

#### 4c — Admin UI Manual Trigger
Add "Enviar recordatorios" button in `AdminScreens.kt` (overflow menu in TopBar or in Reportes tab). Calls `AdminViewModel.dispararRecordatorioPago()` which POSTs to the edge function URL using Ktor client (already a dependency: `ktor.client.android`).

**New functions:**
- `AdminRepository.dispararRecordatorioPago(): Result<Unit>` — HTTP POST to edge function
- `AdminViewModel.dispararRecordatorioPago()` — calls repo, shows toast with result count

### Files Changed
- `app/src/main/java/com/example/gab/ui/admin/AdminScreens.kt` — add button
- `app/src/main/java/com/example/gab/ui/admin/viewmodel/AdminViewModel.kt` — add function
- `app/src/main/java/com/example/gab/data/repository/AdminRepository.kt` — add HTTP call
- `supabase/functions/recordatorio-pago/index.ts` — no changes (deploy only)
- Deliver SQL script as `supabase/recordatorio_pago_cron.sql`

---

## Area 5 — Vehicle Plate Auto-Registration

### Problem
Current flow: guard selects resident → sees their registered vehicles → scans plate → sees match/no-match. The improvement: when scanned plate matches a registered vehicle of the selected resident, auto-register the access entry without manual confirmation.

### Solution
In `SecurityViewModel.onPlacaTextoReconocido()`:
- After plate normalization and DB lookup, if `vehiculo != null` AND `residenteParaPlaca != null` (resident was pre-selected):
  - Automatically call `registrarAcceso(guardId, residenteParaPlaca, "ENTRADA")`
  - Set a new state `_autoRegistrado = true`

In `SecurityPlacasScreen`:
- When `autoRegistrado == true`: show a `SnackBar` or prominent success card: `"Acceso registrado automáticamente para [nombre] · [placa]"`
- Show `"Anular"` action in the SnackBar for 5 seconds (calls `cancelarUltimoAcceso()`)
- If no resident was pre-selected, maintain current behavior (show match, guard confirms manually)

**New states in SecurityViewModel:**
- `_autoRegistrado: MutableStateFlow<Boolean>`
- `_ultimoAccesoId: MutableStateFlow<Int?>` — needed for undo

**New function in SecurityRepository:**
- `cancelarAcceso(accesoId: Int): Result<Unit>` — deletes the accesolog row (table has no `cancelado` field)

### Files Changed
- `app/src/main/java/com/example/gab/ui/security/SecurityScreens.kt` — UI feedback
- `app/src/main/java/com/example/gab/ui/security/viewmodel/SecurityViewModel.kt` — auto-register logic
- `app/src/main/java/com/example/gab/data/repository/SecurityRepository.kt` — `cancelarAcceso`

---

## Implementation Order

These areas are fully independent. Recommended order for a single developer:

1. **Area 3** — Smallest, pure UI fix (30 min)
2. **Area 2** — Small UI fix with LaunchedEffect (30 min)
3. **Area 1** — Edge Function token exchange (45 min)
4. **Area 5** — ViewModel + UI for auto-registration (1 hr)
5. **Area 4** — Admin UI + HTTP call + SQL script (1 hr)

**Total estimated:** ~3.5 hours

---

## Out of Scope
- React/Next.js web panel — not in current stack
- Firebase Cloud Functions — replaced by Supabase Edge Functions
- RLS policy review — no regression from these changes; existing policies unchanged
- `.env` file — Android uses `Secrets.kt` and `local.properties`
