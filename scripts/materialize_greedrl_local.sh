#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
VENV_PATH="${REPO_ROOT}/build/materialization/greedrl-venv"
HELPER_SCRIPT="${SCRIPT_DIR}/materialize_greedrl_local.py"

if [[ ! -d "${VENV_PATH}" ]]; then
  python -m venv "${VENV_PATH}"
fi

"${VENV_PATH}/bin/python" "${HELPER_SCRIPT}" "$@"
