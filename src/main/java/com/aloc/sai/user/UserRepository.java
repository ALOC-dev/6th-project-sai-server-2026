package com.aloc.sai.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByKakaoId(Long kakaoId);

    boolean existsByNickname(String nickname);

    Optional<User> findByNickname(String nickname);

    Optional<User> findByFriendCode(String friendCode);

    boolean existsByFriendCode(String friendCode);
}
