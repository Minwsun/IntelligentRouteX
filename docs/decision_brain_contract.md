# Dispatch V2 Decision Brain Contract

Dispatch V2 runs three brain implementations behind one stage contract:

- `legacy-4ml`
- `llm-brain`
- `student-brain`

## Runtime Policy

- Default mode: `llm`
- Fallback mode: `legacy`
- Shadow benchmark mode: `llm-shadow`
- Stage-authoritative benchmark mode: `llm-authoritative`
- Selective mode: `hybrid`
- Student rollout mode: `student`

`DispatchV2Core` must only consume stage envelopes. It must not call provider-specific logic directly.

## Stage Flow

The decision flow is fixed:

1. `observation-pack`
2. `pair-bundle`
3. `anchor`
4. `driver`
5. `route-generation`
6. `route-critique`
7. `scenario`
8. `final-selection`
9. `safety-execute`

`observation-pack` is normalization only and does not authorize a provider decision.

## Authority Policy

- `legacy`: all stages are legacy-authoritative.
- `llm-shadow`: runtime dispatch stays legacy-authoritative; LLM runs sidecar and emits agreement logs.
- `llm-authoritative`: only explicitly enabled stages are LLM-authoritative; other stages remain legacy-authoritative.
- `hybrid`: same execution shape as `llm-authoritative`, but stage use can be narrowed by hard-case policy.
- `student`: placeholder until training pipeline is stable.

## Fallback Policy

Fallback is stage-local.

Each stage must record:

- `fallbackUsed`
- `fallbackReason`
- `requestedEffort`
- `appliedEffort`

Fallback reasons include:

- `llm-api-key-missing`
- `provider-timeout`
- `provider-http-error`
- `provider-rejected-effort`
- `provider-invalid-json`
- `provider-schema-invalid`
- `provider-empty-response`

## Provider Contract

- Provider: `9router`
- Wire API: `/v1/responses`
- Model: `gpt-5.4`
- Repo default base URL: `http://127.0.0.1:20128/v1`
- Remote tunnel URLs are runtime overrides only.
