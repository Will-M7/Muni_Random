-- Solo se incluye mediante SPRING_FLYWAY_LOCATIONS en el perfil de producción.
-- La restricción aborta si la base contiene datos distintos a la semilla histórica.
CREATE TABLE production_seed_guard (
  id TINYINT NOT NULL PRIMARY KEY,
  verified TINYINT NOT NULL,
  bootstrapped TINYINT NOT NULL DEFAULT 0,
  CONSTRAINT chk_production_seed_guard CHECK (verified = 1)
);

INSERT INTO production_seed_guard (id, verified)
SELECT 1, CASE WHEN
  (SELECT COUNT(*) FROM app_user) = 2
  AND (SELECT COUNT(*) FROM app_user WHERE username IN ('municipio','fiscalizador')) = 2
  AND (SELECT COUNT(*) FROM fiscalizador) = 3
  AND (SELECT COUNT(*) FROM fiscalizador WHERE codigo IN ('FIS-001','FIS-002','FIS-003')) = 3
  AND (SELECT COUNT(*) FROM solicitud) = 7
  AND (SELECT COUNT(*) FROM solicitud WHERE codigo IN ('SM-2026-00418','SM-2026-00419','SM-2026-00420','SM-2026-00421','SM-2026-00422','SM-2026-00423','SM-2026-00424')) = 7
  AND (SELECT COUNT(*) FROM expediente_fiscalizacion) = 7
  AND (SELECT COUNT(*) FROM expediente_fiscalizacion WHERE solicitud_id IS NOT NULL) = 7
  AND (SELECT COUNT(*) FROM user_preference) = 2
  AND (SELECT COUNT(*) FROM predio) = 0
  AND (SELECT COUNT(*) FROM ubicacion_fiscalizador) = 0
  AND (SELECT COUNT(*) FROM expediente_documento) = 0
  AND (SELECT COUNT(*) FROM diligencia_fiscalizacion) = 0
  AND (SELECT COUNT(*) FROM diligencia_participante) = 0
  AND (SELECT COUNT(*) FROM evidencia_fiscalizacion) = 0
  AND (SELECT COUNT(*) FROM acta_fiscalizacion) = 0
  AND (SELECT COUNT(*) FROM revision_fiscalizacion) = 0
  AND (SELECT COUNT(*) FROM solicitud_aclaracion_fiscalizacion) = 0
  AND (SELECT COUNT(*) FROM peruapi_dni_daily_quota) = 0
  AND (SELECT COUNT(*) FROM identity_integration WHERE provider = 'DISABLED' AND encrypted_api_key IS NULL AND encrypted_peruapi_key IS NULL AND encrypted_factiliza_token IS NULL) = 1
  AND NOT EXISTS (SELECT 1 FROM programacion_fiscalizacion p LEFT JOIN expediente_fiscalizacion e ON e.id=p.expediente_id WHERE e.solicitud_id IS NULL)
  AND NOT EXISTS (SELECT 1 FROM expediente_historial h LEFT JOIN expediente_fiscalizacion e ON e.id=h.expediente_id WHERE e.solicitud_id IS NULL)
  AND NOT EXISTS (SELECT 1 FROM solicitud_historial h LEFT JOIN solicitud s ON s.id=h.solicitud_id WHERE s.id IS NULL)
THEN 1 ELSE 0 END;

DELETE FROM expediente_historial;
DELETE FROM programacion_fiscalizacion;
DELETE FROM expediente_fiscalizacion;
DELETE FROM solicitud_historial;
DELETE FROM solicitud;
DELETE FROM user_preference;
DELETE FROM user_role;
DELETE FROM app_user;
DELETE FROM fiscalizador;
