package com.garage.garageapi.auth.token;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface AccountTokenRepository extends JpaRepository<AccountToken, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountToken> findByTokenHashAndType(String tokenHash, AccountTokenType type);
    List<AccountToken> findAllByUserIdAndTypeAndConsumedAtIsNull(Long userId, AccountTokenType type);
}
