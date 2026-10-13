package com.freshflow.inventory.application;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Command (spec-01 s2.1, L5 slide 7: "commands are simple DTOs handled in the application service layer; they do
 * not enter the domain layer").
 *
 * <p>Deliberately primitive-typed. It carries what arrived over the wire, unvalidated and still in
 * wire types. Turning it into domain value objects is the handler's job, and that conversion is
 * where the first rules bite.
 */
public record RegisterGoodsReceiptCommand(String sku,
                                          Integer quantity,
                                          Instant expiryAt,
                                          BigDecimal unitCostAmount,
                                          String unitCostCurrency,
                                          String supplierRef) {
}
