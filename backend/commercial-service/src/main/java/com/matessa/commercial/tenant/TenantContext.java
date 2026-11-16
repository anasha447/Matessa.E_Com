package com.matessa.commercial.tenant;

import java.util.function.Supplier;

/**
 * The store (tenant) the current thread is working for. Set per HTTP request by
 * {@link TenantFilter}, per Kafka record by the record interceptor, and explicitly by
 * background jobs via {@link #callAs}. One store = one Postgres schema in this service's DB.
 */
public final class TenantContext {

    /** Request header carrying the store id (set server-side by each storefront's BFF). */
    public static final String HEADER = "X-Tenant-ID";
    /** JWT claim that pins a token to the store that issued it. */
    public static final String CLAIM = "tenant";
    /** Request-attribute copy, so Feign's circuit-breaker thread can still read it. */
    public static final String REQUEST_ATTRIBUTE = TenantContext.class.getName() + ".TENANT";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    /**
     * False while the application starts (Spring Data validates repository queries and Hibernate
     * reads metadata before any request exists); set once the app is ready. From then on, data
     * access with no store in context is an error rather than a silent default.
     */
    private static volatile boolean strict = false;

    static void enforceFromNowOn() {
        strict = true;
    }

    /** A (new) application context is starting: allow the bootstrap default again. */
    static void bootstrapping() {
        strict = false;
    }

    static boolean isStrict() {
        return strict;
    }

    private TenantContext() {}

    public static String getTenantId() {
        return CURRENT.get();
    }

    public static void setTenantId(String tenantId) {
        CURRENT.set(tenantId);
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Runs {@code work} as {@code tenantId}, restoring whatever was set before. */
    public static <T> T callAs(String tenantId, Supplier<T> work) {
        String previous = CURRENT.get();
        CURRENT.set(tenantId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static void runAs(String tenantId, Runnable work) {
        callAs(tenantId, () -> {
            work.run();
            return null;
        });
    }
}
