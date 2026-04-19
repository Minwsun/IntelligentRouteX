# HCM Data Priors

## Prior Policy

Each prior must state:

- `sourceType`: `sourced` or `inferred`
- `confidence`: `high`, `medium`, or `low`
- `calibrationStatus`: `locked`, `pending`, or `needs-data`

## Traffic

- HCM baseline congestion is modeled as a sourced prior with higher evening slowdown than morning slowdown.
- Corridor classes use inferred multipliers until route-level replay data is available.
- Red-light and intersection delay remain inferred in V1.

## Weather

- Dry and rainy season regimes are sourced at the monthly level.
- Clear, light rain, and heavy rain hourly states are inferred from monthly regime plus stress modifiers unless replay data is injected.
- Rain affects traffic, driver speed, and merchant prep with bounded penalties.

## Demand

- Lunch is the highest demand slice.
- Dinner is the second peak.
- Late-night is a lower-volume stress slice.
- Zone demand mix is inferred from hotspot classes until marketplace demand replay exists.

## Calibration

- sourced priors are preferred over inferred priors at runtime
- inferred priors remain explicitly tagged in docs and artifacts
- calibration V1 compares simulator outputs against dispatch benchmark ranges, not full operational truth
