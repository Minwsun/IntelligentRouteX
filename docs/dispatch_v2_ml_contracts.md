# Dispatch V2 ML Contracts

## Common Envelope

- `schemaVersion`
- `traceId`
- `stageName`
- `timeoutBudgetMs`
- `modelContractVersion`
- `sourceModel`
- `modelVersion`
- `artifactDigest`
- `latencyMs`
- `fallbackUsed`

## Payload Types

- `ScorePayload`
- `BundleProposalPayload`
- `ForecastPayload`
- `RouteAlternativesPayload`

## Rule

No worker response may use a flat universal score-only schema.

For the current tabular slice, Java currently uses only:

- `POST /score/eta-residual`
- `POST /score/pair`
- `POST /score/driver-fit`
- `POST /score/route-value`

`global-value` remains out of runtime scope for now.
