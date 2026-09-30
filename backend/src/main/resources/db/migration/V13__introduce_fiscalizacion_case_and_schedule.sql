CREATE TABLE expediente_fiscalizacion (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    fecha_creacion DATETIME NOT NULL,
    creado_por_id BIGINT NULL,
    origen VARCHAR(30) NOT NULL,
    detalle_origen VARCHAR(500) NULL,
    estado VARCHAR(30) NOT NULL,
    objeto_fiscalizacion VARCHAR(255) NOT NULL,
    observaciones_iniciales TEXT NULL,
    solicitud_id BIGINT NULL UNIQUE,
    tipo_administrado VARCHAR(20) NULL,
    documento_administrado VARCHAR(15) NULL,
    nombre_administrado VARCHAR(280) NULL,
    telefono_contacto VARCHAR(30) NULL,
    direccion_fiscalizada VARCHAR(250) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_expediente_creador FOREIGN KEY (creado_por_id) REFERENCES app_user(id),
    CONSTRAINT fk_expediente_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitud(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE programacion_fiscalizacion (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expediente_id BIGINT NOT NULL,
    fiscalizador_id VARCHAR(20) NULL,
    fecha DATE NULL,
    hora TIME NULL,
    tipo_visita VARCHAR(20) NOT NULL,
    estado_programacion VARCHAR(20) NOT NULL,
    creada_por_id BIGINT NULL,
    fecha_creacion DATETIME NOT NULL,
    motivo_reprogramacion VARCHAR(500) NULL,
    motivo_cancelacion VARCHAR(500) NULL,
    programacion_anterior_id BIGINT NULL,
    resultado_motivo TEXT NULL,
    legacy_solicitud_codigo VARCHAR(30) NULL,
    CONSTRAINT fk_programacion_expediente FOREIGN KEY (expediente_id) REFERENCES expediente_fiscalizacion(id),
    CONSTRAINT fk_programacion_fiscalizador FOREIGN KEY (fiscalizador_id) REFERENCES fiscalizador(id),
    CONSTRAINT fk_programacion_creador FOREIGN KEY (creada_por_id) REFERENCES app_user(id),
    CONSTRAINT fk_programacion_anterior FOREIGN KEY (programacion_anterior_id) REFERENCES programacion_fiscalizacion(id),
    INDEX ix_programacion_expediente (expediente_id),
    INDEX ix_programacion_fecha_estado (fecha, estado_programacion)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE expediente_historial (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    expediente_id BIGINT NOT NULL,
    programacion_id BIGINT NULL,
    fecha_hora DATETIME NOT NULL,
    actor_id BIGINT NULL,
    actor_username VARCHAR(120) NULL,
    tipo_evento VARCHAR(40) NOT NULL,
    descripcion VARCHAR(1000) NOT NULL,
    valores_anteriores TEXT NULL,
    valores_nuevos TEXT NULL,
    CONSTRAINT fk_expediente_historial_expediente FOREIGN KEY (expediente_id) REFERENCES expediente_fiscalizacion(id),
    CONSTRAINT fk_expediente_historial_programacion FOREIGN KEY (programacion_id) REFERENCES programacion_fiscalizacion(id),
    CONSTRAINT fk_expediente_historial_actor FOREIGN KEY (actor_id) REFERENCES app_user(id),
    INDEX ix_expediente_historial_fecha (expediente_id, fecha_hora)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO capability (code, description, category) VALUES
 ('EXPEDIENTE_VER','Consultar expedientes de fiscalización','Expediente de fiscalización'),
 ('EXPEDIENTE_CREAR','Crear expedientes de fiscalización','Expediente de fiscalización'),
 ('EXPEDIENTE_EDITAR','Editar datos de expedientes de fiscalización','Expediente de fiscalización'),
 ('PROGRAMACION_CREAR','Crear programaciones de fiscalización','Programación'),
 ('PROGRAMACION_REPROGRAMAR','Reprogramar visitas conservando su historial','Programación'),
 ('PROGRAMACION_CANCELAR','Cancelar visitas programadas','Programación'),
 ('PROGRAMACION_VER','Consultar programaciones de fiscalización','Programación')
ON DUPLICATE KEY UPDATE description=VALUES(description), category=VALUES(category);

INSERT INTO role_capability (role_id, capability_id)
SELECT r.id,c.id FROM app_role r CROSS JOIN capability c
WHERE r.name='MUNICIPIO' AND c.code IN ('EXPEDIENTE_VER','EXPEDIENTE_CREAR','EXPEDIENTE_EDITAR','PROGRAMACION_CREAR','PROGRAMACION_REPROGRAMAR','PROGRAMACION_CANCELAR','PROGRAMACION_VER','FISCALIZADOR_ASIGNAR')
ON DUPLICATE KEY UPDATE capability_id=VALUES(capability_id);

INSERT INTO expediente_fiscalizacion
 (codigo,fecha_creacion,creado_por_id,origen,detalle_origen,estado,objeto_fiscalizacion,observaciones_iniciales,solicitud_id,tipo_administrado,documento_administrado,nombre_administrado,telefono_contacto,direccion_fiscalizada)
SELECT CONCAT('FIS-',YEAR(COALESCE(s.fecha_registro,CURDATE())),'-',LPAD(s.id,5,'0')),
       TIMESTAMP(COALESCE(s.fecha_registro,CURDATE()),'00:00:00'),NULL,'SOLICITUD',
       CONCAT('Migrado desde solicitud ',s.codigo,' (modelo anterior)'), 'ABIERTO',
       CONCAT('Fiscalización asociada a solicitud ',s.codigo),s.observaciones,s.id,'PERSONA_NATURAL',s.dni,
       CONCAT(s.nombres,' ',s.apellidos),s.telefono,s.direccion
FROM solicitud s;

INSERT INTO programacion_fiscalizacion
 (expediente_id,fiscalizador_id,fecha,hora,tipo_visita,estado_programacion,creada_por_id,fecha_creacion,motivo_reprogramacion,programacion_anterior_id,resultado_motivo,legacy_solicitud_codigo)
SELECT e.id,s.fiscalizador_id,s.fecha_fiscalizacion,s.hora_fiscalizacion,'PROGRAMADA',
       CASE WHEN s.resultado_fiscalizacion='REALIZADA' THEN 'REALIZADA'
            WHEN s.resultado_fiscalizacion='NO_REALIZADA' THEN 'NO_REALIZADA'
            WHEN s.estado='EXPIRADO' THEN 'VENCIDA'
            WHEN s.fecha_fiscalizacion IS NOT NULL AND s.hora_fiscalizacion IS NOT NULL
                 AND TIMESTAMP(s.fecha_fiscalizacion,s.hora_fiscalizacion) < DATE_SUB(UTC_TIMESTAMP(), INTERVAL 5 HOUR) THEN 'VENCIDA'
            ELSE 'PROGRAMADA' END,
       NULL,TIMESTAMP(COALESCE(s.fecha_registro,CURDATE()),'00:00:00'),NULL,NULL,s.observaciones_fiscalizador,s.codigo
FROM solicitud s JOIN expediente_fiscalizacion e ON e.solicitud_id=s.id
WHERE s.fecha_fiscalizacion IS NOT NULL OR s.hora_fiscalizacion IS NOT NULL
   OR s.resultado_fiscalizacion IN ('REALIZADA','NO_REALIZADA') OR s.estado='EXPIRADO';

INSERT INTO expediente_historial (expediente_id,programacion_id,fecha_hora,actor_id,actor_username,tipo_evento,descripcion,valores_anteriores,valores_nuevos)
SELECT e.id,p.id,e.fecha_creacion,NULL,'MIGRACION_V13','EXPEDIENTE_MIGRADO',CONCAT('Expediente creado desde solicitud heredada ',s.codigo),NULL,
       JSON_OBJECT('origen','SOLICITUD','estado','ABIERTO','solicitudCodigo',s.codigo)
FROM solicitud s JOIN expediente_fiscalizacion e ON e.solicitud_id=s.id
LEFT JOIN programacion_fiscalizacion p ON p.expediente_id=e.id;

INSERT INTO expediente_historial (expediente_id,programacion_id,fecha_hora,actor_id,actor_username,tipo_evento,descripcion,valores_anteriores,valores_nuevos)
SELECT e.id,p.id,p.fecha_creacion,NULL,'MIGRACION_V13','PROGRAMACION_MIGRADA',CONCAT('Programación heredada desde solicitud ',s.codigo),NULL,
       JSON_OBJECT('fecha',p.fecha,'hora',p.hora,'estado',p.estado_programacion,'fiscalizadorId',p.fiscalizador_id)
FROM solicitud s JOIN expediente_fiscalizacion e ON e.solicitud_id=s.id JOIN programacion_fiscalizacion p ON p.expediente_id=e.id;
