# Dispatch V2 Portable Runtime Seed Contract

This document freezes the portable runtime seed layer that unblocks deterministic bundle packaging.

## Purpose

Portable bundle packaging must no longer depend on ephemeral runtime state under `build/materialization/`.

From this point onward:

- the default portable runtime seed root is `.portable-runtime-seeds/`
- the override environment variable is `IRX_PORTABLE_RUNTIME_SEED_ROOT`
- the seed root is repo-local and gitignored by default
- `scripts/build_dispatch_v2_bundle.py` is forbidden from reading `build/materialization/*` directly
- bundle packaging may only consume restored seeds referenced by `seed-manifest.json`

## Required Seed Layout

```text
.portable-runtime-seeds/
  seed-manifest.json
  tabular/
  routefinder/
  greedrl/
  chronos/
```

The seed root may contain temporary restore staging directories during repair or rebuild, but the final packaging input is only the stable seed tree plus `seed-manifest.json`.

## Seed Manifest Contract

Manifest path:

- `.portable-runtime-seeds/seed-manifest.json`

Required top-level fields:

- `schemaVersion`
- `seedRoot`
- `generatedAt`
- `seedManifestFingerprint`
- `workers`

Each worker entry must contain:

- `workerName`
- `runtimeRoot`
- `pythonExecutableRelativePath`
- `hostRuntimeRole`
- `modelRuntimeRole`
- `runtimeFingerprint`
- `sourceType`
- `sourcePath`
- `restoredAt`
- `compatibleBundleContractVersion`

Optional additive fields are allowed when a worker uses separate host and model runtimes:

- `hostRuntimeRoot`
- `hostPythonExecutableRelativePath`
- `modelRuntimeRoot`
- `modelPythonExecutableRelativePath`
- `notes`

## Runtime Role Semantics

- `hostRuntimeRole` is the Python runtime used to boot the FastAPI worker process.
- `modelRuntimeRole` is the Python runtime used by the packaged model itself when it differs from the host runtime.
- `tabular`, `routefinder`, and `greedrl` host workers use the shared bundled host Python.
- `greedrl` keeps a separate bundled model runtime Python because the runtime adapter still depends on its packaged model-side Python environment.
- `chronos` uses the same packaged runtime for both host and model execution.

## Restored Seed Expectations

The portable seed restore rail is:

1. resolve seed root from `IRX_PORTABLE_RUNTIME_SEED_ROOT`, defaulting to `.portable-runtime-seeds/`
2. create or refresh `tabular/`, `routefinder/`, `greedrl/`, and `chronos/`
3. restore the required runtime inputs for each worker
4. verify the declared Python executable paths exist under the restored seeds
5. compute runtime fingerprints
6. write `seed-manifest.json`

The restore flow may read from:

- current Python installations
- existing worker runtime sources
- promoted model artifacts under `services/models/materialized/`
- deterministic source repositories and package requirements

The restore flow must not require `build/` to already exist.

## Bundle Builder Rules

The bundle builder must:

- resolve the seed root from `IRX_PORTABLE_RUNTIME_SEED_ROOT` or `--seed-root`
- read `seed-manifest.json`
- verify the stored seed manifest fingerprint
- verify declared runtime roots and Python paths exist
- verify stored runtime fingerprints against actual seed contents
- package runtimes only from the seed root
- fail clearly if the seed manifest is missing, incomplete, or drifted

The bundle builder must never silently fall back to:

- `build/materialization/`
- a previous bundle under `build/portable/`
- user temp
- any other undocumented runtime cache

## Compatibility Lock

This seed layer is packaging infrastructure only. It must not change:

- the 12-stage runtime order
- runtime schema
- replay schema
- replay identity
- worker `/ready`
- worker `/version`
- ML business contracts
