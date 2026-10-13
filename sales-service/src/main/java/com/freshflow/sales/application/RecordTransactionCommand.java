package com.freshflow.sales.application;

import java.time.Instant;
import java.util.List;

/**
 * Command (spec-01 s2.1, L5 slide 7: simple DTOs handled in the application service layer, which do not enter the
 * domain layer). Primitive-typed: it carries what arrived over the wire, before validation.
 */
public record RecordTransactionCommand(String tillId, Instant occurredAt, List<Line> lineItems) {

    public record Line(String sku, Integer quantity) {
    }
}
