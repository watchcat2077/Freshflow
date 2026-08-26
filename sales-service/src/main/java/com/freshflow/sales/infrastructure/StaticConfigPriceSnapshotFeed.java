package com.freshflow.sales.infrastructure;

import com.freshflow.sales.application.PriceSnapshotFeed;
import com.freshflow.sales.domain.Money;
import com.freshflow.sales.domain.PriceSnapshot;
import com.freshflow.sales.domain.Sku;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Week 5 adapter for the {@code PriceSnapshotFeed} inbound port (spec-01 &sect;1.3).
 *
 * <p>Seeds the snapshot from static configuration in {@code application.yml}. No broker is
 * involved, because none runs this week.
 *
 * <p>spec-01 &sect;1.3 is explicit that this is "a Ports and Adapters substitution, not an interim
 * workaround": in Week 6 the {@code price-rule-published} adapter replaces this class behind the
 * same unchanged port, and no application-layer code changes when it does.
 *
 * <p>Every snapshot is built through {@link PriceSnapshot#withoutMarkdown}, which encodes spec-03
 * &sect;2's rule that where no markdown is in force the effective price equals the base price
 * &mdash; "the only case that exists in Week 5".
 */
@Component
@EnableConfigurationProperties(PriceSnapshotSeedProperties.class)
public class StaticConfigPriceSnapshotFeed implements PriceSnapshotFeed {

    private static final Logger log = LoggerFactory.getLogger(StaticConfigPriceSnapshotFeed.class);

    private final PriceSnapshotSeedProperties properties;
    private final Map<String, PriceSnapshot> snapshotsBySku = new HashMap<>();

    public StaticConfigPriceSnapshotFeed(PriceSnapshotSeedProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void loadSeed() {
        for (PriceSnapshotSeedProperties.PriceSnapshotSeed seed : properties.getPriceSnapshotSeed()) {
            String currency = seed.getCurrency();
            PriceSnapshot snapshot = PriceSnapshot.withoutMarkdown(
                    Sku.of(seed.getSku()),
                    Money.of(seed.getBasePrice(), currency),
                    Money.of(seed.getUnitCost(), currency),
                    seed.getCategory());
            snapshotsBySku.put(seed.getSku(), snapshot);
        }
        log.info("Loaded {} price snapshot(s) from freshflow.sales.price-snapshot-seed",
                snapshotsBySku.size());
    }

    @Override
    public Optional<PriceSnapshot> findBySku(Sku sku) {
        return Optional.ofNullable(snapshotsBySku.get(sku.value()));
    }
}
