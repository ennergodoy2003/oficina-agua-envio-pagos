-- Vista que lee el producer (HU-43). Un pago por fila, con sus dos flags de envio.
-- Base: proyecto_agua. Cada tabla va con su base para que la vista no dependa
-- de la base seleccionada al crearla.
CREATE OR REPLACE VIEW proyecto_agua.v_pagos_envio AS
SELECT
    p.id AS id,
    CONCAT('EQA-', LPAD(p.id, 6, '0')) AS numero_documento,
    CONCAT('CLI-', LPAD(c.id, 5, '0')) AS codigo_cliente,
    c.nombre AS nombre_cliente,
    ct.numero_serie AS codigo_contador,
    pe.anio AS periodo_anio,
    pe.mes AS periodo_mes,
    CAST(ROUND(p.monto * 100, 0) AS SIGNED) AS monto_centavos,
    CAST(ROUND(COALESCE(r.monto_consumo, p.monto) * 100, 0) AS SIGNED) AS consumo_centavos,
    CAST(ROUND(COALESCE(r.monto_adicional, 0) * 100, 0) AS SIGNED) AS adicional_centavos,
    r.concepto_adicional AS concepto_adicional,
    p.fecha_pago AS fecha_pago,
    p.metodo AS forma_pago,
    IF(eb.pago_id IS NULL, 'PENDIENTE', 'ENVIADO') AS estado_envio_banco,
    IF(ee.pago_id IS NULL, 'PENDIENTE', 'ENVIADO') AS estado_envio_erp
FROM proyecto_agua.pagos p
LEFT JOIN proyecto_agua.recibos r ON r.id = p.recibo_id
JOIN proyecto_agua.lecturas l ON l.id = COALESCE(p.lectura_id, r.lectura_id)
JOIN proyecto_agua.servicios s ON s.id = l.servicio_id
JOIN proyecto_agua.clientes c ON c.id = s.cliente_id
JOIN proyecto_agua.contadores ct ON ct.id = l.contador_id
JOIN proyecto_agua.periodos pe ON pe.id = l.periodo_id
LEFT JOIN proyecto_agua.pagos_envio eb ON eb.pago_id = p.id AND eb.destino = 'BANCO'
LEFT JOIN proyecto_agua.pagos_envio ee ON ee.pago_id = p.id AND ee.destino = 'ERP';