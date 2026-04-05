from __future__ import annotations

from dataclasses import dataclass

from .config import AgentRuntimeConfig
from .quota_ledger import QuotaLedgerStore
from .schemas import FallbackReason, ModelProfile, RoutingDecision, TaskClass


@dataclass(frozen=True)
class ModelRoutingPolicy:
    config: AgentRuntimeConfig
    quota_ledger: QuotaLedgerStore

    def profiles(self) -> list[ModelProfile]:
        return list(self._profiles().values())

    def route(self, task_class: TaskClass, manual_model_key: str = "") -> RoutingDecision:
        profiles = self._profiles()
        if manual_model_key:
            manual = self._lookup_manual_profile(manual_model_key, profiles)
            return RoutingDecision(
                task_class=task_class,
                primary_profile=manual,
                selected_profile=manual,
                cascade=[manual],
                fallback_reason=FallbackReason.MANUAL_OVERRIDE.value,
                quota_decision=FallbackReason.MANUAL_OVERRIDE.value,
            )

        cascade = self._cascade_for(task_class, profiles)
        primary = cascade[0]

        if task_class == TaskClass.OPS_SHORT_QNA and self._should_budget_guard_short_qna(profiles["gemma4_26b"]):
            selected = profiles["gemma3_12b"]
            return RoutingDecision(
                task_class=task_class,
                primary_profile=primary,
                selected_profile=selected,
                cascade=[selected, profiles["gemma3_27b"]],
                fallback_reason=FallbackReason.BUDGET_GUARD.value,
                quota_decision=FallbackReason.BUDGET_GUARD.value,
            )

        for index, profile in enumerate(cascade):
            if self.quota_ledger.remaining_requests(profile) <= 0:
                continue
            if index == 0:
                return RoutingDecision(
                    task_class=task_class,
                    primary_profile=primary,
                    selected_profile=profile,
                    cascade=cascade,
                )
            fallback_reason = self._fallback_reason_for(task_class)
            return RoutingDecision(
                task_class=task_class,
                primary_profile=primary,
                selected_profile=profile,
                cascade=cascade[index:],
                fallback_reason=fallback_reason,
                quota_decision=fallback_reason,
            )

        selected = profiles["gemma3_12b"]
        return RoutingDecision(
            task_class=task_class,
            primary_profile=primary,
            selected_profile=selected,
            cascade=[selected],
            fallback_reason=FallbackReason.BUDGET_GUARD.value,
            quota_decision="all_tiers_exhausted",
        )

    def _lookup_manual_profile(
        self, manual_model_key: str, profiles: dict[str, ModelProfile]
    ) -> ModelProfile:
        normalized = manual_model_key.strip().lower()
        for profile in profiles.values():
            if normalized in {profile.key.lower(), profile.model_id.lower(), profile.display_name.lower()}:
                return profile
        raise ValueError(f"Unknown manual model key: {manual_model_key}")

    def _fallback_reason_for(self, task_class: TaskClass) -> str:
        if task_class == TaskClass.TRIAGE_DEEP:
            return FallbackReason.ESCALATION_DENIED.value
        return FallbackReason.PRIMARY_EXHAUSTED.value

    def _should_budget_guard_short_qna(self, primary_profile: ModelProfile) -> bool:
        remaining = self.quota_ledger.remaining_requests(primary_profile)
        ratio = remaining / float(primary_profile.daily_request_limit)
        return ratio <= self.config.short_qna_budget_guard_ratio

    def _cascade_for(
        self, task_class: TaskClass, profiles: dict[str, ModelProfile]
    ) -> list[ModelProfile]:
        if task_class == TaskClass.TRIAGE_DEEP:
            return [
                profiles["gemma4_31b"],
                profiles["gemma3_27b"],
                profiles["gemma4_26b"],
                profiles["gemma3_12b"],
            ]
        if task_class in {
            TaskClass.TRIAGE_STANDARD,
            TaskClass.OPS_STANDARD_QNA,
            TaskClass.REVIEW_PACK_SYNTHESIS,
            TaskClass.PROMOTION_SUMMARY,
        }:
            return [
                profiles["gemma4_26b"],
                profiles["gemma3_27b"],
                profiles["gemma3_12b"],
            ]
        return [
            profiles["gemma4_26b"],
            profiles["gemma3_12b"],
            profiles["gemma3_27b"],
        ]

    def _profiles(self) -> dict[str, ModelProfile]:
        return {
            "gemma4_26b": ModelProfile(
                key="gemma4_26b",
                display_name="Gemma 4 26B",
                model_id=self.config.gemma4_26b_model_id,
                daily_request_limit=self.config.gemma4_26b_rpd,
                max_output_tokens=1200,
            ),
            "gemma4_31b": ModelProfile(
                key="gemma4_31b",
                display_name="Gemma 4 31B",
                model_id=self.config.gemma4_31b_model_id,
                daily_request_limit=self.config.gemma4_31b_rpd,
                max_output_tokens=1600,
            ),
            "gemma3_27b": ModelProfile(
                key="gemma3_27b",
                display_name="Gemma 3 27B",
                model_id=self.config.gemma3_27b_model_id,
                daily_request_limit=self.config.gemma3_27b_rpd,
                max_output_tokens=1200,
            ),
            "gemma3_12b": ModelProfile(
                key="gemma3_12b",
                display_name="Gemma 3 12B",
                model_id=self.config.gemma3_12b_model_id,
                daily_request_limit=self.config.gemma3_12b_rpd,
                max_output_tokens=700,
            ),
        }

