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

dump_backend_diagnostics() {
  if [[ ! -f docker-compose.yml || ! -f .env.runtime ]]; then
    echo "Skipping backend diagnostics (compose not initialized yet)"
    return 0
  fi
  echo "=== backend diagnostics (pre-pull) ==="
  docker compose --env-file .env --env-file .env.runtime ps || true
  docker compose --env-file .env --env-file .env.runtime logs --no-color --tail 2000 backend > /tmp/nexus-backend.log 2>/dev/null || true
  if [[ ! -s /tmp/nexus-backend.log ]]; then
    echo "No backend logs yet"
    return 0
  fi
  redact() {
    sed -E \
      -e 's/[Pp]assword[=:][^[:space:]]+/password=<redacted>/g' \
      -e 's/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}/<UUID>/g' \
      -e 's/\b([0-9]{1,3}\.){3}[0-9]{1,3}\b/<IP>/g'
  }
  echo -n 'ERROR: '; grep -c ' ERROR ' /tmp/nexus-backend.log || true
  echo -n 'WARN: '; grep -c ' WARN ' /tmp/nexus-backend.log || true
  echo -n 'Unhandled exception: '; grep -c 'Unhandled exception' /tmp/nexus-backend.log || true
  echo -n 'Broken pipe: '; grep -ci 'broken pipe' /tmp/nexus-backend.log || true
  echo '=== unique ERROR lines ==='
  grep ' ERROR ' /tmp/nexus-backend.log | redact | sed -E 's/^[0-9T:.,+-Z]+ //' | sort | uniq -c | sort -nr | head -40 || true
  echo '=== unique WARN lines ==='
  grep ' WARN ' /tmp/nexus-backend.log | redact | sed -E 's/^[0-9T:.,+-Z]+ //' | sort | uniq -c | sort -nr | head -40 || true
  echo '=== last 60 ERROR/WARN/Exception lines ==='
  grep -E ' ERROR | WARN |Exception' /tmp/nexus-backend.log | tail -n 60 | redact || true
  echo '=== fingerprints by count ==='
  docker compose --env-file .env --env-file .env.runtime exec -T postgres \
    psql -U nexus -d nexus -P pager=off -c \
    "select project_id, service_id, count, left(regexp_replace(sample_message, E'[\n\r]+', ' ', 'g'), 140) as sample, last_seen from log_error_fingerprints order by count desc limit 40;" \
    || true
}

dump_backend_diagnostics

docker compose --env-file .env --env-file .env.runtime pull
docker compose --env-file .env --env-file .env.runtime up -d --no-build --remove-orphans --wait

echo "Removing leftover nexus nginx (Ava Caddy owns :80/:443)"
docker ps -aq \
  --filter label=com.docker.compose.project=nexus \
  --filter label=com.docker.compose.service=nginx \
  | xargs -r docker rm -f || true

# Caddy (Docker) reaches host-networked Next :3000 and Spring :8080 via
# host.docker.internal. Both hit INPUT, so allow the Caddy container IPv4
# plus its bridge gateways. Probe from Caddy's network namespace so the
# source IP matches the rule. Do not drop 172.16.0.0/12 until that probe
# succeeds — a sibling container on the same bridge is a different IP.
caddy_container_id() {
  docker ps -q --filter label=com.docker.compose.service=caddy | head -n1
}

caddy_ipv4s() {
  local cid="${1:-}"
  if [[ -z "${cid}" ]]; then
    return 0
  fi
  docker inspect -f '{{range .NetworkSettings.Networks}}{{if .IPAddress}}{{.IPAddress}} {{end}}{{end}}' "${cid}"
}

caddy_gateways() {
  local cid="${1:-}"
  if [[ -z "${cid}" ]]; then
    return 0
  fi
  docker inspect -f '{{range .NetworkSettings.Networks}}{{if .Gateway}}{{.Gateway}} {{end}}{{end}}' "${cid}"
}

delete_host_api_source() {
  local src="$1"
  local port="$2"
  if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q '^Status: active'; then
    ufw delete allow from "${src}" to any port "${port}" proto tcp >/dev/null 2>&1 || true
  fi
  if command -v iptables >/dev/null 2>&1; then
    while iptables -C INPUT -p tcp -s "${src}" --dport "${port}" -j ACCEPT 2>/dev/null; do
      iptables -D INPUT -p tcp -s "${src}" --dport "${port}" -j ACCEPT || break
    done
  fi
}

allow_host_api_source() {
  local src="$1"
  local port="$2"
  if command -v ufw >/dev/null 2>&1 && ufw status 2>/dev/null | grep -q '^Status: active'; then
    ufw allow from "${src}" to any port "${port}" proto tcp comment "nexus-from-caddy-${port}" || true
  fi
  if command -v iptables >/dev/null 2>&1; then
    iptables -C INPUT -p tcp -s "${src}" --dport "${port}" -j ACCEPT 2>/dev/null \
      || iptables -I INPUT 1 -p tcp -s "${src}" --dport "${port}" -j ACCEPT
  fi
}

allow_docker_to_host_api() {
  local cid src port
  # Heal a previous deploy that dropped the broad rule before the probe
  # used Caddy's own source IP.
  for port in 8080 3000; do
    allow_host_api_source "172.16.0.0/12" "${port}"
  done

  cid=$(caddy_container_id)
  local -a sources=()
  if [[ -n "${cid}" ]]; then
    # shellcheck disable=SC2207
    sources=($(caddy_ipv4s "${cid}") $(caddy_gateways "${cid}"))
    sources+=("172.17.0.1")
    echo "Allowing Caddy sources ${sources[*]} → host :8080 and :3000"
  else
    echo "WARN: Caddy container not found; keeping 172.16.0.0/12 for :8080 and :3000" >&2
  fi
  for src in "${sources[@]}"; do
    [[ -n "${src}" ]] || continue
    for port in 8080 3000; do
      allow_host_api_source "${src}" "${port}"
    done
  done
}

probe_caddy_to_api() {
  local cid gw
  cid=$(caddy_container_id)
  if [[ -z "${cid}" ]]; then
    echo "WARN: Caddy container not found; keeping 172.16.0.0/12" >&2
    return 0
  fi
  local -a targets=()
  # shellcheck disable=SC2207
  targets=($(caddy_gateways "${cid}"))
  targets+=("172.17.0.1")
  for gw in "${targets[@]}"; do
    [[ -n "${gw}" ]] || continue
    echo "Probing ${gw}:8080 from Caddy's network namespace"
    if docker run --rm --network "container:${cid}" --entrypoint curl "${NEXUS_BACKEND_IMAGE}" \
      -fsS --max-time 8 "http://${gw}:8080/actuator/health"; then
      echo
      echo "Caddy→API path is open via ${gw}"
      delete_host_api_source "172.16.0.0/12" 8080
      delete_host_api_source "172.16.0.0/12" 3000
      echo "Dropped 172.16.0.0/12 after Caddy IP rules were confirmed"
      return 0
    fi
  done
  echo "WARN: Caddy namespace probe failed; leaving 172.16.0.0/12 in place" >&2
  return 0
}

allow_docker_to_host_api
probe_caddy_to_api

CADDY_SRC="${ROOT}/caddy/nexus.caddy"
if [[ -f "${CADDY_SRC}" ]]; then
  installed=
  for root in /opt/ai_candidate_assistant /opt/ava-assistant /opt/ava; do
    if [[ -f "${root}/docker-compose.tls.yml" || -d "${root}/deploy/caddy-optional" ]]; then
      mkdir -p "${root}/deploy/caddy-optional"
      cp "${CADDY_SRC}" "${root}/deploy/caddy-optional/nexus.caddy"
      echo "Installed ${root}/deploy/caddy-optional/nexus.caddy"
      if [[ -f "${root}/docker-compose.prod.yml" && -f "${root}/docker-compose.tls.yml" ]]; then
        echo "Reloading Ava Caddy so SSE gzip/h3 fixes take effect"
        (cd "${root}" && docker compose -f docker-compose.prod.yml -f docker-compose.tls.yml up -d --force-recreate --no-deps caddy)
      else
        echo "From ${root}: docker compose -f docker-compose.prod.yml -f docker-compose.tls.yml up -d --force-recreate --no-deps caddy"
      fi
      installed=1
      break
    fi
  done
  if [[ -z "${installed}" ]]; then
    echo "Copy ${CADDY_SRC} to Ava deploy/caddy-optional/nexus.caddy and: docker compose -f docker-compose.prod.yml -f docker-compose.tls.yml up -d"
  fi
fi
