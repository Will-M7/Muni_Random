DELETE FROM app_user WHERE username NOT IN ('municipio', 'fiscalizador');

INSERT INTO app_user (username, password_hash, display_name, cargo, role, fiscalizador_id, activo)
VALUES ('municipio', '$2b$10$nkT/BPxmnZe2ClwQQmCKfu3qi13WS77tvdl2GUzZ3H88SnLw0Jyh6', 'Ventanilla Municipal · San Miguel', 'Atención al Ciudadano y Trámites Catastrales', 'MUNICIPIO', NULL, TRUE)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), display_name = VALUES(display_name), cargo = VALUES(cargo), role = VALUES(role), fiscalizador_id = VALUES(fiscalizador_id), activo = VALUES(activo);

INSERT INTO app_user (username, password_hash, display_name, cargo, role, fiscalizador_id, activo)
VALUES ('fiscalizador', '$2b$10$EwZnxG1xqhjnlAFZNykLUe8cM4pObuWDXkUX/bZkaY9gWGq0rdW1u', 'Ing. Carlos Mendoza Mamani', 'Fiscalizador Técnico de Campo', 'FISCALIZADOR', 'FIS-001', TRUE)
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), display_name = VALUES(display_name), cargo = VALUES(cargo), role = VALUES(role), fiscalizador_id = VALUES(fiscalizador_id), activo = VALUES(activo);
