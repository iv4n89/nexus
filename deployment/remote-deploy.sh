#!/usr/bin/env bash
set -euo pipefail

ROOT=/opt/nexus
MANIFEST_ROOT=/srv/projects

mkdir -p "$ROOT/caddy" "$MANIFEST_ROOT"
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

CADDY_SRC="${ROOT}/caddy/nexus.caddy"
if [[ -f "${CADDY_SRC}" ]]; then
  installed=
  for root in /opt/ava-assistant /opt/ava; do
    if [[ -f "${root}/docker-compose.tls.yml" || -d "${root}/deploy/caddy-optional" ]]; then
      mkdir -p "${root}/deploy/caddy-optional"
      cp "${CADDY_SRC}" "${root}/deploy/caddy-optional/nexus.caddy"
      echo "Installed ${root}/deploy/caddy-optional/nexus.caddy"
      echo "From ${root}: docker compose -f docker-compose.prod.yml -f docker-compose.tls.yml up -d"
      installed=1
      break
    fi
  done
  if [[ -z "${installed}" ]]; then
    echo "Copy ${CADDY_SRC} to Ava deploy/caddy-optional/nexus.caddy and: docker compose -f docker-compose.prod.yml -f docker-compose.tls.yml up -d"
  fi
fi
