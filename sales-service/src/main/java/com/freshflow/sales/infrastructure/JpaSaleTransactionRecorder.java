package com.freshflow.sales.infrastructure;

import com.freshflow.sales.application.SaleTransactionRecorder;
import com.freshflow.sales.domain.SaleTransaction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter implementing the {@code SaleTransactionRecorder} outbound port.
 *
 * <p>Keeps the dependency pointing inward: {@code infrastructure} knows {@code application} and
 * {@code domain}, and neither knows this class exists. Swapping the store would touch this file and
 * nothing else.
 */
@Component
public class JpaSaleTransactionRecorder implements SaleTransactionRecorder {

    private final SaleTransactionJpaRepository jpaRepository;

    public JpaSaleTransactionRecorder(SaleTransactionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void record(SaleTransaction transaction) {
        List<LineItemEmbeddable> lines = new ArrayList<>();
        transaction.lineItems().forEach(item -> lines.add(new LineItemEmbeddable(
                item.lineItemId(),
                item.sku().value(),
                item.quantity(),
                item.unitPrice().amount(),
                item.unitPrice().currency(),
                item.markdownApplied())));

        jpaRepository.save(new SaleTransactionJpaEntity(
                transaction.transactionId(),
                transaction.tillId(),
                transaction.occurredAt(),
                transaction.totalAmount().amount(),
                transaction.totalAmount().currency(),
                lines));
    }
}
