package com.matessa.commercial.tenant;

import java.sql.Connection;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

/**
 * Schema-per-store: hands Hibernate a pooled connection whose {@code search_path} is the
 * store's schema, and resets it before the connection goes back to the pool.
 */
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;
    private final TenantProperties tenants;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource, TenantProperties tenants) {
        this.dataSource = dataSource;
        this.tenants = tenants;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        // Used for startup metadata/validation: point it at a real store schema.
        return withSchema(dataSource.getConnection(), tenants.defaultTenant());
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        if (!tenants.isKnown(tenantIdentifier)) {
            throw new SQLException("Unknown store: " + tenantIdentifier);
        }
        return withSchema(dataSource.getConnection(), tenantIdentifier);
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            connection.setSchema(tenants.defaultTenant());
        } finally {
            connection.close();
        }
    }

    private static Connection withSchema(Connection connection, String schema) throws SQLException {
        try {
            connection.setSchema(schema);
            return connection;
        } catch (SQLException | RuntimeException e) {
            connection.close();
            throw e;
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return null;
    }
}
