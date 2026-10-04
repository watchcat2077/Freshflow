package com.freshflow.insight.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for one recorded tool call, owned by a {@link BriefingEntity} (FR15).
 *
 * <p>A separate table from {@link ToolCallEntity} rather than a shared one with a discriminator,
 * because the two calls belong to two different records with two different owners, and a row that
 * could belong to either would need a column saying which &mdash; a column no query would ever
 * read. The shape is the same as FR14's, deliberately: spec-04 declares one {@code toolCalls} item
 * for both responses.
 *
 * <p>{@code sequence} is stored in {@code call_sequence}, for the reason {@link ToolCallEntity}
 * gives: {@code SEQUENCE} is reserved in SQL. The attribute keeps spec-04's name. The identifier is
 * generated and carries no meaning; nothing outside this table refers to a briefing's tool call on
 * its own.
 */
@Entity
@Table(name = "briefing_tool_call")
public class BriefingToolCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "briefing_id", nullable = false)
    private BriefingEntity briefing;

    @Column(name = "call_sequence", nullable = false)
    private int sequence;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Column(name = "arguments", length = 2000)
    private String arguments;

    @Column(name = "result", length = 4000)
    private String result;

    @Column(name = "called_at", nullable = false)
    private Instant timestamp;

    protected BriefingToolCallEntity() {
        // required by JPA
    }

    public BriefingToolCallEntity(int sequence, String toolName, String arguments, String result,
                                  Instant timestamp) {
        this.sequence = sequence;
        this.toolName = toolName;
        this.arguments = arguments;
        this.result = result;
        this.timestamp = timestamp;
    }

    void setBriefing(BriefingEntity briefing) {
        this.briefing = briefing;
    }

    public Long getId() {
        return id;
    }

    public BriefingEntity getBriefing() {
        return briefing;
    }

    public int getSequence() {
        return sequence;
    }

    public String getToolName() {
        return toolName;
    }

    public String getArguments() {
        return arguments;
    }

    public String getResult() {
        return result;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
