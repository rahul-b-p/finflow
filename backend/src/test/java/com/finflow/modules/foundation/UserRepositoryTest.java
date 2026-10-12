package com.finflow.modules.foundation;

import com.finflow.core.crypto.CryptoUtils;
import com.finflow.modules.foundation.entity.Role;
import com.finflow.modules.foundation.entity.User;
import com.finflow.modules.foundation.repository.RoleRepository;
import com.finflow.modules.foundation.repository.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @BeforeAll
    static void initCrypto() {
        CryptoUtils.init("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    }

    @Test
    @DisplayName("Saves user with transparent PII encryption and retrieves successfully")
    void shouldSaveAndRetrieveUserWithEncryptedPii() {
        Role role = roleRepository.save(new Role("SUPER_ADMIN", "Super Admin Description"));

        User user = new User();
        user.setEmail("ceo@finflow.internal");
        user.setPasswordHash("hashed_pass");
        user.setFirstName("Alice");
        user.setLastName("Fin");
        user.setPhone("+1987654321");
        user.setStatus("ACTIVE");
        user.setMustChangePassword(true);
        user.getRoles().add(role);

        User saved = userRepository.save(user);
        assertThat(saved.getId()).isNotNull();

        Optional<User> found = userRepository.findById(saved.getId());
        assertThat(found).isPresent();
        User retrieved = found.get();

        assertThat(retrieved.getEmail()).isEqualTo("ceo@finflow.internal");
        assertThat(retrieved.getFirstName()).isEqualTo("Alice");
        assertThat(retrieved.getLastName()).isEqualTo("Fin");
        assertThat(retrieved.getPhone()).isEqualTo("+1987654321");
        assertThat(retrieved.getRoles()).hasSize(1);
    }
}
