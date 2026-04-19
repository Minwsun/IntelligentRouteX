# HCM Scenario Catalog

## Canonical Slices

For each month:

- weekday lunch
- weekday dinner
- weekend lunch
- weekend dinner
- late-night

## Stress Slices

- heavy rain
- traffic shock
- merchant backlog
- light supply shortage

## Run Packs

- `single-slice`
- `monthly-pack`
- `stress-pack`
- `calibration-pack`
- `benchmark-pack`

## Corpus Defaults

- 12 month regimes
- 5 base slices per month
- 1 to 4 stress slices per month
- 3 seeds per slice
- target corpus size: 216 to 324 runs

## Seed Strategy

- each run uses one explicit root seed
- all per-tick randomness derives from root seed plus deterministic substream offsets
- same run config and seed must produce identical slice expansion, world events, and artifacts
