package com.aloc.sai.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final int NICKNAME_MAX_LENGTH = 20;
    private static final int NICKNAME_SUFFIX_LENGTH = 4;
    private static final String DEFAULT_NICKNAME_NAME = "사이";
    private static final String FRIEND_CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int FRIEND_CODE_LENGTH = 8;
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;

    @Transactional
    public User registerKakaoUser(Long kakaoId, String kakaoName, String profileImageUrl) {
        User user = new User(kakaoId, generateNickname(kakaoName), profileImageUrl, generateFriendCode());
        return userRepository.saveAndFlush(user);
    }

    private String generateNickname(String kakaoName) {
        String name = normalizeName(kakaoName);

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String nickname = name + (1000 + random.nextInt(9000));
            if (!userRepository.existsByNickname(nickname)) {
                return nickname;
            }
        }

        throw new IllegalStateException("닉네임을 생성하지 못했습니다.");
    }

    // 닉네임 길이는 문자 수 기준이므로 이모지가 잘리지 않도록 코드 포인트로 자른다.
    private String normalizeName(String kakaoName) {
        String name = kakaoName == null ? "" : kakaoName.strip();
        if (name.isEmpty()) {
            return DEFAULT_NICKNAME_NAME;
        }

        int maxLength = NICKNAME_MAX_LENGTH - NICKNAME_SUFFIX_LENGTH;
        if (name.codePointCount(0, name.length()) <= maxLength) {
            return name;
        }

        return name.substring(0, name.offsetByCodePoints(0, maxLength)).strip();
    }

    private String generateFriendCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder(FRIEND_CODE_LENGTH);
            for (int i = 0; i < FRIEND_CODE_LENGTH; i++) {
                code.append(FRIEND_CODE_CHARACTERS.charAt(random.nextInt(FRIEND_CODE_CHARACTERS.length())));
            }

            if (!userRepository.existsByFriendCode(code.toString())) {
                return code.toString();
            }
        }

        throw new IllegalStateException("친구 코드를 생성하지 못했습니다.");
    }
}
