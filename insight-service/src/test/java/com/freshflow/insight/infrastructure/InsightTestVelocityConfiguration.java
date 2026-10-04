package com.freshflow.insight.infrastructure;

import com.freshflow.insight.application.RiskViewStore;
import com.freshflow.insight.application.VelocityBaselineStore;
import com.freshflow.insight.application.VelocityWindowStore;
import com.freshflow.insight.application.WindowedQuantity;
import com.freshflow.insight.domain.BatchRiskView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Test-scope adapters for the two read-side ports of FR12, plus a fixed clock.
 *
 * <p>Under the {@code test} profile {@code freshflow.messaging.kafka.enabled} is false, so the
 * Kafka Streams topology is not bound and these MockMvc tests need no broker. Substituting an
 * adapter behind a port is the same property the Week 5 &rarr; Week 6 swap relied on; here it also
 * makes the read model <b>deterministic</b>, which a live windowed aggregate can never be.
 *
 * <p>The window fixtures below are the point of the exercise. {@link VelocityWindowStore} hands
 * back every window it holds, ignoring the requested range, precisely so that the selection rule
 * &mdash; which window is "the velocity right now" &mdash; is the thing under test rather than
 * something the fake quietly does on the service's behalf.
 *
 * <p>Everything is anchored to {@link #NOW} = 2026-09-07T12:00:30Z, with a 60-second window.
 */
@Configuration
@Profile("test")
public class InsightTestVelocityConfiguration {

    /** The instant every fixture below is relative to. */
    public static final Instant NOW = Instant.parse("2026-09-07T12:00:30Z");

    /** Has three windows and a baseline. Only one of the three may be reported. */
    public static final String SKU_ACTIVE = "SKU-YOG-1KG";

    /** Sold once, but long enough ago that there is no current velocity to report. */
    public static final String SKU_STALE = "SKU-STALE-1KG";

    /** Selling now, but no baseline is stored for it. */
    public static final String SKU_NO_BASELINE = "SKU-NOBASE-1KG";

    private static WindowedQuantity window(String startIso, String endIso, long quantity) {
        return new WindowedQuantity(Instant.parse(startIso), Instant.parse(endIso), quantity);
    }

    /**
     * Deliberately named something other than {@code clock}, so that it stands <i>beside</i> the
     * production {@code ClockConfiguration#clock} bean rather than colliding with it by name, and
     * wins injection on {@link Primary}. FR12 is entirely a statement about time; a test that
     * cannot fix "now" cannot assert any of it.
     */
    @Bean
    @Primary
    public Clock fixedTestClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Bean
    public VelocityWindowStore velocityWindowStore() {
        Map<String, List<WindowedQuantity>> windows = new HashMap<>();

        windows.put(SKU_ACTIVE, List.of(
                // Fully elapsed and the most recent such window. This is the one that must win.
                window("2026-09-07T11:59:25Z", "2026-09-07T12:00:25Z", 5),
                // Started, but has not finished yet at NOW. Reporting it would under-report the
                // rate, because only part of its span has happened.
                window("2026-09-07T11:59:35Z", "2026-09-07T12:00:35Z", 1),
                // Elapsed, but older. A big number here would be caught if selection took the
                // maximum quantity, or the first entry, instead of the latest window.
                window("2026-09-07T11:58:00Z", "2026-09-07T11:59:00Z", 99)));

        // Elapsed more than one window length before NOW: stale, not current.
        windows.put(SKU_STALE, List.of(
                window("2026-09-07T11:54:00Z", "2026-09-07T11:55:00Z", 7)));

        windows.put(SKU_NO_BASELINE, List.of(
                window("2026-09-07T11:59:25Z", "2026-09-07T12:00:25Z", 2)));

        return (sku, from, to) -> new ArrayList<>(windows.getOrDefault(sku, List.of()));
    }

    /**
     * FR13 fixtures. Three assessed batches, only two of them flagged: a handler that returned
     * everything the store held, or that sorted the wrong way, fails the API test.
     *
     * <p>BAT-WORST matches the numbers in {@code RiskAssessmentPolicyTest}'s first case exactly --
     * 40 units, 10 hours, 2 an hour -- so the two tests describe the same scenario from the two
     * ends of the service.
     */
    @Bean
    public RiskViewStore riskViewStore() {
        List<BatchRiskView> views = List.of(
                new BatchRiskView("BAT-MILD", SKU_ACTIVE, 20, 10, 1.0, 10.0, 10.0,
                        Instant.parse("2026-09-07T12:00:00Z"), true),
                new BatchRiskView("BAT-WORST", SKU_ACTIVE, 40, 10, 2.0, 20.0, 20.0,
                        Instant.parse("2026-09-07T12:00:00Z"), true),
                new BatchRiskView("BAT-SAFE", SKU_ACTIVE, 40, 10, 5.0, 50.0, -10.0,
                        Instant.parse("2026-09-07T12:00:00Z"), false));
        return () -> views;
    }

    @Bean
    public VelocityBaselineStore velocityBaselineStore() {
        Map<String, Double> baselines = Map.of(SKU_ACTIVE, 120.0);
        return sku -> baselines.containsKey(sku)
                ? OptionalDouble.of(baselines.get(sku))
                : OptionalDouble.empty();
    }
}
