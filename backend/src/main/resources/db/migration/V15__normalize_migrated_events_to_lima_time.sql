-- MySQL stores Flyway's TIMESTAMP in UTC on this instance; domain DATETIME values
-- are interpreted by the application as America/Lima (UTC-05:00).
UPDATE expediente_historial eh
JOIN flyway_schema_history fsh ON fsh.version = '13' AND fsh.success = 1
SET eh.fecha_hora = DATE_SUB(fsh.installed_on, INTERVAL 5 HOUR)
WHERE eh.actor_username = 'MIGRACION_V13';
