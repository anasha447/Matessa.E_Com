package com.matessa.order.service.impl;

import com.matessa.order.exception.ConflictException;
import com.matessa.order.exception.IdempotencyException;
import com.matessa.order.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Checkout idempotency keys, recorded before any work starts.
 *
 * <ul>
 *   <li>First use of a key: the row is inserted as IN_PROGRESS and the checkout proceeds.</li>
 *   <li>Same key, same request, finished: the original order id is returned (no new order).</li>
 *   <li>Same key, different request body: {@code 422 IDEMPOTENCY_KEY_REUSED} — the key belongs
 *       to a different purchase, so answering with that order would be wrong.</li>
 *   <li>Same key while the first request is still running: {@code 409 REQUEST_IN_PROGRESS}.
 *       An IN_PROGRESS row older than {@link #STALE} (its request died) can be taken over.</li>
 *   <li>Key used by somebody else: the same 409 as a duplicate, revealing nothing.</li>
 *   <li>A failed attempt deletes its row, so retrying with the same key is allowed.</li>
 * </ul>
 *
 * <p>Plain JDBC with schema-qualified table names: JdbcTemplate does not go through Hibernate's
 * per-store connection provider. Store ids are validated identifiers (TenantProperties), which
 * is what makes the concatenation safe.
 */
@Component
public class IdempotencyStore {

    static final Duration STALE = Duration.ofMinutes(2);

    private final JdbcTemplate jdbc;

    public IdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Claims the key, or returns the order it already produced. */
    public Optional<Long> begin(String key, String requester, String requestHash) {
        String table = table();
        int inserted = jdbc.update("insert into " + table + " (idem_key, requester, request_hash, status) "
                + "values (?, ?, ?, 'IN_PROGRESS') on conflict (idem_key) do nothing", key, requester, requestHash);
        if (inserted == 1) {
            return Optional.empty();
        }
        List<Map<String, Object>> rows = jdbc.queryForList("select requester, request_hash, status, order_id, created_at from "
                + table + " where idem_key = ?", key);
        if (rows.isEmpty()) {
            // Deleted by a failed attempt between our insert and read: claim it again.
            return begin(key, requester, requestHash);
        }
        Map<String, Object> row = rows.getFirst();
        if (!requester.equals(row.get("requester"))) {
            throw new ConflictException("Duplicate order request — idempotency key already used.");
        }
        if (!requestHash.equals(row.get("request_hash"))) {
            throw new IdempotencyException(422, "IDEMPOTENCY_KEY_REUSED",
                    "This checkout was already submitted with a different basket. Check your orders before trying again.");
        }
        if ("DONE".equals(row.get("status"))) {
            return Optional.of(((Number) row.get("order_id")).longValue());
        }
        Instant created = ((Timestamp) row.get("created_at")).toInstant();
        if (created.plus(STALE).isBefore(Instant.now())
                && jdbc.update("update " + table + " set created_at = now() where idem_key = ? and status = 'IN_PROGRESS' "
                + "and created_at = ?", key, row.get("created_at")) == 1) {
            return Optional.empty(); // the earlier attempt died; this one takes over
        }
        throw new IdempotencyException(409, "REQUEST_IN_PROGRESS",
                "Your order is still being placed. Please wait a moment, then check your orders.");
    }

    /** Marks the key done; call inside the transaction that commits the order. */
    public void complete(String key, long orderId) {
        jdbc.update("update " + table() + " set status = 'DONE', order_id = ? where idem_key = ?", orderId, key);
    }

    /** Frees the key after a failed attempt so the shopper can retry with it. */
    public void abandon(String key) {
        jdbc.update("delete from " + table() + " where idem_key = ? and status = 'IN_PROGRESS'", key);
    }

    /** Housekeeping: keys older than {@code age} (the job runs per store). */
    public int purgeOlderThan(String store, Duration age) {
        return jdbc.update("delete from " + store + ".idempotency_request where created_at < ?",
                Timestamp.from(Instant.now().minus(age)));
    }

    private static String table() {
        String store = TenantContext.getTenantId();
        if (store == null) {
            throw new IllegalStateException("Idempotency key outside a store context");
        }
        return store + ".idempotency_request";
    }
}
