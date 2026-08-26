/**
 * Application service layer (spec-01 &sect;2.1) &mdash; and, for this service, the Transaction Script itself (spec-01 &sect;2.3). Sales has low domain complexity: it records a transaction and applies the price currently in force. Aggregates and repository abstractions would be over-design and are forbidden here. <p>Consequently this is where the service's business invariants live (spec-01 &sect;2.5): the transaction script is the enforcement point, because &sect;2.3 denies this service an aggregate to hold them. <p>The {@code PriceSnapshotFeed} inbound port (spec-01 &sect;1.3) is the only channel by which price data enters this service. Introducing a synchronous {@code pricing-service} client is a specification violation.
 */
package com.freshflow.sales.application;
