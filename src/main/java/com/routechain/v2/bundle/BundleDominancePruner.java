package com.routechain.v2.bundle;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BundleDominancePruner {

    public List<BundleCandidate> prune(List<BundleCandidate> candidates) {
        Map<String, BundleCandidate> bestByFamilyAndSignature = new LinkedHashMap<>();
        for (BundleCandidate candidate : candidates.stream().sorted(bundleComparator()).toList()) {
            String key = candidate.family().name() + "|" + candidate.orderSetSignature();
            bestByFamilyAndSignature.merge(key, candidate, this::better);
        }

        Map<String, BundleCandidate> bestBySignature = new LinkedHashMap<>();
        for (BundleCandidate candidate : bestByFamilyAndSignature.values().stream().sorted(bundleComparator()).toList()) {
            bestBySignature.merge(candidate.orderSetSignature(), candidate, this::better);
        }
        return bestBySignature.values().stream().sorted(bundleComparator()).toList();
    }

    public Comparator<BundleCandidate> bundleComparator() {
        return Comparator.comparingDouble(BundleCandidate::score).reversed()
                .thenComparing((BundleCandidate candidate) -> candidate.orderIds().size(), Comparator.reverseOrder())
                .thenComparing(BundleCandidate::orderSetSignature)
                .thenComparing(candidate -> candidate.family().name());
    }

    private BundleCandidate better(BundleCandidate left, BundleCandidate right) {
        return bundleComparator().compare(left, right) <= 0 ? left : right;
    }
}
