# oficina-agua-envio-pagos

**Feature 3 — Envío de pagos a sistemas externos** · Fase 2 · Desarrollo Web (UMG)

## Descripción

Este servicio envía a dos sistemas externos (un **banco** y un **ERP** municipal) los pagos registrados en el sistema "Oficina del Agua" (`proyecto-agua-keko`), **sin modificar el monolito**.

El producer lee los pagos pendientes y los publica en RabbitMQ. Un consumer por destino los entrega con reintentos y sin duplicarlos, y registra cada intento en DynamoDB. Una Lambda escucha ese registro y marca en MySQL los pagos entregados, para que no se vuelvan a enviar. Lo que no se pudo entregar queda en una cola de errores (DLQ) y se puede reprocesar.

## Equipo

| Integrante | Rol |
|---|---|
| Yourgen Kraven Thommel Arévalo | Coordinador |
| Karen Yamileth Jiménez Galicia | Integrante |
| Enner Osvaldo Godoy Ramírez | Responsable de la Feature 3 |
| Oliver Isaac Godoy Salguero | Integrante |

## Arquitectura

```text
 MySQL                                                               API Banco
 v_pagos_envio ──► PRODUCER ──► RabbitMQ ──► CONSUMER ──(Feign)──►   API ERP
 (pendientes)      (8081)       pagos.banco   BancoListener
                     ▲          pagos.erp     ErpListener
                     │          pagos.dlq ◄── (reintentos agotados)
                     │                            │
                     │                            ▼ cada intento
                     │                      DynamoDB Logs ── stream ──► LAMBDA
                     │                                                    │
                     └──── ya no lo publica ◄── pagos_envio (ENVIADO) ◄───┘
```

1. El **producer** consulta la vista `v_pagos_envio` y publica un mensaje por cada pago y destino pendiente, con una **llave estable** (`EQA-000123`) y un `correlationId`.
2. El **consumer** toma cada mensaje y revisa en DynamoDB si ya se entregó (idempotencia). Si no, lo traduce al contrato de cada destino y lo envía con FeignClient.
3. Ante fallos temporales **reintenta con backoff exponencial**, respetando `Retry-After`. Ante un timeout o un 504, primero **consulta con GET** si el destino ya registró el pago.
4. Cada intento queda en **DynamoDB Logs**. Si se agotan los reintentos, el mensaje va a `pagos.dlq` con el motivo en sus headers.
5. La **Lambda** lee el stream de DynamoDB y, por cada intento `EXITOSO`, inserta la marca en `pagos_envio`. Desde ese momento la vista muestra ese destino como `ENVIADO` y el producer ya no lo publica.

## Estado actual

Funciona de punta a punta, en local y contra los simuladores del docente.

| Historia | Qué hace |
|---|---|
| HU-40 | Repositorio inicial |
| HU-41 | Contrato OpenAPI del producer y specs del docente |
| HU-42 | RabbitMQ con su topología en Docker Compose |
| HU-43 | Producer: consulta los pendientes y los publica en la cola |
| HU-44 | Consumer del banco |
| HU-45 | Consumer del ERP |
| HU-46 | Registro de cada intento en DynamoDB y endpoint de historial |
| HU-47 | Idempotencia: un pago nunca se registra dos veces |
| HU-48 | Reintentos con backoff, DLQ con motivo y reproceso |
| HU-49 | Lambda que marca los pagos enviados |
| HU-60 | Reconciliación con GET tras timeout o 504 |
| HU-61 | Pruebas con los simuladores del docente y re-marca de pagos ya entregados |

## Estructura del repositorio

```text
oficina-agua-envio-pagos/
├── producer/              # Spring Boot: publica pagos pendientes y expone la API (Swagger)
├── consumer/              # Spring Boot: listeners de banco y ERP, reintentos, DLQ
├── lambda/marcar_flags/   # Lambda en Python que marca los pagos enviados
├── wiremock/              # Banco y ERP falsos para probar en local
├── rabbitmq/              # Topología de RabbitMQ (definitions.json)
├── db/                    # Scripts SQL: tabla de marcas, vista y usuarios
├── docs/
│   ├── contrato/          # Contrato OpenAPI del producer
│   ├── openapi/           # Specs del banco y del ERP (del docente, sin modificar)
│   └── evidencias/        # Logs y capturas de las pruebas
├── .github/               # Plantilla de Pull Request
├── docker-compose.yml     # Infraestructura local
└── .env.example           # Plantilla de variables de entorno
```

## Servicios y puertos

| Servicio | Dónde | Para qué |
|---|---|---|
| Producer | http://localhost:8081/swagger-ui/index.html | Disparar envíos y consultar el historial |
| Consumer | http://localhost:8082 | Entregar pagos y reprocesar la DLQ |
| RabbitMQ | http://localhost:15672 | Panel de las colas (usuario y clave del `.env`) |
| DynamoDB Local | localhost:8000 | Registro de intentos (`pagos-envio-logs`). Es una API, no tiene página web |
| Banco falso | localhost:8089 | WireMock que imita al banco |
| ERP falso | localhost:8090 | WireMock que imita al ERP |
| Lambda | contenedor `lambda-marcar-flags` | Lee el stream y marca en MySQL |

## Cómo levantarla

**Requisitos:** Java 21+, Maven 3.9+, Docker con Docker Compose v2 y MariaDB/MySQL con la base de pagos.

1. **Clonar** el repositorio:

   ```bash
   git clone https://github.com/ennergodoy2003/oficina-agua-envio-pagos.git
   cd oficina-agua-envio-pagos
   ```

2. **Preparar la base de datos.** Como `root`, ejecutar en orden los scripts de `db/`: `00` crea la tabla `pagos_envio`, `01` crea la vista `v_pagos_envio` y `02` crea los usuarios `pagos_ro` (solo lectura) y `lambda_flags` (solo INSERT en `pagos_envio`). Cambiar las contraseñas antes de ejecutarlos.

3. **Crear el `.env`** a partir de la plantilla y completar las contraseñas:

   ```bash
   cp .env.example .env
   ```

   En Windows: `copy .env.example .env`. El `.env` no se sube a Git.

4. **Levantar la infraestructura:**

   ```bash
   docker compose up -d
   docker compose ps -a
   ```

   Deben quedar `Up` rabbitmq (`healthy`), dynamodb, banco-falso, erp-falso y lambda-marcar-flags. Los contenedores `rabbitmq-init` y `dynamodb-init` deben terminar en `Exited (0)`: son de un solo uso y crean las colas y la tabla.

5. **Arrancar el consumer**, en una terminal:

   ```bash
   cd consumer
   mvn spring-boot:run
   ```

6. **Arrancar el producer**, en otra terminal:

   ```bash
   cd producer
   mvn spring-boot:run
   ```

7. **Disparar un envío** desde Swagger (http://localhost:8081/swagger-ui/index.html): `POST /api/v1/envios` con `{ "limite": 20 }`. La respuesta indica cuántos pagos encontró y cuántos mensajes publicó.

## Endpoints

| Servicio | Endpoint | Qué hace |
|---|---|---|
| Producer | `POST /api/v1/envios` | Publica hasta `limite` pagos pendientes (de 1 a 100; por defecto 20) |
| Producer | `GET /api/v1/pagos/{pagoId}/envios` | Historial de intentos de un pago por destino (404 si el pago no existe) |
| Consumer | `POST /api/v1/dlq/reprocesar?limite=20` | Devuelve mensajes de `pagos.dlq` a su cola original |

## Comportamiento ante fallos

- **Idempotencia:** cada pago viaja siempre con la misma llave (`Idempotency-Key` en el banco, `documento_origen.numero` en el ERP). Un 409 de duplicado se trata como entregado y se guarda el comprobante original. Antes de enviar, el consumer revisa en DynamoDB si el pago ya se entregó a ese destino.
- **Re-marca:** si un pago llega otra vez pero DynamoDB dice que ya se entregó, no se reenvía. En su lugar, el consumer escribe una línea `EXITOSO` de re-marca ("Ya entregado antes: no se reenvia, se vuelve a marcar") y la Lambda repone la marca en MySQL si se había perdido.
- **Reintentos:** los 429, 500, 502, 503, 504 y los timeouts se reintentan con backoff exponencial (1 s, 2 s, 4 s…), respetando `Retry-After`. El máximo de intentos se configura con `envio.reintentos.*` en `consumer/src/main/resources/application.properties`.
- **Sin reintento:** los 400, 403, 422 y los 409 por llave o documento reutilizado van directo a la DLQ.
- **Reconciliación:** ante un timeout o un 504, el consumer pregunta con `GET` si el destino ya registró el pago. Con 200 lo da por entregado; con 404, reintenta.
- **DLQ:** los mensajes que agotan los reintentos llegan a `pagos.dlq` con los headers `destino`, `motivo-fallo`, `codigo-error`, `http-status` e `intentos`. Cuando el destino se recupera, se reprocesan con el endpoint del consumer.
- **Registro:** cada intento queda en DynamoDB. Si DynamoDB falla, se avisa en el log pero la decisión del envío no cambia.
- **Lambda:** si MySQL no responde, espera 5 s y vuelve a intentar sin perder nada, porque los registros siguen en el stream. Si la marca ya existe, responde `YA_MARCADO` sin error.

## Ver el registro de DynamoDB

DynamoDB Local no trae panel web. Para revisar los intentos desde el navegador se puede levantar, solo de forma temporal, **dynamodb-admin**:

```bash
docker run -d --name dynamo-panel -p 8001:8001 -e DYNAMO_ENDPOINT=http://host.docker.internal:8000 aaronshaf/dynamodb-admin
```

Queda en http://localhost:8001. Para ver la historia de un pago, filtrar por `numeroDocumento = EQA-000001`. Para apagarlo: `docker rm -f dynamo-panel`.

## Destinos falsos y simuladores del docente

Por defecto, el `.env.example` apunta al banco y al ERP **falsos** (WireMock), que fallan a propósito con algunos pagos para probar los casos de error. Para usar los **simuladores del docente** basta con cambiar en el `.env`:

```text
BANCO_URL=https://<api-id>.execute-api.us-east-1.amazonaws.com/prod
BANCO_API_KEY=<key del docente>
ERP_URL=https://<api-id>.execute-api.us-east-1.amazonaws.com/prod
ERP_API_KEY=<key del docente>
SIMULACION_ESCENARIO=          # vacío = AUTO; o SUCCESS, SLOW, ERROR_503, ERROR_500...
```

Si `SIMULACION_ESCENARIO` tiene valor, el consumer manda el header `X-Simulation-Scenario` a los dos destinos. Después de cambiar el `.env` hay que reiniciar el consumer.

**Importante:** el número `EQA-000001` sale del `id` del pago y debe ser único para siempre. El simulador recuerda los números ya registrados, y todos los integrantes de `EQUIPO_A` comparten la misma key. Por eso no hay que reutilizar números con otros datos ni cambiar de base sin vaciar antes el registro de DynamoDB.

## Variables de entorno

| Variable | Para qué sirve |
|---|---|
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | Usuario y contraseña de RabbitMQ. Obligatorias para el Compose. |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_MANAGEMENT_PORT` | Conexión a RabbitMQ y puerto del panel. |
| `PAGOS_DB_*` | Conexión del producer a la base de pagos (usuario de solo lectura). |
| `BANCO_URL`, `BANCO_API_KEY`, `BANCO_TEAM_CODE` | Destino banco: URL, key y código de equipo. |
| `ERP_URL`, `ERP_API_KEY`, `ERP_EQUIPO` | Destino ERP: URL, key y código de equipo. |
| `BANCO_FALSO_PORT`, `ERP_FALSO_PORT` | Puertos de los destinos falsos (8089 y 8090). |
| `DYNAMODB_ENDPOINT`, `DYNAMODB_PORT`, `DYNAMODB_REGION`, `DYNAMODB_TABLE` | DynamoDB Local y su tabla de registro. |
| `LAMBDA_DB_HOST`, `LAMBDA_DB_USER`, `LAMBDA_DB_PASSWORD` | Conexión de la Lambda a MySQL (usuario que solo puede hacer INSERT en `pagos_envio`). |
| `SIMULACION_ESCENARIO` | Escenario del simulador del docente; vacío = AUTO. |

## Topología de RabbitMQ (HU-42)

La topología está en `rabbitmq/definitions.json` y la aplica `rabbitmq-init` cada vez que se levanta el Compose. El usuario no está en ese archivo: RabbitMQ lo crea a partir del `.env`, para que no haya credenciales en el repositorio.

| Exchange | Tipo | Routing key | Cola destino |
|---|---|---|---|
| `pagos.exchange` | direct | `banco` | `pagos.banco` |
| `pagos.exchange` | direct | `erp` | `pagos.erp` |
| `pagos.dlx` | fanout | (vacía) | `pagos.dlq` |

Las colas `pagos.banco` y `pagos.erp` son durables y tienen `pagos.dlx` como dead-letter exchange.

## Pruebas

```bash
cd consumer && mvn test     # 103 pruebas (necesita DynamoDB Local encendido)
cd producer && mvn test     # 14 pruebas (necesita la base de pagos)
```

Pruebas de la Lambda, sin instalar Python:

```bash
docker run --rm -v "${PWD}/lambda/marcar_flags:/app" -w /app python:3.12-slim \
  sh -c "pip install -q -r requirements-dev.txt && python -m pytest -q"
```

## Apagar y empezar de cero

- `docker compose stop`: apaga los contenedores y conserva los datos.
- `docker compose down -v`: apaga y borra también los volúmenes (colas y estado de RabbitMQ).
- Para vaciar el registro de DynamoDB: borrar la tabla, recrearla y reiniciar la Lambda para que se conecte al stream nuevo.

  ```bash
  docker compose run --rm --entrypoint aws dynamodb-init dynamodb delete-table --table-name pagos-envio-logs --endpoint-url http://dynamodb:8000 --region us-east-1 --no-cli-pager
  docker compose run --rm dynamodb-init
  docker compose restart lambda-marcar-flags
  ```

- Las marcas de MySQL (`pagos_envio`) se reconstruyen desde DynamoDB: si se borran por error, basta con reiniciar la Lambda (`docker compose restart lambda-marcar-flags`), que vuelve a leer el stream completo. En AWS el stream solo guarda 24 horas; después de ese tiempo, la re-marca del consumer es la que repone las marcas.

## Problemas comunes

- **`docker compose` pide `RABBITMQ_USER`, `LAMBDA_DB_USER` u otra variable:** falta crear el `.env` o esa variable está vacía.
- **La Lambda muestra `Can't connect to MySQL server ... Connection refused`:** MariaDB todavía no está encendido. La Lambda reintenta cada 5 s sola.
- **La Lambda muestra `Access denied for user 'lambda_flags'@'localhost'`:** con Docker Desktop, MariaDB ve la conexión como `localhost`. Crear también `'lambda_flags'@'localhost'` con la misma contraseña y el mismo permiso.
- **El producer publica 0 mensajes:** todos los pagos ya tienen marca en `pagos_envio`.
- **Cambié el `.env` y el consumer sigue igual:** el consumer lee el `.env` solo al arrancar; hay que reiniciarlo.
- **`mvn test` dice que no hay POM:** se ejecutó en la raíz; hay que entrar a `consumer/` o `producer/`.
- **Cambié el usuario de RabbitMQ y el panel no me deja entrar:** RabbitMQ crea el usuario solo la primera vez. Ejecutar `docker compose down -v` y `docker compose up -d`.
- **El simulador ignora algunos escenarios:** RATE_LIMIT, TIMEOUT y LOST_ACK no se aplican en el simulador actual; ver `docs/evidencias/hu-61/README.md`.

## Flujo de trabajo

- La rama `main` está protegida: no se hace push directo, todo entra por Pull Request.
- Cada historia de Jira se trabaja en su propia rama: `feature/SDGODA-XX-descripcion-corta`.
- Prefijos de commit: `feat:` (funcionalidad nueva), `fix:` (corrección), `test:` (pruebas), `chore:` (mantenimiento), `docs:` (documentación).
- Cada Pull Request se revisa antes de unirse a `main`.

## Reglas de la fase

- El monolito `proyecto-agua-keko` no se modifica: ni código, ni endpoints, ni PHP.
- Los contratos OpenAPI del docente se guardan sin modificar.
- No se generan clientes desde el YAML: se usan DTOs escritos a mano y clientes definidos con FeignClient.
- URLs y API keys van por configuración (`.env`), nunca en el código.
- Se trabaja con datos de prueba y nunca se envían todos los pagos de golpe.

## Evidencias

- [HU-61: pruebas con los simuladores del docente](docs/evidencias/hu-61/README.md): escenarios ERROR_503, ERROR_500, SUCCESS y modo AUTO contra el simulador real del banco y del ERP, llamada sin x-api-key (403), logs y capturas.