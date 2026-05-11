# GuardianApp — Pasos de Configuración Manual

> Estos pasos no se pueden automatizar desde el código. Requieren acceso al Dashboard de Supabase y la consola de Firebase.

---

## PASO 1 — Configurar URL de confirmación de email en Supabase

**Dónde:** Supabase Dashboard → Authentication → URL Configuration

**Por qué:** Sin este cambio, los correos de confirmación siguen apuntando a `localhost:3000` y el usuario ve un error en el navegador.

### Instrucciones:

1. Abre [https://supabase.com/dashboard](https://supabase.com/dashboard)
2. Selecciona el proyecto **GuardianApp** (`spbrzuxvlljowwjawmkv`)
3. En el menú izquierdo: **Authentication** → **URL Configuration**
4. Cambia los siguientes campos:

| Campo | Valor a poner |
|-------|---------------|
| **Site URL** | `https://spbrzuxvlljowwjawmkv.supabase.co/functions/v1/auth-confirm` |
| **Redirect URLs** (agregar) | `https://spbrzuxvlljowwjawmkv.supabase.co/functions/v1/auth-confirm` |

5. Haz clic en **Save**

### Verificación:
- Crea un usuario de prueba desde el panel Admin de la app
- Abre el correo que llega
- Haz clic en el enlace de confirmación
- Debe abrir una página web con el logo de GuardianApp y el mensaje **"¡Cuenta verificada exitosamente!"**
- Si el enlace ya expiró, debe mostrar la página de error (fondo rojo con "Link de verificación inválido")

---

## PASO 2 — Obtener Service Account de Firebase (FCM v1)

**Dónde:** Firebase Console → Configuración del proyecto → Cuentas de servicio

**Por qué:** Las Edge Functions `recordatorio-pago` y `send-push` usan la API FCM v1 (la heredada/Legacy está deshabilitada en el proyecto `guardianapp-a0b54`). FCM v1 requiere autenticarse con una cuenta de servicio en lugar de una Server Key.

### Instrucciones:

1. Abre [https://console.firebase.google.com](https://console.firebase.google.com)
2. Selecciona el proyecto de **GuardianApp** (`guardianapp-a0b54`)
3. Haz clic en el ícono de engrane ⚙️ → **Configuración del proyecto**
4. Ve a la pestaña **Cuentas de servicio** (Service accounts)
5. Haz clic en **Generar nueva clave privada** → **Generar clave**
6. Se descargará un archivo JSON. Ábrelo — tiene esta estructura:

```json
{
  "type": "service_account",
  "project_id": "guardianapp-a0b54",
  "private_key_id": "...",
  "private_key": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n",
  "client_email": "firebase-adminsdk-xxxxx@guardianapp-a0b54.iam.gserviceaccount.com",
  ...
}
```

7. Copia los valores de `project_id`, `client_email`, y `private_key` — los necesitas en el Paso 3.

> **Seguridad:** Guarda este archivo JSON en lugar seguro. Nunca lo subas al repositorio ni lo compartas.

---

## PASO 3 — Agregar secrets de Firebase en Supabase

**Dónde:** Supabase Dashboard → Edge Functions → Secrets

**Por qué:** Las Edge Functions `recordatorio-pago` y `send-push` leen estas 3 variables del entorno para autenticarse con FCM v1.

### Instrucciones:

1. Abre el Dashboard de Supabase → proyecto GuardianApp
2. En el menú izquierdo: **Edge Functions**
3. Haz clic en **Manage secrets**
4. Agrega los siguientes 3 secretos (con los valores del JSON del Paso 2):

| Nombre | Valor |
|--------|-------|
| `FIREBASE_PROJECT_ID` | `guardianapp-a0b54` |
| `FIREBASE_CLIENT_EMAIL` | *(el valor de `client_email` del JSON)* |
| `FIREBASE_PRIVATE_KEY` | *(el valor completo de `private_key`, incluyendo los `-----BEGIN/END PRIVATE KEY-----`)* |

5. Haz clic en **Save** después de cada uno.

> **Nota sobre `FIREBASE_PRIVATE_KEY`:** El valor incluye saltos de línea como `\n`. Pégalo exactamente como aparece en el JSON (con los `\n` literales o con saltos de línea reales — Supabase lo maneja correctamente).

---

## PASO 4 — Desplegar las Edge Functions

**Dónde:** Terminal con Supabase CLI instalado

**Por qué:** Las funciones `auth-confirm`, `recordatorio-pago` y `send-push` tienen cambios que deben subirse a Supabase.

### Requisito previo — Instalar Supabase CLI (si no lo tienes):
```powershell
# En PowerShell como administrador:
winget install Supabase.CLI

# Verificar instalación:
supabase --version
```

### Login y despliegue:
```powershell
# En PowerShell, desde la carpeta del proyecto:
cd "C:\Users\PC\AndroidStudioProjects\GAB"

# Login (abre el navegador para autenticar):
supabase login

# Desplegar auth-confirm (página de confirmación de email):
supabase functions deploy auth-confirm --project-ref spbrzuxvlljowwjawmkv

# Desplegar recordatorio-pago (recordatorios de pago programados):
supabase functions deploy recordatorio-pago --project-ref spbrzuxvlljowwjawmkv

# Desplegar send-push (notificaciones individuales por trigger de DB):
supabase functions deploy send-push --project-ref spbrzuxvlljowwjawmkv
```

### Verificación:
Después del deploy, en el Dashboard → Edge Functions debes ver las 3 funciones con estado **Active**.

---

## PASO 5 — Habilitar extensiones en Supabase (pg_cron y pg_net)

**Dónde:** Supabase Dashboard → Database → Extensions

**Por qué:** El cron job de pagos usa `pg_cron` para programar la tarea y `pg_net` para hacer la llamada HTTP. Sin estas extensiones el SQL del cron falla.

### Instrucciones:

1. En el Dashboard: **Database** → **Extensions**
2. Busca y activa **pg_cron** (toggle ON)
3. Busca y activa **pg_net** (toggle ON)
4. Espera 30 segundos para que se apliquen

---

## PASO 6 — Configurar el Cron Job de recordatorios de pago

**Dónde:** Supabase Dashboard → SQL Editor

**Por qué:** Este SQL programa la ejecución automática de `recordatorio-pago` cada 7 días a las 9:00 AM UTC.

### Instrucción previa — Obtener tu Service Role Key:
1. Dashboard → **Settings** → **API**
2. Copia el valor de **service_role** (la clave secreta, no la anon key)

### SQL a ejecutar (pega esto en SQL Editor → New query):

```sql
-- Programar recordatorio de pagos cada 7 días a las 09:00 UTC
SELECT cron.schedule(
  'recordatorio-pago-semanal',
  '0 9 */7 * *',
  $$
  SELECT net.http_post(
    url     := 'https://spbrzuxvlljowwjawmkv.supabase.co/functions/v1/recordatorio-pago',
    headers := jsonb_build_object(
      'Authorization', 'Bearer TU_SERVICE_ROLE_KEY_AQUI',
      'Content-Type',  'application/json'
    ),
    body    := '{}'::jsonb
  )
  $$
);
```

> **Importante:** Reemplaza `TU_SERVICE_ROLE_KEY_AQUI` con la Service Role Key que copiaste.

### Verificar que el cron quedó registrado:

```sql
SELECT jobid, jobname, schedule, active
FROM cron.job
WHERE jobname = 'recordatorio-pago-semanal';
```

Debes ver una fila con `active = true`.

---

## PASO 7 — Probar el recordatorio de pago manualmente

Puedes disparar un recordatorio en cualquier momento desde la app:

1. Entra a la app como **Administrador**
2. Toca el ícono ⋮ (tres puntos) en la barra superior
3. Selecciona **"Enviar recordatorios de pago"**
4. Aparecerá un Toast: `"Recordatorios enviados: X usuarios"`

Si el Toast dice `"Error al enviar recordatorios"`, revisa:
- Que los 3 secrets de Firebase estén guardados correctamente (Paso 3)
- Que la función esté desplegada (Paso 4)
- Que los usuarios tengan FCM token registrado (la app lo registra automáticamente al iniciar sesión)

---

## Resumen de pasos

| # | Paso | Plataforma | Tiempo estimado |
|---|------|-----------|-----------------|
| 1 | Cambiar Site URL en Supabase Auth | Supabase Dashboard | 2 min |
| 2 | Generar Service Account JSON en Firebase | Firebase Console | 3 min |
| 3 | Agregar 3 secrets de Firebase en Supabase | Supabase Dashboard | 3 min |
| 4 | Desplegar las 3 Edge Functions | Terminal (Supabase CLI) | 5 min |
| 5 | Habilitar extensiones pg_cron y pg_net | Supabase Dashboard | 2 min |
| 6 | Ejecutar SQL del cron job | Supabase SQL Editor | 3 min |
| 7 | Probar desde la app | App Android | 2 min |
| | **Total** | | **~20 min** |

---

## Notas adicionales

- **FCM v1 vs Legacy:** El proyecto Firebase `guardianapp-a0b54` tiene la API Legacy deshabilitada. Las Edge Functions usan FCM v1 con OAuth2 (Service Account JWT), que es la forma moderna y recomendada por Google.
- **El cron usa hora UTC.** Si quieres que los recordatorios lleguen a las 9:00 AM hora de México (UTC-6), usa `'0 15 */7 * *'` en el SQL del cron.
- **FCM tokens:** Los usuarios deben haber abierto la app al menos una vez con la versión más reciente para tener su FCM token registrado. Sin token, el recordatorio se saltea ese usuario silenciosamente.
- **La Service Role Key nunca va en el código.** Solo en los Secrets de Supabase y localmente en `Secrets.kt` (ya está en `.gitignore`).
- **El archivo JSON de la Service Account tampoco va en el repositorio.** Guárdalo en local o en un gestor de secretos.
