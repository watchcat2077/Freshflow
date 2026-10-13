package com.freshflow.sales.application;

import com.freshflow.sales.domain.LineItem;
import com.freshflow.sales.domain.Money;
import com.freshflow.sales.domain.PriceSnapshot;
import com.freshflow.sales.domain.SaleTransaction;
import com.freshflow.sales.domain.Sku;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Transaction Script for the Sales context (spec-01 &sect;2.3).
 *
 * <p><b>This class is the enforcement point for this service's business invariants</b>, and that is
 * deliberate rather than a shortcut. spec-01 &sect;2.5 says so explicitly: for a Transaction Script
 * service "the transaction script is the enforcement point, because &sect;2.3 denies this service an
 * aggregate to hold them". The rules below are therefore in the right layer. They are still not in
 * a controller, which is what &sect;2.5 actually forbids.
 *
 * <p>Implements FR5 (record a transaction, publish one {@code item-sold} per line item), FR6a
 * (apply the base price in force at the moment of scan) and, since 4 October 2026, FR6b (apply a
 * live markdown at the moment of scan). FR6b changed one step of this script and nothing about
 * its shape: the snapshot now carries any markdown Pricing has authorised for the SKU, and the
 * script asks it for the price in force at the transaction's {@code occurredAt} instead of
 * reading the base price unconditionally.
 *
 * <p>What it must never do: call {@code pricing-service}. spec-01 &sect;1.3 permits no synchronous
 * path from Sales to Pricing. Price data arrives only through
 * {@link PriceSnapshotFeed}.
 */
@Service
public class TransactionApplicationService {

    private final PriceSnapshotFeed priceSnapshotFeed;
    private final SaleTransactionRecorder saleTransactionRecorder;
    private final SaleEventPublisher saleEventPublisher;
    private final Clock clock;

    public TransactionApplicationService(PriceSnapshotFeed priceSnapshotFeed,
                                         SaleTransactionRecorder saleTransactionRecorder,
                                         SaleEventPublisher saleEventPublisher,
                                         Clock clock) {
        this.priceSnapshotFeed = priceSnapshotFeed;
        this.saleTransactionRecorder = saleTransactionRecorder;
        this.saleEventPublisher = saleEventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ApplicationResult<TransactionView> recordTransaction(RecordTransactionCommand command) {
        if (command == null) {
            return ApplicationResult.failure("VALIDATION_FAILED", "Request body is required.");
        }
        if (command.tillId() == null || command.tillId().isBlank()) {
            return ApplicationResult.failure("VALIDATION_FAILED", "Field 'tillId' is required.");
        }
        // spec-04 CreateTransactionRequest: lineItems has minItems 1.
        if (command.lineItems() == null || command.lineItems().isEmpty()) {
            return ApplicationResult.failure("EMPTY_LINE_ITEMS",
                    "A transaction must contain at least one line item.");
        }

        // FR5: "Each event carries producer-assigned occurredAt". The producer assigns it here, and
        // falls back to now only when the caller omitted it -- never to broker ingestion time.
        Instant occurredAt = command.occurredAt() != null ? command.occurredAt() : clock.instant();

        List<LineItem> lineItems = new ArrayList<>();
        Money total = null;

        for (RecordTransactionCommand.Line line : command.lineItems()) {
            if (line == null || line.sku() == null || line.sku().isBlank()) {
                return ApplicationResult.failure("VALIDATION_FAILED",
                        "Each line item requires a 'sku'.");
            }
            if (line.quantity() == null) {
                return ApplicationResult.failure("VALIDATION_FAILED",
                        "Each line item requires a 'quantity'.");
            }
            if (line.quantity() < 1) {
                return ApplicationResult.failure("QUANTITY_NOT_POSITIVE",
                        "Line item quantity must be at least 1.");
            }

            Sku sku = Sku.of(line.sku());
            Optional<PriceSnapshot> snapshot = priceSnapshotFeed.findBySku(sku);
            if (snapshot.isEmpty()) {
                // FR6a: "A scan of a SKU absent from the snapshot is a 400, never a silent zero
                // price." Returning here is what makes the second half of that true -- there is no
                // path from this branch to a priced line item.
                return ApplicationResult.failure("UNKNOWN_SKU",
                        "No price is in force for SKU '" + line.sku()
                                + "'. The SKU is absent from the local price snapshot.");
            }

            // FR6a and FR6b: the price recorded on the line is the price in force at the
            // transaction's occurredAt -- never at the time of processing (spec-03 s2). The
            // snapshot knows its base price, any markdown Pricing has authorised for the SKU and
            // that markdown's promotion window; it is asked about the instant of the scan and
            // answers with the price and whether a markdown produced it. Inside the window that is
            // the discounted price and true (FR6b); otherwise -- before the window opens, or after
            // effectiveTo with no further event having arrived -- the base price and false (FR6a).
            PriceSnapshot.PriceInForce priceInForce = snapshot.get().priceAt(occurredAt);
            LineItem lineItem = new LineItem(newLineItemId(), sku, line.quantity(),
                    priceInForce.unitPrice(), priceInForce.markdownApplied());
            lineItems.add(lineItem);

            // FR6a: totalAmount is the sum of unitPrice x quantity across all line items.
            total = (total == null) ? lineItem.lineTotal() : total.add(lineItem.lineTotal());
        }

        SaleTransaction transaction = new SaleTransaction(
                newTransactionId(), command.tillId(), occurredAt, lineItems, total);

        saleTransactionRecorder.record(transaction);
        saleEventPublisher.publishItemSold(transaction);

        return ApplicationResult.success(toView(transaction));
    }

    private String newTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private String newLineItemId() {
        return "LI-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private TransactionView toView(SaleTransaction transaction) {
        List<TransactionView.Line> lines = transaction.lineItems().stream()
                .map(item -> new TransactionView.Line(
                        item.sku().value(),
                        item.quantity(),
                        item.unitPrice().amount(),
                        item.unitPrice().currency(),
                        item.markdownApplied()))
                .toList();

        return new TransactionView(
                transaction.transactionId(),
                transaction.tillId(),
                transaction.occurredAt(),
                transaction.totalAmount().amount(),
                transaction.totalAmount().currency(),
                lines);
    }
}
