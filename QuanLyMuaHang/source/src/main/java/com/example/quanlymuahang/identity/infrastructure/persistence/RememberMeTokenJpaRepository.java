package com.example.quanlymuahang.identity.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RememberMeTokenJpaRepository extends JpaRepository<RememberMeTokenEntity, Long> {
    Optional<RememberMeTokenEntity> findBySeries(String series);

    @Modifying
    @Query("DELETE FROM RememberMeTokenEntity t WHERE t.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM RememberMeTokenEntity t WHERE t.series = :series")
    void deleteBySeries(@Param("series") String series);

    @Modifying
    @Query("DELETE FROM RememberMeTokenEntity t WHERE t.expiresAt < :now")
    void deleteAllExpired(@Param("now") Instant now);
}
