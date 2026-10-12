ALTER TABLE expediente_documento
    ADD COLUMN categoria VARCHAR(24) NOT NULL DEFAULT 'DOCUMENTO';

CREATE INDEX idx_expediente_documento_categoria ON expediente_documento (expediente_id, categoria, adjuntado_en);
