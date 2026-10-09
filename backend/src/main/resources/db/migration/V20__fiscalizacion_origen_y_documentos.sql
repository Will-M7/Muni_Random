ALTER TABLE expediente_fiscalizacion
    ADD COLUMN dependencia_procedencia VARCHAR(160) NULL,
    ADD COLUMN referencia_solicitud VARCHAR(160) NULL,
    ADD CONSTRAINT uk_expediente_solicitud_externa UNIQUE (dependencia_procedencia, referencia_solicitud);

ALTER TABLE app_user
    ADD CONSTRAINT uk_app_user_fiscalizador UNIQUE (fiscalizador_id);

CREATE TABLE expediente_documento (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    expediente_id BIGINT NOT NULL,
    nombre_original VARCHAR(255) NOT NULL,
    nombre_interno VARCHAR(120) NOT NULL UNIQUE,
    tipo_mime VARCHAR(120) NOT NULL,
    tamano_bytes BIGINT NOT NULL,
    adjuntado_en DATETIME NOT NULL,
    adjuntado_por_id BIGINT NOT NULL,
    CONSTRAINT fk_expediente_documento_expediente FOREIGN KEY (expediente_id) REFERENCES expediente_fiscalizacion(id),
    CONSTRAINT fk_expediente_documento_usuario FOREIGN KEY (adjuntado_por_id) REFERENCES app_user(id),
    INDEX idx_expediente_documento_expediente (expediente_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
