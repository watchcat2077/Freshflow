package com.freshflow.possimulator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * The SKUs a simulated till scans, and how often each comes across the scanner.
 *
 * <p>Written as {@code SKU:weight,SKU:weight}, for example the default
 * {@code SKU-YOG-1KG:0.7,SKU-STR-250G:0.3}. Weights are relative and need not sum to one: a mix of
 * {@code A:7,B:3} scans A seventy per cent of the time. Selection is driven by whatever
 * {@link Random} the caller supplies, so a seeded run picks the same sequence every time.
 *
 * <p>Nothing here knows about prices or batches. The simulator is a client of the Sales context's
 * REST contract, and which SKUs exist, and at what price, is decided by the services it talks to.
 */
public final class SkuMix {

    private final List<String> skus;
    private final double[] cumulativeWeights;
    private final double totalWeight;
    private final String spec;

    private SkuMix(List<String> skus, double[] weights, String spec) {
        this.skus = Collections.unmodifiableList(new ArrayList<>(skus));
        this.cumulativeWeights = new double[weights.length];
        double running = 0.0;
        for (int i = 0; i < weights.length; i++) {
            running += weights[i];
            this.cumulativeWeights[i] = running;
        }
        this.totalWeight = running;
        this.spec = spec;
    }

    /**
     * Parses {@code SKU:weight,SKU:weight}.
     *
     * @throws IllegalArgumentException when the spec is empty, an entry lacks a weight, a weight is
     *                                  not a positive number, a SKU is blank, or a SKU repeats
     */
    public static SkuMix parse(String spec) {
        Objects.requireNonNull(spec, "spec");
        if (spec.isBlank()) {
            throw new IllegalArgumentException("--skus must list at least one SKU:weight pair");
        }
        List<String> skus = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String entry : spec.split(",")) {
            String trimmed = entry.trim();
            int colon = trimmed.lastIndexOf(':');
            if (colon <= 0 || colon == trimmed.length() - 1) {
                throw new IllegalArgumentException(
                        "--skus entry '" + trimmed + "' must be SKU:weight");
            }
            String sku = trimmed.substring(0, colon).trim();
            String weightText = trimmed.substring(colon + 1).trim();
            if (sku.isBlank()) {
                throw new IllegalArgumentException("--skus entry '" + trimmed + "' has a blank SKU");
            }
            double weight;
            try {
                weight = Double.parseDouble(weightText);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "--skus weight for " + sku + " must be a number, got '" + weightText + "'");
            }
            if (!(weight > 0.0) || Double.isInfinite(weight)) {
                throw new IllegalArgumentException(
                        "--skus weight for " + sku + " must be positive, got " + weightText);
            }
            if (!seen.add(sku)) {
                throw new IllegalArgumentException("--skus lists " + sku + " more than once");
            }
            skus.add(sku);
            weights.add(weight);
        }
        double[] weightArray = new double[weights.size()];
        for (int i = 0; i < weightArray.length; i++) {
            weightArray[i] = weights.get(i);
        }
        return new SkuMix(skus, weightArray, String.join(",", normalised(skus, weightArray)));
    }

    private static List<String> normalised(List<String> skus, double[] weights) {
        List<String> entries = new ArrayList<>();
        for (int i = 0; i < skus.size(); i++) {
            String weight = weights[i] == Math.rint(weights[i])
                    ? Long.toString((long) weights[i])
                    : Double.toString(weights[i]);
            entries.add(skus.get(i) + ":" + weight);
        }
        return entries;
    }

    /** The SKUs in the order they were listed. */
    public List<String> skus() {
        return skus;
    }

    /** Picks one SKU, each with probability proportional to its weight. */
    public String pick(Random random) {
        double point = random.nextDouble() * totalWeight;
        for (int i = 0; i < cumulativeWeights.length; i++) {
            if (point < cumulativeWeights[i]) {
                return skus.get(i);
            }
        }
        // Only reachable through floating-point rounding at the very top of the range.
        return skus.get(skus.size() - 1);
    }

    /** The mix as it would be written on the command line. */
    @Override
    public String toString() {
        return spec;
    }

    /** A value: two mixes listing the same SKUs with the same weights in the same order are equal. */
    @Override
    public boolean equals(Object other) {
        return other instanceof SkuMix that && spec.equals(that.spec);
    }

    @Override
    public int hashCode() {
        return spec.hashCode();
    }
}
