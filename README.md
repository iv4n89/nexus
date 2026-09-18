# Nexus

A self-hosted control plane for the Docker applications that live on your own VPS. Nexus discovers containers, groups them into projects, streams logs, runs allowlisted deploys and rollbacks, and records alerts, activity, and audit — without Kubernetes, a plugin marketplace, or an arbitrary shell.

> **Danger (optional VPS / container terminal):** When `nexus.terminal.enabled=true`, Nexus exposes ADMIN-only interactive shells:
> - `ws://…/api/terminal/vps` — host `/bin/bash`
> - `ws://…/ws/terminal/projects/{id}/containers/{containerId}` (also `/api/terminal/projects/...`) — `docker exec -i` into a project-labeled container
>
> Sessions are audited (start/end), origin-checked, and time out after 15 minutes; transcripts are **not** stored. Leave disabled unless you accept shell risk.

## What is Nexus?

Nexus sits next to the workloads it manages. Docker Engine is the source of truth for running containers. PostgreSQL stores operational history (deployments, fingerprints, alerts, activity, audit). Project manifests (`nexus.yml`) declare the desired deploy/rollback commands and health URL.

It is not a generic Portainer replacement. The UI is a black-and-white operations console: dashboard, project, logs, deployments, activity, settings.

## Architecture

```text
                    INTERNET
                       │
                       ▼
                  ┌─────────┐
                  │  NGINX  │
                  └────┬────┘
                       │
          /             \ /api /actuator/health
          ▼             ▼
     Next.js        Spring Boot
     Frontend           API
                        │
            ┌───────────┼────────────┐
            ▼           ▼            ▼
         Docker      PostgreSQL   Scheduler
         Engine
```

Same-origin `/api` in production (Nginx). Local `next dev` rewrites `/api/*` to `http://localhost:8080`. Realtime uses MVC `SseEmitter`, not WebFlux. See [docs/architecture.md](docs/architecture.md).

## Features

- Docker discovery grouped by `nexus.project` label (Compose project / name fallback)
- Project and container views with live status and basic host/container metrics
- Log tail, search, and SSE stream
- Error fingerprints and scheduled operational alerts
- Allowlisted deploy and rollback from `nexus.yml` (script + health check, live output)
- Service restart, deployment history, live activity timeline
- Session cookie auth (`ADMIN` / `VIEWER`) with CSRF
- In-app project database explorer and query (Postgres, MySQL, Mongo)
- Read-only settings (version and retention)

## Local development

Prerequisites: Java 21, Node 20+, Docker Engine, Maven wrapper in `backend/`.

1. Start Postgres:

```bash
docker compose up -d
```

2. Optional lab target (two labeled containers):

```bash
docker compose -f projects/lab/docker-compose.yml up -d
```

3. Backend (`NEXUS_MANIFEST_ROOT` must contain the lab directory):

```bash
cd backend
NEXUS_ADMIN_USERNAME=admin \
NEXUS_ADMIN_PASSWORD=changeme \
NEXUS_MANIFEST_ROOT=/home/ibetanzos/dev/nexus_project/projects \
./mvnw spring-boot:run
```

4. Frontend:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:3000 and sign in as `admin` / `changeme`. Copy `.env.example` for the local variable set. Tests: `cd backend && ./mvnw test`.

## Schedulers (H4)

Spring 6-field crons (seconds first). Defaults:

| Job | Property | Default | Flag |
|-----|----------|---------|------|
| Retention cleanup | `nexus.retention.cron` | `0 0 3 * * *` (03:00) | always on |
| Backups | `nexus.backup.cron` | `0 0 3 * * *` (03:00) | `nexus.backup.scheduler-enabled` |
| Security (Trivy) | `nexus.security.trivy.cron` | `0 0 4 * * *` (04:00) | `nexus.security.trivy.scheduler-enabled` |
| GitHub sync stub | `nexus.github.sync-cron` | `0 */15 * * * *` (every 15m) | `nexus.github.sync-enabled` |

Deploy pipeline orchestration (`RunDeployPipeline`) is controlled by `nexus.automation.*` flags (security gate, traffic watch stub, post-deploy backup) — all **OFF** by default.

## Deployment

Production stack: Nginx + Next.js standalone + Spring Boot + PostgreSQL. The backend mounts the **Docker socket** (host-root equivalent) and bind-mounts `/srv/projects` at the **same host path** so deploy scripts see the paths in `nexus.yml`.

VPS target: `root@161.97.116.30`, stack directory `/opt/nexus`. Public host **0nexus.duckdns.org** (same Caddy as `ava-assistant.duckdns.org`). Postgres stays on localhost; the API (`:8080`) and Next (`:3000`) listen on the host so Caddy in Docker can reach `host.docker.internal`. `remote-deploy.sh` allows Docker bridges (`172.16.0.0/12`) to host `:8080` because UFW blocks host-network processes but not docker-published `:3000`.

### Continuous deploy

A push (or manual run) on `main` builds images, pushes them to GHCR (`ghcr.io/<owner>/nexus/backend` and `.../frontend`), rsyncs compose to `/opt/nexus`, and runs `deployment/remote-deploy.sh`. That script creates `/opt/nexus` and `/srv/projects` if needed, reads `DOCKER_GID` from the host socket, and runs `docker compose up -d --no-build`.

Add this repository secret before the first deploy:

- **`NEXUS_SSH_PRIVATE_KEY`** — private key whose public half is in `root@161.97.116.30:~/.ssh/authorized_keys`

Optional: **`NEXUS_ADMIN_PASSWORD`**. If omitted, the first run writes a random admin password to `/opt/nexus/.env` on the server (mode 600). Later deploys do not overwrite that file. Production login is `admin` plus that value — not the local `changeme`.

```bash
gh secret set NEXUS_SSH_PRIVATE_KEY < deploy_key
```

Then **Actions → Deploy → Run workflow**, or merge to `main`.

### Manual compose

```bash
cp deployment/.env.example deployment/.env
# set NEXUS_ADMIN_PASSWORD and DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)
cd deployment
docker compose --env-file .env up -d --build
```

Do not expose this stack on the public internet without authentication. Ava's **Caddy** terminates TLS and proxies `/api/` (flush, 3600s read timeout for SSE) and `/actuator/health` to `:8080`, everything else to `:3000`. Site file: `deployment/caddy/nexus.caddy`.

Create the DuckDNS name `0nexus` pointing at `161.97.116.30`. Copy the site file into Ava:

```bash
cp /opt/nexus/caddy/nexus.caddy deploy/caddy-optional/nexus.caddy
# from the Ava project directory:
docker compose -f docker-compose.prod.yml -f docker-compose.tls.yml up -d
```

Caddy must resolve `host.docker.internal` (`extra_hosts: host.docker.internal:host-gateway` on Linux). Then open `https://0nexus.duckdns.org`. After copying `nexus.caddy`, recreate the Caddy container so gzip/SSE and `Alt-Svc` changes load. If `/api/auth/csrf` returns **502**, Caddy cannot reach host `:8080` (login HTML can still load from `:3000`). On the VPS:

```bash
ufw allow from 172.16.0.0/12 to any port 8080 proto tcp comment 'nexus-api-from-caddy'
ss -ltnp | grep 8080
docker run --rm --network bridge --add-host=host.docker.internal:host-gateway \
  --entrypoint curl ghcr.io/iv4n89/nexus/backend:latest \
  -fsS --max-time 5 http://host.docker.internal:8080/actuator/health
```

Production login is `admin` plus `NEXUS_ADMIN_PASSWORD` in `/opt/nexus/.env`, not `changeme`.

## Project manifest

Place `nexus.yml` under `NEXUS_MANIFEST_ROOT`. Commands are relative, allowlisted (no `&&`, pipes, `$()`, or backticks). `workingDirectory` must stay under that root.

```yaml
project:
  id: lab
  name: Lab
  workingDirectory: /srv/projects/lab

services:
  - api
  - web

deployment:
  command: ./deploy.sh

rollback:
  command: ./rollback.sh

health:
  url: http://127.0.0.1:18080
  timeoutSeconds: 5
```

Label Compose services with `nexus.project` and `nexus.service`. Local stand-in: `projects/lab/`.

## Security model

- First admin is bootstrapped from `NEXUS_ADMIN_USERNAME` / `NEXUS_ADMIN_PASSWORD` (required when `SPRING_PROFILES_ACTIVE=prod`)
- `ADMIN` can deploy, rollback, restart, and acknowledge alerts; `VIEWER` is read-only
- No `/execute` endpoint and no arbitrary Docker or shell
- Secrets stay in environment variables — never in `nexus.yml`, the UI, or git
- Sensitive actions are audited
- Docker socket access is privileged; prefer `group_add` with the host docker gid over running as root

## V1 readiness (operations baseline)

Product direction: [Nexus_Product_Roadmapnew.md](Nexus_Product_Roadmapnew.md) (Personal VPS Control Plane). **V1 Operations is the current shipped baseline.** Answers to the roadmap’s daily questions *today*:

| Question | V1 answer |
|----------|-----------|
| What is deployed? | Project discovery + services from Docker labels / manifests |
| Is it working? | Container status, health URL after deploy, operational alerts |
| Are there updates? | Not yet — needs GitHub integration (V1.5) |
| Any vulnerabilities? | Not yet — needs security scanners (V1.8) |
| How much traffic? | Not yet — needs Caddy traffic analytics (V1.7) |
| Does the domain work? | External Caddy only for Nexus itself; per-project domains are V1.6 |
| Last backup? | Not yet — backups & restore are V2 |
| Can I recover? | Rollback of allowlisted scripts only; full restore is V2 |
| What happened recently? | Activity timeline + audit + alerts + deployment history |
| Do I need a terminal? | No in-app terminal yet (V1.9); SSH/Docker CLI still required for deep ops |

Deferred from this baseline (see roadmap phases B–H): GitHub, secrets vault UI, managed domains, traffic, scanners, backups, web terminal, multi-host.

`VIEWER` exists in Spring Security as read-only; there is no user-provisioning UI yet (admin bootstrap only).

### Post-deploy smoke checklist

After a `main` Deploy workflow succeeds:

1. `GET https://0nexus.duckdns.org/actuator/health` → `UP`
2. `GET /login` → 200; unauthenticated `/api/projects` → 401
3. Sign in as `admin`; dashboard loads projects, alerts, activity
4. Open a project → services list; optional deploy/rollback only on a lab project
5. Deployment history + SSE stream (`event: log`) for an existing deployment
6. Activity SSE `/api/events/stream` receives heartbeats / events

## Roadmap

Shipped in tree (V1): foundation, discovery, logs, deployments, fingerprints, alerts, activity, audit, rollback, database manager, hexagonal application boundary.

Next (execution order in the product roadmap):

- **V1.5** GitHub (OAuth, checkout, commit status, webhook, optional auto-deploy)
- **Environment & secrets** (encrypted store, inject at deploy)
- **V1.6** Domains + HTTPS via Caddy
- **V1.7** Traffic analytics
- **V1.8** Security scanners (+ optional LLM context)
- **V2** Backups & restore
- **V1.9** Web terminal
- **Automation** — end-to-end pipeline + unified project health

Out of scope for now: Kubernetes, Terraform, Elasticsearch, Kafka, Prometheus/Grafana, Redis, marketplace, plugins, multi-server agents, disaster-recovery export across VPS hosts.
