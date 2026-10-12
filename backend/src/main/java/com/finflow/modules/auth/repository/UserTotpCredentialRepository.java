package com.finflow.modules.auth.repository;

import com.finflow.modules.auth.entity.UserTotpCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserTotpCredentialRepository extends JpaRepository<UserTotpCredential, UUID> {

    Optional<UserTotpCredential> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
