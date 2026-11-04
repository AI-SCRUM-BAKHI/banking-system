#!/usr/bin/env bash
set -euo pipefail

backend_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$backend_root"

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Created .env from .env.example"
fi

./gradlew :app-bootstrap:bootRun
