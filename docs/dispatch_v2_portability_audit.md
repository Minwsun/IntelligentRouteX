# Dispatch V2 Portability Audit

This audit records machine-coupling risks that affect `V1_PORTABLE_CLOSED`.

## Findings

| component/file | issue | severity | required fix |
| --- | --- | --- | --- |
| `src/main/java/com/routechain/v2/DispatchV2Configuration.java` | runtime worker clients and ops readiness used a repo-relative hard-coded manifest path under `services/models/model-manifest.yaml` | `blocker` | make manifest path configurable and route all runtime manifest loading through that path |
| `src/main/resources/application.yml` | runtime had no explicit manifest-path config hook for launcher or bundle overrides | `blocker` | add additive config for model manifest resolution, with env override support |
| `src/main/resources/application*.yml` | no official prod/demo/fallback profile set was encoded in config | `major` | add frozen Spring profile configs for `dispatch-v2-prod`, `dispatch-v2-demo`, and `dispatch-v2-fallback` |
| `src/main/java/com/routechain/config/RouteChainDispatchV2Properties.java` | file-backed feedback root defaults to `build/dispatch-v2-feedback`, which is repo-local rather than bundle-local | `major` | keep the repo-friendly default, but freeze official portable profiles to write into `data/dispatch-v2-feedback` |
| `src/test/java/com/routechain/v2/integration/*` | multiple integration tests hard-coded absolute `E:/Code _Project/...` artifact paths | `major` | replace absolute test paths with relative portable paths so validation is machine-independent |
| `services/ml-*-worker/app.py` | all four worker entrypoints assume the model manifest lives under repo-style `services/models/model-manifest.yaml` relative to the worker source tree | `blocker` | add bundle-local manifest path override support so launcher can point workers at the packaged manifest |
| `services/models/materialized/greedrl/model/greedrl-runtime-manifest.json` and `services/ml-greedrl-worker/app.py` | GreedRL readiness and adapter execution depend on a machine-specific absolute `runtimePythonExecutable` path from local materialization | `blocker` | preserve the frozen manifest artifact, but let launcher inject a bundle-local runtime Python override for readiness and adapter execution |
| `scripts/build_dispatch_v2_bundle.py` and `build/materialization/*-venv` | the first bundle builder still depends on runtime seed directories under `build/materialization`, which disappear after `gradlew clean` and leave no stable portable input for routefinder, greedrl, and chronos packaging | `blocker` | move portable runtime seeds to a non-ephemeral source of truth, or teach the builder how to restore them before packaging instead of assuming `build/` survives |
| `scripts/verify_dispatch_v2_phase3.py` and `scripts/verify_dispatch_v2_release.py` | validation scripts default to repo-local artifact roots and repo working-directory assumptions | `minor` | keep for repo validation, but launcher and bundle flow must use separate portable artifact and data roots |
| `scripts/materialize_*` | rematerialization flows assume repo checkout and build roots under `build/materialization` | `major` | packaging must treat rematerialization as a maintenance flow, not a boot-time requirement, and bundle must ship promoted artifacts directly |
| bundle tooling | no bundle builder, launcher, stop utility, or health utility exists yet | `blocker` | implement bundle build pipeline and launcher utilities after the bundle contract is frozen |
| clean-machine validation | no portable validation artifact report exists yet | `major` | emit `artifacts/release/portable_validation_report.md` during clean-machine validation |
| authority closure | no authority validation artifact report exists yet | `major` | emit `artifacts/release/authority_validation_report.md` during authority runs |

## Audit Result

- `blocker` issues must be closed before the first portable bundle candidate is treated as valid
- `major` issues must be closed before `V1_PORTABLE_CLOSED`
- `minor` issues may remain only if they do not affect bundle boot, bundle writes, or machine independence
