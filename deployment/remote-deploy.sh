#!/usr/bin/env bash
set -euo pipefail

ROOT=/opt/nexus
MANIFEST_ROOT=/srv/projects

mkdir -p "$ROOT/nginx" "$MANIFEST_ROOT"
cd "$ROOT"
chmod 700 "$ROOT"

if [[ ! -S /var/run/docker.sock ]]; then
  echo "Docker socket /var/run/docker.sock is missing" >&2
  exit 1
fi

DOCKER_GID=$(stat -c '%g' /var/run/docker.sock)

if [[ ! -f .env ]]; then
  if [[ -z "${NEXUS_ADMIN_PASSWORD:-}" ]]; then
    NEXUS_ADMIN_PASSWORD=$(openssl rand -base64 18 | tr -d '\n')
  fi
  if [[ -z "${POSTGRES_PASSWORD:-}" ]]; then
    POSTGRES_PASSWORD=$(openssl rand -base64 18 | tr -d '\n')
  fi
  umask 077
  cat > .env <<EOF
POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
NEXUS_ADMIN_USERNAME=admin
NEXUS_ADMIN_PASSWORD=${NEXUS_ADMIN_PASSWORD}
DOCKER_GID=${DOCKER_GID}
EOF
  echo "Created ${ROOT}/.env (mode 600). Read it on the server for the admin password."
fi

umask 077
cat > .env.runtime <<EOF
DOCKER_GID=${DOCKER_GID}
NEXUS_BACKEND_IMAGE=${NEXUS_BACKEND_IMAGE:?NEXUS_BACKEND_IMAGE is required}
NEXUS_FRONTEND_IMAGE=${NEXUS_FRONTEND_IMAGE:?NEXUS_FRONTEND_IMAGE is required}
NEXUS_VERSION=${NEXUS_VERSION:-unknown}
EOF

if [[ -z "${GHCR_TOKEN:-}" ]]; then
  echo "GHCR_TOKEN is required to pull images" >&2
  exit 1
fi

echo "${GHCR_TOKEN}" | docker login ghcr.io -u "${GHCR_USER:?GHCR_USER is required}" --password-stdin

docker compose --env-file .env --env-file .env.runtime pull
docker compose --env-file .env --env-file .env.runtime up -d --no-build --remove-orphans
