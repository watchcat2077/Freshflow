/**
 * Infrastructure layer (spec-01 &sect;2.1) &mdash; persistence for the CQRS read models and agent traces, the Kafka Streams topology (Week 7) and the LLM integration (Week 9). <p>There is no {@code domain} package in this service to depend inward on (spec-01 &sect;2.3): it is a Conformist consumer (spec-01 &sect;1.5) that accepts the upstream event models from the {@code contracts} module without negotiation.
 */
package com.freshflow.insight.infrastructure;
