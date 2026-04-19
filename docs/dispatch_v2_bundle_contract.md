# Dispatch V2 Bundle Contract

This document freezes the target portable bundle contract before launcher and packaging code is implemented.

## Bundle Topology

The portable bundle root is:

```text
DispatchV2/
  launcher/
  runtimes/
  app/
  workers/
  models/
    materialized/
  config/
  data/
```

Required `data/` subtree:

```text
data/
  logs/
  snapshots/
  replay/
  bronze/
  silver/
```

## Runtime Resolution Rules

- app runtime resolves all portable paths from bundle root
- worker runtimes resolve all portable paths from bundle root
- config is read from bundle `config/`
- promoted model artifacts are read from bundle `models/materialized/`
- runtime writes are restricted to bundle `data/`
- no bundle process may require system Java or system Python
- no bundle process may write to user home or system temp by default

## Launcher Contract

Launcher flow is fixed:

1. verify bundle integrity
2. verify packaged worker fingerprints
3. start workers
4. check `/health`, `/ready`, `/version`
5. start app
6. verify readiness snapshot
7. return exactly one final status

Allowed final statuses:

- `READY_FULL`
- `READY_DEGRADED`
- `BOOT_FAILED`

## Bundle Build Manifest

Each bundle must contain a build manifest with:

- bundle version
- build commit SHA
- build timestamp
- packaged worker fingerprints
- packaged profile set
- launcher version

The build manifest identifies exactly which bundle is under validation.

## Integrity Manifest

Each bundle must also contain an integrity manifest covering:

- app artifact identity
- worker package identities
- bundled runtime identities
- packaged config identities
- packaged model identities

The launcher must verify integrity before attempting boot.

## Non-Negotiable Compatibility Rules

- no change to the 12-stage runtime order
- no change to runtime semantics
- no change to replay identity
- no change to runtime or replay schema
- no change to worker `/ready`
- no change to worker `/version`
- no change to ML business contracts

## Validation Outputs

Portable validation report path:

- `artifacts/release/portable_validation_report.md`

Authority validation report path:

- `artifacts/release/authority_validation_report.md`
