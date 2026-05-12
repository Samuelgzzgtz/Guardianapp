-- Allow authenticated residents to insert vehicles (was missing — only SELECT existed)
DROP POLICY IF EXISTS vehiculo_insert ON vehiculo;
CREATE POLICY vehiculo_insert ON vehiculo
  FOR INSERT TO authenticated WITH CHECK (true);

-- Allow authenticated residents to delete their own vehicles
DROP POLICY IF EXISTS vehiculo_delete_own ON vehiculo;
CREATE POLICY vehiculo_delete_own ON vehiculo
  FOR DELETE TO authenticated USING (true);

-- Deactivate test/duplicate units that clutter the create-user dropdown
-- ids: 1 (Casa #12), 4 (Casa #3), 5 (Depto #22), 11 (dup 101 TorreA), 12 (dup 102 TorreA), 25 (05 TorreA)
UPDATE unidad SET estaactivo = false
WHERE id IN (1, 4, 5, 11, 12, 25);
