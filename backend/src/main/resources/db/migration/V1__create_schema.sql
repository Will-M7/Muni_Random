CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(120) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    cargo VARCHAR(180) NOT NULL,
    role VARCHAR(20) NOT NULL,
    fiscalizador_id VARCHAR(20) NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE fiscalizador (
    id VARCHAR(20) PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(160) NOT NULL,
    zona VARCHAR(180) NOT NULL,
    telefono VARCHAR(30) NOT NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE solicitud (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    nombres VARCHAR(120) NOT NULL,
    apellidos VARCHAR(160) NOT NULL,
    dni VARCHAR(8) NOT NULL,
    telefono VARCHAR(30) NOT NULL,
    direccion VARCHAR(250) NOT NULL,
    referencia VARCHAR(250) NULL,
    documento VARCHAR(255) NOT NULL,
    documento_tamano VARCHAR(30) NULL,
    latitud DECIMAL(10,7) NOT NULL,
    longitud DECIMAL(10,7) NOT NULL,
    fecha DATE NOT NULL,
    hora VARCHAR(30) NOT NULL,
    fiscalizador_id VARCHAR(20) NULL,
    estado VARCHAR(30) NOT NULL,
    observaciones TEXT NULL,
    resultado_visita TEXT NULL,
    fecha_registro DATE NOT NULL,
    CONSTRAINT fk_solicitud_fiscalizador FOREIGN KEY (fiscalizador_id) REFERENCES fiscalizador(id),
    CONSTRAINT uk_solicitud_cita UNIQUE (fecha, hora)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE solicitud_historial (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    solicitud_id BIGINT NOT NULL,
    fecha DATE NOT NULL,
    estado VARCHAR(30) NOT NULL,
    nota VARCHAR(500) NULL,
    CONSTRAINT fk_historial_solicitud FOREIGN KEY (solicitud_id) REFERENCES solicitud(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ubicacion_fiscalizador (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    fiscalizador_id VARCHAR(20) NOT NULL UNIQUE,
    latitud DECIMAL(10,7) NOT NULL,
    longitud DECIMAL(10,7) NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT FALSE,
    actualizada_en DATETIME NULL,
    CONSTRAINT fk_ubicacion_fiscalizador FOREIGN KEY (fiscalizador_id) REFERENCES fiscalizador(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
