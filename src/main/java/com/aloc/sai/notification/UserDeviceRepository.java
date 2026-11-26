package com.aloc.sai.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {
    Optional<UserDevice> findByPushToken(String pushToken);

    // 푸시를 보낼 사용자의 모든 기기 토큰
    @Query("select d.pushToken from UserDevice d where d.user.userId = :userId")
    List<String> findPushTokensByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from UserDevice d where d.pushToken = :pushToken and d.user.userId = :userId")
    int deleteByPushTokenAndUserId(@Param("pushToken") String pushToken, @Param("userId") Long userId);

    @Modifying
    @Query("delete from UserDevice d where d.user.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
