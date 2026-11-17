package com.matessa.order.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Cluster-wide scheduling: with several order-service replicas, each {@code @SchedulerLock}
 * job (analytics view refresh, idempotency-key cleanup) runs on one replica at a time. The
 * lock table lives in the service's own {@code scheduling} schema: per service, the jobs loop over stores.
 */
@Configuration(proxyBeanMethods = false)
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulingLockConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        // Its own schema, created by the service role (it owns its database). Not public: on
        // PostgreSQL 15+ ordinary roles may not create tables there, and a database created
        // before the least-privilege roles keeps public owned by the bootstrap superuser.
        jdbc.execute("create schema if not exists scheduling");
        jdbc.execute("create table if not exists scheduling.shedlock ("
                + "name varchar(64) primary key, lock_until timestamptz not null, "
                + "locked_at timestamptz not null, locked_by varchar(255) not null)");
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbc)
                .withTableName("scheduling.shedlock")
                .usingDbTime()
                .build());
    }
}
