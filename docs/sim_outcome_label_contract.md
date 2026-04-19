# Outcome Label Contract

## Required Fields

- `actualPickupTravelSeconds`
- `actualMerchantWaitSeconds`
- `actualDropoffTravelSeconds`
- `actualTotalCompletionSeconds`
- `realizedTrafficDelaySeconds`
- `realizedWeatherModifier`
- `delivered`

## Positive-Flow Rule

- `delivered` is always `true` in V1
- failure labels are intentionally absent

## Anti-Leakage

- outcome labels are realized after dispatch execution, never copied from projected route values
- dispatch candidates and selector scores remain inputs
- actual completion labels remain outputs
