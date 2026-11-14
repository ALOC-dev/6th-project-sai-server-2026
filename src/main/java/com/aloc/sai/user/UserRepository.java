package com.aloc.sai.user;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByKakaoId(Long kakaoId);

    // 프로필 이미지 변경·탈퇴가 동시에 와도 이전 이미지 URL을 차례로 읽도록 행을 잠근다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.userId = :userId")
    Optional<User> findByIdForUpdate(@Param("userId") Long userId);

    boolean existsByNickname(String nickname);

    Optional<User> findByNickname(String nickname);

    Optional<User> findByFriendCode(String friendCode);

    boolean existsByFriendCode(String friendCode);
}
