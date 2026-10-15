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
├── .github/             # Plantilla de Pull Request
├── docker-compose.yml   # Infraestructura local (se completa por historias)
└── .env.example         # Plantilla de variables de entorno
```

## Cómo levantarla

> Estado actual: estructura base inicializada. La infraestructura se agrega por historias de usuario.

1. Clonar el repositorio:
```bash
   git clone https://github.com/ennergodoy2003/oficina-agua-envio-pagos.git
   cd oficina-agua-envio-pagos
```
2. Copiar la plantilla de variables y ajustar los valores:
```bash
   cp .env.example .env
```
En Windows (cmd): `copy .env.example .env`
3. Levantar la infraestructura (disponible a partir de la HU-42, RabbitMQ):
```bash
   docker compose up -d
```

Requisitos: Java 21, Maven 3.9+, Docker y Docker Compose v2.

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