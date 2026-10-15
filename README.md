# oficina-agua-envio-pagos

**Feature 3 — Envío de pagos a sistemas externos** · Fase 2 · Desarrollo Web (UMG)

## Descripción

Este servicio envía a sistemas externos (un banco y un ERP) los pagos registrados en el sistema "Oficina del Agua" (`proyecto-agua-keko`), sin modificar el monolito original.

Los pagos se publican como mensajes en una cola (RabbitMQ). Un consumer por destino toma cada mensaje y llama a la API correspondiente. Cada intento queda registrado, y los envíos que fallan repetidamente van a una cola de mensajes no entregados (DLQ), desde donde se pueden reprocesar.

## Equipo

| Integrante | Rol |
|---|---|
| Yourgen Kraven Thommel Arévalo | Coordinador |
| Karen Yamileth Jiménez Galicia | Integrante |
| Enner Osvaldo Godoy Ramírez | Responsable de la Feature 3 |
| Oliver Isaac Godoy Salguero | Integrante |

## Arquitectura

```text
Pago pendiente (MySQL)
        │
        ▼
   PRODUCER ──► RabbitMQ ──► CONSUMER banco ──► API Banco
 (Spring Boot)      │   └──► CONSUMER ERP   ──► API ERP
                    │
                    └──► DLQ (mensajes que agotaron reintentos)

Cada intento ──► DynamoDB Logs ──► Lambda ──► marca "enviado" en MySQL
```

## Estructura del repositorio

```text
oficina-agua-envio-pagos/
├── producer/            # Servicio que publica los pagos pendientes en RabbitMQ
├── consumer/            # Consumers por destino (banco y ERP)
├── db/                  # Scripts y datos de prueba
├── docs/                # Documentación y contratos (specs del docente sin modificar)
├── rabbitmq/            # Topología de RabbitMQ (definitions.json)
├── .github/             # Plantilla de Pull Request
├── docker-compose.yml   # Infraestructura local (se completa por historias)
└── .env.example         # Plantilla de variables de entorno
```

## Estado actual

La infraestructura se agrega por historias de usuario. Hoy está disponible RabbitMQ con su topología (HU-42). Producer, consumers, DynamoDB y Lambda llegan en las historias siguientes.

## Cómo levantarla

**Requisitos:** Java 21, Maven 3.9+, Docker y Docker Compose v2. Para levantar solo RabbitMQ basta con Docker.

1. Clonar el repositorio:

```bash
   git clone https://github.com/ennergodoy2003/oficina-agua-envio-pagos.git
   cd oficina-agua-envio-pagos
```

2. Copiar la plantilla de variables:

```bash
   cp .env.example .env
```

En Windows (cmd): `copy .env.example .env`

3. Editar `.env` y poner un `RABBITMQ_USER` y un `RABBITMQ_PASSWORD` propios. El `.env` no se sube a Git. Si faltan esas dos variables, `docker compose` se detiene con un mensaje de error.

4. Levantar RabbitMQ:

```bash
   docker compose up -d
```

5. Verificar:

```bash
   docker compose ps -a
```

- `rabbitmq` debe estar en `healthy`.
- `rabbitmq-init` debe estar en `Exited (0)`. Es un contenedor de un solo uso: aplica la topología y termina. Eso es lo normal.

El panel de administración queda en http://localhost:15672 (usuario y contraseña del `.env`).

## Variables de entorno

| Variable | Para qué sirve |
|---|---|
| `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | Usuario y contraseña de RabbitMQ. Obligatorias para el Compose. |
| `RABBITMQ_PORT` | Puerto AMQP en tu máquina (por defecto 5672). |
| `RABBITMQ_MANAGEMENT_PORT` | Puerto del panel de administración (por defecto 15672). |
| `RABBITMQ_HOST` | Host que usarán los servicios Java (producer y consumers). |
| `PAGOS_DB_*` | Conexión a la base de datos de pagos del monolito (usuario de solo lectura). |

## Topología de RabbitMQ (HU-42)

La topología está en `rabbitmq/definitions.json` y la aplica el contenedor `rabbitmq-init` cada vez que se levanta el Compose. El usuario no está en ese archivo: lo crea RabbitMQ a partir del `.env`, para que no haya credenciales en el repositorio.

| Exchange | Tipo | Routing key | Cola destino |
|---|---|---|---|
| `pagos.exchange` | direct | `banco` | `pagos.banco` |
| `pagos.exchange` | direct | `erp` | `pagos.erp` |
| `pagos.dlx` | fanout | (vacía) | `pagos.dlq` |

Las colas `pagos.banco` y `pagos.erp` son durables y tienen `pagos.dlx` como dead-letter exchange, así que un mensaje rechazado sin reencolar termina en `pagos.dlq`. Cuándo y por qué se rechaza (reintentos agotados) se define en la HU-48.

### Prueba rápida desde el panel

1. En **Exchanges → pagos.exchange → Publish message**, publicar con routing key `banco` (o `erp`) y cualquier texto como payload.
2. En **Queues and Streams**, la cola correspondiente debe mostrar Ready 1.
3. Para ver la DLQ: abrir la cola, **Get messages** con Ack Mode `Reject requeue false`. `pagos.dlq` debe pasar a Ready 1.
4. Al terminar, usar **Purge** en cada cola para dejarlas vacías.

## Apagar y empezar de cero

- `docker compose down`: apaga los contenedores y conserva los datos.
- `docker compose down -v`: apaga y borra también los volúmenes (mensajes, usuario y estado de RabbitMQ). Al volver a ejecutar `docker compose up -d`, la topología se recrea sola.

## Problemas comunes

- **`docker compose` se detiene pidiendo `RABBITMQ_USER` o `RABBITMQ_PASSWORD`:** falta crear el `.env` o esas variables están vacías.
- **El puerto 5672 o 15672 ya está en uso:** cambiar `RABBITMQ_PORT` o `RABBITMQ_MANAGEMENT_PORT` en el `.env`.
- **`rabbitmq-init` no termina en `Exited (0)`:** ver el motivo con `docker compose logs rabbitmq-init`.
- **Cambié el usuario o la contraseña en el `.env` y el panel no me deja entrar:** RabbitMQ crea el usuario solo la primera vez que arranca con un volumen vacío. Ejecutar `docker compose down -v` y luego `docker compose up -d`.

## Flujo de trabajo

- La rama `main` está protegida: no se hace push directo, todo entra por Pull Request.
- Cada historia de Jira se trabaja en su propia rama: `feature/SDGODA-XX-descripcion-corta`.
- Prefijos de commit: `feat:` (funcionalidad nueva), `fix:` (corrección), `chore:` (tareas de mantenimiento), `docs:` (documentación).
- Cada Pull Request se revisa antes de unirse a `main`.

## Reglas de la fase

- El monolito `proyecto-agua-keko` no se modifica: ni código, ni endpoints, ni PHP.
- Los contratos OpenAPI del docente se guardan sin modificar.
- No se generan clientes desde el YAML: se usan DTOs escritos a mano y clientes definidos con FeignClient.
- URLs y API keys van por configuración (`.env`), nunca en el código.
- Se trabaja con datos de prueba y nunca se envían todos los pagos de golpe.