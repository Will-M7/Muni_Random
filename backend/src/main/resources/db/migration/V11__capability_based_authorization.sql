CREATE TABLE app_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(60) NOT NULL UNIQUE,
    description VARCHAR(200) NOT NULL,
    configurable BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE capability (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(200) NOT NULL,
    category VARCHAR(80) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE role_capability (
    role_id BIGINT NOT NULL,
    capability_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, capability_id),
    CONSTRAINT fk_role_capability_role FOREIGN KEY (role_id) REFERENCES app_role(id) ON DELETE CASCADE,
    CONSTRAINT fk_role_capability_capability FOREIGN KEY (capability_id) REFERENCES capability(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES app_role(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO app_role (name, description, configurable) VALUES
('ADMIN_SISTEMA', 'Administración técnica y configuración del sistema', TRUE),
('MUNICIPIO', 'Gestión municipal del proceso operativo', TRUE),
('FISCALIZADOR', 'Ejecución de visitas de fiscalización asignadas', TRUE);

INSERT INTO capability (code, description, category) VALUES
('USUARIOS_VER', 'Consultar usuarios', 'Administración'),
('USUARIOS_CREAR', 'Crear usuarios', 'Administración'),
('USUARIOS_EDITAR', 'Editar datos de usuarios', 'Administración'),
('USUARIOS_ESTADO', 'Activar o desactivar usuarios', 'Administración'),
('ROLES_VER', 'Consultar roles y sus capacidades', 'Administración'),
('ROLES_GESTIONAR', 'Gestionar roles y asignar capacidades', 'Administración'),
('CAPACIDADES_GESTIONAR', 'Consultar y gestionar capacidades', 'Administración'),
('SOLICITUD_VER', 'Consultar solicitudes y sus documentos', 'Fiscalización municipal'),
('SOLICITUD_CREAR', 'Registrar solicitudes', 'Fiscalización municipal'),
('SOLICITUD_EDITAR', 'Editar solicitudes', 'Fiscalización municipal'),
('SOLICITUD_ESTADO_CAMBIAR', 'Cambiar estados administrativos de solicitudes', 'Fiscalización municipal'),
('PROGRAMACION_VER', 'Consultar la programación', 'Fiscalización municipal'),
('PROGRAMACION_GESTIONAR', 'Gestionar la programación', 'Fiscalización municipal'),
('FISCALIZADOR_ASIGNAR', 'Asignar fiscalizadores', 'Fiscalización municipal'),
('TAREAS_PROPIAS_VER', 'Consultar tareas propias asignadas', 'Fiscalizador de campo'),
('FISCALIZACION_EJECUTAR', 'Ejecutar visitas asignadas', 'Fiscalizador de campo'),
('RESULTADO_REGISTRAR', 'Registrar el resultado de una visita propia', 'Fiscalizador de campo'),
('RUTA_VER', 'Consultar las rutas propias', 'Fiscalizador de campo'),
('DOCUMENTO_TAREA_VER', 'Consultar documentos de tareas propias', 'Fiscalizador de campo');

INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id FROM app_role r CROSS JOIN capability c
WHERE r.name = 'ADMIN_SISTEMA'
  AND c.code IN ('USUARIOS_VER','USUARIOS_CREAR','USUARIOS_EDITAR','USUARIOS_ESTADO','ROLES_VER','ROLES_GESTIONAR','CAPACIDADES_GESTIONAR');

INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id FROM app_role r CROSS JOIN capability c
WHERE r.name = 'MUNICIPIO'
  AND c.code IN ('USUARIOS_VER','USUARIOS_CREAR','SOLICITUD_VER','SOLICITUD_CREAR','SOLICITUD_EDITAR','SOLICITUD_ESTADO_CAMBIAR','PROGRAMACION_VER','PROGRAMACION_GESTIONAR','FISCALIZADOR_ASIGNAR');

INSERT INTO role_capability (role_id, capability_id)
SELECT r.id, c.id FROM app_role r CROSS JOIN capability c
WHERE r.name = 'FISCALIZADOR'
  AND c.code IN ('TAREAS_PROPIAS_VER','FISCALIZACION_EJECUTAR','RESULTADO_REGISTRAR','RUTA_VER','DOCUMENTO_TAREA_VER');

INSERT INTO user_role (user_id, role_id)
SELECT u.id, r.id FROM app_user u JOIN app_role r ON r.name = u.role;
