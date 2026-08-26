/**
 * Infrastructure layer (spec-01 &sect;2.1) &mdash; JPA entities, persistence, messaging bindings, external adapters. <p>Under the Transaction Script pattern the application service orchestrates directly against JPA entities (spec-03 &sect;2); there is no repository abstraction to implement. <p>This is also the home of the {@code PriceSnapshotFeed} adapters (spec-01 &sect;1.3): the Week 5 adapter seeds the snapshot from static configuration in {@code application.yml}; the Week 6 adapter subscribes to {@code price-rule-published} and replaces it behind the unchanged port.
 */
package com.freshflow.sales.infrastructure;
