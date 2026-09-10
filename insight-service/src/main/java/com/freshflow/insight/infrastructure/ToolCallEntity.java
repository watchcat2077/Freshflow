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
 * JPA entity for one recorded tool call, owned by an {@link AgentTraceEntity}.
 *
 * <p>Stored as rows rather than as a serialised blob on the trace. A blob would be less code and
 * would be wrong: spec-04 gives each call its own {@code sequence}, {@code toolName},
 * {@code arguments}, {@code result} and {@code timestamp}, so they are structure and not an opaque
 * payload, and a trace is the artefact FR14 exists to make inspectable. A reviewer investigating
 * why the agent recommended what it did should be able to query the calls, not parse them.
 *
 * <p>{@code sequence} is stored in a column called {@code call_sequence}, because {@code SEQUENCE}
 * is reserved in SQL and a column of that name would fail against some databases and not others.
 * The attribute keeps spec-04's name; only the column is renamed.
 *
 * <p>The identifier is generated and carries no meaning. Nothing outside this table refers to a
 * tool call on its own &mdash; spec-04 exposes calls only as a nested array within one trace &mdash;
 * so there is no natural key to preserve and inventing one would advertise an identity the contract
 * does not have.
 */
@Entity
@Table(name = "agent_tool_call")
public class ToolCallEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agent_trace_id", nullable = false)
    private AgentTraceEntity trace;

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

    protected ToolCallEntity() {
        // required by JPA
    }

    public ToolCallEntity(int sequence, String toolName, String arguments, String result,
                          Instant timestamp) {
        this.sequence = sequence;
        this.toolName = toolName;
        this.arguments = arguments;
        this.result = result;
        this.timestamp = timestamp;
    }

    void setTrace(AgentTraceEntity trace) {
        this.trace = trace;
    }

    public Long getId() {
        return id;
    }

    public AgentTraceEntity getTrace() {
        return trace;
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
