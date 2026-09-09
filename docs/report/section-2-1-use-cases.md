\# Section 2.1 — Use Cases



\## UC-01 — Register a goods receipt



| Field | Value |

|---|---|

| Requirement | FR1 |

| Actor | Store manager |

| Preconditions | The SKU exists in the product range |



\### Main flow



1\. The store manager submits a delivery record with SKU, quantity, expiry date and unit cost.

2\. The system validates that quantity is positive.

3\. The system validates that the expiry date is in the future.

4\. The system creates a stock batch and assigns it an identifier.

5\. The system publishes a batch-received event.

6\. The system returns the batch identifier to the store manager.



\### Alternative flows



\- 2a. Quantity is zero or negative. The system rejects the request with 400 and records no batch.

\- 3a. The expiry date is in the past. The system rejects the request with 400 and records no batch.



\### Postconditions



A stock batch exists with status ACTIVE and remaining quantity equal to the received quantity. Downstream contexts have been notified

