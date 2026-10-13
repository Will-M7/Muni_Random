-- A municipal account manages several inspectors. This does not change inspector login identities.
ALTER TABLE fiscalizador
    ADD COLUMN municipio_responsable_id BIGINT NULL,
    ADD CONSTRAINT fk_fiscalizador_municipio FOREIGN KEY (municipio_responsable_id) REFERENCES app_user(id);
CREATE INDEX idx_fiscalizador_municipio ON fiscalizador(municipio_responsable_id);
CREATE TABLE fiscalizador_gestion_historial (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    fiscalizador_id VARCHAR(20) NOT NULL,
    municipio_anterior_id BIGINT NULL,
    municipio_nuevo_id BIGINT NULL,
    actor_id BIGINT NOT NULL,
    fecha DATETIME NOT NULL,
    CONSTRAINT fk_gestion_fiscalizador FOREIGN KEY (fiscalizador_id) REFERENCES fiscalizador(id),
    CONSTRAINT fk_gestion_anterior FOREIGN KEY (municipio_anterior_id) REFERENCES app_user(id),
    CONSTRAINT fk_gestion_nuevo FOREIGN KEY (municipio_nuevo_id) REFERENCES app_user(id),
    CONSTRAINT fk_gestion_actor FOREIGN KEY (actor_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
-- Existing assignments stay undecided. No users or operational identities are inferred or modified.
