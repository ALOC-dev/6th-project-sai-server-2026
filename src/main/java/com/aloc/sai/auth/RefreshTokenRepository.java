package com.aloc.sai.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    @Modifying
    @Query("delete from RefreshToken r where r.tokenHash = :tokenHash")
    int deleteByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying
    @Query("delete from RefreshToken r where r.tokenHash = :tokenHash and r.user.userId = :userId")
    int deleteByTokenHashAndUserId(@Param("tokenHash") String tokenHash, @Param("userId") Long userId);
}
