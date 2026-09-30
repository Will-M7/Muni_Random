-- V13 had only solicitud.fecha_registro (DATE), so midnight was not a verifiable
-- event time. Use Flyway's recorded installation timestamp for the migration events.
UPDATE expediente_historial eh
JOIN flyway_schema_history fsh ON fsh.version = '13' AND fsh.success = 1
SET eh.fecha_hora = fsh.installed_on
WHERE eh.actor_username = 'MIGRACION_V13';
