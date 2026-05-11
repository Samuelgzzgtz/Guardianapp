-- Visitor pass table: residents create passes; guards scan QR to validate
CREATE TABLE IF NOT EXISTS pase_visita (
  id              SERIAL PRIMARY KEY,
  fk_residente    INT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  nombre_visitante TEXT NOT NULL,
  modelo_vehiculo  TEXT,
  color_vehiculo   TEXT,
  placa_vehiculo   TEXT,
  vigencia         TEXT NOT NULL DEFAULT 'hoy',   -- 'hoy' | 'semana' | 'mes'
  usos_maximos     INT  NOT NULL DEFAULT 1,
  usos_realizados  INT  NOT NULL DEFAULT 0,
  fecha_creacion   TIMESTAMPTZ   DEFAULT now(),
  fecha_expiracion DATE,
  activo           BOOLEAN       DEFAULT true
);

-- Residents may manage their own passes; security (role 2) may read and update any pass
ALTER TABLE pase_visita ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "residents_manage_own_pases" ON pase_visita;
CREATE POLICY "residents_manage_own_pases"
  ON pase_visita
  FOR ALL
  USING (
    fk_residente = (
      SELECT id FROM usuario WHERE email = auth.jwt() ->> 'email' LIMIT 1
    )
  )
  WITH CHECK (
    fk_residente = (
      SELECT id FROM usuario WHERE email = auth.jwt() ->> 'email' LIMIT 1
    )
  );

DROP POLICY IF EXISTS "security_read_pases" ON pase_visita;
CREATE POLICY "security_read_pases"
  ON pase_visita
  FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM usuario
      WHERE email = auth.jwt() ->> 'email'
        AND fkrolusuario = 2
    )
  );

DROP POLICY IF EXISTS "security_update_pases" ON pase_visita;
CREATE POLICY "security_update_pases"
  ON pase_visita
  FOR UPDATE
  USING (
    EXISTS (
      SELECT 1 FROM usuario
      WHERE email = auth.jwt() ->> 'email'
        AND fkrolusuario = 2
    )
  );

-- Add INE photo field to visita table (used by guard during access logging)
ALTER TABLE visita ADD COLUMN IF NOT EXISTS foto_ine_url TEXT;
