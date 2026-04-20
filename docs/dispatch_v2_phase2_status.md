# Dispatch V2 Phase 2 Status

Status: `FAIL`

Last verified: `2026-04-20`

## Completed In Repo

- Decision brain resolver and runtime mode wiring now distinguish:
  - `legacy`
  - `llm`
  - `llm-shadow`
  - `llm-authoritative`
  - `hybrid`
  - `student`
- Stage-level authority only applies when the stage output is a real LLM result.
- Fallback stage outputs no longer override legacy runtime selections.
- Benchmark decision-mode overrides now preserve shadow vs authoritative semantics.
- Route-vector traces and route-vector summaries remain additive-only and benchmark-safe.
- Dataset builder can now recover trace linkage from file names when feedback payloads omit `traceId`.

## Validated

- Java compile and targeted runtime tests passed.
- Decision, routing, and benchmark test suites passed.
- Python benchmark and dataset-builder tests passed.
- Benchmark artifacts were generated locally under `artifacts/benchmark/phase2/`.
- Dataset build succeeded locally from benchmark feedback:
  - `artifacts/benchmark/phase2/dataset/normal-clear-m-llm-authoritative/`

## Blocking Issue

This rail still fails the completion gate because the required provider contract is unavailable:

- `GET http://127.0.0.1:20128/v1` returned `200`
- `GET http://127.0.0.1:20128/v1/models` returned `200`
- `POST http://127.0.0.1:20128/v1/responses` returned `404`
- `GET https://r8cp2m4.9router.com/v1` returned `200`
- `GET https://r8cp2m4.9router.com/v1/models` returned `200`
- `POST https://r8cp2m4.9router.com/v1/responses` returned `404`

Because this rail is explicitly locked to `9router` and `/v1/responses`, authoritative LLM execution cannot be completed from inside the repo until the provider exposes that endpoint.

## Practical Outcome

- `llm-authoritative` is safe to exercise because stage fallback works.
- Benchmarks for `pair-bundle` and `final-selection` authority can run and emit artifacts.
- Those runs currently prove fallback behavior, not real LLM authority, because all `/v1/responses` attempts fail before producing token usage.

## Next External Action

Provide a working 9router gateway that exposes `POST /v1/responses` for model `gpt-5.4`, then rerun the existing benchmark matrix from `artifacts/benchmark/phase2/`.
