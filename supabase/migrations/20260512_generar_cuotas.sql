CREATE OR REPLACE FUNCTION generar_cuotas_mensuales(p_monto NUMERIC DEFAULT 1500.00)
RETURNS INT
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_count INT;
  v_periodo TEXT;
  v_fecha_vencimiento DATE;
BEGIN
  v_periodo := TO_CHAR(CURRENT_DATE, 'YYYY-MM');
  v_fecha_vencimiento := (DATE_TRUNC('month', CURRENT_DATE) + INTERVAL '1 month - 1 day')::DATE;
  INSERT INTO cuota (fkusuario, monto, estatus, fechavencimiento, periodo)
  SELECT u.id, p_monto, 'pendiente', v_fecha_vencimiento, v_periodo
  FROM usuario u
  WHERE u.fkrolusuario = 1 AND u.estaactivo = TRUE
    AND NOT EXISTS (SELECT 1 FROM cuota c WHERE c.fkusuario = u.id AND c.periodo = v_periodo);
  GET DIAGNOSTICS v_count = ROW_COUNT;
  RETURN v_count;
END;
$$;
