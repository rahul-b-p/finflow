package com.finflow.core.startup;

import com.finflow.modules.foundation.entity.Role;
import com.finflow.modules.foundation.entity.User;
import com.finflow.modules.foundation.repository.RoleRepository;
import com.finflow.modules.foundation.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(2)
public class SuperAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminInitializer.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${finflow.security.seed.super-admin.email:admin@finflow.internal}")
    private String adminEmail;

    @Value("${finflow.security.seed.super-admin.password:AdminSecurePassword123!}")
    private String adminPassword;

    @Value("${finflow.security.seed.super-admin.first-name:Super}")
    private String adminFirstName;

    @Value("${finflow.security.seed.super-admin.last-name:Admin}")
    private String adminLastName;

    @Value("${finflow.security.seed.super-admin.phone:+1234567890}")
    private String adminPhone;

    public SuperAdminInitializer(UserRepository userRepository,
                                  RoleRepository roleRepository,
                                  PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        long superAdminCount = userRepository.countByRoleName("SUPER_ADMIN");
        if (superAdminCount > 0) {
            log.info("Super Admin account already exists; skipping bootstrap seed initialization.");
            return;
        }

        log.info("No Super Admin found. Provisioning default Super Admin account...");

        Role superAdminRole = roleRepository.findByName("SUPER_ADMIN")
                .orElseGet(() -> roleRepository.save(new Role("SUPER_ADMIN", "Highest system privilege; full administrative and bootstrap access")));

        User superAdmin = new User();
        superAdmin.setEmail(adminEmail);
        superAdmin.setPasswordHash(passwordEncoder.encode(adminPassword));
        superAdmin.setFirstName(adminFirstName);
        superAdmin.setLastName(adminLastName);
        superAdmin.setPhone(adminPhone);
        superAdmin.setStatus("ACTIVE");
        superAdmin.setMustChangePassword(true);
        superAdmin.getRoles().add(superAdminRole);

        User savedUser = userRepository.save(superAdmin);
        log.info("Provisioned initial Super Admin account with ID [{}] and email [{}]",
                savedUser.getId(), "[EMAIL_MASKED]");
    }
}
