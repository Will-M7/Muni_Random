-- Reconstruye únicamente resultados definitivos existentes con fecha de ejecución real.
INSERT INTO solicitud_historial (solicitud_id, fecha, estado, nota)
SELECT s.id, DATE(s.fecha_ejecucion), s.estado,
       LEFT(CONCAT('Resultado operativo: ', s.resultado_fiscalizacion,
           CASE WHEN s.resultado_fiscalizacion = 'REALIZADA' AND s.reporte_nombre_original IS NOT NULL
               THEN '; reporte PDF adjunto'
               WHEN s.resultado_fiscalizacion = 'NO_REALIZADA' AND NULLIF(TRIM(s.observaciones_fiscalizador), '') IS NOT NULL
               THEN CONCAT('; motivo: ', TRIM(s.observaciones_fiscalizador))
               ELSE '' END,
           ' (reconstruido de datos existentes)'), 500)
FROM solicitud s
WHERE s.resultado_fiscalizacion IN ('REALIZADA', 'NO_REALIZADA')
  AND s.fecha_ejecucion IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM solicitud_historial h
      WHERE h.solicitud_id = s.id AND h.nota LIKE CONCAT('Resultado operativo: ', s.resultado_fiscalizacion, '%')
  );
