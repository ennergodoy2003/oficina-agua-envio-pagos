-- Usuarios con el minimo permiso. Cambiar las contrasenas antes de ejecutar.
-- Base: proyecto_agua.

-- Producer (HU-43): solo lee la vista.
-- Se crea en 'localhost' y en '127.0.0.1': segun como se conecte Java,
-- MariaDB lo identifica con uno u otro host.
CREATE USER IF NOT EXISTS 'pagos_ro'@'localhost' IDENTIFIED BY 'cambiar_password';
GRANT SELECT ON proyecto_agua.v_pagos_envio TO 'pagos_ro'@'localhost';
CREATE USER IF NOT EXISTS 'pagos_ro'@'127.0.0.1' IDENTIFIED BY 'cambiar_password';
GRANT SELECT ON proyecto_agua.v_pagos_envio TO 'pagos_ro'@'127.0.0.1';

-- Lambda (HU-49): solo inserta marcas. Se conecta desde el contenedor de Docker,
-- que no es localhost, por eso el host es '%'.
CREATE USER IF NOT EXISTS 'lambda_flags'@'%' IDENTIFIED BY 'cambiar_password';
GRANT INSERT ON proyecto_agua.pagos_envio TO 'lambda_flags'@'%';