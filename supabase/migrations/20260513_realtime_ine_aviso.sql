-- ── Realtime: add missing tables to publication ─────────────────────────────
-- Run this if aviso / accesolog are not already in the realtime publication.
-- Check first: SELECT * FROM pg_publication_tables WHERE pubname = 'supabase_realtime';

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND tablename = 'aviso'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE aviso;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND tablename = 'accesolog'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE accesolog;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND tablename = 'reserva'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE reserva;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_publication_tables
    WHERE pubname = 'supabase_realtime' AND tablename = 'notificacion'
  ) THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE notificacion;
  END IF;
END $$;

-- ── Replica identity FULL (needed for UPDATE/DELETE events) ──────────────────
ALTER TABLE aviso      REPLICA IDENTITY FULL;
ALTER TABLE accesolog  REPLICA IDENTITY FULL;
ALTER TABLE reserva    REPLICA IDENTITY FULL;
ALTER TABLE notificacion REPLICA IDENTITY FULL;

-- ── aviso: add fecha_creacion with default if not present ────────────────────
ALTER TABLE aviso
  ADD COLUMN IF NOT EXISTS fecha_creacion timestamptz DEFAULT now();

-- ── usuario: add ineurl column for INE photo ────────────────────────────────
ALTER TABLE usuario
  ADD COLUMN IF NOT EXISTS ineurl text;

-- ── RLS policies for realtime access ────────────────────────────────────────
-- Allow any authenticated user to receive aviso events
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE tablename = 'aviso' AND policyname = 'realtime_aviso_select'
  ) THEN
    CREATE POLICY realtime_aviso_select ON aviso
      FOR SELECT TO authenticated USING (true);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE tablename = 'accesolog' AND policyname = 'realtime_accesolog_select'
  ) THEN
    CREATE POLICY realtime_accesolog_select ON accesolog
      FOR SELECT TO authenticated USING (true);
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE tablename = 'notificacion' AND policyname = 'realtime_notificacion_select'
  ) THEN
    CREATE POLICY realtime_notificacion_select ON notificacion
      FOR SELECT TO authenticated USING (true);
  END IF;
END $$;
