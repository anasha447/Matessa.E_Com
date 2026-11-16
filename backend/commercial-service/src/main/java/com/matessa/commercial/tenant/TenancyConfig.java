package com.matessa.commercial.tenant;

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
                String tenant = TenantContext.getTenantId();
                return tenant != null ? tenant : tenants.defaultTenant();
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
