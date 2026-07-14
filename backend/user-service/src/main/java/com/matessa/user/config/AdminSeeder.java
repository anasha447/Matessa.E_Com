package com.matessa.user.config;

import com.matessa.user.entity.Role;
import com.matessa.user.entity.User;
import com.matessa.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds an initial ADMIN (also granted MANAGER) on first boot so the catalog and users
 * can be managed through the API without hand-editing the database.
 *
 * <p>Runs after Flyway + JPA are ready. Idempotent: if a user with the configured admin
 * email already exists, it does nothing on every subsequent boot. Credentials come from
 * {@code app.admin.*} (environment-overridable) — no default password is shipped, so if
 * {@code ADMIN_PASSWORD} is unset the seeder logs a warning and skips. Disable entirely
 * with {@code app.admin.seed-enabled=false}.
 */
@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final String email;
    private final String password;

    public AdminSeeder(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.admin.seed-enabled:true}") boolean enabled,
                       @Value("${app.admin.email:admin@matessa.com}") String email,
                       @Value("${app.admin.password:}") String password) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("Admin seeding enabled but app.admin.password is empty — skipping. "
                    + "Set ADMIN_PASSWORD to seed the initial admin '{}'.", email);
            return;
        }
        if (userRepository.existsByEmail(email)) {
            return; // already present — no-op on every subsequent boot
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setFirstName("Adventure");
        admin.setLastName("Admin");
        admin.setEnabled(true);
        admin.getRoles().add(Role.ADMIN);
        admin.getRoles().add(Role.MANAGER);
        userRepository.save(admin);

        log.warn("Seeded initial admin '{}' (ADMIN + MANAGER). Change this password "
                + "immediately in any shared environment.", email);
    }
}
