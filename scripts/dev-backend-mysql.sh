#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [ ! -x "${JAVA_HOME:-}/bin/java" ] && [ -x /c/develop/java17/bin/java ]; then
  export JAVA_HOME='C:\develop\java17'
fi
export PATH="/c/develop/java17/bin:$PATH"

cd backend
exec mvn spring-boot:run -Dspring-boot.run.profiles=mysql "$@"
