-- Usuarios con el minimo permiso. Cambiar las contrasenas antes de ejecutar.

-- Producer (HU-43): solo lee la vista.
CREATE USER IF NOT EXISTS 'pagos_ro'@'localhost' IDENTIFIED BY 'cambiar_password';
GRANT SELECT ON keko_copia.v_pagos_envio TO 'pagos_ro'@'localhost';

-- Lambda (HU-49): solo inserta marcas. Se crea en '%' y en 'localhost'
-- porque con Docker Desktop la conexion llega como localhost.
CREATE USER IF NOT EXISTS 'lambda_flags'@'%' IDENTIFIED BY 'cambiar_password';
GRANT INSERT ON keko_copia.pagos_envio TO 'lambda_flags'@'%';
CREATE USER IF NOT EXISTS 'lambda_flags'@'localhost' IDENTIFIED BY 'cambiar_password';
GRANT INSERT ON keko_copia.pagos_envio TO 'lambda_flags'@'localhost';