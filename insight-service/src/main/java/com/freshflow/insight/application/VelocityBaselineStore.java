package com.freshflow.insight.application;

import java.util.OptionalDouble;

/**
 * Outbound port &mdash; read access to the stored velocity baseline for a SKU, in units per hour.
 *
 * <p>FR12: "Compares against a stored velocity baseline held in a {@code KTable}." The adapter
 * behind this port reads exactly that KTable's state store by Interactive Query. The port hides
 * it, so the comparison itself can be exercised without a broker.
 *
 * <p>Returns {@link OptionalDouble} rather than a primitive with a zero default. A SKU with no
 * baseline is a real and reportable state &mdash; "we do not know this SKU's normal rate" &mdash;
 * and it is not the same claim as "this SKU normally sells nothing".
 */
public interface VelocityBaselineStore {

    OptionalDouble baselineFor(String sku);
}
