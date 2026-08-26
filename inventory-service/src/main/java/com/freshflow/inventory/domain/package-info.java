/**
 * Domain layer (spec-01 &sect;2.1) &mdash; Domain Model pattern (spec-01 &sect;2.3): aggregate roots, entities, value objects, domain events, domain services and repository interfaces. <p>HARD CONSTRAINT (spec-01 &sect;2.2): this package depends on NOTHING. It must not import from {@code presentation}, {@code application}, {@code infrastructure}, Spring, JPA, Jackson or Kafka. Domain classes are POJOs and persistence ignorant &mdash; no JPA annotations. <p>Business invariants live here (spec-01 &sect;2.5): stock cannot go negative, expired batches cannot be sold, deduction follows FEFO.
 */
package com.freshflow.inventory.domain;
