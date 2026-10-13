package com.freshflow.sales.application;

import com.freshflow.sales.domain.SaleTransaction;

/**
 * Outbound port for recording a completed sale (FR5 &mdash; "Record a sale transaction"; spec-01
 * &sect;1.4 makes {@code sales-service} the owner of {@code SaleTransaction} and {@code LineItem}).
 *
 * <p>Write-only and single-method by design. It is NOT a repository: there is no {@code findById},
 * no query, and no aggregate behind it. spec-01 &sect;2.3 rules out aggregates and repositories for
 * this service, and FR7 &mdash; querying transaction history &mdash; is {@code [POST-MVP]}, so
 * nothing reads these rows back and no read method is offered.
 *
 * <p>It exists because the alternative is worse: spec-03 &sect;2 says the application service
 * "orchestrates directly against JPA entities", but those entities live in {@code infrastructure},
 * and importing them here would make {@code application} depend on {@code infrastructure} against
 * spec-01 &sect;2.2, which is a HARD CONSTRAINT. A one-method outbound port keeps the dependency
 * pointing the right way.
 */
public interface SaleTransactionRecorder {

    void record(SaleTransaction transaction);
}
