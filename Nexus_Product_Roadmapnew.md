# Nexus — Roadmap de evolución e integración

## Objetivo

Nexus evolucionará desde un panel de administración Docker/VPS hacia un **Personal VPS Control Plane**: una única interfaz para gestionar el ciclo de vida completo de los proyectos desplegados.

Debe cubrir:

- GitHub y código desplegado
- configuración y secretos
- despliegues
- health checks
- dominios y HTTPS
- tráfico por proyecto
- logs, errores e incidentes
- seguridad
- backups y restauración
- terminal administrativa
- actividad y auditoría
- rollback

La prioridad es **valor de uso diario y simplicidad**, sin convertir Nexus en Kubernetes, GitLab o un cloud provider completo.

## Ciclo central

```text
GitHub → Checkout → Configuración → deploy.sh → Health
   ↓                                      ↓
Domain + HTTPS ← Traffic ← Monitoring ← Security
   ↓
Alerts / Timeline / Backup / Restore / Rollback
```

El concepto central de UI debe ser **Project**.

---

# V1 — Operations

Base actual:

- Docker/projects
- deployments
- logs
- errors
- alerts
- activity
- database
- metrics
- health checks
- deployment history
- rollback

Mantener la arquitectura hexagonal existente.

# V1.5 — GitHub

## Objetivo

Conectar una cuenta GitHub y administrar desde Nexus la relación entre repositorio y proyecto desplegado.

## Funcionalidades

- OAuth/App de GitHub
- listado de repositorios accesibles
- selección de branch
- último commit remoto
- commit actualmente desplegado
- detección de diferencias
- detección de `deploy.sh`
- detección de `nexus.yml`
- crear proyecto desde repositorio
- clonar/actualizar checkout
- deploy manual
- webhook de push
- auto-deploy opcional por branch

Ejemplo:

```text
PANTRY

GitHub
  Repository: ivan/pantry
  Branch: main

Production
  Commit: a82f31c
  GitHub:  f91a82d
  Status: Update available

[Deploy]
```

Los tokens de GitHub deben almacenarse de forma segura y nunca en texto plano.

# Deployment mediante `deploy.sh`

Nexus debe ser un **orquestador**, no tener que conocer cada stack.

```text
project/
├── nexus.yml
├── deploy.sh
├── docker-compose.yml
└── ...
```

Ejemplo:

```yaml
project:
  id: pantry
  workingDirectory: /srv/projects/pantry

deployment:
  command: ./deploy.sh

health:
  url: http://localhost:3000/api/health
```

Nexus debe:

1. validar el proyecto
2. preparar checkout
3. ejecutar el comando permitido
4. capturar stdout/stderr
5. registrar duración
6. registrar commit
7. ejecutar health check
8. determinar éxito/fallo
9. registrar actividad
10. publicar progreso SSE
11. generar resumen
12. permitir rollback

No debe existir una entrada arbitraria desde UI que permita ejecutar cualquier comando como deployment.

# V1.6 — Domains + HTTPS

## Objetivo

Gestionar:

```text
domain → project → service
```

Ejemplo:

```text
pantry.example.com
        ↓
     pantry
        ↓
  frontend:3000
```

Usar **Caddy** como componente especializado para:

- reverse proxy
- TLS
- certificados
- renovación automática
- HTTPS

Nexus administra la configuración; Caddy ejecuta proxy/TLS.

## Funcionalidades

- añadir/cambiar/eliminar dominio
- comprobar DNS
- comprobar HTTPS
- estado del certificado
- asociación con proyecto
- varios dominios por proyecto

Inicialmente el DNS puede seguir gestionándose mediante Cloudflare u otro proveedor.

# V1.7 — Traffic Analytics

## Objetivo

Analizar el tráfico **separado por proyecto/dominio**.

Caddy será el punto de entrada y Nexus consumirá eventos/estadísticas desde una capa de observabilidad adecuada.

Datos posibles:

- requests
- requests/min
- bandwidth
- status codes
- latencia
- endpoints
- errores 4xx/5xx
- user-agent, opcional
- IP origen solo si existe una necesidad clara y con retención mínima

Ejemplo:

```text
PANTRY

Traffic · 24h

Requests       18,432
Bandwidth      2.8 GB
Peak           213 req/min

2xx            18,102
3xx               182
4xx               137
5xx                11
```

## Persistencia

No almacenar logs HTTP completos indefinidamente.

Preferir:

```text
raw events → aggregation → hourly/daily metrics → retention
```

Ejemplo:

```text
traffic_hourly
- project_id
- domain_id
- bucket_start
- requests
- bytes_in
- bytes_out
- status_2xx
- status_3xx
- status_4xx
- status_5xx
- latency_avg
- latency_p95
```

Retención configurable.

## Correlación con deployments

Una capacidad especialmente útil:

```text
Deployment #42
commit a82f31c
14:32

Después del deployment:
5xx       +0.8%
p95       +120 ms
traffic   +34%
```

Esto debe alimentar la timeline de incidentes.

# V1.8 — Security

## Principio

El LLM **no debe ser el detector primario**.

Primero utilizar herramientas especializadas:

- Trivy
- OSV
- npm audit
- pip-audit
- Semgrep
- GitHub Dependabot/Security Advisories
- análisis de imágenes Docker

Después usar un LLM para contextualizar y resumir.

```text
Project
   ↓
Security scanner
   ↓
Findings
   ↓
Normalization
   ↓
Fingerprint
   ↓
LLM contextualization
   ↓
Alert
```

Ejemplo:

```text
Dependency vulnerable

Severity: High
Package: example-package
Installed: 2.1.0
Fixed: 2.1.4

Context:
La dependencia está presente en la imagen de producción,
pero el componente vulnerable no parece estar expuesto
directamente por el servicio.
```

El LLM no debe modificar código ni ejecutar acciones automáticamente a partir del análisis sin una política explícita.

# V1.9 — Web Terminal

## Objetivo

Terminal administrativa dentro de Nexus.

Dos modos:

### VPS

```text
root@nexus:/#
```

### Proyecto/contenedor

```text
root@pantry:/app#
```

Arquitectura:

```text
Browser
   │
 WebSocket
   ↓
Nexus
   │
  PTY
   ↓
Shell
```

Soportar:

- stdin
- stdout/stderr
- resize
- Ctrl+C
- sesiones
- cierre de sesión
- timeout
- terminal por contenedor

## Seguridad

Es una superficie crítica porque Docker socket y root tienen privilegios muy altos.

Requisitos:

- autenticación fuerte
- autorización específica
- timeout
- cierre al logout
- protección WebSocket
- auditoría
- opción de desactivar Terminal
- no almacenar secretos escritos
- registrar inicio/fin de sesión
- evitar almacenar indiscriminadamente todo el contenido

# V2 — Backups & Restore

## Objetivo

No solo administrar el VPS cuando funciona, sino **poder recuperar los proyectos cuando algo falla**.

## Qué respaldar

### Código

GitHub puede ser la fuente de verdad.

Guardar:

- commit/version desplegado
- configuración
- `nexus.yml`
- metadata

### Bases de datos

Prioridad alta.

Ejemplos:

- PostgreSQL dump
- MySQL/MariaDB dump

Preferir dumps consistentes y restaurables frente a copiar archivos de una DB en ejecución.

### Volúmenes

Respaldar únicamente los volúmenes persistentes declarados.

```yaml
backup:
  volumes:
    - pantry_uploads
    - pantry_data
```

### Configuración Nexus

Respaldar:

- proyectos
- dominios
- schedules
- metadata
- referencias a repositorios
- configuración de backups

### Secrets

Los secretos deben almacenarse cifrados y nunca convertirse en texto plano dentro del backup.

## Arquitectura

```text
Nexus Scheduler
       │
       ↓
Backup Job
       │
 ┌─────┴──────────┐
 │                │
DB dump       Volume backup
 │                │
 └──────┬─────────┘
        ↓
   Backup artifact
        ↓
 Storage target
```

Inicialmente:

- almacenamiento local adicional
- almacenamiento remoto compatible con S3
- almacenamiento externo adicional opcional

No depender exclusivamente del mismo disco del VPS para backups críticos.

## Políticas

```text
Daily
Retention: 7

Weekly
Retention: 4

Monthly
Retention: 3
```

Configurables por proyecto.

## Restore

Debe ser explícito y visible:

```text
PANTRY

Backup
2026-09-18 03:00
Database + uploads

[Restore]
```

Antes:

```text
WARNING

This operation will replace:
- PostgreSQL data
- pantry_uploads

Current state will be backed up first.

[Cancel] [Continue]
```

Flujo:

```text
Current state
      ↓
Safety backup
      ↓
Stop affected services
      ↓
Restore DB
      ↓
Restore volumes
      ↓
Start services
      ↓
Health checks
      ↓
Verify
      ↓
Activity event
```

Nunca hacer un restore destructivo sin crear previamente un punto de recuperación cuando sea técnicamente posible.

# Disaster Recovery

Evolución posterior:

```text
Nexus
  ↓
Export configuration
  ↓
New VPS
  ↓
Install Nexus
  ↓
Import
  ↓
Connect GitHub
  ↓
Restore backups
  ↓
Deploy projects
```

Nexus no debe depender exclusivamente del VPS que administra.

La configuración exportada no debe contener secretos en claro.

# Unified Project Health

Cada proyecto debe tener una vista consolidada:

```text
PANTRY

● Application       Healthy
● PostgreSQL        Healthy
● HTTPS             Healthy
● Domain            Healthy
● Deployment        Healthy
● Traffic           Normal
● Security          2 warnings
● Backup            Healthy

Last deployment
commit a82f31c
2h ago
```

Debe ser una de las principales pantallas del producto.

# Incident Timeline

Unificar acontecimientos:

```text
14:32  Deployment #42 started
14:33  Deployment #42 completed
14:35  5xx rate increased
14:36  Health check degraded
14:37  Alert created
14:40  Rollback #43 started
14:41  Health restored
```

Relacionar:

- deployments
- errores
- alertas
- security findings
- cambios de dominio
- backups/restores
- actividad administrativa
- health degradado
- cambios relevantes de tráfico

Usar fingerprints para evitar alertas duplicadas.

# Environment & Secrets

Por proyecto:

```text
Environment

NODE_ENV       production
DATABASE_URL   ••••••••••
API_KEY        ••••••••••
```

Funciones:

- añadir
- modificar
- eliminar
- rotar
- comparar cambios
- aplicar al deployment

Los secretos deben:

- cifrarse en reposo
- no aparecer en logs
- no aparecer en errores
- no enviarse al frontend en claro salvo necesidad
- protegerse criptográficamente en backups

# Activity & Audit

Operaciones importantes:

```text
GitHub connected
Project created
Deployment started
Deployment completed
Domain added
Certificate renewed
Security scan completed
Backup created
Backup restored
Terminal session started
Project configuration changed
```

Distinguir:

### Activity

Información operacional para el usuario.

### Audit

Información de seguridad/administración con mayor rigor de conservación.

# Arquitectura backend

Mantener la arquitectura hexagonal existente.

## Domain

Solo conceptos de negocio:

```text
Project
Deployment
Domain
TrafficSnapshot
SecurityFinding
Backup
BackupPolicy
Activity
AuditEvent
```

Sin:

- JPA
- Docker SDK
- GitHub SDK
- Caddy SDK
- WebSocket
- Spring Data
- infraestructura

## Application

Casos de uso y ports:

```text
GitHubRepository
GitHubClient

ProjectStore
DeploymentStore

DomainStore
CertificateManager

TrafficReader
TrafficStore

SecurityScanner
SecurityFindingStore

BackupProvider
BackupStore
RestoreManager

TerminalSessionManager

SecretStore

ActivityPublisher
AuditRecorder
```

Los ports pertenecen a application.

## Infrastructure

Implementaciones:

```text
GitHub adapter
Docker adapter
Caddy adapter
PostgreSQL/JPA adapter
Traffic/log adapter
Trivy adapter
Backup storage adapter
PTY/process adapter
Secret encryption adapter
```

## Interfaces

REST/SSE/WebSocket/controllers.

Mantener las reglas actuales de `HexagonalArchitectureTest`.

# Módulos recomendados

No crear todo de golpe.

```text
domain/
  project/
  deployment/
  domain/
  traffic/
  security/
  backup/
  activity/
  alert/

application/
  project/
  deployment/
  github/
  domain/
  traffic/
  security/
  backup/
  terminal/
  secrets/
  activity/

infrastructure/
  github/
  docker/
  caddy/
  persistence/
  traffic/
  security/
  backup/
  terminal/
  secrets/

interfaces/
  rest/
  sse/
  websocket/
```

La estructura exacta puede adaptarse al código existente.

# Scheduler

Extender el scheduler actual para:

```text
Security scan
Backup
Traffic aggregation
Cleanup
Certificate checks
GitHub synchronization
```

Ejemplo:

```text
03:00 backup
04:00 security scan
every 5 min health
every 15 min GitHub sync
continuous traffic ingestion
```

Intervalos configurables.

# Alertas

Fuentes:

- deployment failed
- health degraded
- 5xx spike
- traffic anomaly
- security finding
- backup failed
- backup stale
- certificate problem
- disk almost full
- memory pressure
- container stopped

Ejemplo:

```text
CRITICAL
PANTRY

PostgreSQL backup failed.

Last successful backup:
2026-09-17 03:00

[View details]
```

Un backup fallido debe poder generar una alerta importante porque implica pérdida de capacidad de recuperación.

# Orden de implementación

## Fase A — Consolidación

- estabilizar arquitectura
- completar tests
- deployments
- health checks
- activity/audit
- errores
- permisos

## Fase B — GitHub

1. OAuth/App
2. repositorios
3. branch
4. commit status
5. checkout
6. `deploy.sh`
7. deploy manual
8. webhook
9. auto-deploy opcional

## Fase C — Domains

1. modelo Domain
2. Caddy
3. reverse proxy
4. TLS
5. health
6. UI

## Fase D — Traffic

1. ingestion
2. agregación
3. persistencia
4. dashboard
5. status/latency
6. correlación con deployment

## Fase E — Security

1. scanner abstraction
2. Trivy/OSV/etc.
3. findings normalizados
4. fingerprints
5. alerts
6. LLM contextualization

## Fase F — Backups

1. Backup model
2. policies
3. DB dumps
4. volume backup
5. storage abstraction
6. scheduler
7. retention
8. restore
9. safety backup
10. alerts

## Fase G — Terminal

1. PTY
2. WebSocket
3. terminal VPS
4. terminal container
5. autenticación/autorización
6. timeout
7. audit

## Fase H — Automation

```text
GitHub push
     ↓
Checkout
     ↓
Security scan
     ↓
Build
     ↓
Deploy
     ↓
Health
     ↓
Traffic monitoring
     ↓
Backup
     ↓
Alerts
```

# Principios de diseño

### Nexus orquesta; las herramientas especializadas ejecutan.

GitHub → código

Docker → containers

Caddy → proxy/TLS

Trivy/OSV → seguridad

PostgreSQL → datos

S3 → almacenamiento

PTY/shell → terminal

Nexus → coordinación, estado, UI, historial y automatización.

### No duplicar herramientas innecesariamente.

### No almacenar datos que puedan regenerarse.

### No convertir Nexus en un sistema distribuido.

### Priorizar operaciones reversibles.

### Todo cambio importante debe dejar trazabilidad.

### Los secretos deben tratarse como secretos desde el diseño.

### El VPS tiene recursos limitados.

Con 8 GB RAM / 4 cores / 100 GB SSD, cada componente adicional debe justificar su coste.

# Resultado esperado

Al abrir Nexus debería ser posible responder rápidamente:

```text
¿Qué tengo desplegado?
        ↓
¿Está funcionando?
        ↓
¿Hay actualizaciones?
        ↓
¿Hay vulnerabilidades?
        ↓
¿Cuánto tráfico recibe?
        ↓
¿El dominio funciona?
        ↓
¿Cuándo fue el último backup?
        ↓
¿Puedo recuperarlo?
        ↓
¿Qué ha ocurrido recientemente?
        ↓
¿Necesito entrar por terminal?
```

Nexus debe ser el lugar desde el que administrar diariamente todo el VPS, reduciendo la necesidad de entrar manualmente por SSH, Docker CLI, GitHub, Caddy u otras herramientas.

# Visión final

```text
                         NEXUS
                Personal VPS Control Plane
                           │
       ┌───────────────────┼───────────────────┐
       │                   │                   │
    PROJECTS             SERVER            SECURITY
       │                   │                   │
   ┌───┼────┐          ┌───┼────┐          ┌───┼────┐
   │   │    │          │   │    │          │   │    │
 Git Deploy Domain   Metrics Traffic   Scan Alerts LLM
   │   │    │          │   │    │          │
   └───┴────┴──────────┴───┴────┴──────────┘
                           │
                      OPERATIONS
                           │
                 ┌─────────┼─────────┐
                 │         │         │
              Backup    Restore   Terminal
                 │         │         │
                 └─────────┴─────────┘
                           │
                     Activity / Audit
```

El objetivo no es tener cientos de opciones. Es que **Nexus sea una herramienta que quieras abrir cada día porque reduce el trabajo operativo del VPS**, además de servir como pieza técnica sólida para el portfolio.
