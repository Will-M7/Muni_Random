ALTER TABLE diligencia_fiscalizacion
    ADD COLUMN registro_posterior BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN registrado_en DATETIME NULL,
    ADD COLUMN registrado_por_id BIGINT NULL,
    ADD COLUMN fecha_hora_ejecucion DATETIME NULL,
    ADD COLUMN origen_hora_ejecucion VARCHAR(20) NULL,
    ADD CONSTRAINT fk_diligencia_registrado_por FOREIGN KEY (registrado_por_id) REFERENCES app_user(id),
    ADD INDEX idx_diligencia_registrado_por (registrado_por_id);

