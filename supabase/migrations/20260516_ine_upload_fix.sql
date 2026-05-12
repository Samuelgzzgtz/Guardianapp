-- ── fotos-ine storage bucket policies ────────────────────────────────────────
-- Bucket ya creado vía API; asegurar que exista y sea público
INSERT INTO storage.buckets (id, name, public)
  VALUES ('fotos-ine', 'fotos-ine', true)
  ON CONFLICT (id) DO UPDATE SET public = true;

-- Permitir a usuarios autenticados subir fotos de INE
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects'
      AND policyname = 'fotos-ine upload autenticado'
  ) THEN
    CREATE POLICY "fotos-ine upload autenticado" ON storage.objects
      FOR INSERT TO authenticated
      WITH CHECK (bucket_id = 'fotos-ine');
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects'
      AND policyname = 'fotos-ine lectura publica'
  ) THEN
    CREATE POLICY "fotos-ine lectura publica" ON storage.objects
      FOR SELECT TO public
      USING (bucket_id = 'fotos-ine');
  END IF;

  -- También permitir update/upsert del archivo en el bucket
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects'
      AND policyname = 'fotos-ine upsert autenticado'
  ) THEN
    CREATE POLICY "fotos-ine upsert autenticado" ON storage.objects
      FOR UPDATE TO authenticated
      USING (bucket_id = 'fotos-ine');
  END IF;
END $$;

-- ── Política UPDATE en usuario para que el residente guarde su ineurl ─────────
-- La política actual (usuario_admin_update) solo permite rol 3 (admin).
-- Agregamos una política que deja a cada usuario actualizar su propio registro
-- identificándolo por authuid (el UUID de auth.users que coincide con auth.uid()).
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'usuario'
      AND policyname = 'usuario_update_own'
  ) THEN
    CREATE POLICY usuario_update_own ON usuario
      FOR UPDATE TO authenticated
      USING  (authuid = auth.uid()::text)
      WITH CHECK (authuid = auth.uid()::text);
  END IF;
END $$;
