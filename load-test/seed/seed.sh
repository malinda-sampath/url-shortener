#!/usr/bin/env bash
# Seeds the local Compose Postgres with legacy links (Stage 3).
# Run from the project root (next to compose.yaml):
#   ./load-test/seed/seed.sh            -> 5,000,000 rows
#   ./load-test/seed/seed.sh 100000     -> a smaller dry run
#   ./load-test/seed/seed.sh remove     -> delete only the seeded rows
set -euo pipefail
# Git Bash on Windows rewrites arguments that look like Unix paths (/tmp/...)
# into Windows paths before docker sees them. Turn that off for this script.
export MSYS_NO_PATHCONV=1
cd "$(dirname "$0")/../.."

if [ "${1:-}" = "remove" ]; then
  file=remove-legacy-links.sql; rows=0
else
  file=seed-legacy-links.sql; rows="${1:-5000000}"
fi

# Copy the file into the container instead of piping it, so this behaves the
# same in Git Bash, WSL and macOS/Linux shells.
docker compose cp "load-test/seed/$file" "postgres:/tmp/$file"
docker compose exec postgres psql -U shortener -d shortener -v rows="$rows" -f "/tmp/$file"
docker compose exec postgres rm -f "/tmp/$file"