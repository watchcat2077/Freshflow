package com.freshflow.pricing.infrastructure;

import com.freshflow.pricing.domain.MarginFloor;
import com.freshflow.pricing.domain.Money;
import com.freshflow.pricing.domain.PriceRule;
import com.freshflow.pricing.domain.PriceRuleRepository;
import com.freshflow.pricing.domain.Sku;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the configured price rules into the repository at startup.
 *
 * <p>An infrastructure adapter: it turns a configuration source into domain objects and hands them
 * to the domain's own repository interface. The application and domain layers are unaware that
 * configuration is where these came from, which is what will let FR8's full CRUD
 * ({@code [POST-MVP]}) replace this without touching either.
 *
 * <p>Note that it builds each rule through {@link PriceRule#of}, so a malformed seed is rejected by
 * the same domain invariants that any other source would face. Configuration is not a way in
 * around the domain.
 */
@Component
@EnableConfigurationProperties(PriceRuleSeedProperties.class)
public class PriceRuleSeeder {

    private static final Logger log = LoggerFactory.getLogger(PriceRuleSeeder.class);

    private final PriceRuleSeedProperties properties;
    private final PriceRuleRepository priceRuleRepository;

    public PriceRuleSeeder(PriceRuleSeedProperties properties, PriceRuleRepository priceRuleRepository) {
        this.properties = properties;
        this.priceRuleRepository = priceRuleRepository;
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
}
