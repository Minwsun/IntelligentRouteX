from __future__ import annotations

import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch


SERVICE_ROOT = Path(__file__).resolve().parents[1]
if str(SERVICE_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVICE_ROOT))

from route_intelligence_agent.config import AgentRuntimeConfig


class AgentRuntimeConfigTest(unittest.TestCase):
    def test_repo_root_defaults_to_workspace_when_env_is_missing(self) -> None:
        with patch.dict(os.environ, {}, clear=False):
            config = AgentRuntimeConfig.from_env()
        self.assertTrue((config.repo_root / "build.gradle.kts").exists())


if __name__ == "__main__":
    unittest.main()
