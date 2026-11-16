package com.matessa.order.tenant;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The stores this deployment serves ({@code app.tenancy.tenants}, env {@code APP_TENANCY_TENANTS}).
 * Each id is also its Postgres schema name, so ids are restricted to a safe identifier shape —
 * they end up in {@code search_path} and schema-qualified SQL.
 */
@ConfigurationProperties(prefix = "app.tenancy")
public record TenantProperties(List<String> tenants) {

    private static final Pattern SAFE_ID = Pattern.compile("[a-z][a-z0-9_]{1,30}");

    public TenantProperties {
        if (tenants == null || tenants.isEmpty()) {
            throw new IllegalStateException("app.tenancy.tenants must list at least one store id");
        }
        for (String t : tenants) {
            if (!SAFE_ID.matcher(t).matches()) {
                throw new IllegalStateException("Invalid store id '" + t + "' (allowed: " + SAFE_ID + ")");
            }
            if (t.equals("public") || t.startsWith("pg_") || t.equals("information_schema")) {
                throw new IllegalStateException("Store id '" + t + "' is a reserved Postgres schema name");
            }
        }
        tenants = List.copyOf(tenants);
    }

    public boolean isKnown(String tenantId) {
        return tenantId != null && tenants.contains(tenantId);
    }

    /**
     * Schema for Hibernate's startup metadata connection only. Never a fallback for real work:
     * data access without a store in context fails (see TenancyConfig).
     */
    public String defaultTenant() {
        return tenants.getFirst();
    }
}
