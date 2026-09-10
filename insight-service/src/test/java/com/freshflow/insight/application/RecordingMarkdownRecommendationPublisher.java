package com.freshflow.insight.application;

import com.freshflow.contracts.MarkdownRecommendedEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Test adapter for the {@code MarkdownRecommendationPublisher} port &mdash; it keeps what it was
 * asked to publish instead of publishing it.
 *
 * <p>It records a list rather than a last value on purpose. The assertions that matter for FR14 are
 * about <b>how many</b> events crossed into the Pricing context: exactly one per proposal, none at
 * all when the agent declines, and still exactly one when the same trigger is delivered twice. A
 * double that remembered only the most recent publication could not tell any of those apart.
 */
public final class RecordingMarkdownRecommendationPublisher
        implements MarkdownRecommendationPublisher {

    private final List<MarkdownRecommendedEvent> published = new ArrayList<>();

    @Override
    public void publish(MarkdownRecommendedEvent event) {
        published.add(event);
    }

    /** Everything published, in order. */
    public List<MarkdownRecommendedEvent> published() {
        return List.copyOf(published);
    }
}
