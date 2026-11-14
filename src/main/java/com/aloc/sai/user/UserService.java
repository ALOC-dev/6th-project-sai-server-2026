package com.aloc.sai.user;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.user.dto.KakaoUserResult;
import com.aloc.sai.user.dto.NicknameCheckResponse;
import com.aloc.sai.user.dto.UserProfileResponse;
import com.aloc.sai.user.dto.UserUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

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
    private static final long PROFILE_IMAGE_MAX_BYTES = 5L * 1024 * 1024;

    private final SecureRandom random = new SecureRandom();
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ProfileImageStorage profileImageStorage;
    private final TransactionTemplate transactionTemplate;

    // 트랜잭션을 걸지 않는다. 동시 가입으로 저장이 실패해도 롤백 전용 상태가 되지 않고 바로 다시 조회할 수 있게 하기 위해서다.
    public KakaoUserResult findOrRegisterKakaoUser(Long kakaoId, String kakaoName, String profileImageUrl) {
        Optional<User> existingUser = userRepository.findByKakaoId(kakaoId);
        if (existingUser.isPresent()) {
            return new KakaoUserResult(existingUser.get(), false);
        }

        try {
            User user = new User(kakaoId, generateNickname(kakaoName), profileImageUrl, generateFriendCode());
            return new KakaoUserResult(userRepository.saveAndFlush(user), true);
        } catch (DataIntegrityViolationException e) {
            User concurrentlyRegisteredUser = userRepository.findByKakaoId(kakaoId).orElseThrow(() -> e);
            return new KakaoUserResult(concurrentlyRegisteredUser, false);
        }
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

    @Transactional(readOnly = true)
    public NicknameCheckResponse checkNickname(Long userId, String requestedNickname) {
        String nickname = normalizeNickname(requestedNickname);
        return new NicknameCheckResponse(nickname, isNicknameAvailable(findUser(userId), nickname));
    }

    // 사용자를 참조하는 데이터는 각 도메인이 UserWithdrawEvent 리스너로 정리한다.
    // user를 참조하는 도메인은 대부분 UserService에 의존하므로, 순환 의존을 피하려고 직접 호출하지 않는다.
    // 리스너는 같은 트랜잭션에서 사용자 행을 지우기 전에 실행되며, DB 정리만 하고 예외를 삼키지 않아야 한다(UserWithdrawEvent 참고).
    @Transactional
    public void withdraw(Long userId, String confirmation) {
        if (!WITHDRAW_CONFIRMATION.equals(confirmation)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "탈퇴 확인 문구가 올바르지 않습니다.");
        }

        // 프로필 이미지 변경과 겹쳐도 지울 이미지 URL을 놓치지 않도록 잠그고 읽는다.
        User user = findUserForUpdate(userId);
        String profileImageUrl = user.getProfileImageUrl();
        refreshTokenRepository.deleteByUserId(userId);
        eventPublisher.publishEvent(new UserWithdrawEvent(userId));
        userRepository.delete(user);

        // 탈퇴가 롤백되면 이미지가 남아 있어야 하므로 커밋 후에 지운다.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                profileImageStorage.delete(profileImageUrl);
            }
        });
    }

    // 업로드는 트랜잭션 밖에서 한다. S3 요청 동안 DB 커넥션을 잡고 있지 않기 위해서다.
    // DB 반영이 실패하면 방금 올린 이미지를 지우고, 성공하면 이전 이미지를 지운다.
    // 동시에 여러 번 올려도 각 요청이 직전 요청의 이미지를 이전 이미지로 읽도록 사용자 행을 잠근다.
    public UserProfileResponse updateProfileImage(Long userId, MultipartFile image) {
        byte[] content = readImage(image);
        ProfileImageType type = ProfileImageType.detect(content)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "JPG, PNG, WEBP, HEIC 이미지만 업로드할 수 있습니다."));
        // 없는 사용자면 업로드 전에 404로 끝낸다.
        findUser(userId);

        String newImageUrl = profileImageStorage.upload(userId, content, type);
        ProfileImageChange change;
        try {
            change = transactionTemplate.execute(status -> {
                User user = findUserForUpdate(userId);
                String oldImageUrl = user.getProfileImageUrl();
                user.updateProfileImageUrl(newImageUrl);
                return new ProfileImageChange(oldImageUrl, UserProfileResponse.from(user));
            });
        } catch (RuntimeException e) {
            profileImageStorage.delete(newImageUrl);
            throw e;
        }

        profileImageStorage.delete(change.oldImageUrl());
        return change.profile();
    }

    // 다른 도메인이 사용자를 조회할 때 쓴다. 없으면 404다.
    @Transactional(readOnly = true)
    public User getUser(Long userId) {
        return findUser(userId);
    }

    // 다른 도메인이 사용자 행을 잠그고 읽을 때 쓴다. 탈퇴도 같은 행을 잠그므로 둘이 차례로 처리되고,
    // 탈퇴가 먼저 끝났으면 404다. 잠금은 호출한 쪽 트랜잭션이 끝날 때 풀린다.
    @Transactional
    public User getUserForUpdate(Long userId) {
        return findUserForUpdate(userId);
    }

    // 다른 도메인이 닉네임(완전 일치)이나 친구 코드로 사용자를 찾을 때 쓴다. 없을 때의 응답은 호출한 쪽이 정한다.
    @Transactional(readOnly = true)
    public Optional<User> findByNickname(String nickname) {
        return userRepository.findByNickname(nickname);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByFriendCode(String friendCode) {
        return userRepository.findByFriendCode(friendCode);
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private User findUserForUpdate(Long userId) {
        return userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private byte[] readImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "이미지 파일을 첨부해 주세요.");
        }
        if (image.getSize() > PROFILE_IMAGE_MAX_BYTES) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "프로필 이미지는 5MB 이하만 업로드할 수 있습니다.");
        }
        try {
            return image.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "이미지 파일을 읽을 수 없습니다.", e);
        }
    }

    private record ProfileImageChange(String oldImageUrl, UserProfileResponse profile) {
    }

    private void changeNickname(User user, String requestedNickname) {
        String nickname = normalizeNickname(requestedNickname);
        if (!isNicknameAvailable(user, nickname)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다.");
        }

        user.updateNickname(nickname);
    }

    // 중복 확인과 회원정보 수정이 같은 규칙을 쓰도록 공백 제거와 길이 검사를 한 곳에서 한다.
    private String normalizeNickname(String requestedNickname) {
        String nickname = requestedNickname == null ? "" : requestedNickname.strip();
        int length = length(nickname);
        if (length < 1 || length > NICKNAME_MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "닉네임은 1자 이상 20자 이하여야 합니다.");
        }
        return nickname;
    }

    // 본인의 현재 닉네임은 사용 가능으로 본다.
    private boolean isNicknameAvailable(User user, String nickname) {
        return nickname.equals(user.getNickname()) || !userRepository.existsByNickname(nickname);
    }

    // 빈 문자열이면 상태 메시지를 지운다.
    private String normalizeStatusMessage(String statusMessage) {
        String message = statusMessage.strip();
        if (message.isEmpty()) {
            return null;
        }
        if (length(message) > STATUS_MESSAGE_MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "상태 메시지는 50자 이하여야 합니다.");
        }
        return message;
    }

    private String generateNickname(String kakaoName) {
        String name = normalizeName(kakaoName);
        return generateUnique(
                () -> name + (1000 + random.nextInt(9000)),
                userRepository::existsByNickname,
                "닉네임을 생성하지 못했습니다.");
    }

    // 이모지가 잘리지 않도록 코드 포인트 기준으로 자른다.
    private String normalizeName(String kakaoName) {
        String name = kakaoName == null ? "" : kakaoName.strip();
        if (name.isEmpty()) {
            return DEFAULT_NICKNAME_NAME;
        }

        int maxLength = NICKNAME_MAX_LENGTH - NICKNAME_SUFFIX_LENGTH;
        if (length(name) <= maxLength) {
            return name;
        }

        return name.substring(0, name.offsetByCodePoints(0, maxLength)).strip();
    }

    // 글자 수는 코드 포인트 기준으로 센다. 이모지 하나를 한 글자로 보기 위해서다.
    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }

    private String generateFriendCode() {
        return generateUnique(this::randomFriendCode, userRepository::existsByFriendCode, "친구 코드를 생성하지 못했습니다.");
    }

    private String randomFriendCode() {
        StringBuilder code = new StringBuilder(FRIEND_CODE_LENGTH);
        for (int i = 0; i < FRIEND_CODE_LENGTH; i++) {
            code.append(FRIEND_CODE_CHARACTERS.charAt(random.nextInt(FRIEND_CODE_CHARACTERS.length())));
        }
        return code.toString();
    }

    // 무작위 후보를 만들어 이미 쓰는 값이 아니면 돌려준다. 정해진 횟수 안에 못 찾으면 실패한다.
    private String generateUnique(Supplier<String> candidates, Predicate<String> exists, String failureMessage) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String candidate = candidates.get();
            if (!exists.test(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException(failureMessage);
    }
}
