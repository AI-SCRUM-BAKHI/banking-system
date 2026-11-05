#!/usr/bin/env bash
set -euo pipefail

project_dir="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
cd "$project_dir"

health_attempts="${DEPLOY_HEALTH_ATTEMPTS:-60}"
health_delay="${DEPLOY_HEALTH_DELAY_SECONDS:-2}"
compose=(docker compose -f infra/docker-compose.yml)

wait_for_health() {
  local service_name="$1"
  local health_url="$2"
  local attempt
  for ((attempt = 1; attempt <= health_attempts; attempt++)); do
    if curl --fail --silent --show-error --connect-timeout 2 --max-time 5 "$health_url" 2>/dev/null |
      grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
      echo "$service_name is ready."
      return 0
    fi
    echo "Waiting for $service_name ($attempt/$health_attempts)..."
    sleep "$health_delay"
  done
  echo "$service_name did not become ready; deployment is not successful." >&2
  "${compose[@]}" ps
  return 1
}

command -v curl >/dev/null
echo "Rebuilding and starting services..."
"${compose[@]}" up -d --build

# A running Java container can still be starting Spring/database migrations.
wait_for_health backend http://127.0.0.1:8080/actuator/health/readiness

# Frontend can remain running when only backend is recreated. Reload its upstream
# DNS after readiness so nginx does not retain the previous backend container IP.
"${compose[@]}" exec -T frontend nginx -t
"${compose[@]}" exec -T frontend nginx -s reload
wait_for_health frontend-api http://127.0.0.1:5173/api/v1/system/ping

docker image prune -f
"${compose[@]}" ps
echo "Deployment completed: backend and frontend API are ready."
