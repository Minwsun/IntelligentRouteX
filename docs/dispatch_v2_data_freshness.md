# Dispatch V2 Data Freshness

## Policy Fields

- `weather_max_age`
- `traffic_max_age`
- `forecast_max_age`
- `tomtom_refine_budget_per_tick`
- `confidence_downgrade_policy`
- `scenario_disable_threshold`
- `scenario_reenable_threshold`

## Rule

Every decision log record must include freshness metadata for live-source dependent stages.

