/**
 * Domain layer (spec-01 &sect;2.1). Deliberately thin: spec-01 &sect;2.3 designates this service Transaction Script, so this package holds plain data structures only &mdash; {@code SaleTransaction}, {@code LineItem}, {@code PriceSnapshot} (spec-03 &sect;2). <p>NO aggregate roots and NO repository interfaces are to be declared here (spec-01 &sect;2.3). Behaviour belongs in the application-layer transaction script, not in these types. <p>The dependency rule of spec-01 &sect;2.2 still applies in full: this package imports nothing outside itself.
 */
package com.freshflow.sales.domain;
