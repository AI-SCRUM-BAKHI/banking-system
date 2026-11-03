package com.scrumai.auth.repository;

import com.scrumai.auth.entity.RefreshTokenEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshTokenEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select token from RefreshTokenEntity token join fetch token.user where token.tokenHash = :hash")
  Optional<RefreshTokenEntity> findForUpdate(@Param("hash") String hash);
}
