CREATE TABLE user_preference (
  user_id BIGINT NOT NULL PRIMARY KEY,
  mapa_interno BOOLEAN NOT NULL DEFAULT TRUE,
  google_externo BOOLEAN NOT NULL DEFAULT TRUE,
  google_url BOOLEAN NOT NULL DEFAULT TRUE,
  descargar_jornada_html BOOLEAN NOT NULL DEFAULT TRUE,
  actualizado_por VARCHAR(120) NULL,
  actualizado_en DATETIME NULL,
  CONSTRAINT fk_user_preference_user FOREIGN KEY (user_id) REFERENCES app_user(id)
);

INSERT INTO user_preference (user_id) SELECT id FROM app_user;

CREATE TABLE global_parameter (
  name VARCHAR(80) NOT NULL PRIMARY KEY,
  int_value INT NOT NULL,
  actualizado_por VARCHAR(120) NULL,
  actualizado_en DATETIME NULL
);
INSERT INTO global_parameter (name, int_value) VALUES ('limite_visitas_diarias', 5);

CREATE TABLE identity_integration (
  id TINYINT NOT NULL PRIMARY KEY,
  provider VARCHAR(24) NOT NULL DEFAULT 'DISABLED',
  endpoint VARCHAR(500) NULL,
  encrypted_api_key TEXT NULL,
  encrypted_client_id TEXT NULL,
  encrypted_client_secret TEXT NULL,
  actualizado_por VARCHAR(120) NULL,
  actualizado_en DATETIME NULL
);
INSERT INTO identity_integration (id, provider) VALUES (1, 'DISABLED');
