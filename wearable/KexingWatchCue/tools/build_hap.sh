#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$project_dir"

"$project_dir/tools/verify_project.sh"

if [[ -z "${HVIGOR_USER_HOME:-}" ]]; then
  export HVIGOR_USER_HOME="$project_dir/.hvigor-user"
fi
mkdir -p "$HVIGOR_USER_HOME"

if [[ -z "${NPM_CONFIG_CACHE:-}" ]]; then
  export NPM_CONFIG_CACHE="$HVIGOR_USER_HOME/npm-cache"
fi
mkdir -p "$NPM_CONFIG_CACHE"

if [[ -n "${HVIGORW_PATH:-}" && -x "${HVIGORW_PATH}" ]]; then
  hvigor_command="${HVIGORW_PATH}"
elif command -v hvigorw >/dev/null 2>&1; then
  hvigor_command="$(command -v hvigorw)"
elif [[ -n "${DEVECO_COMMAND_LINE_HOME:-}" && -x "${DEVECO_COMMAND_LINE_HOME}/bin/hvigorw" ]]; then
  hvigor_command="${DEVECO_COMMAND_LINE_HOME}/bin/hvigorw"
elif [[ -n "${DEVECO_COMMAND_LINE_HOME:-}" && -x "${DEVECO_COMMAND_LINE_HOME}/hvigor/bin/hvigorw" ]]; then
  hvigor_command="${DEVECO_COMMAND_LINE_HOME}/hvigor/bin/hvigorw"
else
  echo "未找到 hvigorw。请安装华为 Command Line Tools，并把 bin 加入 PATH，或设置 DEVECO_COMMAND_LINE_HOME/HVIGORW_PATH。" >&2
  exit 2
fi

echo "使用 Hvigor：$hvigor_command"
echo "Hvigor 缓存：$HVIGOR_USER_HOME"
echo "npm 缓存：$NPM_CONFIG_CACHE"

"$hvigor_command" assembleHap \
  --mode module \
  -p product=default \
  -p module=entry@default \
  -p buildMode=debug \
  --no-daemon
