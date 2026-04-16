# Dispatch V2 ML Contracts

## Common Envelope

- `schemaVersion`
- `traceId`
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

