CREATE TABLE diligencia_fiscalizacion (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    expediente_id BIGINT NOT NULL,
    programacion_id BIGINT NOT NULL,
    fiscalizador_responsable_id VARCHAR(20) NOT NULL,
    fecha_inicio DATETIME NOT NULL,
    fecha_cierre DATETIME NULL,
    latitud_inicio DECIMAL(10,7) NULL,
    longitud_inicio DECIMAL(10,7) NULL,
    gps_inicio_estado VARCHAR(24) NOT NULL,
    latitud_cierre DECIMAL(10,7) NULL,
    longitud_cierre DECIMAL(10,7) NULL,
    gps_cierre_estado VARCHAR(24) NOT NULL,
    lugar_diligencia VARCHAR(250) NOT NULL,
    estado VARCHAR(24) NOT NULL,
    situacion_persona VARCHAR(32) NOT NULL DEFAULT 'PENDIENTE',
    persona_nombres VARCHAR(160) NULL,
    persona_apellidos VARCHAR(160) NULL,
    persona_tipo_documento VARCHAR(24) NULL,
    persona_numero_documento VARCHAR(24) NULL,
    persona_condicion VARCHAR(32) NULL,
    persona_observaciones TEXT NULL,
    hechos_constatados TEXT NULL,
    ocurrencias TEXT NULL,
    observaciones_fiscalizador TEXT NULL,
    observaciones_fiscalizado TEXT NULL,
    motivo_no_realizada VARCHAR(40) NULL,
    detalle_motivo_no_realizada VARCHAR(1000) NULL,
    copia_entregada BOOLEAN NOT NULL DEFAULT FALSE,
    medio_entrega VARCHAR(24) NOT NULL DEFAULT 'PENDIENTE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_diligencia_expediente FOREIGN KEY (expediente_id) REFERENCES expediente_fiscalizacion(id),
    CONSTRAINT fk_diligencia_programacion FOREIGN KEY (programacion_id) REFERENCES programacion_fiscalizacion(id),
    CONSTRAINT fk_diligencia_responsable FOREIGN KEY (fiscalizador_responsable_id) REFERENCES fiscalizador(id),
    CONSTRAINT uq_diligencia_programacion UNIQUE (programacion_id),
    INDEX idx_diligencia_expediente (expediente_id),
    INDEX idx_diligencia_responsable (fiscalizador_responsable_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE diligencia_participante (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    diligencia_id BIGINT NOT NULL,
    tipo_participante VARCHAR(32) NOT NULL,
    nombre VARCHAR(160) NOT NULL,
    apellidos VARCHAR(160) NULL,
    tipo_documento VARCHAR(24) NULL,
    numero_documento VARCHAR(24) NULL,
    entidad_area VARCHAR(180) NULL,
    cargo_condicion VARCHAR(120) NULL,
    observaciones VARCHAR(1000) NULL,
    creado_por_id BIGINT NULL,
    creado_en DATETIME NOT NULL,
    CONSTRAINT fk_participante_diligencia FOREIGN KEY (diligencia_id) REFERENCES diligencia_fiscalizacion(id),
    CONSTRAINT fk_participante_autor FOREIGN KEY (creado_por_id) REFERENCES app_user(id),
    INDEX idx_participante_diligencia (diligencia_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE evidencia_fiscalizacion (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    diligencia_id BIGINT NOT NULL,
    tipo VARCHAR(24) NOT NULL,
    nombre_original VARCHAR(255) NOT NULL,
    nombre_interno VARCHAR(120) NOT NULL UNIQUE,
    tipo_mime VARCHAR(100) NOT NULL,
    tamano_bytes BIGINT NOT NULL,
    descripcion VARCHAR(500) NULL,
    creado_por_id BIGINT NULL,
    fecha_hora DATETIME NOT NULL,
    CONSTRAINT fk_evidencia_diligencia FOREIGN KEY (diligencia_id) REFERENCES diligencia_fiscalizacion(id),
    CONSTRAINT fk_evidencia_autor FOREIGN KEY (creado_por_id) REFERENCES app_user(id),
    INDEX idx_evidencia_diligencia (diligencia_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE acta_fiscalizacion (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    diligencia_id BIGINT NOT NULL UNIQUE,
    contenido_json JSON NOT NULL,
    firma_fiscalizado_estado VARCHAR(32) NOT NULL,
    firma_fiscalizado_observacion VARCHAR(1000) NULL,
    pdf_generado_nombre VARCHAR(255) NOT NULL,
    pdf_generado_interno VARCHAR(120) NOT NULL UNIQUE,
    pdf_firmado_nombre VARCHAR(255) NULL,
    pdf_firmado_interno VARCHAR(120) NULL UNIQUE,
    generado_por_id BIGINT NULL,
    generado_en DATETIME NOT NULL,
    CONSTRAINT fk_acta_diligencia FOREIGN KEY (diligencia_id) REFERENCES diligencia_fiscalizacion(id),
    CONSTRAINT fk_acta_autor FOREIGN KEY (generado_por_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO capability (code, description, category) VALUES
('DILIGENCIA_INICIAR', 'Iniciar una visita de fiscalización propia', 'Fiscalizador de campo'),
('DILIGENCIA_EDITAR_PROPIA', 'Guardar avances de una diligencia propia', 'Fiscalizador de campo'),
('DILIGENCIA_FINALIZAR', 'Finalizar una diligencia propia', 'Fiscalizador de campo'),
('EVIDENCIA_AGREGAR', 'Agregar evidencia a una diligencia propia', 'Fiscalizador de campo'),
('ACTA_GENERAR', 'Generar el acta de una diligencia propia', 'Fiscalizador de campo'),
('ACTA_VER', 'Consultar actas de fiscalización', 'Fiscalización'),
('ACTA_FIRMADA_SUBIR', 'Subir copia firmada en papel del acta propia', 'Fiscalizador de campo');

INSERT IGNORE INTO role_capability (role_id, capability_id)
SELECT r.id, c.id FROM app_role r CROSS JOIN capability c
WHERE r.name = 'FISCALIZADOR'
  AND c.code IN ('DILIGENCIA_INICIAR','DILIGENCIA_EDITAR_PROPIA','DILIGENCIA_FINALIZAR','EVIDENCIA_AGREGAR','ACTA_GENERAR','ACTA_VER','ACTA_FIRMADA_SUBIR');

INSERT IGNORE INTO role_capability (role_id, capability_id)
SELECT r.id, c.id FROM app_role r CROSS JOIN capability c
WHERE r.name = 'MUNICIPIO' AND c.code = 'ACTA_VER';
