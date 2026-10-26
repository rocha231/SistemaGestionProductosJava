CREATE DATABASE IF NOT EXISTS gestion_productos
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE gestion_productos;

CREATE TABLE IF NOT EXISTS productos (
    codigo VARCHAR(30) PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    descripcion VARCHAR(255) NOT NULL,
    precio DECIMAL(12,2) NOT NULL,
    cantidad INT NOT NULL,
    CONSTRAINT chk_producto_precio CHECK (precio >= 0),
    CONSTRAINT chk_producto_cantidad CHECK (cantidad >= 0)
);
