# Dispatch V2 Outcome Label Contract

## Purpose

`dispatch-outcome` is an asynchronous label contract. It is not a required runtime dependency for Bronze decision logging.

## v1 Rules

- Bronze runtime must succeed without any outcome source
- outcome ingestion is external to the decision pipeline
- outcome-joined Gold may fail if required outcome rows are missing
- teacher-only Gold must remain buildable without outcomes

## Outcome Keys

Primary key:

- `assignmentId`

Fallback linkage:

- `traceId`
- `proposalId`
- business keys derived from assignment and order lineage

## Required Fields

- Bronze envelope
- `assignmentId`
- `proposalId`
- `traceId`
- `actualPickupTravelMinutes`
- `actualMerchantWaitMinutes`
- `actualDropoffTravelMinutes`
- `actualTotalCompletionMinutes`
- `realizedTrafficDelayMinutes`
- `realizedWeatherModifier`
- `delivered`
- `outcomeSource`
- `reconciledAt`

## Source Policy

Accepted sources include:

- external execution events
- simulation outcomes
- benchmark or harness artifacts only when explicitly marked non-production

Outcome source provenance must remain explicit so teacher-only and outcome-joined datasets can filter safely.
