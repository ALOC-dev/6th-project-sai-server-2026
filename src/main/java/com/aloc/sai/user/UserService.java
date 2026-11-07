package com.aloc.sai.user;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.user.dto.UserProfileResponse;
import com.aloc.sai.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final int NICKNAME_MAX_LENGTH = 20;
    private static final int NICKNAME_SUFFIX_LENGTH = 4;
    private static final String DEFAULT_NICKNAME_NAME = "사이";
    private static final String FRIEND_CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int FRIEND_CODE_LENGTH = 8;
    private static final int MAX_GENERATION_ATTEMPTS = 20;
    private static final int STATUS_MESSAGE_MAX_LENGTH = 50;
    private static final String WITHDRAW_CONFIRMATION = "DELETE";

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public User registerKakaoUser(Long kakaoId, String kakaoName, String profileImageUrl) {
        User user = new User(kakaoId, generateNickname(kakaoName), profileImageUrl, generateFriendCode());
        return userRepository.saveAndFlush(user);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(findUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserUpdateRequest request) {
        if (request.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "수정할 항목이 없습니다.");
        }

        User user = findUser(userId);

        // 초기 설정 전이면 학번과 학과를 함께 받아야 profileCompleted가 true가 된다.
        if (!user.isProfileCompleted() && (request.studentId() == null || request.department() == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "초기 설정에는 학번과 학과가 모두 필요합니다.");
        }

        if (request.nickname() != null) {
            changeNickname(user, request.nickname());
        }
        if (request.studentId() != null) {
            user.updateStudentId(request.studentId());
        }
        if (request.department() != null) {
            user.updateDepartment(request.department().strip());
        }
        if (request.statusMessage() != null) {
            user.updateStatusMessage(normalizeStatusMessage(request.statusMessage()));
        }
        if (request.marketingAgreed() != null) {
            user.updateMarketingAgreed(request.marketingAgreed(), LocalDateTime.now());
        }

        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.", e);
        }

        return UserProfileResponse.from(user);
    }

    // 사용자를 참조하는 테이블이 생기면 여기서 함께 정리해야 한다.
    @Transactional
    public void withdraw(Long userId, String confirmation) {
        if (!WITHDRAW_CONFIRMATION.equals(confirmation)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "탈퇴 확인 문구가 올바르지 않습니다.");
        }

        User user = findUser(userId);
        refreshTokenRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private void changeNickname(User user, String requestedNickname) {
        String nickname = requestedNickname.strip();
        int length = nickname.codePointCount(0, nickname.length());
        if (length < 1 || length > NICKNAME_MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "닉네임은 1자 이상 20자 이하여야 합니다.");
        }

        if (nickname.equals(user.getNickname())) {
            return;
        }
        if (userRepository.existsByNickname(nickname)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
        }

        user.updateNickname(nickname);
    }

    // 빈 문자열이면 상태 메시지를 지운다.
    private String normalizeStatusMessage(String statusMessage) {
        String message = statusMessage.strip();
        if (message.isEmpty()) {
            return null;
        }
        if (message.codePointCount(0, message.length()) > STATUS_MESSAGE_MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "상태 메시지는 50자 이하여야 합니다.");
        }
        return message;
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
