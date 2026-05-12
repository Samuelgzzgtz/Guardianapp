-- Supabase Realtime conecta el WebSocket con el anon key aunque el usuario esté autenticado
-- (bug de propagación de JWT en supabase-kt con importSession). Como resultado, el rol del
-- WebSocket es "anon", y las políticas TO authenticated bloquean todos los eventos.
-- Solución: agregar políticas SELECT para el rol anon en las tablas de realtime.
-- NOTA: esto permite lectura anon via REST API. Las escrituras siguen protegidas.

DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'reporte','cuota','usuario','tarealimpieza','vehiculo',
    'amenidad','unidad','pase_visita','aviso','accesolog',
    'notificacion','reserva','areacomun'
  ] LOOP
    EXECUTE format(
      'DROP POLICY IF EXISTS realtime_%s_anon_select ON %I',
      t, t
    );
    EXECUTE format(
      'CREATE POLICY realtime_%s_anon_select ON %I FOR SELECT TO anon USING (true)',
      t, t
    );
  END LOOP;
END $$;
