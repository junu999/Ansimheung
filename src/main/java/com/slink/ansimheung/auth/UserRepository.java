package com.slink.ansimheung.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** email 은 소문자로 저장되어 있으므로 {@link User#normalizeEmail} 한 값으로 찾는다 */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
