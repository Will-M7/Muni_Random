ALTER TABLE expediente_fiscalizacion
    MODIFY COLUMN estado VARCHAR(30) NOT NULL;

CREATE TABLE revision_fiscalizacion (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    expediente_id BIGINT NOT NULL,
    diligencia_id BIGINT NOT NULL,
    acta_id BIGINT NOT NULL,
    revisor_id BIGINT NULL,
    fecha_inicio_revision DATETIME NULL,
    fecha_final_revision DATETIME NULL,
    estado_revision VARCHAR(30) NOT NULL,
    observaciones_revision TEXT NULL,
    conclusion VARCHAR(60) NULL,
    fundamento_conclusion TEXT NULL,
    requiere_seguimiento BOOLEAN NOT NULL DEFAULT FALSE,
    descripcion_conclusion TEXT NULL,
    plazo_conclusion VARCHAR(120) NULL,
    requiere_derivacion BOOLEAN NOT NULL DEFAULT FALSE,
    denominacion_otra VARCHAR(180) NULL,
    referencia_documentos TEXT NULL,
    informe_pdf_nombre VARCHAR(255) NULL,
    informe_pdf_interno VARCHAR(120) NULL,
    informe_checksum VARCHAR(64) NULL,
    creado_en DATETIME NOT NULL,
    actualizado_en DATETIME NOT NULL,
    CONSTRAINT fk_revision_expediente FOREIGN KEY (expediente_id) REFERENCES expediente_fiscalizacion(id),
    CONSTRAINT fk_revision_diligencia FOREIGN KEY (diligencia_id) REFERENCES diligencia_fiscalizacion(id),
    CONSTRAINT fk_revision_acta FOREIGN KEY (acta_id) REFERENCES acta_fiscalizacion(id),
    CONSTRAINT fk_revision_revisor FOREIGN KEY (revisor_id) REFERENCES app_user(id),
    CONSTRAINT uq_revision_diligencia UNIQUE (diligencia_id),
    INDEX idx_revision_estado (estado_revision, fecha_inicio_revision)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE solicitud_aclaracion_fiscalizacion (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    revision_id BIGINT NOT NULL,
    mensaje TEXT NOT NULL,
    solicitada_por_id BIGINT NOT NULL,
    fecha_solicitud DATETIME NOT NULL,
    respondida_por_id BIGINT NULL,
    fecha_respuesta DATETIME NULL,
    respuesta TEXT NULL,
    estado VARCHAR(20) NOT NULL,
    cerrada_en DATETIME NULL,
    CONSTRAINT fk_aclaracion_revision FOREIGN KEY (revision_id) REFERENCES revision_fiscalizacion(id),
    CONSTRAINT fk_aclaracion_solicitante FOREIGN KEY (solicitada_por_id) REFERENCES app_user(id),
    CONSTRAINT fk_aclaracion_respondedor FOREIGN KEY (respondida_por_id) REFERENCES app_user(id),
    INDEX idx_aclaracion_revision (revision_id, estado)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE programacion_fiscalizacion
    ADD COLUMN motivo_seguimiento VARCHAR(1000) NULL,
    ADD COLUMN revision_origen_id BIGINT NULL,
    ADD CONSTRAINT fk_programacion_revision_origen FOREIGN KEY (revision_origen_id) REFERENCES revision_fiscalizacion(id);

INSERT INTO capability (code, description, category) VALUES
('REVISION_VER','Consultar la bandeja y detalle de revisiones','Revisión municipal'),
('REVISION_INICIAR','Iniciar o retomar revisión de fiscalización','Revisión municipal'),
('ACLARACION_SOLICITAR','Solicitar aclaración al fiscalizador responsable','Revisión municipal'),
('ACLARACION_RESPONDER','Responder aclaraciones de diligencias propias','Fiscalizador de campo'),
('REVISION_CONCLUIR','Registrar conclusión municipal de fiscalización','Revisión municipal'),
('EXPEDIENTE_CERRAR','Cerrar expediente de fiscalización','Revisión municipal'),
('SEGUIMIENTO_CREAR','Crear programación de seguimiento','Revisión municipal'),
('INFORME_CONCLUSION_VER','Consultar informe de conclusión','Revisión municipal');

INSERT IGNORE INTO role_capability (role_id, capability_id)
SELECT r.id,c.id FROM app_role r JOIN capability c ON c.code IN
('REVISION_VER','REVISION_INICIAR','ACLARACION_SOLICITAR','REVISION_CONCLUIR','EXPEDIENTE_CERRAR','SEGUIMIENTO_CREAR','INFORME_CONCLUSION_VER')
WHERE r.name='MUNICIPIO';
INSERT IGNORE INTO role_capability (role_id, capability_id)
SELECT r.id,c.id FROM app_role r JOIN capability c ON c.code='ACLARACION_RESPONDER'
WHERE r.name='FISCALIZADOR';

ALTER TABLE expediente_fiscalizacion
    MODIFY COLUMN estado VARCHAR(30) NOT NULL;
