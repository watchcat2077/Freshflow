package com.freshflow.inventory.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One consumed {@code eventId}, recorded so that a redelivery can be recognised (FR2: "idempotent
 * with respect to {@code eventId}").
 *
 * <p>Persisted rather than held in memory, and that is the point of the class. A deduction is a
 * durable change to stock; if the record of having made it lived only in memory, a restart would
 * make every redelivered event deduct a second time, and the stock figures FR13 depends on would
 * drift downward with every restart. This is the difference between this and
 * {@code sales-service}'s in-memory deduplication, where re-consuming a price rule merely
 * overwrites a snapshot with the same value.
 *
 * <p>Purely an infrastructure concern: no domain type knows this table exists.
 */
@Entity
@Table(name = "consumed_event")
public class ConsumedEventJpaEntity {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(name = "topic", nullable = false)
    private String topic;

    @Column(name = "consumed_at", nullable = false)
    private Instant consumedAt;

    protected ConsumedEventJpaEntity() {
        // required by JPA
    }

    public ConsumedEventJpaEntity(String eventId, String topic, Instant consumedAt) {
        this.eventId = eventId;
        this.topic = topic;
        this.consumedAt = consumedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getTopic() {
        return topic;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }
}
