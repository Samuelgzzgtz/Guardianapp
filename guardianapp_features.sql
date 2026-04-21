-- guardianapp_features.sql
-- Run AFTER guardianapp_fresh.sql and guardianapp_rls_fix.sql

-- ── 1. Unidad: soft-delete flag ─────────────────────────────────────────────
ALTER TABLE unidad ADD COLUMN IF NOT EXISTS estaactivo BOOLEAN NOT NULL DEFAULT true;

-- ── 2. Vista morosos (cuotas vencidas más de 30 días sin pagar) ──────────────
CREATE OR REPLACE VIEW morosos AS
SELECT
  u.id        AS userid,
  u.nombre,
  u.email,
  c.id        AS cuotaid,
  c.periodo,
  COALESCE(c.monto, 0) AS monto,
  c.fechavencimiento
FROM usuario u
JOIN cuota c ON c.fkusuario = u.id
WHERE c.estatus <> 'pagado'
  AND c.fechavencimiento IS NOT NULL
  AND (CURRENT_DATE - c.fechavencimiento::DATE) > 30;

GRANT SELECT ON morosos TO authenticated;

-- ── 3. RLS para tabla unidad ─────────────────────────────────────────────────
ALTER TABLE unidad ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS unidad_select    ON unidad;
DROP POLICY IF EXISTS unidad_insert    ON unidad;
DROP POLICY IF EXISTS unidad_update    ON unidad;
DROP POLICY IF EXISTS unidad_delete    ON unidad;

CREATE POLICY unidad_select ON unidad FOR SELECT USING (true);
CREATE POLICY unidad_insert ON unidad FOR INSERT WITH CHECK (auth_user_role() = 3);
CREATE POLICY unidad_update ON unidad FOR UPDATE USING (auth_user_role() = 3) WITH CHECK (auth_user_role() = 3);
CREATE POLICY unidad_delete ON unidad FOR DELETE USING (auth_user_role() = 3);

-- ── 4. RLS cuota: admin puede ver todas ──────────────────────────────────────
DROP POLICY IF EXISTS cuota_admin_all ON cuota;
CREATE POLICY cuota_admin_all ON cuota FOR ALL USING (auth_user_role() = 3) WITH CHECK (auth_user_role() = 3);

-- ── 5. Historial de cuotas para residente de prueba ──────────────────────────
-- NOTA: ajusta el valor de fkusuario al id de tu residente de prueba
-- (corre: SELECT id, nombre FROM usuario WHERE fkrolusuario = 1;  para obtenerlo)
INSERT INTO cuota (fkusuario, monto, estatus, fechavencimiento, periodo) VALUES
(1, 1500, 'pagado',   '2025-05-10', '2025-05'),
(1, 1500, 'pagado',   '2025-06-10', '2025-06'),
(1, 1500, 'pagado',   '2025-07-10', '2025-07'),
(1, 1500, 'pagado',   '2025-08-10', '2025-08'),
(1, 1500, 'pagado',   '2025-09-10', '2025-09'),
(1, 1500, 'vencido',  '2025-10-10', '2025-10'),
(1, 1500, 'vencido',  '2025-11-10', '2025-11'),
(1, 1500, 'pagado',   '2025-12-10', '2025-12'),
(1, 1500, 'pagado',   '2026-01-10', '2026-01'),
(1, 1500, 'pagado',   '2026-02-10', '2026-02'),
(1, 1500, 'pendiente','2026-03-10', '2026-03')
ON CONFLICT DO NOTHING;

-- ── 6. Unidades de prueba adicionales ───────────────────────────────────────
INSERT INTO unidad (numero, torre, tipo) VALUES
('101', 'A', 'depto'),
('102', 'A', 'depto'),
('201', 'B', 'depto'),
('202', 'B', 'casa'),
('301', 'C', 'depto')
ON CONFLICT DO NOTHING;
