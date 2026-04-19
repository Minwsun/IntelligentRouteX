# Dispatch V2 Local Evaluation Report

## 1. Build / commit info

- scope = `LOCAL_16GB_VALIDATION`
- branch: `eval/dispatch-v2-local-benchmark-gate`
- commit SHA: `f7b4435cb9c67fc5ec40a26aa4b90ccf49d8899f`
- runtime profile: `dispatch-v2-benchmark-lite`
- started at: `2026-04-19T09:51:33Z`
- finished at: `2026-04-19T10:08:15Z`
- machine profile: `{"hostname": "DESKTOP-SEIH8FO", "platform": "Windows-11-10.0.26200-SP0", "pythonVersion": "3.13.11", "processor": "Intel64 Family 6 Model 158 Stepping 10, GenuineIntel", "machine": "AMD64"}`
- scope notes: not authority closure; soak is smoke-only; `L/XL` are outside the required local minimum.

## 2. Runtime correctness

- Required gates are derived from dry-run, targeted tests, release verify, and required artifact presence.
- `dry-run-phase3`: `passed`
- `dry-run-release`: `passed`
- `targeted-tests`: `passed`
- `perf-required`: `passed`
- `quality-required`: `passed`
- `quality-optional`: `skipped`
- `ablation-required`: `passed`
- `large-scale-smoke`: `passed`
- `soak-smoke`: `passed`
- `chaos-smoke`: `passed`
- `release-verify`: `passed`

## 3. Perf summary

- result count: `6`
- latency: `A/S/cold` p50/p95/p99 = 761/761/761 ms; `A/S/warm` p50/p95/p99 = 94/94/94 ms; `A/S/hot` p50/p95/p99 = 135/135/135 ms; `C/S/hot` p50/p95/p99 = 212/212/212 ms; `A/M/cold` p50/p95/p99 = 1103/1103/1103 ms; `C/M/hot` p50/p95/p99 = 1117/1117/1117 ms
- stage breakdown: Stage latency breakdown is available in the per-run perf JSON artifacts.
- budget breach summary: Observed budget breach rates are stored per artifact; latest artifact count = 6.

## 4. AI quality summary

- comparison summary: Full V2 has 11 advantages and 2 regressions against selected baselines; Full V2 has 8 advantages and 8 regressions against selected baselines
- degrade and fallback signals remain sourced from benchmark metrics only; no new semantics were added.

## 5. Route quality summary

- route quality: Pickup ETA values: [5.671029117058617, 5.210553428097426, 4.838975576978078, 1.0243780137450027, 1.0243780137450027, 1.0683207734686664]; completion ETA values: [17.450643182106557, 16.990167493145364, 16.268589642026015, 43.19190058486548, 43.19190058486548, 8.804511395278006]; robust utility values: [0.7728431310805507, 0.7726180110805507, 0.8284514115804126, 0.665989713980737, 0.665989713980737, 0.8495235785822296].
- route validity and source comparison remain in per-run benchmark artifacts.

## 6. Bundle quality summary

- bundle quality: Bundle rate values: [0.0, 0.0, 0.0, 0.5, 0.5, 0.0]; ablation summary: forecast: selectedProposalCount delta=0, executedAssignmentCount delta=0, bundleRate delta=0.0, robustUtilityAverage delta=0.0016634390364985707, selectorObjectiveValue delta=0.002038048414769955; greedrl: selectedProposalCount delta=0, executedAssignmentCount delta=0, bundleRate delta=0.0, robustUtilityAverage delta=0.0, selectorObjectiveValue delta=0.0; routefinder: selectedProposalCount delta=0, executedAssignmentCount delta=0, bundleRate delta=0.0, robustUtilityAverage delta=-0.0025463749999999896, selectorObjectiveValue delta=-0.0028822666666663554; tabular: selectedProposalCount delta=0, executedAssignmentCount delta=0, bundleRate delta=0.5, robustUtilityAverage delta=-0.17322227047493477, selectorObjectiveValue delta=-0.20711254297652548
- bundle pruning and retention remain sourced from existing artifact metrics and ablation deltas.

## 7. Robustness summary

- robustness: large-scale=C/M passed=True; soak=normal-clear/M passed=True samples=3; chaos=open-meteo-stale/M passed=True, tabular-unavailable/M passed=True

## 8. Verdict

- verdict: `LOCAL_PASS_WITH_LIMITS`
- known limits: `['Optional step skipped: quality-optional']`
