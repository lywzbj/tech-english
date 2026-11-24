#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [ -x /e/software/nvm/v22.22.0/node.exe ]; then
  export PATH="/e/software/nvm/v22.22.0:$PATH"
fi

cd frontend
exec npm run dev -- --host 127.0.0.1
