#!/usr/bin/env bash
# Starts the Prism backend (:8080) and frontend (:5173) together.
set -euo pipefail
cd "$(dirname "$0")"

echo "Starting backend (:8080)…"
( cd backend && ./gradlew bootRun ) &
BACK=$!

echo "Starting frontend (:5173)…"
( cd frontend && npm install && npm run dev ) &
FRONT=$!

trap 'echo; echo "Stopping…"; kill "$BACK" "$FRONT" 2>/dev/null || true' EXIT INT TERM
echo "Open http://localhost:5173"
wait
