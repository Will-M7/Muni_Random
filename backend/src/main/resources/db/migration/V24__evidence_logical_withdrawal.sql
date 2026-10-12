ALTER TABLE evidencia_fiscalizacion
    ADD COLUMN retirado_en DATETIME NULL,
    ADD COLUMN retirado_por_id BIGINT NULL,
    ADD COLUMN motivo_retiro VARCHAR(500) NULL,
    ADD CONSTRAINT fk_evidencia_retirado_por FOREIGN KEY (retirado_por_id) REFERENCES app_user(id);

CREATE INDEX idx_evidencia_diligencia_retirado ON evidencia_fiscalizacion (diligencia_id, retirado_en);
