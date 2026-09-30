INSERT INTO capability (code, description, category)
VALUES ('FISCALIZADORES_DISPONIBLES_VER', 'Consultar fiscalizadores activos disponibles para asignación', 'Fiscalización municipal')
ON DUPLICATE KEY UPDATE description = VALUES(description), category = VALUES(category);

DELETE rc FROM role_capability rc
JOIN app_role r ON r.id = rc.role_id
JOIN capability c ON c.id = rc.capability_id
WHERE r.name = 'MUNICIPIO'
  AND c.code IN ('USUARIOS_VER', 'USUARIOS_CREAR', 'USUARIOS_EDITAR', 'USUARIOS_ESTADO');

INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id FROM app_role r JOIN capability c ON c.code = 'FISCALIZADORES_DISPONIBLES_VER'
WHERE r.name = 'MUNICIPIO'
ON DUPLICATE KEY UPDATE capability_id = VALUES(capability_id);
