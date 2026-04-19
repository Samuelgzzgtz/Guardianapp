# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run a single test class
./gradlew test --tests "com.example.gabguadianappbuilding.ExampleUnitTest"

# Clean build
./gradlew clean
```

To install on a connected device/emulator: use Android Studio's Run button or `./gradlew installDebug`.

## Architecture

Single-module Android app (`:app`). No MVVM, no Hilt, no Jetpack Compose — plain Activities with XML layouts. The entry point is `MainActivity.kt` (splash/redirect), which leads to `LoginActivity`.

**Three user roles** — routing is determined at login (`LoginActivity`) by username lookup in `SharedPreferences("UsuariosDB")`:
- `ADMIN` / hardcoded `"administrador"` → `AdminActivity`
- `SEGURIDAD` / hardcoded `"seguridad"` → `SeguridadActivity`
- `USUARIO` / hardcoded `"usuario"` → `UsuarioActivity`

**Persistent storage uses SharedPreferences only — no database:**
| SharedPreferences name | Contents |
|---|---|
| `UsuariosDB` | `username (lowercase) → role ("USUARIO"/"SEGURIDAD"/"ADMIN")` |
| `DetallesUsuarios` | `username (lowercase) → address string ("Casa #5" or "Depto #3")` |
| `CuotasDB` | `username (lowercase) → fee amount (Long)` |
| `HistorialAccesosDB` | `timestamp millis → "name\|time\|ENTRADA/SALIDA"` |
| `ReportesDB` | Reports submitted by users |
| `AmenidadesReservasDB` | Amenity reservations |

**Profile photos** are stored as JPEG files in `getFilesDir()` named `<username_lowercase>_perfil.jpg`.

**Admin section screens** (accessed from `AdminActivity`):
- `MenuUsuariosActivity` → `GestionUsuariosActivity` (create) / `PerfilesExistentesActivity` (edit/delete)
- `MenuAmenidadesActivity` → `CatalogoAmenidadesActivity` / `SeleccionarAmenidadActivity` / `ControlReservasActivity`
- `MenuCuotasActivity` — fee management
- `VerReportesActivity` — view user-submitted reports

**Security guard section** (`SeguridadActivity`): registers building entry/exit events per user with time picker; links to `HistorialAccesosActivity`.

**User section** (`UsuarioActivity`): shows greeting, home address, pending fee amount, profile photo upload, and access to `RedactarReporteActivity`.

## Language Mix

Activities are Java (`.java`); `MainActivity` is Kotlin (`.kt`). New activities should follow the existing Java convention unless Kotlin is preferred.

## Key Dependencies

- `compileSdk 36`, `minSdk 24`, `targetSdk 36`
- AndroidX AppCompat, ConstraintLayout, Material Components, RecyclerView
- No Firebase, no Room, no networking libraries
