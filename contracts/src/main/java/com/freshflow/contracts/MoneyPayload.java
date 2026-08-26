package com.freshflow.contracts;

import java.math.BigDecimal;

/**
 * Monetary amount as it appears on the wire.
 *
 * <p>Not a domain {@code Money}: spec-03 &sect;6 states there is deliberately no shared kernel, and
 * each context re-declares its own. This type exists only so that the Published Language can carry
 * an amount across a context boundary without any context having to import another's model.
 */
public record MoneyPayload(BigDecimal amount, String currency) {
}
