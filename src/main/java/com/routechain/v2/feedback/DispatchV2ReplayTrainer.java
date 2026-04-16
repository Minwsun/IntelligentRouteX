package com.routechain.v2.feedback;

import com.routechain.core.AdaptiveWeightEngine;
import com.routechain.v2.DispatchV2Result;

/**
 * Compatibility replay hook for Dispatch V2.
 *
 * <p>The first cutover slice keeps compact learning as the authoritative
 * updater. This trainer exists so the compatibility shell has a stable place
 * to attach richer replay behavior later without changing the seam again.
 */
public final class DispatchV2ReplayTrainer {

    public void onDispatchCompleted(DispatchV2Result result, AdaptiveWeightEngine compatibilityWeightEngine) {
        if (result == null || compatibilityWeightEngine == null) {
            return;
        }
        // Learning updates still happen on resolved outcomes through the existing
        // compact runtime path. This hook only reserves a stable extension point.
    }
}
