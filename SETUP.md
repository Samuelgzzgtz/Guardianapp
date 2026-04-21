# GuardianApp – Configuración Manual

Todo lo que tienes que hacer tú antes de correr la app.

---

## PASO 1 – Crear proyecto en Supabase

1. Ve a [https://supabase.com](https://supabase.com) e inicia sesión.
2. Clic en **New project**, ponle nombre (ej. `guardianapp`), elige región y crea una contraseña segura.
3. Espera ~2 minutos a que se aprovisione.

---

## PASO 2 – Ejecutar el script SQL

1. En tu proyecto de Supabase ve a **SQL Editor → New query**.
2. Abre el archivo `guardianapp_fresh.sql` que está en la raíz de este proyecto.
3. Copia todo el contenido, pégalo en el editor y clic en **Run**.
4. Debe decir "Success". Si hay error, repórtalo.

Esto crea todas las tablas, datos de prueba y políticas de seguridad (RLS).

---

## PASO 3 – Crear los usuarios de login en Supabase Auth

La app usa Supabase Auth para autenticar. Hay que crear los usuarios en el panel:

1. Ve a **Authentication → Users → Add user → Create new user**.
2. Crea estos 4 usuarios (uno por uno):

| Email                          | Contraseña      |
|--------------------------------|-----------------|
| residente@guardianapp.test     | Guardian123!    |
| seguridad@guardianapp.test     | Guardian123!    |
| admin@guardianapp.test         | Guardian123!    |
| limpieza@guardianapp.test      | Guardian123!    |

> Los emails deben coincidir exactamente con los del script SQL. La app hace match por email al hacer login.

---

## PASO 4 – Poner las credenciales en el código

1. En Supabase ve a **Project Settings → API**.
2. Copia:
   - **Project URL** (ej. `https://xxxxxxxxxxxx.supabase.co`)
   - **anon public key** (empieza con `eyJ...`)

3. Abre el archivo:
   ```
   app/src/main/java/com/example/gab/data/remote/SupabaseClient.kt
   ```
4. Reemplaza los dos placeholders:
   ```kotlin
   const val SUPABASE_URL = "https://TU_PROYECTO.supabase.co"
   const val SUPABASE_KEY = "eyJ..."
   ```

---

## PASO 5 – Crear el bucket de Storage (fotos de reportes)

1. En Supabase ve a **Storage → New bucket**.
2. Nombre: `fotos-reportes`
3. Activa **Public bucket**.
4. Clic en **Create bucket**.

> Si no necesitas subir fotos en reportes por ahora, puedes omitir este paso.

---

## PASO 6 – Compilar y probar

1. Abre el proyecto en Android Studio.
2. **File → Sync Project with Gradle Files**.
3. Conecta un dispositivo físico o emulador (API 26+).
4. Clic en **Run**.
5. Prueba el login con cada rol:
   - `residente@guardianapp.test` / `Guardian123!` → pantalla de Residente
   - `admin@guardianapp.test` / `Guardian123!` → pantalla de Administrador
   - `seguridad@guardianapp.test` / `Guardian123!` → pantalla de Seguridad
   - `limpieza@guardianapp.test` / `Guardian123!` → pantalla de Limpieza

---

## PASO 7 – Firebase (opcional, solo si necesitas push notifications)

1. Ve a [https://console.firebase.google.com](https://console.firebase.google.com) y crea un proyecto.
2. Añade app Android con package name: `com.example.gab`.
3. Descarga `google-services.json` y colócalo en la carpeta `app/`.
4. En el `build.gradle.kts` raíz agrega:
   ```kotlin
   id("com.google.gms.google-services") version "4.4.0" apply false
   ```
5. En `app/build.gradle.kts` agrega:
   ```kotlin
   id("com.google.gms.google-services")
   ```

> Sin esto la app funciona normalmente, solo no recibirás notificaciones push.

---

## Resumen rápido

| Paso | Qué hace | Obligatorio |
|------|----------|-------------|
| 1 | Crear proyecto Supabase | Sí |
| 2 | Crear tablas y datos (`guardianapp_fresh.sql`) | Sí |
| 3 | Crear usuarios de login en Auth | Sí |
| 4 | Poner URL y KEY en el código | Sí |
| 5 | Bucket para fotos | Opcional |
| 6 | Compilar y probar | Sí |
| 7 | Firebase push notifications | Opcional |
