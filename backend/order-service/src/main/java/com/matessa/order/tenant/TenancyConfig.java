package com.matessa.order.tenant;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.hibernate.cfg.MultiTenancySettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Wires schema-per-store multi-tenancy into this service: the request filter, Hibernate's
 * tenant resolver + connection provider, and a Flyway strategy that migrates every store's
 * schema (creating it on first boot) instead of only {@code public}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TenantProperties.class)
public class TenancyConfig {

    public TenancyConfig() {
        TenantContext.bootstrapping(); // created early in every context, before repositories
    }

    @Bean
    FilterRegistrationBean<TenantFilter> tenantFilter(TenantProperties tenants) {
        FilterRegistrationBean<TenantFilter> registration = new FilterRegistrationBean<>(new TenantFilter(tenants));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE); // before Spring Security's chain
        return registration;
    }

    @Bean
    SchemaMultiTenantConnectionProvider tenantConnectionProvider(DataSource dataSource, TenantProperties tenants) {
        return new SchemaMultiTenantConnectionProvider(dataSource, tenants);
    }

    @Bean
    CurrentTenantIdentifierResolver<String> tenantIdentifierResolver(TenantProperties tenants) {
        return new CurrentTenantIdentifierResolver<>() {
            @Override
            public String resolveCurrentTenantIdentifier() {
                // No fallback store: code that reaches the database without a store in
                // context (a new @Scheduled job without runAs, an @Async method, a listener
                // without the record interceptor) must fail loudly, not quietly read and
                // write the first store's data.
                String tenant = TenantContext.getTenantId();
                if (tenant != null) {
                    return tenant;
                }
                if (TenantContext.isStrict()) {
                    throw new IllegalStateException("Database access outside a store context");
                }
                return tenants.defaultTenant(); // startup only: repository/query bootstrap
            }

            @Override
            public boolean validateExistingCurrentSessions() {
                return true;
            }
        };
    }

    @Bean
    HibernatePropertiesCustomizer tenantHibernateCustomizer(SchemaMultiTenantConnectionProvider provider,
                                                            CurrentTenantIdentifierResolver<String> resolver) {
        return properties -> {
            properties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, provider);
            properties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, resolver);
        };
    }

    /** From the moment the app is ready, no store in context means a bug — fail loudly. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void enforceStoreContext() {
        TenantContext.enforceFromNowOn();
    }

    @Bean
    FlywayMigrationStrategy migrateEveryStore(TenantProperties tenants) {
        return flyway -> tenants.tenants().forEach(schema ->
                Flyway.configure()
                        .configuration(flyway.getConfiguration())
                        .schemas(schema)
                        .defaultSchema(schema)
                        .load()
                        .migrate());
    }
}
