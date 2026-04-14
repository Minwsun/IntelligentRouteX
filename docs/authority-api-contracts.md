# Authority API Contracts

## Purpose

This note locks the current meaning of app-facing dispatch DTOs so clients can bind directly to one authority model.

Field intent terms used below:

- `authority`: the field is part of the source-of-truth contract clients should trust for business state
- `derived/display`: the field is useful for presentation, but should not be used to infer business truth
- `compatibility-only`: the field remains for backward compatibility and should not be treated as the primary state signal

## Customer surface

### `UserOrderResponse`

- `orderId`, `customerId`, `serviceTier`, `quotedFee`: authority
- `lifecycleStage`: authority
- `offerSnapshot`: authority
- `lifecycleHistory`: authority
- `assignedDriverId`: authority after lock, empty before lock
- `createdAt`, `assignedAt`, `arrivedPickupAt`, `pickedUpAt`, `arrivedDropoffAt`, `droppedOffAt`, `cancelledAt`, `failedAt`: authority timestamps
- `offerBatchId`: derived/display shortcut for the latest active batch
- `status`: compatibility-only raw operational status string

### `TripTrackingView`

- `orderId`, `customerId`, `serviceTier`, `quotedFee`: authority
- `lifecycleStage`: authority
- `offerSnapshot`: authority
- `lifecycleHistory`: authority
- `assignedDriverId`: authority after lock
- `pickup`, `dropoff`: authority order geometry
- `createdAt`, `assignedAt`, `arrivedPickupAt`, `pickedUpAt`, `arrivedDropoffAt`, `droppedOffAt`, `cancelledAt`, `failedAt`: authority timestamps
- `offerBatchId`: derived/display shortcut
- `etaMinutes`, `assignedDriver`, `runtimeDriverLocation`, `routePolyline`, `routeSource`, `routeGeneratedAt`, `activeRoutePolyline`, `activeRouteSource`, `activeRouteGeneratedAt`, `remainingRoutePreviewPolyline`, `remainingRoutePreviewSource`: derived/display runtime map state
- `status`: compatibility-only raw operational status string
- `stage`: compatibility-only legacy lifecycle token

## Driver surface

### Driver offer snapshot (`OfferBrokerService.OfferView`)

- `offerId`, `offerBatchId`, `orderId`, `driverId`: authority identity
- `status`: authority low-level offer terminal/runtime state
- `offerStage`: authority app-facing offer state
- `wave`, `previousBatchId`, `reservationVersion`, `createdAt`, `expiresAt`: authority offer timeline fields
- `serviceTier`, `score`, `acceptanceProbability`, `deadheadKm`, `borrowed`, `rationale`: derived/display decision context

### `DriverActiveTaskView`

- `driverId`, `taskId`, `orderId`, `serviceTier`, `customerId`: authority identity
- `lifecycleStage`: authority
- `assignedAt`, `arrivedPickupAt`, `pickedUpAt`, `arrivedDropoffAt`: authority timestamps
- `pickup`, `dropoff`: authority stop geometry
- `etaMinutes`, `currentLocation`, `runtimeDriverLocation`, `routePolyline`, `routeSource`, `routeGeneratedAt`, `activeRoutePolyline`, `activeRouteSource`, `activeRouteGeneratedAt`, `remainingRoutePreviewPolyline`, `remainingRoutePreviewSource`: derived/display runtime map state
- `status`: compatibility-only raw task status string

### `DriverRealtimeSnapshot`

- `offers`, `activeTask`: authority payloads for driver state
- `mapSnapshot`: derived/display map aggregate
- `driverId`: authority audience identity

## Merchant surface

### `MerchantOrderView`

- `merchantId`, `orderId`, `customerId`, `quotedFee`: authority identity/business fields
- `lifecycleStage`: authority
- `offerSnapshot`: authority
- `assignedDriverId`: authority after lock
- `createdAt`, `updatedAt`: authority timestamps for merchant timeline binding
- `status`: compatibility-only raw operational status string

## Ops surface

### `OpsOrderMonitorView`

- `orderId`, `customerId`, `merchantId`: authority identity
- `lifecycleStage`: authority
- `offerSnapshot`: authority
- `assignedDriverId`: authority after lock
- `createdAt`, `updatedAt`: authority timestamps for monitoring timelines
- `status`: compatibility-only raw operational status string

### `OpsRealtimeSnapshot`

- `activeOrders`: authority monitor payload

## Shared authority sub-models

### `OrderOfferSnapshot`

- `stage`: authority app-facing offer state
- `activeBatchId`, `activeWave`, `totalWaves`: authority offer wave state
- `reofferEligible`, `pendingOffersPresent`: authority dispatch state flags
- `latestResolutionReason`: authority reason token for the latest offer outcome
- `latestWave`: authority latest-wave summary
- `assignmentLock`: authority current lock view

### `OfferWaveSummary`

- all fields are authority for the latest batch wave summary

### `AssignmentLockView`

- all fields are authority for current or terminal reservation lock state

## Binding rule

Clients should bind business state from:

- `lifecycleStage`
- `offerSnapshot`
- `lifecycleHistory`
- timestamp fields that correspond to those stages

Clients should not infer business state from raw `status` strings when an authority lifecycle or offer field is present.
