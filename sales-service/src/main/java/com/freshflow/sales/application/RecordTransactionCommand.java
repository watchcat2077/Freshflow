package com.freshflow.sales.application;

import java.time.Instant;
import java.util.List;

/**
 * Command (CLAUDE.md: simple DTOs handled in the application service layer, which do not enter the
 * domain layer). Primitive-typed: it carries what arrived over the wire, not yet validated.
 */
public record RecordTransactionCommand(String tillId, Instant occurredAt, List<Line> lineItems) {

    public record Line(String sku, Integer quantity) {
    }
}
