ALTER TABLE solicitud
    ADD COLUMN resultado_fiscalizacion VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    ADD COLUMN observaciones_fiscalizador TEXT NULL,
    ADD COLUMN reporte_nombre_original VARCHAR(255) NULL,
    ADD COLUMN reporte_nombre_interno VARCHAR(120) NULL,
    ADD COLUMN reporte_content_type VARCHAR(80) NULL,
    ADD COLUMN reporte_tamano BIGINT NULL,
    ADD COLUMN reporte_ruta VARCHAR(255) NULL,
    ADD COLUMN reporte_fecha_subida DATETIME NULL,
    ADD COLUMN fecha_ejecucion DATETIME NULL;
