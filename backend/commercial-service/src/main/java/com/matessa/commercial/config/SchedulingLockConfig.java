package com.matessa.commercial.config;

import com.matessa.commercial.security.InternalApiKeyFilter;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Cluster-wide scheduling: with several commercial-service replicas, a {@code @SchedulerLock}
 * job (the reservation expiry) runs on one replica at a time. The lock table lives in the
 * {@code public} schema — it is per service, not per store; the job itself loops over stores.
 *
 * <p>Also registers the {@code /internal/**} service-key filter ahead of everything else.
 */
@Configuration(proxyBeanMethods = false)
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class SchedulingLockConfig {

    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table if not exists public.shedlock ("
                + "name varchar(64) primary key, lock_until timestamptz not null, "
                + "locked_at timestamptz not null, locked_by varchar(255) not null)");
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbc)
                .withTableName("public.shedlock")
                .usingDbTime()
                .build());
    }

    @Bean
    FilterRegistrationBean<InternalApiKeyFilter> internalApiKeyFilter(@Value("${app.internal.api-key:}") String key) {
        FilterRegistrationBean<InternalApiKeyFilter> registration = new FilterRegistrationBean<>(new InternalApiKeyFilter(key));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
