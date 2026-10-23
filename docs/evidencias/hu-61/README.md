# Evidencias HU-61 (SDGODA-146): pruebas con los simuladores del docente

- Fecha: 22/10/2026
- Simulador: https://gdsi5lyj34.execute-api.us-east-1.amazonaws.com/prod (banco y ERP)
- Equipo: EQUIPO_A
- El escenario se elige con la variable SIMULACION_ESCENARIO del .env. El consumer manda el
  header X-Simulation-Scenario a los dos destinos. Si la variable esta vacia, no se manda y el
  simulador usa el modo AUTO.

## Archivos

- `00-matriz-escenarios.txt`: prueba directa de cada escenario contra el simulador, sin el
  sistema de por medio. Muestra cuales escenarios aplica el simulador y cuales no.
- `01-error-503.log`: ERROR_503. Respuesta 503 en banco y ERP; se respeta el Retry-After
  (esperas de 2 s); tras 4 intentos el mensaje va a la DLQ con el motivo.
- `02-error-500.log`: ERROR_500. Respuesta 500 en banco y ERP; sin Retry-After, el consumer usa
  su backoff exponencial (1 s, 2 s, 4 s); tras 4 intentos va a la DLQ.
- `03-rate-limit.log`: el simulador ignoro RATE_LIMIT y respondio en modo aleatorio (AUTO).
  Aun asi muestra un 429 real del ERP con su Retry-After respetado, un timeout real y una
  reconciliacion con GET exitosa (el ERP ya tenia el documento).
- `04-timeout.log`: el simulador ignoro TIMEOUT (modo AUTO). Muestra un fallo del banco
  recuperado al reintentar.
- `05-success.log`: SUCCESS. Entregas a la primera con comprobantes reales. Ademas, el banco
  respondio 409 duplicado para EQA-000009 a EQA-000012 (registrados el 21/10 con la misma key
  del equipo): el consumer los trato como entregados y guardo el comprobante original.
- `06-sin-api-key-403.txt`: banco y ERP responden 403 si falta el header x-api-key.
- `07-swagger-historial-EQA-000009.png`: historial de un pago ya registrado en el banco real,
  entregado sin duplicarse.
- `08-heidisql-flags.png`: los 12 pagos marcados como ENVIADO en banco y ERP por la Lambda.
- `09-rabbitmq-colas.png`: colas pagos.banco, pagos.erp y pagos.dlq vacias al final.

## Escenarios que el simulador no aplica

Probado directo (`00-matriz-escenarios.txt`) y reportado al docente:
- RATE_LIMIT responde 201 en lugar de 429 (banco y ERP).
- TIMEOUT responde 201 en menos de 3 s en lugar de tardar 4 a 8 s sin registrar.
- LOST_ACK responde 201 en 2 s (banco) o 503 (ERP) en lugar de registrar y tardar 4 a 8 s.
- SLOW funciona en el banco (2.3 s, por debajo del timeout de 3 s del cliente).

Mientras tanto, esos casos estan cubiertos por las pruebas automaticas del consumer con WireMock
(429 con Retry-After, timeout con reconciliacion GET 200 y 404, LOST_ACK con 409 duplicado y con
GET) y por las corridas locales de HU-48 y HU-60.

## Modo AUTO

Las corridas 03 y 04 son, en la practica, corridas en modo AUTO: el simulador ignoro el
escenario y respondio con su comportamiento aleatorio. El sistema entrego todos los pagos sin
duplicarlos, como muestran las capturas 08 y 09.