# Idempotency Key

## 1. What is it?

An **idempotency key** is a unique identifier (usually a UUID or hash string) that a client attaches to a request so the server can detect if that exact request has already been processed before.

If the server receives two requests with the same idempotency key, it treats the second one as a duplicate and returns the **same result** as the first one — without executing the operation again.

## 2. Why use it?

- **Prevent duplicate side effects**: network retries, double-clicks, or timeouts can cause the same request to be sent more than once. Without an idempotency key, this could mean charging a customer twice, creating two orders, or sending two emails.
- **Safe retries**: clients (or API gateways) can safely retry a failed/timeout request without fear of causing duplicate actions.
- **Consistency in distributed systems**: when multiple services or instances might process the same message (e.g., from a message queue), the key ensures the operation only takes effect once.

## 3. How to use it?

**General flow:**

1. Client generates a unique key (e.g., UUID v4) for each *logical* operation (not each HTTP call — the same key is reused on retries of the same operation).
2. Client sends the key in the request, typically as a header:
   ```http
   POST /api/payments
   Idempotency-Key: 7b3f2a10-8e13-4c9a-9d3e-1a2b3c4d5e6f
   ```
3. Server checks a store (e.g., Redis, database table) to see if this key was already processed:
   - **Not found** → process the request normally, save the key + result (with an expiration/TTL), then return the result.
   - **Found** → skip processing, return the previously stored result directly.

**Implementation notes:**
- Store `{key → response, status}` with a reasonable TTL (e.g., 24 hours) so storage doesn't grow forever.
- Use a locking mechanism (e.g., Redis `SETNX` or a unique DB constraint) to handle concurrent requests with the same key arriving at the same time.
- Combine the key with a request payload hash if you want to detect "same key, different body" as an error case.

## 4. Real-life example

**Payment processing (e.g., Stripe API):**

- A user clicks "Pay Now." The client generates `Idempotency-Key: order-12345-attempt1` and sends it with the payment request.
- The request times out on the client side (maybe the response got lost), so the client automatically retries with the **same key**.
- Stripe's server sees the key was already used, finds the payment was actually successful the first time, and returns that same success response — **without charging the customer a second time**.

Other common use cases:
- Order creation APIs (avoid creating duplicate orders on retry).
- Message queue consumers (avoid processing the same message twice, e.g., Kafka consumers with at-least-once delivery).
- Scheduled jobs (avoid double-running a job if triggered twice due to a scheduler glitch).

---

## Summary

An idempotency key is a client-generated unique ID attached to a request so the server can recognize duplicate requests and return the original result instead of repeating the operation. It's essential for safe retries and preventing duplicate side effects (double charges, double orders, etc.) in distributed or network-unreliable systems. Implementation typically involves storing `key → result` with a TTL and using a locking mechanism for concurrency.
