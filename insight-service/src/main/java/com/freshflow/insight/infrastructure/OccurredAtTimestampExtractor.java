package com.freshflow.insight.infrastructure;

import com.freshflow.contracts.ItemSoldEvent;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.processor.TimestampExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes Kafka Streams window on <b>producer-assigned event time</b>.
 *
 * <p>This class is the single most load-bearing thing in FR12, and it exists because the default
 * is wrong for this system. Kafka Streams' default extractor reads
 * {@code ConsumerRecord#timestamp()}, which for our producers is the wall-clock instant the
 * broker record was created &mdash; ingestion time. spec-01 &sect;2.6 states that windowing uses
 * {@code occurredAt}, written by the producer, and "MUST NOT rely on Kafka ingestion time". Without
 * this extractor the topology would silently satisfy neither.
 *
 * <p>The difference is not academic. A till that buffers while offline and flushes ten minutes of
 * scans at once would, under ingestion time, appear as a colossal spike in the current window; the
 * velocity would read as ten times reality and FR13's risk detector would conclude that a batch is
 * selling fine. Under event time those scans land in the windows they actually happened in.
 *
 * <p><b>On the missing-timestamp path it returns -1, which makes Kafka Streams drop the record.</b>
 * That is deliberate. The alternatives are to fall back to the record timestamp or to the current
 * partition time, and both would quietly reintroduce ingestion time for exactly the events whose
 * event time is unknown &mdash; the ones most likely to distort a window. An event that does not
 * carry the field &sect;2.6 makes mandatory is not windowable, and the honest response is to refuse
 * it loudly rather than to place it wrongly and silently.
 */
public class OccurredAtTimestampExtractor implements TimestampExtractor {

    private static final Logger log = LoggerFactory.getLogger(OccurredAtTimestampExtractor.class);

    @Override
    public long extract(ConsumerRecord<Object, Object> record, long partitionTime) {
        if (record.value() instanceof ItemSoldEvent event && event.occurredAt() != null) {
            return event.occurredAt().toEpochMilli();
        }

        log.warn("[dropped] topic={} partition={} offset={} carries no usable occurredAt; it cannot "
                        + "be windowed on event time and will NOT be placed using ingestion time "
                        + "(spec-01 s2.6)",
                record.topic(), record.partition(), record.offset());
        return -1;
    }
}
