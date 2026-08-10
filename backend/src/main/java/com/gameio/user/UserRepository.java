package com.gameio.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserAccount, UUID> {
    Optional<UserAccount> findByUsernameNormalized(String usernameNormalized);

    Optional<UserAccount> findByEmailNormalized(String emailNormalized);

    boolean existsByUsernameNormalized(String usernameNormalized);

    boolean existsByEmailNormalized(String emailNormalized);
}
