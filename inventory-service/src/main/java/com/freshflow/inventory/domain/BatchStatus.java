package com.freshflow.inventory.domain;

/** spec-03 &sect;1 &mdash; lifecycle state of a {@link StockBatch}. */
public enum BatchStatus {
    ACTIVE,
    DEPLETED,
    WRITTEN_OFF
}
