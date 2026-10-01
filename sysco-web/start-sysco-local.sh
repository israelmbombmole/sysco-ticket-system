#!/usr/bin/env bash
# Start SYSCO Web locally with the default H2 file database (no Oracle).
set -euo pipefail
cd "$(dirname "$0")"
exec mvn spring-boot:run "$@"
