# GuardianApp — Pendientes finales (3 pasos + 1 seguridad)

> Estado actual: Edge Functions deployadas, secrets de Firebase configurados.
> Solo faltan estos pasos manuales en el Dashboard.

---

## 1. Cambiar URL de confirmación de email
**Supabase Dashboard → Authentication → URL Configuration**

| Campo | Valor |
|-------|-------|
| Site URL | `https://spbrzuxvlljowwjawmkv.supabase.co/functions/v1/auth-confirm` |
| Redirect URLs (agregar) | `https://spbrzuxvlljowwjawmkv.supabase.co/functions/v1/auth-confirm` |

Clic en **Save**.

---

## 2. Activar extensiones de base de datos
**Supabase Dashboard → Database → Extensions**

- Busca **pg_cron** → activar (toggle ON)
- Busca **pg_net** → activar (toggle ON)

Espera 30 segundos.

---

## 3. Crear el cron job de recordatorios de pago
**Supabase Dashboard → SQL Editor → New query**

Primero obtén tu **Service Role Key**:
Dashboard → Settings → API → copiar el valor de **service_role**

Luego pega este SQL (reemplazando el texto marcado):

```sql
SELECT cron.schedule(
  'recordatorio-pago-semanal',
  '0 9 */7 * *',
  $$
  SELECT net.http_post(
    url     := 'https://spbrzuxvlljowwjawmkv.supabase.co/functions/v1/recordatorio-pago',
    headers := jsonb_build_object(
      'Authorization', 'Bearer PEGA_AQUI_TU_SERVICE_ROLE_KEY',
      'Content-Type',  'application/json'
    ),
    body    := '{}'::jsonb
  )
  $$
);
```

Verifica que quedó registrado:
```sql
SELECT jobid, jobname, schedule, active FROM cron.job WHERE jobname = 'recordatorio-pago-semanal';
```
Debes ver una fila con `active = true`.

---

## ⚠️ Seguridad — Regenerar clave de Firebase

La private key fue compartida en el chat. Hay que revocarla y crear una nueva.

**Firebase Console → ⚙️ Configuración del proyecto → Cuentas de servicio**

1. En la lista de claves, localiza la que generaste hoy y haz clic en **Revocar**
2. Haz clic en **Generar nueva clave privada**
3. Del nuevo JSON, copia `client_email` y `private_key`
4. Ve a **Supabase Dashboard → Edge Functions → Manage secrets**
5. Actualiza los valores de `FIREBASE_CLIENT_EMAIL` y `FIREBASE_PRIVATE_KEY` con los nuevos

> `FIREBASE_PROJECT_ID` no cambia — es siempre `guardianapp-a0b54`.

---

## Verificación final

Después de completar los 3 pasos:
1. Crea un usuario de prueba desde la app (Admin) → confirma que el email de verificación funciona
2. En la app como Admin → menú ⋮ → **"Enviar recordatorios de pago"** → debe mostrar `"Recordatorios enviados: X usuarios"`
