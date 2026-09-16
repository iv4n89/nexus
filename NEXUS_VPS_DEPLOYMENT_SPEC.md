# NEXUS — Self-hosted Deployment & Operations Platform

## 1. Visión

Nexus es una plataforma self-hosted para administrar y observar aplicaciones Docker desplegadas en un VPS propio.

No pretende ser otro Portainer genérico, sino una herramienta orientada a **despliegue + operaciones + observabilidad ligera**, diseñada alrededor de los proyectos que realmente viven en el VPS.

### Objetivos

- Descubrir automáticamente contenedores Docker.
- Agruparlos por proyecto.
- Mostrar estado de aplicaciones y servicios.
- Consultar y buscar logs.
- Detectar y agrupar errores.
- Generar alertas operativas.
- Ejecutar despliegues mediante scripts previamente definidos.
- Mostrar el progreso de un despliegue en tiempo real.
- Consultar historial de despliegues.
- Ejecutar rollback mediante una operación configurada.
- Mostrar métricas básicas del VPS y contenedores.
- Mantener una línea temporal de actividad en tiempo real.
- Registrar acciones administrativas mediante auditoría.

### Principio arquitectónico

Separar:

- **Desired state:** configuración declarada del proyecto.
- **Observed state:** estado real obtenido de Docker y del sistema.

Docker es la fuente de verdad sobre el estado actual de los contenedores. PostgreSQL conserva información operacional e histórica, no una copia completa de Docker.

---

## 2. Posicionamiento

**Nexus — Self-hosted Deployment & Operations Platform**

> A lightweight self-hosted control plane for managing Docker-based applications, deployments, logs and operational alerts on your own infrastructure.

Para portfolio:

> Nexus is a self-hosted control plane built to manage the applications running on my own VPS. It combines Docker discovery, deployments, live logs, operational alerts and deployment history in a lightweight interface.

Tecnologías destacables:

- Java
- Spring Boot
- Spring Security
- Next.js
- TypeScript
- PostgreSQL
- Docker Engine API
- SSE/WebSockets
- Redis (fase posterior)
- Docker
- Linux

---

## 3. Principios de producto

La interfaz debe ser:

- negra
- blanca
- minimalista
- rápida
- sin gradientes
- sin glassmorphism
- sin tarjetas gigantes
- sin exceso de animaciones

Conceptualmente: **terminal + herramienta SaaS moderna**.

---

## 4. Arquitectura general

```text
                    INTERNET
                       │
                       ▼
                  ┌─────────┐
                  │  NGINX  │
                  └────┬────┘
                       │
                       ▼
              ┌─────────────────┐
              │     Next.js     │
              │    Frontend     │
              └────────┬────────┘
                       │
                 REST + SSE/WS
                       │
                       ▼
              ┌─────────────────┐
              │  Spring Boot   │
              │      API       │
              └───────┬─────────┘
                      │
          ┌───────────┼────────────┐
          │           │            │
          ▼           ▼            ▼
       Docker      PostgreSQL   Scheduler
       Engine
```

Redis no es obligatorio inicialmente.

---

## 5. Estructura del repositorio

```text
nexus/
├── frontend/
│   ├── app/
│   ├── components/
│   ├── features/
│   ├── hooks/
│   ├── lib/
│   └── types/
│
├── backend/
│   ├── src/main/java/
│   │   └── com/ivan/nexus/
│   │       ├── application/
│   │       ├── domain/
│   │       ├── infrastructure/
│   │       └── interfaces/
│   └── src/test/
│
├── deployment/
│   ├── docker-compose.yml
│   ├── nginx/
│   └── scripts/
│
├── projects/
│   └── example/
│       └── nexus.yml
│
├── docs/
├── .env.example
├── README.md
└── docker-compose.yml
```

---

## 6. Backend

### Stack

- Java 21 LTS
- Spring Boot
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- Flyway
- Docker Engine API/client
- SSE o WebSocket
- Actuator
- Bean Validation
- JUnit
- Testcontainers

Evitar dependencias innecesarias.

---

## 7. Arquitectura backend

Arquitectura inspirada en Clean Architecture / Hexagonal.

```text
domain
    ↓
application
    ↓
interfaces
    ↓
infrastructure
```

### Domain

Entidades/conceptos:

- Project
- Service
- Deployment
- DeploymentStatus
- AlertRule
- AlertEvent
- LogErrorFingerprint
- AuditEvent

No debe conocer Docker, PostgreSQL, HTTP ni Spring MVC.

### Application

Casos de uso:

```text
DiscoverProjects
GetProject
GetProjectServices
GetContainerLogs
SearchLogs
AnalyzeLogs
DeployProject
RollbackProject
GetDeploymentHistory
GetSystemMetrics
GetContainerMetrics
EvaluateAlerts
GetActivityTimeline
```

### Infrastructure

Implementaciones:

```text
DockerProjectRepository
DockerContainerRepository
DockerLogProvider
DockerStatsProvider
DockerDeploymentExecutor
PostgresProjectRepository
PostgresDeploymentRepository
```

### Interfaces

Controllers:

```text
ProjectController
ContainerController
LogController
DeploymentController
AlertController
MetricsController
ActivityController
AuthController
```

---

## 8. Integración con Docker

Nexus puede conectarse al Docker Engine mediante:

```text
/var/run/docker.sock
```

Ejemplo de Compose:

```yaml
volumes:
  - /var/run/docker.sock:/var/run/docker.sock
```

### Seguridad crítica

El acceso de escritura al Docker socket proporciona un nivel de control muy elevado sobre el host.

Por tanto:

- Nexus nunca debe exponerse públicamente sin autenticación.
- No debe existir un endpoint de ejecución arbitraria.
- Las operaciones deben estar explícitamente definidas.
- Todas las acciones sensibles deben auditarse.
- Las credenciales nunca deben estar en el repositorio.

### Evolución recomendada

A futuro puede existir un agente:

```text
Nexus API
    │
    ▼
Nexus Agent
    │
    ├── Docker
    ├── deploy scripts
    └── system metrics
```

El Agent puede actuar como frontera de privilegios.

---

## 9. Descubrimiento y agrupación

Docker Compose proporciona labels como:

```text
com.docker.compose.project
com.docker.compose.service
```

Nexus también soportará:

```text
nexus.project
nexus.service
```

Ejemplo:

```yaml
services:
  api:
    labels:
      nexus.project: "portfolio"
      nexus.service: "api"

  web:
    labels:
      nexus.project: "portfolio"
      nexus.service: "web"
```

Prioridad:

1. `nexus.project`
2. `com.docker.compose.project`
3. fallback por nombre del contenedor

---

## 10. Desired State vs Observed State

### Desired state

Procede de `nexus.yml`:

```yaml
project:
  id: portfolio
  name: Portfolio
  description: Personal portfolio
  workingDirectory: /srv/projects/portfolio

services:
  - api
  - web

deployment:
  command: ./deploy.sh

rollback:
  command: ./rollback.sh

health:
  url: https://portfolio.example.com/api/health
  timeoutSeconds: 10

alerts:
  errorRatePerMinute: 10
  restartCount: 3
  memoryPercent: 90
```

### Observed state

Proviene de Docker/sistema:

```text
containers
images
health
status
restart count
cpu
memory
network
logs
uptime
```

---

## 11. Project Manifest

Cada proyecto administrado puede tener un `nexus.yml`.

El manifest define metadatos y operaciones permitidas.

### Regla crítica

El usuario **no puede introducir comandos arbitrarios desde la interfaz**.

Incorrecto:

```http
POST /execute
{
  "command": "rm -rf ..."
}
```

Correcto:

```http
POST /projects/portfolio/deploy
```

El backend ejecuta únicamente el comando configurado para ese proyecto.

---

## 12. Dashboard

Diseño conceptual:

```text
NEXUS

────────────────────────────────────────────

VPS

CPU       18%
RAM       52%
DISK      37%

────────────────────────────────────────────

PROJECTS

NEXUS              ● 4/4
PORTFOLIO          ● 3/3
AI-DOSSIER         ● 2/2

────────────────────────────────────────────

ALERTS

2 active alerts

────────────────────────────────────────────

RECENT ACTIVITY

00:42  nexus-api deployed
00:38  portfolio-web restarted
00:31  nexus-api 3 errors
00:21  ai-worker deployment successful
```

La información importante debe ser visible sin navegar por múltiples pantallas.

---

## 13. Página de proyecto

```text
PORTFOLIO

Status: HEALTHY

[ DEPLOY ] [ ROLLBACK ]

Services

web        ● running
api        ● running
worker     ● running

CPU         11%
Memory      420 MB
Restarts    0

────────────────────────

Recent errors

Database timeout       ×12
HTTP 500               ×4

────────────────────────

Deployments

#31   00:42   SUCCESS
#30   yesterday SUCCESS
#29   yesterday FAILED
```

---

## 14. Container view

Mostrar:

- nombre
- imagen
- estado
- health
- uptime
- restart count
- CPU
- RAM
- network
- ports
- metadatos de entorno sin revelar secretos
- logs

Nunca mostrar variables sensibles directamente.

---

## 15. Sistema de logs

### MVP

No almacenar permanentemente todos los logs en PostgreSQL.

El VPS tiene almacenamiento limitado y el log bruto puede crecer rápidamente.

Los logs se solicitan directamente a Docker usando las capacidades equivalentes a:

```text
tail
since
until
follow
timestamps
```

### UI

```text
[ ALL ] [ INFO ] [ WARN ] [ ERROR ]

Search: timeout

Service: api

From: 30 min
```

Primera versión: búsqueda por substring.

Posteriormente: filtros estructurados y regex opcional.

---

## 16. Detección y fingerprinting de errores

Detectar inicialmente patrones como:

```text
ERROR
Exception
Traceback
FATAL
failed
connection refused
timeout
HTTP 500
```

No limitarse a palabras exactas en versiones posteriores.

### Fingerprint

Normalizar un mensaje y generar, por ejemplo:

```text
SHA-256(normalized_error)
```

La normalización puede eliminar timestamps, UUIDs, IDs, IPs y números variables.

Ejemplo:

```text
Connection to 10.0.0.31 failed at 12:42
Connection to 10.0.0.44 failed at 12:43
```

→

```text
Connection to <IP> failed
```

Y mostrar:

```text
Database connection timeout ×37
```

Modelo:

```text
LogErrorFingerprint

id
projectId
serviceId
fingerprint
firstSeen
lastSeen
count
sampleMessage
```

---

## 17. Alertas

Tipos iniciales:

### Container stopped

Detectar transición `running → exited`.

### Restart spike

Ejemplo: más de 3 reinicios en 5 minutos.

### High memory

Ejemplo: memoria > 90%.

### Disk

Ejemplo: disco > 85%.

### Error rate

Ejemplo: > 10 errores/minuto.

### Healthcheck failure

Docker health = unhealthy.

### HTTP health failure

Endpoint no devuelve 2xx.

---

## 18. Alert engine

Inicialmente usar tareas programadas de Spring:

```text
cada 30 segundos
    obtener estado Docker
    evaluar reglas
    generar alertas
    resolver alertas recuperadas
```

No utilizar Kafka para esto.

Redis tampoco es necesario en V1.

Estados:

```text
ACTIVE
ACKNOWLEDGED
RESOLVED
```

---

## 19. Deployments

Entidad:

```text
Deployment

id
projectId
status
startedAt
finishedAt
triggeredBy
commit
outputSummary
```

Estados:

```text
PENDING
RUNNING
SUCCESS
FAILED
CANCELLED
```

### Flujo

```text
1. validar proyecto
2. validar manifest
3. registrar deployment
4. status = RUNNING
5. ejecutar script configurado
6. transmitir output
7. esperar resultado
8. ejecutar health check
9. marcar SUCCESS/FAILED
10. registrar auditoría
```

Conceptualmente:

```text
DEPLOY
  │
  ▼
script
  │
  ▼
docker compose build
  │
  ▼
docker compose up
  │
  ▼
health check
  │
  ├── OK → SUCCESS
  └── FAIL → FAILED
```

---

## 20. Streaming de deployment

El usuario debe poder ver:

```text
NEXUS / DEPLOY

00:42:12 deployment started
00:42:13 pulling repository
00:42:15 building api
00:42:28 building web
00:42:31 container recreated
00:42:33 health check
00:42:35 health check OK

DEPLOYMENT SUCCESS
```

Para V1, SSE es suficiente.

WebSockets pueden añadirse cuando exista una necesidad bidireccional real.

---

## 21. Rollback

Debe ser una operación explícita:

```text
[ ROLLBACK ]
```

Con confirmación:

```text
ROLLBACK

Current deployment: #31
Previous deployment: #30

[ CANCEL ] [ ROLLBACK ]
```

El proyecto define:

```yaml
rollback:
  command: ./rollback.sh
```

Nexus no inventa el mecanismo de recuperación.

### Auto rollback

Fase posterior y desactivada por defecto:

```text
deploy
  ↓
health check
  ↓
FAIL
  ↓
rollback
  ↓
health check
```

---

## 22. Historial

Mostrar:

```text
#31

SUCCESS
Started 00:42
Duration 23s

Triggered by:
Ivan

Commit:
a83f9d2

Health:
OK
```

Asociar posteriormente repository, branch, commit, autor y mensaje.

---

## 23. Command Center

Una timeline global en tiempo real:

```text
NEXUS / LIVE

00:42:12  portfolio-api deployment started
00:42:15  image build started
00:42:31  container recreated
00:42:33  health check OK

00:38:11  portfolio-web restarted

00:31:42  nexus-api error detected
00:31:43  error fingerprint updated

00:21:08  ai-worker deployment successful
```

Modelo:

```text
ActivityEvent

id
timestamp
type
projectId
serviceId
message
metadata
```

Tipos:

```text
DEPLOYMENT_STARTED
DEPLOYMENT_SUCCESS
DEPLOYMENT_FAILED
CONTAINER_STARTED
CONTAINER_STOPPED
CONTAINER_RESTARTED
ERROR_DETECTED
ALERT_CREATED
ALERT_RESOLVED
HEALTH_CHECK_FAILED
```

---

## 24. Incidents

Fase posterior: agrupar eventos relacionados en incidentes.

Ejemplo:

```text
Incident #14

Portfolio API outage

Duration: 2m 13s

Events:
00:31:02 deployment started
00:31:18 container restarted
00:31:19 health check failed
00:31:21 12 errors detected
00:31:22 alert created
00:31:40 deployment failed
```

Esta funcionalidad puede convertir Nexus en algo más cercano a una herramienta de operaciones que a un simple dashboard Docker.

---

## 25. Métricas

### VPS

- CPU
- RAM
- disk
- load average
- uptime

### Containers

- CPU
- memory
- network RX
- network TX
- restart count
- uptime

Inicialmente obtenerlas mediante Docker stats y APIs del sistema.

No introducir Prometheus/Grafana obligatoriamente.

### Históricos

Fase posterior: muestrear cada 30s/1m y guardar agregados min/avg/max. Si la observabilidad crece mucho, considerar Prometheus.

---

## 26. PostgreSQL

Tablas iniciales:

```text
projects
deployments
deployment_events
alert_rules
alert_events
log_error_fingerprints
activity_events
audit_events
users
```

No crear una copia persistente de todos los containers Docker salvo necesidad concreta.

---

## 27. API REST

### Projects

```http
GET /api/projects
GET /api/projects/{id}
GET /api/projects/{id}/services
```

### Containers

```http
GET /api/containers
GET /api/containers/{id}
GET /api/containers/{id}/stats
```

### Logs

```http
GET /api/containers/{id}/logs
GET /api/containers/{id}/logs/search
```

### Deployments

```http
GET  /api/projects/{id}/deployments
GET  /api/projects/{id}/deployments/{deploymentId}
POST /api/projects/{id}/deploy
POST /api/projects/{id}/rollback
```

### Alerts

```http
GET /api/alerts
POST /api/alerts/{id}/acknowledge
```

### Metrics

```http
GET /api/metrics/system
GET /api/projects/{id}/metrics
```

### Activity

```http
GET /api/activity
```

---

## 28. Realtime API

Para V1:

```text
SSE
```

Endpoints:

```http
GET /api/events/stream
GET /api/deployments/{id}/stream
GET /api/containers/{id}/logs/stream
```

---

## 29. Frontend

Stack:

- Next.js
- React
- TypeScript
- Tailwind CSS
- TanStack Query

Organización por features:

```text
features/
├── dashboard/
├── projects/
├── containers/
├── logs/
├── deployments/
├── alerts/
├── activity/
└── auth/
```

---

## 30. Navegación

```text
/
├── Dashboard
├── Projects
│   └── [project]
│       ├── Overview
│       ├── Services
│       ├── Logs
│       ├── Deployments
│       └── Alerts
├── Activity
└── Settings
```

---

## 31. Seguridad

Spring Security.

Inicialmente puede existir un administrador único. Posteriormente:

- múltiples usuarios
- roles
- OAuth/OIDC

Roles iniciales:

```text
ADMIN
VIEWER
```

ADMIN puede desplegar, rollback y cambiar configuración. VIEWER puede consultar logs, métricas y actividad.

### Operaciones peligrosas

No permitir:

```text
arbitrary shell command
arbitrary docker command
arbitrary file access
```

Todas las operaciones deben proceder de una allowlist:

```text
deploy
rollback
restart service
stop service
start service
```

Cada operación comprueba autenticación, autorización, configuración del proyecto y auditoría.

---

## 32. Auditoría

Registrar como mínimo:

```text
LOGIN
DEPLOY
ROLLBACK
SERVICE_RESTART
CONFIG_CHANGE
ALERT_ACKNOWLEDGE
```

Modelo:

```text
AuditEvent

id
userId
action
projectId
serviceId
timestamp
ip
metadata
```

No almacenar secretos.

---

## 33. Secretos

Nunca guardar passwords, API keys, tokens o private keys en:

- `nexus.yml`
- PostgreSQL
- logs
- frontend

Usar variables de entorno/secretos del servidor.

---

## 34. Self-monitoring

Nexus comparte infraestructura con las aplicaciones que administra.

Limitación inevitable:

```text
Nexus cae
↓
Nexus no puede mostrar que Nexus cayó
```

V1 acepta esta limitación.

Posteriormente se puede usar:

- watchdog externo
- healthcheck Docker
- Uptime Kuma externo
- segundo servidor
- Agent independiente

---

## 35. Recursos del VPS

Para un VPS de 4 CPU, 8 GB RAM y 100 GB SSD, mantener la arquitectura ligera.

Inicialmente:

```text
Nginx
Next.js
Spring Boot
PostgreSQL
```

Opcional:

```text
Redis
```

No instalar inicialmente:

```text
Kafka
Elasticsearch
Kubernetes
Prometheus + Grafana
```

salvo que una funcionalidad posterior lo justifique.

---

## 36. Retención

Nexus no debe convertirse en un almacén infinito de logs.

Raw logs → Docker.

Nexus → fingerprints, metadata, eventos y resumen de deployments.

Retenciones iniciales sugeridas:

```text
deployment output: 30 días
activity: 30 días
fingerprints: 90 días
```

Hacerlas configurables.

---

## 37. Testing

### Unit tests

Testear:

- manifest parser
- error normalizer
- fingerprint generator
- alert evaluator
- deployment state machine

### Integration

Usar Testcontainers para PostgreSQL y pruebas controladas de integración Docker.

### Frontend

Testear:

- project rendering
- deployment states
- log filtering
- alert states

---

## 38. Deployment state machine

No permitir transiciones arbitrarias.

```text
PENDING
   ↓
RUNNING
   ├── SUCCESS
   ├── FAILED
   └── CANCELLED
```

Un deployment terminado no vuelve a RUNNING. Un nuevo deployment crea una nueva instancia.

---

## 39. Health checks

Dos niveles:

### Docker

`HEALTHCHECK`.

### HTTP

```yaml
health:
  url: https://example.com/api/health
  timeoutSeconds: 10
```

Deployment exitoso:

```text
process exit code = 0
AND
health check = OK
```

---

## 40. Failure handling

Si falla:

```text
deployment = FAILED
```

Guardar:

- exit code
- error
- duration
- último output relevante

No hacer rollback automático en V1.

---

## 41. API errors

Formato consistente:

```json
{
  "error": {
    "code": "PROJECT_NOT_FOUND",
    "message": "Project not found",
    "timestamp": "..."
  }
}
```

Nunca devolver stack traces al frontend.

---

## 42. Observabilidad de Nexus

Spring Boot Actuator:

```text
/actuator/health
```

Nexus debe tener health, readiness y liveness. Docker Compose debe usar healthchecks.

---

## 43. Configuración

Separar configuración de desarrollo y producción.

Secretos mediante environment variables, por ejemplo:

```text
DATABASE_URL
DATABASE_USERNAME
DATABASE_PASSWORD
NEXUS_ADMIN_PASSWORD
```

Nunca commitearlos.

---

## 44. Documentación

README mínimo:

```text
What is Nexus?
Architecture
Features
Screenshots
Local development
Deployment
Project manifest
Security model
Roadmap
```

Incluir diagrama de arquitectura.

---

## 45. Portfolio

Título:

**Nexus**

Descripción:

> Self-hosted deployment and operations platform for Docker-based applications.

Features:

- Docker discovery
- Live logs
- Error detection
- Deployments
- Rollbacks
- Health checks
- Alerts
- Real-time activity

Stack:

```text
Java
Spring Boot
Next.js
TypeScript
PostgreSQL
Docker
SSE/WebSockets
```

El proyecto debe presentarse como infraestructura real que administra proyectos propios, no como un CRUD académico.

---

## 46. Demo recomendada

La demo debe contar una historia:

1. Entrar en Dashboard.
2. Mostrar VPS.
3. Abrir Portfolio.
4. Mostrar servicios.
5. Abrir logs.
6. Provocar un error controlado.
7. Ver error detectado.
8. Mostrar alerta.
9. Ejecutar deployment.
10. Mostrar output en tiempo real.
11. Ejecutar health check.
12. Mostrar deployment SUCCESS.
13. Mostrar timeline.

---

## 47. Roadmap

### V0 — Foundation

- Next.js
- Spring Boot
- PostgreSQL
- Docker Compose
- Nginx

Resultado: frontend → backend → PostgreSQL.

### V1 — Docker Discovery

- Docker client
- container discovery
- Compose project detection
- Nexus labels
- project grouping
- container status

### V2 — Logs

- Docker logs
- tail
- since
- search
- service filters
- realtime streaming

### V3 — Deployments

- `nexus.yml`
- manifest validation
- deployment executor
- deployment history
- live output
- health checks

### V4 — Error Detection

- error parser
- fingerprints
- aggregation
- error counts
- recent errors

### V5 — Alerts

- restart alerts
- health alerts
- memory alerts
- disk alerts
- error-rate alerts
- lifecycle

### V6 — Command Center

- ActivityEvent
- SSE
- live timeline
- deployment events
- container events
- error events
- alert events

### V7 — Rollback

- rollback scripts
- confirmation
- history
- health verification
- optional auto rollback

### V8 — GitHub integration

```text
GitHub webhook
       ↓
Nexus
       ↓
Deploy
```

Asociar repository, branch, commit, autor y mensaje.

### V9 — Notifications

- Telegram
- Discord
- Email

### V10 — Multi-server

```text
                 Nexus Control Plane
                         │
              ┌──────────┼──────────┐
              ▼          ▼          ▼
            Agent 1    Agent 2    Agent 3
              │          │          │
           Docker     Docker     Docker
```

---

## 48. Qué NO implementar inicialmente

Evitar scope creep.

No empezar con:

- Kubernetes
- multi-server
- GitHub OAuth
- Terraform
- Elasticsearch
- Kafka
- Prometheus
- Grafana
- secret vault
- marketplace
- plugins
- arbitrary terminal
- editor de archivos remoto

Primero conseguir completamente funcional:

```text
Docker
→ Projects
→ Logs
→ Errors
→ Deploy
→ Health
→ History
```

---

## 49. Vertical slice inicial

Antes de construir todo el backend:

```text
1. Spring Boot
2. Docker connection
3. GET /projects
4. Next.js project list
5. Project detail
6. Container status
7. Container logs
8. Deploy button
9. deployment script
10. live output
11. deployment history
```

Cuando este flujo end-to-end funcione, añadir alertas y análisis.

---

## 50. Orden de implementación

```text
PHASE 1
Repository + Docker Compose
        ↓
PHASE 2
Spring Boot foundation
        ↓
PHASE 3
Docker adapter
        ↓
PHASE 4
Project discovery API
        ↓
PHASE 5
Next.js dashboard
        ↓
PHASE 6
Project detail
        ↓
PHASE 7
Live logs
        ↓
PHASE 8
Manifest
        ↓
PHASE 9
Deployment executor
        ↓
PHASE 10
Deployment streaming
        ↓
PHASE 11
Deployment history
        ↓
PHASE 12
Error detection
        ↓
PHASE 13
Alerts
        ↓
PHASE 14
Activity timeline
        ↓
PHASE 15
Rollback
        ↓
PHASE 16
GitHub integration
```

---

## 51. Criterios de éxito del MVP

- [ ] Conectarse a Docker.
- [ ] Detectar contenedores.
- [ ] Agruparlos por proyecto.
- [ ] Mostrar estado.
- [ ] Mostrar métricas básicas.
- [ ] Abrir logs.
- [ ] Buscar texto en logs.
- [ ] Detectar errores básicos.
- [ ] Cargar `nexus.yml`.
- [ ] Ejecutar un deployment configurado.
- [ ] Mostrar output en tiempo real.
- [ ] Ejecutar health check.
- [ ] Almacenar historial.
- [ ] Mostrar actividad reciente.
- [ ] Proteger operaciones con autenticación.
- [ ] Registrar acciones sensibles.

---

## 52. Resultado esperado

El resultado no debe parecer:

> "He hecho un dashboard para Docker."

Debe parecer:

> "He construido una pequeña plataforma de control para la infraestructura que utilizo."

Nexus debe demostrar:

- arquitectura backend seria
- integración con infraestructura
- ejecución de procesos
- programación asíncrona/concurrente
- realtime
- persistencia
- seguridad
- observabilidad
- diseño frontend
- deployment real
- capacidad para diseñar software alrededor de un problema real

---

## 53. Definición final

Nexus es un control plane self-hosted ligero para administrar aplicaciones Docker desplegadas en infraestructura propia.

Su núcleo es:

```text
DISCOVER
   ↓
OBSERVE
   ↓
ANALYZE
   ↓
DEPLOY
   ↓
VERIFY
   ↓
ALERT
   ↓
AUDIT
```

Filosofía:

> **Know what is running. Know what went wrong. Deploy safely.**
