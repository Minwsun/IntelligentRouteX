# Dispatch V2 Authority Validation Report

- scope: `AUTHORITY_CLOSURE`
- status: `FAIL`
- final recommendation: `NO_GO`
- authority_candidate_commit: `7026435cc97c4ace52294df3484d810fa0886bb7`
- portable_closed_commit: `7026435cc97c4ace52294df3484d810fa0886bb7`
- bundle_candidate_name: `DispatchV2-rc9`
- attempted_from: `branch=main; path=E:\Code _Project\IntelligentRouteX`
- rerun_from_gate: `none`

## Worker Fingerprints

- `ml-tabular-worker`: `sha256:ec0d0209af16146823a1cae51289113680a864fb42a2ea1635f5b9a87a0ce7cc`
- `ml-routefinder-worker`: `sha256:b61f325ea67688bfa39bbece94f11c10023178070cbab3bd790c2922cbbef1bc`
- `ml-greedrl-worker`: `sha256:3b4a1c0c0fbb9bc84bb5df184bec7b6bd1d43254d16327904fa595e72544a1aa`
- `ml-forecast-worker`: `sha256:f5e9d3c877a42ef06ba8da80bcf4c758f0e8be0ae0f99a9b73a03c1ae9501333`

## Machine Profile

- machine label: `dispatch-v2-benchmark-authority-v1`
- CPU: `Intel(R) Core(TM) i7-8750H CPU @ 2.20GHz`
- RAM: `15.84 GB`
- OS: `Microsoft Windows 11 Home Single Language 10.0.26200`
- PowerShell: `5.1.26100.8115`
- JDK: `Temurin 21.0.10+7 LTS`

## Gate Status

- phase3 full gate: `PASS`
  - command: `python scripts/verify_dispatch_v2_phase3.py --include-full-suite`
  - result: `7/7 checks passed`
- full gradle suite: `PASS`
  - command: `.\gradlew.bat --no-daemon test`
  - result: `BUILD SUCCESSFUL`
- benchmark critical matrix: `FAIL`
  - commands:
    - `python scripts/run_dispatch_v2_benchmark.py --baseline all --size L --scenario-pack normal-clear --execution-mode local-real --output-dir artifacts/benchmark`
    - `python scripts/run_dispatch_v2_benchmark.py --baseline all --size L --scenario-pack heavy-rain --execution-mode local-real --output-dir artifacts/benchmark`
    - `python scripts/run_dispatch_v2_benchmark.py --baseline all --size L --scenario-pack traffic-shock --execution-mode local-real --output-dir artifacts/benchmark`
    - `python scripts/run_dispatch_v2_benchmark.py --baseline all --size L --scenario-pack forecast-heavy --execution-mode local-real --output-dir artifacts/benchmark`
  - artifact summary:
    - `normal-clear`: `Full V2 has 2 advantages and 3 regressions against selected baselines`
    - `heavy-rain`: `Full V2 has 2 advantages and 3 regressions against selected baselines`
    - `traffic-shock`: `Full V2 has 0 advantages and 5 regressions against selected baselines`
    - `forecast-heavy`: `Full V2 has 2 advantages and 3 regressions against selected baselines`
  - authority interpretation:
    - comparison artifacts show regressions against selected baselines in every critical scenario pack
    - artifact notes still mark these runs as `non-authoritative-local-real-run`
- large-scale authority matrix: `PASS`
  - commands:
    - `python scripts/run_dispatch_v2_large_scale.py --baseline all --size L --scenario-pack normal-clear --execution-mode local-real --output-dir artifacts/large-scale`
    - `python scripts/run_dispatch_v2_large_scale.py --baseline all --size L --scenario-pack heavy-rain --execution-mode local-real --output-dir artifacts/large-scale`
    - `python scripts/run_dispatch_v2_large_scale.py --baseline all --size L --scenario-pack traffic-shock --execution-mode local-real --output-dir artifacts/large-scale`
    - `python scripts/run_dispatch_v2_large_scale.py --baseline all --size L --scenario-pack forecast-heavy --execution-mode local-real --output-dir artifacts/large-scale`
    - `python scripts/run_dispatch_v2_large_scale.py --baseline all --size L --scenario-pack worker-degradation --execution-mode local-real --output-dir artifacts/large-scale`
    - `python scripts/run_dispatch_v2_large_scale.py --baseline all --size L --scenario-pack live-source-degradation --execution-mode local-real --output-dir artifacts/large-scale`
  - result: `18/18 L cells passed`
  - artifact paths: `artifacts/large-scale/dispatch-large-scale-*.json`, `artifacts/large-scale/dispatch-large-scale-summary.md`
- chaos authority matrix: `PASS`
  - command: `python scripts/run_dispatch_v2_chaos.py --fault all --size L --scenario-pack all --execution-mode local-real --output-dir artifacts/chaos`
  - result: `64/64 latest L local-real cells passed`
  - fault families covered:
    - `tabular-unavailable`
    - `routefinder-unavailable`
    - `greedrl-unavailable`
    - `forecast-unavailable`
    - `worker-ready-false-optional-path`
    - `worker-malformed-response`
    - `worker-fingerprint-mismatch`
    - `open-meteo-stale`
    - `open-meteo-unavailable`
    - `tomtom-timeout`
    - `tomtom-auth-or-quota`
    - `tomtom-http-error`
    - `tomtom-missing-api-key`
    - `warm-boot-invalid-snapshot`
    - `reuse-state-load-missing-or-invalid`
    - `partial-hot-start-drift`
- soak authority: `FAIL`
  - commands:
    - `python scripts/run_dispatch_v2_soak.py --duration 6h --size L --scenario-pack normal-clear --execution-mode local-real --output-dir artifacts/soak`
    - `python scripts/run_dispatch_v2_soak.py --duration 6h --size L --scenario-pack heavy-rain --execution-mode local-real --output-dir artifacts/soak`
    - `python scripts/run_dispatch_v2_soak.py --duration 6h --size L --scenario-pack traffic-shock --execution-mode local-real --output-dir artifacts/soak`
    - `python scripts/run_dispatch_v2_soak.py --duration 6h --size L --scenario-pack worker-degradation --execution-mode local-real --output-dir artifacts/soak`
  - artifact result:
    - all 4 cells wrote `passed=true`
    - all 4 cells recorded `sampleCount=3`
    - all 4 cells recorded notes: `non-authoritative-local-real-run`
  - authority interpretation:
    - current soak runner still injects `sample-count-override=3`
    - current soak harness explicitly marks `local-real` as non-authoritative
    - this does not satisfy the rail requirement for authority soak closure
- final release verify: `PASS`
  - command: `python scripts/verify_dispatch_v2_release.py`
  - result: `8/8 checks passed`

## Threshold Table For This Run

### Correctness Thresholds

- 12-stage order must remain exact: `PINNED`
- replay identity must not drift: `PINNED`
- conflict-free execution must remain `100%`: `PINNED`

### Performance Thresholds

- p95 latency threshold: `NOT NUMERICALLY PINNED`
- budget breach rate threshold: `NOT NUMERICALLY PINNED`
- catastrophic timeout spiral tolerance: `PINNED AS ZERO TOLERANCE`

### Quality Thresholds

- no severe regression vs authority baseline: `PINNED, BUT BASELINE NOT YET ACCEPTED`
- route fallback share threshold: `NOT NUMERICALLY PINNED`
- bundle rate floor: `NOT NUMERICALLY PINNED`
- executed assignment count floor: `NOT NUMERICALLY PINNED`
- robust utility floor: `NOT NUMERICALLY PINNED`

### Robustness Thresholds

- no crash: `PINNED`
- no stuck worker state: `PINNED`
- restart-safe behavior: `PINNED`

## Known Issues

- benchmark critical matrix commands completed, but the comparison artifacts show regressions in every required scenario pack, including `traffic-shock` with `0 advantages and 5 regressions`
- benchmark comparison artifacts still mark local-real runs as `non-authoritative-local-real-run`, so the benchmark evidence is not yet authority-grade
- soak authority closure is blocked by current repo semantics:
  - `scripts/run_dispatch_v2_soak.py` always passes `sample-count-override`
  - `DispatchSoakHarness` marks every `local-real` run as `non-authoritative-local-real-run`
- because the threshold table is only partially numeric and no accepted authority baseline has been pinned yet, this run cannot produce final recommendation `GO`

## Artifact Evidence

- benchmark summary: `artifacts/benchmark/dispatch-quality-summary.md`
- large-scale summary: `artifacts/large-scale/dispatch-large-scale-summary.md`
- chaos summary: `artifacts/chaos/dispatch-chaos-summary.md`
- soak summary: `artifacts/soak/dispatch-soak-summary.md`

## Conclusion

- authority validation attempted: `YES`
- `V1_AUTHORITY_CLOSED`: `NOT_ACHIEVED`
- release-ready: `NO`

## Required Next Action

- treat the first failing authority gate as `benchmark critical matrix`
- fix the real blocker(s) only:
  - benchmark authority semantics and thresholds must be pinned to an accepted authority baseline
  - soak authority semantics must stop treating `local-real` as non-authoritative and must stop forcing validation-only sample override for authority runs
- rerun from gate: `benchmark critical matrix`
