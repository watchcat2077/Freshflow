/**
 * Application service layer (spec-01 &sect;2.1). Read-side use case orchestration over the materialised views, plus agent orchestration from Week 9. <p>NOTE (spec-01 &sect;2.3): this service has NO {@code domain} package. It owns no transactional business data and therefore no business invariants (spec-01 &sect;2.5). Complexity lives in the stream topology, not in a domain model.
 */
package com.freshflow.insight.application;
