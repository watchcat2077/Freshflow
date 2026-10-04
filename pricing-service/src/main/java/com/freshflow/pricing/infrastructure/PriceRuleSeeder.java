package com.freshflow.pricing.infrastructure;

import com.freshflow.pricing.application.PriceRulePublicationService;
import com.freshflow.pricing.domain.MarginFloor;
import com.freshflow.pricing.domain.Money;
import com.freshflow.pricing.domain.PriceRule;
import com.freshflow.pricing.domain.Sku;
import com.freshflow.pricing.infrastructure.repository.PriceRuleRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads the configured price rules into the repository at startup.
 *
 * <p>An infrastructure adapter: it turns a configuration source into domain objects and hands them
 * to the {@code PriceRuleRepository} port. The application and domain layers are unaware that
 * configuration is where these came from, which is what let FR8's {@code PUT /api/prices/{sku}}
 * (added 4 October 2026) sit beside this seeder without touching either.
 *
 * <p>Note that it builds each rule through {@link PriceRule#of}, so a malformed seed is rejected by
 * the same domain invariants that any other source would face. Configuration is not a way in
 * around the domain.
 *
 * <p><b>Week 6.</b> It also triggers the announcement of the seeded rules on
 * {@code price-rule-published} (spec-01 &sect;2.6), which is how {@code sales-service} obtains its
 * price data now that the static seed on that side is retired. Two things about how:
 * <ul>
 *   <li>It <i>triggers</i> the use case, it does not implement it. The publication is orchestrated
 *       by {@link PriceRulePublicationService} in the application layer, which asks the domain for
 *       the event and hands it to an outbound port. Infrastructure depending inward on
 *       {@code application} is the direction spec-01 &sect;2.2 requires.</li>
 *   <li>It announces on {@link ApplicationReadyEvent}, not from {@code @PostConstruct}. Seeding is
 *       an in-process write and can happen during construction; publishing crosses a process
 *       boundary and must wait until the context, and with it the binder, is actually up.</li>
 * </ul>
 */
@Component
@EnableConfigurationProperties(PriceRuleSeedProperties.class)
public class PriceRuleSeeder {

    private static final Logger log = LoggerFactory.getLogger(PriceRuleSeeder.class);

    private final PriceRuleSeedProperties properties;
    private final PriceRuleRepository priceRuleRepository;
    private final PriceRulePublicationService priceRulePublicationService;

    public PriceRuleSeeder(PriceRuleSeedProperties properties,
                           PriceRuleRepository priceRuleRepository,
                           PriceRulePublicationService priceRulePublicationService) {
        this.properties = properties;
        this.priceRuleRepository = priceRuleRepository;
        this.priceRulePublicationService = priceRulePublicationService;
    }

    @PostConstruct
    @Transactional
    public void seed() {
        for (PriceRuleSeedProperties.PriceRuleSeed seed : properties.getPriceRules()) {
            String currency = seed.getCurrency();
            PriceRule rule = PriceRule.of(
                    Sku.of(seed.getSku()),
                    Money.of(seed.getBasePrice(), currency),
                    Money.of(seed.getUnitCost(), currency),
                    seed.getCategory(),
                    MarginFloor.of(seed.getMarginFloorRatio()));
            priceRuleRepository.save(rule);
        }
        log.info("Seeded {} price rule(s) from freshflow.pricing.price-rules",
                properties.getPriceRules().size());
    }

    /**
     * FR8 + spec-01 &sect;2.6 &mdash; announce every seeded rule on {@code price-rule-published}
     * once the context is up.
     *
     * <p>Republishing on every start is deliberate. The topic is the only channel by which price
     * data reaches {@code sales-service} (spec-01 &sect;1.3), so the standing rules must be on it,
     * and a consumer that replays from the beginning and deduplicates on {@code eventId} handles
     * the repetition exactly as spec-01 &sect;2.6 prescribes.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void announceSeededRules() {
        List<String> skus = properties.getPriceRules().stream()
                .map(PriceRuleSeedProperties.PriceRuleSeed::getSku)
                .toList();

        int published = priceRulePublicationService.announceAll(skus);
        log.info("Announced {} of {} seeded price rule(s) on price-rule-published",
                published, skus.size());
    }
}
