# Nexus

A self-hosted control plane for the Docker applications that live on your own VPS. Nexus discovers containers, groups them into projects, streams logs, runs allowlisted deploys and rollbacks, and records alerts, activity, and audit — without Kubernetes, a plugin marketplace, or an arbitrary shell.

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

## Deployment

Production stack: Nginx + Next.js standalone + Spring Boot + PostgreSQL. The backend mounts the **Docker socket** (host-root equivalent) and bind-mounts `/srv/projects` at the **same host path** so deploy scripts see the paths in `nexus.yml`.

VPS target: `root@161.97.116.30`, stack directory `/opt/nexus`. Public host **nexus-project.duckdns.org** (same edge nginx as `ava-assistant.duckdns.org`). Postgres, Next, and the API bind to localhost.

### Continuous deploy

A push (or manual run) on `main` builds images, pushes them to GHCR (`ghcr.io/<owner>/nexus/backend` and `.../frontend`), rsyncs compose to `/opt/nexus`, and runs `deployment/remote-deploy.sh`. That script creates `/opt/nexus` and `/srv/projects` if needed, reads `DOCKER_GID` from the host socket, and runs `docker compose up -d --no-build`.

Add this repository secret before the first deploy:

- **`NEXUS_SSH_PRIVATE_KEY`** — private key whose public half is in `root@161.97.116.30:~/.ssh/authorized_keys`

Optional: **`NEXUS_ADMIN_PASSWORD`**. If omitted, the first run writes a random admin password to `/opt/nexus/.env` on the server (mode 600). Later deploys do not overwrite that file.

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

Do not expose this stack on the public internet without authentication. The **edge** nginx (ava-assistant) proxies `/api/` (buffering off, 3600s read timeout for SSE) and `/actuator/health` only; other actuator endpoints stay internal. Vhost: `deployment/nginx/nexus-project.duckdns.org.conf`.

Create the DuckDNS name `nexus-project` pointing at `161.97.116.30`. After HTTP works, add TLS the same way as ava-assistant (`certbot -d nexus-project.duckdns.org`). Reload that nginx after copying the vhost.

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

## Roadmap

V0–V7 in this tree: foundation, discovery, logs, deployments, error fingerprints, alerts, activity, rollback.

Later (not in MVP):

- **V8** GitHub webhooks (repo, branch, commit on each deploy)
- **V9** Notifications (Telegram, Discord, email)
- **V10** Multi-server agents

Out of scope for now: Kubernetes, Terraform, Elasticsearch, Kafka, Prometheus/Grafana, Redis, secret vault, marketplace, plugins, arbitrary terminal, remote file editor.
