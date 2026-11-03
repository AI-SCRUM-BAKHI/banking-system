#!/usr/bin/env bash
set -euo pipefail

backend_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo_root="$(cd "$backend_root/.." && pwd)"
cd "$backend_root"

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example"
fi

docker compose -f "$repo_root/infra/docker-compose.yml" up -d postgres
./gradlew :app-bootstrap:bootRun
