package com.freshflow.inventory.application;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Command (CLAUDE.md: "commands are simple DTOs handled in the application service layer; they do
 * not enter the domain layer").
 *
 * <p>Deliberately primitive-typed. It carries what arrived over the wire, unvalidated and not yet
 * converted into value objects. Turning it into domain types is the handler's job, and that
 * conversion is where the first rules bite.
 */
public record RegisterGoodsReceiptCommand(String sku,
                                          Integer quantity,
                                          Instant expiryAt,
                                          BigDecimal unitCostAmount,
                                          String unitCostCurrency,
                                          String supplierRef) {
}
