ALTER TABLE expediente_fiscalizacion
    ADD COLUMN administrado_apellidos VARCHAR(160) NULL,
    ADD COLUMN referencia_domicilio VARCHAR(250) NULL,
    ADD COLUMN latitud DECIMAL(10,7) NULL,
    ADD COLUMN longitud DECIMAL(10,7) NULL,
    ADD COLUMN documento_sustento_nombre VARCHAR(255) NULL,
    ADD COLUMN documento_sustento_interno VARCHAR(120) NULL,
    ADD COLUMN documento_sustento_tipo VARCHAR(80) NULL,
    ADD COLUMN documento_sustento_fecha DATETIME NULL;

UPDATE expediente_fiscalizacion e
JOIN solicitud s ON s.id=e.solicitud_id
SET e.administrado_apellidos=s.apellidos,
    e.referencia_domicilio=s.referencia,
    e.latitud=s.latitud,
    e.longitud=s.longitud,
    e.documento_sustento_nombre=s.documento,
    e.documento_sustento_interno=s.documento_nombre_interno,
    e.documento_sustento_tipo=s.documento_content_type,
    e.documento_sustento_fecha=s.documento_fecha_subida;
