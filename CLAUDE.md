# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

Build and install debug APK via Gradle wrapper (from repo root):
```bash
./gradlew assembleDebug
./gradlew installDebug          # requires connected device/emulator (API 26+)
```

Run unit tests:
```bash
./gradlew test
./gradlew testDebugUnitTest     # single variant
```

Run instrumented tests (requires device):
```bash
./gradlew connectedDebugAndroidTest
```

There is no lint or format command configured — use Android Studio's built-in tools.

## Architecture

Single-module Android app (`app/`) with a clean layered structure:

```
data/
  model/Models.kt          — all @Serializable data classes (Supabase rows)
  remote/SupabaseClient.kt — singleton Supabase client (Auth, Postgrest, Realtime, Storage)
  remote/Secrets.kt        — ADMIN_SERVICE_KEY (gitignored, never commit)
  local/SessionDataStore.kt— DataStore persistence for userId, role, authToken
  repository/              — one repo per domain (Auth, Admin, Resident, Security, Cleaning, Notificacion)
ui/
  auth/                    — LoginScreen + AuthViewModel
  admin/                   — AdminScreens.kt + AdminViewModel
  resident/                — screens per tab + ResidentViewModel
  security/                — SecurityScreens.kt + CameraScreen + SecurityViewModel
  cleaning/                — CleaningScreens.kt + CleaningViewModel
  notifications/           — NotificacionViewModel
  common/                  — AppShell.kt (shared Scaffold), Components.kt
  navigation/AppNavGraph.kt— root routing and role dispatch
  theme/                   — Color, Type, Theme
util/
  NetworkMonitor.kt
  Extensions.kt
```

### Navigation model

`GuardianApp()` (in `AppNavGraph.kt`) is the root composable. After auth it renders one of four role-specific Shells:

- `ResidentShell` / `SecurityShell` / `AdminShell` / `CleaningShell`

Each Shell owns its own `NavController` + flat `NavHost`. There is **no nested navigation**. All route constants live in `Routes` object in `AppNavGraph.kt`.

`AppShell.kt` is the shared `Scaffold` wrapper (TopBar + NetworkBanner + BottomNavigationBar). Navigation uses `launchSingleTop = true` with `inclusive = (item == startRoute)` — do not add a `currentRoute != item.route` guard.

### Data layer

- All Supabase queries go through Repository classes, never directly from ViewModels.
- Models are in one file: `data/model/Models.kt`. All fields use `@SerialName` matching exact Supabase column names.
- **Naming quirk:** `Vehiculo` uses `fk_usuario` (underscore), while all other models use `fkusuario` (no underscore).
- `TareaLimpieza.fkAsignado` is nullable — null means the task was requested by a resident, not assigned by admin.
- Realtime subscriptions (admin panel) use channel `"admin-live"` and subscribe to `reporte` + `tarealimpieza`.

### Auth flow

1. `AuthViewModel.init` → `checkSession()` reads DataStore; emits `SessionRestored` if token exists.
2. Login → `AuthRepository.signIn` → Supabase Auth → matches `usuario` table by email → emits `AuthState.Success`.
3. Admin user creation: calls Supabase admin REST API (`/auth/v1/admin/users`) then POST `/auth/v1/resend` to auto-send confirmation email. Requires `ADMIN_SERVICE_KEY` from `Secrets.kt`.
4. Full user deletion: deletes child rows (vehiculo, notificacion, cuota, reporte, reserva, tarealimpieza, accesolog) + `usuario` row + `auth.users` via admin API.

### Role IDs (fkrolusuario)

| ID | Role |
|----|------|
| 1  | Residente |
| 2  | Seguridad |
| 3  | Administrador |
| 4  | Limpieza |

### Unidad structure

3 bloques (A, B, C) × 4 pisos × 5 deptos/piso = 60 unidades. `Unidad.displayUbicacion()` returns `"Bloque X · Piso N · Depto NN"`.

## Known Pending Items

- `TC-ADMIN-07`: "Marcar como pagado" button in resident detail panel not implemented.
- Deprecation warnings: `LocalClipboardManager` → use `LocalClipboard`; `Icons.Filled.Notes` → use `AutoMirrored`.
- `Cuota` table has no `notas` column (unlike `tarealimpieza` which does).
- Firebase FCM not configured — push notifications are a no-op.
- Late payment surcharge (5%/30 days) is calculated client-side, not in the DB.

## Supabase Setup

Credentials are in `SupabaseClient.kt` (anon key) and `Secrets.kt` (service role key, gitignored). See `SETUP.md` for full environment setup instructions including SQL scripts to run and storage bucket configuration.
