# Pendientes Manuales — GuardianApp

## Archivos generados en escritorio

| Archivo | Ruta | Tamaño |
|---|---|---|
| APK | `C:\Users\PC\Desktop\GuardianAppBuildingReinforce.apk` | 22.2 MB |
| ZIP proyecto | `C:\Users\PC\Desktop\GuardianAppBuilding_COMPLETO.zip` | 44.7 MB |

---

## SQLs a ejecutar en Supabase (en orden)

1. **guardianapp_fresh.sql** — esquema base (solo si es BD nueva)
2. **guardianapp_rls_fix.sql** — corrige políticas RLS
3. **guardianapp_features.sql** — unidades, vista morosos, historial cuotas
4. **SQL manual** — pega esto en Supabase SQL Editor:

```sql
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS estaactivo BOOLEAN NOT NULL DEFAULT true;

DROP POLICY IF EXISTS usuario_admin_insert ON usuario;
CREATE POLICY usuario_admin_insert ON usuario
  FOR INSERT WITH CHECK (auth_user_role() = 3);

DROP POLICY IF EXISTS usuario_admin_update ON usuario;
CREATE POLICY usuario_admin_update ON usuario
  FOR UPDATE USING (auth_user_role() = 3) WITH CHECK (auth_user_role() = 3);
```

---

## Service Role Key

Ya configurada en `SupabaseClient.kt`. No requiere cambio.

---

## Instalar APK en otro celular

**Por ADB:**
```
adb install C:\Users\PC\Desktop\GuardianAppBuildingReinforce.apk
```
**Manual:** Pasa el APK por WhatsApp/Drive, ábrelo desde el explorador.
Requiere: Ajustes → Seguridad → Instalar apps de fuentes desconocidas.

---

## Notas

- Firebase FCM no configurado (sin notificaciones push)
- El recargo (5%/30 días) se calcula en el cliente, no en BD
- Para crear usuarios desde Admin: ir a Usuarios → botón "+"
- `applicationId = "com.guardianbuilding"` — si ya tenías instalada la versión anterior (`com.example.gab`), ambas pueden coexistir en el celular
