/**
 * Domain layer (spec-01 &sect;2.1) &mdash; Domain Model pattern (spec-01 &sect;2.3): aggregate roots, entities, value objects, domain events, domain services and repository interfaces. <p>HARD CONSTRAINT (spec-01 &sect;2.2): this package depends on NOTHING. It must not import from {@code presentation}, {@code application}, {@code infrastructure}, Spring, JPA, Jackson or Kafka. Domain classes are POJOs and persistence ignorant &mdash; no JPA annotations. <p>The margin floor is a business invariant and is enforced here (spec-01 &sect;2.5), never in a controller and never delegated to an LLM prompt. It applies identically whether a markdown proposal originated from a human or from the agent &mdash; the Anti-Corruption Layer of spec-01 &sect;1.5.
 */
package com.freshflow.pricing.domain;
