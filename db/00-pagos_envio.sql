-- Marcas de envio (HU-49): la fila existe = ENVIADO a ese destino; no existe = PENDIENTE.
CREATE TABLE IF NOT EXISTS keko_copia.pagos_envio (
    pago_id    INT(10) UNSIGNED NOT NULL,
    destino    ENUM('BANCO','ERP') NOT NULL,
    enviado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP(),
    PRIMARY KEY (pago_id, destino),
    CONSTRAINT pagos_envio_pago_fk FOREIGN KEY (pago_id) REFERENCES keko_copia.pagos (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;