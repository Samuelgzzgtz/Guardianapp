-- ── Comprehensive Realtime fix: publication + REPLICA IDENTITY + RLS ─────────
-- Adds ALL app tables to the realtime publication with proper SELECT policies.

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'reporte','cuota','usuario','tarealimpieza','vehiculo',
    'amenidad','unidad','pase_visita'
  ] LOOP
    IF NOT EXISTS (
      SELECT 1 FROM pg_publication_tables
      WHERE pubname = 'supabase_realtime' AND tablename = t
    ) THEN
      EXECUTE format('ALTER PUBLICATION supabase_realtime ADD TABLE %I', t);
    END IF;
  END LOOP;
END $$;

-- REPLICA IDENTITY FULL lets UPDATE/DELETE events carry old row data
ALTER TABLE reporte        REPLICA IDENTITY FULL;
ALTER TABLE cuota          REPLICA IDENTITY FULL;
ALTER TABLE usuario        REPLICA IDENTITY FULL;
ALTER TABLE tarealimpieza  REPLICA IDENTITY FULL;
ALTER TABLE vehiculo       REPLICA IDENTITY FULL;
ALTER TABLE amenidad       REPLICA IDENTITY FULL;
ALTER TABLE unidad         REPLICA IDENTITY FULL;
ALTER TABLE pase_visita    REPLICA IDENTITY FULL;

-- ── RLS SELECT policies (allow authenticated to receive realtime events) ──────
DO $$
BEGIN
  -- reporte
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename='reporte' AND policyname='realtime_reporte_select') THEN
    CREATE POLICY realtime_reporte_select ON reporte FOR SELECT TO authenticated USING (true);
  END IF;
  -- cuota
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename='cuota' AND policyname='realtime_cuota_select') THEN
    CREATE POLICY realtime_cuota_select ON cuota FOR SELECT TO authenticated USING (true);
  END IF;
  -- usuario
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename='usuario' AND policyname='realtime_usuario_select') THEN
    CREATE POLICY realtime_usuario_select ON usuario FOR SELECT TO authenticated USING (true);
  END IF;
  -- tarealimpieza
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename='tarealimpieza' AND policyname='realtime_tarealimpieza_select') THEN
    CREATE POLICY realtime_tarealimpieza_select ON tarealimpieza FOR SELECT TO authenticated USING (true);
  END IF;
  -- vehiculo
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename='vehiculo' AND policyname='realtime_vehiculo_select') THEN
    CREATE POLICY realtime_vehiculo_select ON vehiculo FOR SELECT TO authenticated USING (true);
  END IF;
  -- reserva (missing from prev migration)
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename='reserva' AND policyname='realtime_reserva_select') THEN
    CREATE POLICY realtime_reserva_select ON reserva FOR SELECT TO authenticated USING (true);
  END IF;
  -- amenidad
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename='amenidad' AND policyname='realtime_amenidad_select') THEN
    CREATE POLICY realtime_amenidad_select ON amenidad FOR SELECT TO authenticated USING (true);
  END IF;
  -- unidad
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename='unidad' AND policyname='realtime_unidad_select') THEN
    CREATE POLICY realtime_unidad_select ON unidad FOR SELECT TO authenticated USING (true);
  END IF;
  -- pase_visita
  IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE tablename='pase_visita' AND policyname='realtime_pase_visita_select') THEN
    CREATE POLICY realtime_pase_visita_select ON pase_visita FOR SELECT TO authenticated USING (true);
  END IF;
END $$;
