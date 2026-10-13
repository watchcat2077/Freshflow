package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.MoneyPayload;
import com.freshflow.insight.application.BatchDetails;
import com.freshflow.insight.application.BatchDetailsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * The adapter behind {@link BatchDetailsProvider} &mdash; a synchronous
 * {@code GET /api/batches/&#123;batchId&#125;} against {@code inventory-service}
 * (spec-06 &sect;3, team ruling R6).
 *
 * <p><b>This is the one synchronous call the architecture permits, and it is permitted for exactly
 * this.</b> spec-01 &sect;1.3 allows synchronous REST "ONLY for agent tool calls from
 * {@code insight-service} to the other services"; spec-01 &sect;1.4 repeats the permission; and
 * spec-04 already documents the endpoint as "Implements FR4. Also backs the {@code getBatchDetails}
 * agent tool (FR14)". Three clauses anticipate this call. It carries the {@code unitCost} that
 * exists nowhere else in this service, because the FR13 batch-state fold drops it (spec-05
 * &sect;3.1).
 *
 * <p><b>A REST read returns the batch as it is now</b>, where the batch-state table holds it as of
 * the last event the topology folded. An agent deciding what to discount should see current stock,
 * which spec-06 &sect;3 records as a substantive advantage of this option rather than a consolation
 * for its cost.
 *
 * <h2>When inventory-service is unreachable</h2>
 * <p>The two cases are answered differently, on purpose.
 *
 * <table>
 *   <caption>Failure behaviour</caption>
 *   <tr><th>Situation</th><th>Answer</th><th>Why</th></tr>
 *   <tr><td>{@code 404}</td><td>{@link Optional#empty()}</td>
 *       <td>The port's contract: "an empty result means the batch is unknown". The tool then
 *           answers {@code error=BATCH_NOT_FOUND} and the agent may correct itself</td></tr>
 *   <tr><td>Connection refused, timeout, {@code 5xx}, unparseable body</td>
 *       <td>The exception propagates</td>
 *       <td>The batch is <b>not</b> known to be absent, and saying that it is would be a lie the
 *           agent could act on. Propagating leaves the trigger un-acknowledged, so at-least-once
 *           delivery retries it once Inventory is back; no trace is written, which is correct,
 *           because nothing was decided</td></tr>
 * </table>
 *
 * <p>The port's javadoc is explicit that an empty result "is not an error and must not be turned
 * into one". The converse holds with equal force and is the harder half: an error must not be
 * turned into an empty result.
 *
 * <p><b>The base URL is configuration</b> ({@code freshflow.insight.inventory.base-url}, from
 * {@code ${INVENTORY_BASE_URL}}), never a literal. spec-01 &sect;1.2 fixes the port at 8081 for
 * local running and nothing else should fix it in code.
 *
 * <p><b>Two calls per deliberation, and no cache.</b> The {@code getBatchDetails} tool makes one,
 * and {@code MarkdownAgentOrchestrator} makes another to derive {@code effectiveTo} from the
 * batch's expiry. The duplication is deliberate. A cache would have
 * to choose a lifetime, and the second read is the one the published {@code effectiveTo} is built
 * from &mdash; reading it fresh is worth one HTTP round trip on a path that has just spent seconds
 * in a language model.
 */
public class RestBatchDetailsProvider implements BatchDetailsProvider {

    private static final Logger log = LoggerFactory.getLogger(RestBatchDetailsProvider.class);

    /** spec-04 {@code getBatch}. FR4's endpoint, which spec-04 also names as backing FR14. */
    static final String BATCH_PATH = "/api/batches/{batchId}";

    private final RestClient restClient;

    public RestBatchDetailsProvider(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public Optional<BatchDetails> findBatch(String batchId) {
        if (batchId == null || batchId.isBlank()) {
            return Optional.empty();
        }

        try {
            BatchResponse response = restClient.get()
                    .uri(BATCH_PATH, batchId)
                    .retrieve()
                    // A 404 is an answer, not a failure: the batch is genuinely not there.
                    .onStatus(status -> status.value() == 404, (request, ignored) -> { })
                    .body(BatchResponse.class);

            return Optional.ofNullable(response).map(RestBatchDetailsProvider::toDetails);
        } catch (RestClientException e) {
            // Deliberately not swallowed into an empty Optional. See the class javadoc: an
            // unreachable Inventory is not a batch that does not exist.
            log.error("[fr14-batch-lookup-failed] batchId={} reason={}", batchId, e.getMessage());
            throw e;
        }
    }

    private static BatchDetails toDetails(BatchResponse response) {
        MoneyPayload unitCost = response.unitCost() == null
                ? new MoneyPayload(BigDecimal.ZERO, "")
                : new MoneyPayload(response.unitCost().amount(), response.unitCost().currency());

        return new BatchDetails(response.batchId(), response.sku(), response.remainingQuantity(),
                response.expiryAt(), unitCost);
    }

    /**
     * spec-04's {@code BatchResponse}, reduced to the fields FR14 reads.
     *
     * <p>A local record rather than a shared type, because the Insight context is a Conformist
     * (spec-01 &sect;1.5): it accepts the upstream model as published and does not negotiate over
     * it. Importing {@code inventory-service}'s DTO would make two services share a presentation
     * type, which is the coupling bounded contexts exist to prevent; the {@code contracts} module
     * is for events, and this is an HTTP response. Unlisted fields are ignored.
     */
    record BatchResponse(String batchId,
                         String sku,
                         int remainingQuantity,
                         Instant expiryAt,
                         Money unitCost) {

        record Money(BigDecimal amount, String currency) {
        }
    }
}
