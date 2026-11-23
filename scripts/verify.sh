#!/usr/bin/env bash
# 构建 + 单元测试门禁。用法：scripts/verify.sh [额外 gradle 参数...]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
source "${ROOT}/scripts/env.sh"

cd "${ROOT}"
GRADLE_ARGS=(--no-daemon --console=plain :app:testDebugUnitTest :app:assembleDebug)
if [ "$#" -gt 0 ]; then GRADLE_ARGS+=("$@"); fi

exec ./gradlew "${GRADLE_ARGS[@]}"
