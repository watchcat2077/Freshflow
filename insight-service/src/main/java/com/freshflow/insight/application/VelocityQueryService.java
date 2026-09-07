package com.freshflow.insight.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Application service (spec-01 &sect;2.1) &mdash; the read-side use case behind
 * {@code GET /api/insight/velocity/&#123;sku&#125;} (FR12).
 *
 * <p>This service holds the one judgement FR12 needs and the stream topology cannot make:
 * <b>which window is the velocity right now</b>. It is here rather than in the Kafka Streams
 * adapter deliberately. spec-01 &sect;2.5 says this context has no business invariants because it
 * owns no transactional data, but "the newest fully elapsed window, and only if it is still
 * fresh" is a read-model rule all the same, and burying it inside a state store lookup would put
 * it somewhere no test can reach without a broker.
 *
 * <h2>The selection rule</h2>
 * <ol>
 *   <li>Consider only windows that have <b>fully elapsed</b> at {@code now}. A window that has
 *       started but not finished has had only part of its span happen, so its count would
 *       understate the rate &mdash; reporting it makes velocity appear to collapse and recover
 *       every few seconds.</li>
 *   <li>Of those, take the <b>latest</b>. With a window advancing every few seconds, that value is
 *       at most one advance-interval behind real time.</li>
 *   <li>If even that window ended <b>more than one window length ago</b>, there is no current
 *       velocity: the SKU has stopped selling, and the last complete window is history. Answer
 *       404, as spec-04 requires, rather than presenting a stale number as a live one.</li>
 * </ol>
 *
 * <p>Rule 3 is the one that matters for FR13 next week. A risk detector fed a stale velocity would
 * project sales that are not happening, and conclude that a batch is fine when it is not.
 */
@Service
public class VelocityQueryService {

    private static final int SECONDS_PER_HOUR = 3600;

    private final VelocityWindowStore velocityWindowStore;
    private final VelocityBaselineStore velocityBaselineStore;
    private final java.time.Clock clock;
    private final Duration windowSize;

    public VelocityQueryService(VelocityWindowStore velocityWindowStore,
                                VelocityBaselineStore velocityBaselineStore,
                                java.time.Clock clock,
                                @Value("${freshflow.insight.velocity.window-seconds:60}")
                                long windowSeconds) {
        this.velocityWindowStore = velocityWindowStore;
        this.velocityBaselineStore = velocityBaselineStore;
        this.clock = clock;
        this.windowSize = Duration.ofSeconds(windowSeconds);
    }

    /**
     * FR12 &mdash; the current rolling sell-through velocity for a SKU, compared against its
     * stored baseline. An empty result is spec-04's 404: no windowed data for this SKU yet.
     */
    public Optional<SkuVelocityView> findCurrentVelocity(String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }

        Instant now = clock.instant();
        // Two window lengths back: enough to see the newest elapsed window and to recognise when
        // the newest one is too old to report.
        Instant from = now.minus(windowSize).minus(windowSize);

        List<WindowedQuantity> windows = velocityWindowStore.fetchWindows(sku, from, now);

        return windows.stream()
                .filter(window -> !window.windowEnd().isAfter(now))                  // rule 1
                .max(Comparator.comparing(WindowedQuantity::windowEnd))              // rule 2
                .filter(window -> !window.windowEnd().isBefore(now.minus(windowSize))) // rule 3
                .map(window -> toView(sku, window));
    }

    private SkuVelocityView toView(String sku, WindowedQuantity window) {
        double windowHours = (double) Duration.between(window.windowStart(), window.windowEnd())
                .toSeconds() / SECONDS_PER_HOUR;
        double velocityPerHour = windowHours > 0 ? window.quantitySold() / windowHours : 0.0;

        // FR12's comparison. Absent baseline means absent comparison -- see SkuVelocityView.
        OptionalDouble baseline = velocityBaselineStore.baselineFor(sku);
        Double baselineVelocity = null;
        Double velocityRatio = null;
        if (baseline.isPresent() && baseline.getAsDouble() > 0) {
            baselineVelocity = baseline.getAsDouble();
            velocityRatio = velocityPerHour / baselineVelocity;
        }

        return new SkuVelocityView(sku, window.windowStart(), window.windowEnd(),
                window.quantitySold(), velocityPerHour, baselineVelocity, velocityRatio);
    }
}
