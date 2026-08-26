package com.freshflow.inventory.application;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Read model returned by the application layer.
 *
 * <p>Exists so that {@code presentation} never handles a {@link com.freshflow.inventory.domain}
 * aggregate directly. The layering would permit it &mdash; outer may depend on inner &mdash; but
 * handing an aggregate to a JSON serialiser is how persistence-ignorant domain objects quietly
 * acquire serialisation concerns.
 */
public record BatchView(String batchId,
                        String sku,
                        int receivedQuantity,
                        int remainingQuantity,
                        Instant expiryAt,
                        BigDecimal unitCostAmount,
                        String unitCostCurrency,
                        String status,
                        long remainingShelfLifeHours) {
}
