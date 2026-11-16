package com.matessa.user.config;

import com.matessa.user.entity.Role;
import com.matessa.user.entity.User;
import com.matessa.user.repository.UserRepository;
import com.matessa.user.tenant.TenantContext;
import com.matessa.user.tenant.TenantProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds one initial ADMIN (also granted MANAGER) per store on boot, so each store's catalog
 * and users can be managed without hand-editing its schema.
 *
 * <p>Credentials come from {@code app.admins.<store>.email|password} (env
 * {@code <STORE>_ADMIN_EMAIL} / {@code <STORE>_ADMIN_PASSWORD}); no default password is
 * shipped, so a store whose password is unset is skipped with a warning. Idempotent: an
 * existing account with that email is left untouched. Disable with {@code app.admin.seed-enabled=false}.
 *
 * <p>Each store is seeded in its own transaction opened <em>after</em> the store is put in
 * context — Hibernate picks the schema when the session opens.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactions;
    private final TenantProperties tenants;
    private final Environment env;
    private final boolean enabled;

    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       TransactionTemplate transactions,
                       TenantProperties tenants,
                       Environment env,
                       @Value("${app.admin.seed-enabled:true}") boolean enabled) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactions = transactions;
        this.tenants = tenants;
        this.env = env;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        for (String store : tenants.tenants()) {
            String email = com.matessa.user.util.Emails.normalize(env.getProperty("app.admins." + store + ".email"));
            String password = env.getProperty("app.admins." + store + ".password");
            if (email == null || email.isBlank() || password == null || password.isBlank()) {
                log.warn("No admin credentials configured for store '{}' — skipping seeding. "
                        + "Set {}_ADMIN_EMAIL and {}_ADMIN_PASSWORD.", store, store.toUpperCase(), store.toUpperCase());
                continue;
            }
            TenantContext.runAs(store, () -> transactions.executeWithoutResult(tx -> seed(store, email, password)));
        }
    }

    private void seed(String store, String email, String password) {
        if (userRepository.existsByEmail(email)) {
            return; // already present — no-op on every subsequent boot
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFirstName(Character.toUpperCase(store.charAt(0)) + store.substring(1));
        admin.setLastName("Admin");
        admin.setEnabled(true);
        admin.getRoles().add(Role.ADMIN);
        admin.getRoles().add(Role.MANAGER);
        userRepository.save(admin);
        log.warn("Seeded initial admin '{}' for store '{}' (ADMIN + MANAGER).", email, store);
    }
}
