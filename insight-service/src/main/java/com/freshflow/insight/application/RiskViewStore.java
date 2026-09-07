package com.freshflow.insight.application;

import java.util.List;

/**
 * Outbound port &mdash; read access to the risk assessments the Kafka Streams topology materialises
 * (FR13).
 *
 * <p>Declared in {@code application}, per spec-01 &sect;2.3's table for a service with no domain
 * package. The adapter behind it reads the state store by Interactive Query.
 *
 * <p>It returns <b>every</b> batch the topology has assessed, at risk or not, and leaves the
 * filtering to {@link RiskQueryService}. Same reasoning as {@code VelocityWindowStore}: what
 * counts as reportable is a read-model decision, and a port that pre-filtered would put it behind
 * a state store no test can reach without a broker.
 */
public interface RiskViewStore {

    List<BatchRiskView> findAll();
}
