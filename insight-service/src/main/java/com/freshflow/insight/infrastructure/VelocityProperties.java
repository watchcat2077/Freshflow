package com.freshflow.insight.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Binds {@code freshflow.insight.velocity}, recorded in spec-01 &sect;2.7 alongside the other
 * seeded configuration contracts.
 *
 * <p>The window defaults are spec-02 FR12's MVP simplification: "60-second window for
 * demonstration; production target is a 60-minute window advancing every 5 minutes". Both are
 * configuration, so moving to the production shape is a YAML change and not a code change. The
 * 12:1 ratio between window and advance is kept at both scales.
 *
 * <p>{@code baselines} is the FR12 velocity baseline. spec-02 requires the comparison but no
 * requirement says where the number comes from, a specification gap spec-01 &sect;2.7 records.
 * Seeding it from configuration mirrors what FR8 does for price rules.
 */
@ConfigurationProperties(prefix = "freshflow.insight.velocity")
public class VelocityProperties {

    /** spec-02 FR12 MVP simplification. */
    private long windowSeconds = 60;

    /** How far the window advances. 5 seconds against a 60-second window is the 12:1 production ratio. */
    private long advanceSeconds = 5;

    /**
     * How long a window stays open to late-arriving events. Non-zero because event time and arrival
     * order are not the same thing, which is the entire reason spec-01 &sect;2.6 insists on
     * {@code occurredAt}.
     */
    private long graceSeconds = 5;

    /** How long windows are retained in the state store, so that a query can still see them. */
    private long retentionMinutes = 10;

    private List<Baseline> baselines = new ArrayList<>();

    public Duration window() {
        return Duration.ofSeconds(windowSeconds);
    }

    public Duration advance() {
        return Duration.ofSeconds(advanceSeconds);
    }

    public Duration grace() {
        return Duration.ofSeconds(graceSeconds);
    }

    public Duration retention() {
        return Duration.ofMinutes(retentionMinutes);
    }

    public long getWindowSeconds() {
        return windowSeconds;
    }

    public void setWindowSeconds(long windowSeconds) {
        this.windowSeconds = windowSeconds;
    }

    public long getAdvanceSeconds() {
        return advanceSeconds;
    }

    public void setAdvanceSeconds(long advanceSeconds) {
        this.advanceSeconds = advanceSeconds;
    }

    public long getGraceSeconds() {
        return graceSeconds;
    }

    public void setGraceSeconds(long graceSeconds) {
        this.graceSeconds = graceSeconds;
    }

    public long getRetentionMinutes() {
        return retentionMinutes;
    }

    public void setRetentionMinutes(long retentionMinutes) {
        this.retentionMinutes = retentionMinutes;
    }

    public List<Baseline> getBaselines() {
        return baselines;
    }

    public void setBaselines(List<Baseline> baselines) {
        this.baselines = baselines;
    }

    public static class Baseline {

        private String sku;
        private double baselineVelocityPerHour;

        public String getSku() {
            return sku;
        }

        public void setSku(String sku) {
            this.sku = sku;
        }

        public double getBaselineVelocityPerHour() {
            return baselineVelocityPerHour;
        }

        public void setBaselineVelocityPerHour(double baselineVelocityPerHour) {
            this.baselineVelocityPerHour = baselineVelocityPerHour;
        }
    }
}
