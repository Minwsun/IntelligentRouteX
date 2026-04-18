# Local Model Rematerialization Runbook

Use this runbook when a local worker fails readiness because the promoted model is missing, the loaded fingerprint drifts, provenance no longer matches, or a machine move requires a fresh materialization.

## RouteFinder

Prerequisites:

- Python interpreter available for the RouteFinder tooling flow
- repo checkout with `services/models/model-manifest.yaml`

Entry points:

- Windows: `python scripts/materialize_routefinder_local.py`
- PowerShell: `powershell -File scripts/materialize_routefinder_local.ps1`
- Shell: `bash scripts/materialize_routefinder_local.sh`

Expected output:

- `services/models/materialized/routefinder/model/routefinder-model.json`
- `services/models/materialized/routefinder/materialization-metadata.json`

## Chronos

Prerequisites:

- caller-provided Python interpreter suitable for the pinned Chronos materialization flow

Entry points:

- Windows: `python scripts/materialize_chronos_local.py`
- PowerShell: `powershell -File scripts/materialize_chronos_local.ps1`
- Shell: `bash scripts/materialize_chronos_local.sh`

Expected output:

- `services/models/materialized/chronos-2/model/chronos-runtime-manifest.json`
- `services/models/materialized/chronos-2/model/snapshot/...`
- `services/models/materialized/chronos-2/materialization-metadata.json`

## GreedRL

Prerequisites:

- Python 3.8-compatible interpreter for the pinned community-edition build flow
- machine-local build dependencies required by the upstream source build

Entry points:

- Windows: `python scripts/materialize_greedrl_local.py`
- PowerShell: `powershell -File scripts/materialize_greedrl_local.ps1`
- Shell: `bash scripts/materialize_greedrl_local.sh`

Expected output:

- `services/models/materialized/greedrl/model/greedrl-runtime-manifest.json`
- `services/models/materialized/greedrl/model/runtime/...`
- `services/models/materialized/greedrl/materialization-metadata.json`

GreedRL note:

- this flow may need to be rerun on another machine because the pinned build path is interpreter- and machine-specific

## Tabular

Prerequisites:

- repo-local tabular source artifact present at `services/ml-tabular-worker/artifacts/tabular-model.json`

Entry points:

- Windows: `python scripts/materialize_tabular_local.py`
- PowerShell: `powershell -File scripts/materialize_tabular_local.ps1`
- Shell: `bash scripts/materialize_tabular_local.sh`

Expected output:

- `services/models/materialized/tabular/model/tabular-runtime-manifest.json`
- `services/models/materialized/tabular/materialization-metadata.json`

## Verification

After rematerialization, verify:

- `artifact_digest` in `services/models/model-manifest.yaml` matches the promoted runtime manifest identity
- `loaded_model_fingerprint` in `services/models/model-manifest.yaml` matches the promoted model directory fingerprint
- the worker `/version` payload reports `loadedFromLocal=true`
- the worker `/version.loadedModelFingerprint` matches the manifest pin
- the worker `/ready` payload returns `ready=true`

## When To Rematerialize

Run rematerialization when any of the following happens:

- promoted local model files are missing
- worker boot reports fingerprint mismatch
- worker boot reports provenance drift or local-load mismatch
- the pinned model/version/source changes in `services/models/model-manifest.yaml`
- the machine/interpreter changes for GreedRL
