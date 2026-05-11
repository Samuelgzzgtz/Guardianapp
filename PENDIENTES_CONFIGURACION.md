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

## PASO 2 — Obtener FCM Server Key de Firebase

**Dónde:** Firebase Console → Configuración del proyecto → Cloud Messaging

**Por qué:** Sin esta clave, la Edge Function `recordatorio-pago` no puede enviar notificaciones push.

### Instrucciones:

1. Abre [https://console.firebase.google.com](https://console.firebase.google.com)
2. Selecciona el proyecto de **GuardianApp**
3. Haz clic en el ícono de engrane ⚙️ → **Configuración del proyecto**
4. Ve a la pestaña **Cloud Messaging**
5. En la sección **API de Cloud Messaging de Firebase para Android**, copia el valor de:
   - **Clave de servidor** (empieza con `AAAA...`)
6. Guarda ese valor, lo necesitas en el Paso 3

> **Nota:** Si ves "API de Cloud Messaging (heredada) deshabilitada", actívala haciendo clic en los 3 puntos ⋮ → Administrar API en Google Cloud Console → Habilitar.

---

## PASO 3 — Agregar FCM_SERVER_KEY como secreto en Supabase

**Dónde:** Supabase Dashboard → Edge Functions → Secrets

**Por qué:** La Edge Function `recordatorio-pago` lee `FCM_SERVER_KEY` del entorno. Sin este secreto, las notificaciones push fallan silenciosamente.

### Instrucciones:

1. Abre el Dashboard de Supabase → proyecto GuardianApp
2. En el menú izquierdo: **Edge Functions**
3. Haz clic en **Manage secrets** (o **Secrets** en la barra superior)
4. Agrega el siguiente secreto:

| Nombre | Valor |
|--------|-------|
| `FCM_SERVER_KEY` | *(la clave que copiaste en el Paso 2)* |

5. Haz clic en **Save**

---

## PASO 4 — Desplegar las Edge Functions

**Dónde:** Terminal con Supabase CLI instalado

**Por qué:** Las funciones `auth-confirm` y `recordatorio-pago` tienen cambios que deben subirse a Supabase.

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

# Desplegar recordatorio-pago (recordatorios de pago):
supabase functions deploy recordatorio-pago --project-ref spbrzuxvlljowwjawmkv

# Desplegar send-push (notificaciones individuales):
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
- Que el `FCM_SERVER_KEY` esté guardado correctamente (Paso 3)
- Que la función esté desplegada (Paso 4)
- Que los usuarios tengan FCM token registrado (la app lo registra automáticamente al iniciar sesión)

---

## Resumen de pasos

| # | Paso | Plataforma | Tiempo estimado |
|---|------|-----------|-----------------|
| 1 | Cambiar Site URL en Supabase Auth | Supabase Dashboard | 2 min |
| 2 | Obtener FCM Server Key | Firebase Console | 3 min |
| 3 | Agregar FCM_SERVER_KEY como secreto | Supabase Dashboard | 2 min |
| 4 | Desplegar las 3 Edge Functions | Terminal (Supabase CLI) | 5 min |
| 5 | Habilitar extensiones pg_cron y pg_net | Supabase Dashboard | 2 min |
| 6 | Ejecutar SQL del cron job | Supabase SQL Editor | 3 min |
| 7 | Probar desde la app | App Android | 2 min |
| | **Total** | | **~19 min** |

---

## Notas adicionales

- **El cron usa hora UTC.** Si quieres que los recordatorios lleguen a las 9:00 AM hora de México (UTC-6), usa `'0 15 */7 * *'` en el SQL del cron.
- **FCM tokens:** Los usuarios deben haber abierto la app al menos una vez con la versión más reciente para tener su FCM token registrado. Sin token, el recordatorio se saltea ese usuario silenciosamente.
- **La Service Role Key nunca va en el código.** Solo en los Secrets de Supabase y localmente en `Secrets.kt` (ya está en `.gitignore`).
