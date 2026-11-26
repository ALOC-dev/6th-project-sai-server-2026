package com.aloc.sai.friend;

import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.friend.dto.FriendInviteResponse;
import com.aloc.sai.friend.dto.FriendListResponse;
import com.aloc.sai.friend.dto.FriendRequestAction;
import com.aloc.sai.friend.dto.FriendRequestCreateResponse;
import com.aloc.sai.friend.dto.FriendRequestListResponse;
import com.aloc.sai.friend.dto.FriendRequestRespondResponse;
import com.aloc.sai.friend.dto.FriendSearchResponse;
import com.aloc.sai.friend.dto.FriendshipStatus;
import com.aloc.sai.notification.NotificationService;
import com.aloc.sai.notification.NotificationType;
import com.aloc.sai.timetable.AcademicTerm;
import com.aloc.sai.timetable.Semester;
import com.aloc.sai.timetable.TimetableItem;
import com.aloc.sai.timetable.TimetableService;
import com.aloc.sai.timetable.dto.TimetableResponse;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserService;
import com.aloc.sai.user.UserWithdrawEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {
    private final UserService userService;
    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;
    private final NotificationService notificationService;
    private final TimetableService timetableService;

    // 초대 링크 도메인은 FE·딥링크 설정과 함께 확정되므로 설정값으로 둔다.
    @Value("${friend.invite-base-url}")
    private String inviteBaseUrl;

    // 닉네임은 완전 일치로 찾는다. 닉네임 규칙과 같게 앞뒤 공백은 제거한다.
    @Transactional(readOnly = true)
    public FriendSearchResponse searchByNickname(Long userId, String nickname) {
        String keyword = nickname == null ? "" : nickname.strip();
        if (keyword.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "검색할 닉네임을 입력해 주세요.");
        }

        User target = userService.findByNickname(keyword)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "해당 닉네임의 사용자를 찾을 수 없습니다."));
        if (target.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "자기 자신은 친구로 추가할 수 없습니다.");
        }
        return toSearchResponse(userId, target);
    }

    // 초대 링크로 앱에 들어왔을 때 링크의 친구 코드로 상대를 찾는다.
    @Transactional(readOnly = true)
    public FriendSearchResponse findByInviteCode(Long userId, String friendCode) {
        String code = friendCode.strip().toUpperCase(Locale.ROOT);
        User target = userService.findByFriendCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "초대 링크의 사용자를 찾을 수 없습니다."));
        if (target.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "내 초대 링크입니다. 친구에게 공유해 주세요.");
        }
        return toSearchResponse(userId, target);
    }

    // 친구 코드는 바뀌지 않으므로 닉네임을 바꿔도 링크는 그대로다.
    // 설정값이 "/"로 끝나도 "//"가 생기지 않도록 끝의 "/"를 지우고 붙인다.
    @Transactional(readOnly = true)
    public FriendInviteResponse getMyInvite(Long userId) {
        User user = userService.getUser(userId);
        String baseUrl = inviteBaseUrl.replaceAll("/+$", "");
        return new FriendInviteResponse(user.getNickname(), user.getFriendCode(), baseUrl + "/" + user.getFriendCode());
    }

    @Transactional
    public FriendRequestCreateResponse sendRequest(Long userId, Long receiverId) {
        if (userId.equals(receiverId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "자기 자신에게는 친구 요청을 보낼 수 없습니다.");
        }

        User requester = userService.getUser(userId);
        User receiver = userService.getUser(receiverId);

        if (areFriends(userId, receiverId)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 친구입니다.");
        }
        friendRequestRepository.findByActivePairKey(FriendRequest.pairKey(userId, receiverId))
                .ifPresent(active -> {
                    throw activePairConflict(active, userId);
                });

        // 동시에 서로 요청하거나, 수락과 재요청이 겹치면 위 검사를 통과할 수 있다.
        // 그때는 activePairKey 유니크 제약이 막는다(수락된 요청도 키를 유지한다).
        // 제약 위반의 원인(유니크 충돌, 그 사이 상대 탈퇴로 인한 FK 위반 등)을 이 트랜잭션에서 다시 조회해 가릴 수 없으므로 중립적으로 안내한다.
        FriendRequest request;
        try {
            request = friendRequestRepository.saveAndFlush(new FriendRequest(requester, receiver));
        } catch (DataIntegrityViolationException e) {
            throw retryLater(e);
        }

        // 같은 트랜잭션에 알림 기록을 남기고, 푸시는 요청이 커밋된 뒤에 나간다.
        notificationService.send(receiverId, userId, NotificationType.FRIEND_REQUEST, request.getFriendRequestId());
        return FriendRequestCreateResponse.from(request);
    }

    @Transactional(readOnly = true)
    public FriendRequestListResponse getRequests(Long userId) {
        return FriendRequestListResponse.of(
                friendRequestRepository.findReceived(userId, FriendRequestStatus.PENDING),
                friendRequestRepository.findSent(userId, FriendRequestStatus.PENDING)
        );
    }

    @Transactional
    public FriendRequestRespondResponse respond(Long userId, Long friendRequestId, FriendRequestAction action) {
        FriendRequest request = friendRequestRepository.findByIdForUpdate(friendRequestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "친구 요청을 찾을 수 없습니다."));

        if (!request.isReceivedBy(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "내가 받은 친구 요청만 처리할 수 있습니다.");
        }
        if (!request.isPending()) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 처리된 친구 요청입니다.");
        }

        // 거절은 요청자에게 알리지 않는다. 받은 사람의 알림 창에서 FRIEND_REQUEST 알림이 거절 로그로 보일 뿐이다.
        LocalDateTime now = LocalDateTime.now();
        if (action == FriendRequestAction.REJECT) {
            request.reject(now);
            return FriendRequestRespondResponse.of(request, null);
        }

        request.accept(now);
        Friendship friendship;
        try {
            friendship = friendshipRepository.saveAndFlush(new Friendship(request.getRequester(), request.getReceiver()));
        } catch (DataIntegrityViolationException e) {
            throw retryLater(e);
        }

        notificationService.send(
                request.getRequester().getUserId(), userId, NotificationType.FRIEND_ACCEPTED, friendRequestId);
        return FriendRequestRespondResponse.of(request, friendship.getFriendshipId());
    }

    // 각 친구의 오늘(KST) 수업을 함께 준다. available=true면 지금 수업 중이 아닌 친구만 준다(오늘 수업이 없는 친구 포함).
    @Transactional(readOnly = true)
    public FriendListResponse getFriends(Long userId, boolean available) {
        return getFriends(userId, available, LocalDateTime.now(AcademicTerm.KST));
    }

    // 기준 시각을 받아 공강 여부를 판단한다. 수업은 [시작, 종료)로 보며, 종료 시각에는 공강이다.
    @Transactional(readOnly = true)
    public FriendListResponse getFriends(Long userId, boolean available, LocalDateTime now) {
        List<Friendship> friendships = friendshipRepository.findAllWithUsersByUserId(userId);
        Map<Long, List<TimetableItem>> classesByFriend = timetableService.getClassesOn(
                friendships.stream().map(friendship -> friendship.getFriendOf(userId).getUserId()).toList(),
                now.toLocalDate());
        LocalTime time = now.toLocalTime();

        List<FriendListResponse.FriendItem> friends = friendships.stream()
                .map(friendship -> {
                    User friend = friendship.getFriendOf(userId);
                    List<TimetableItem> classes = classesByFriend.getOrDefault(friend.getUserId(), List.of());
                    return new FriendWithClasses(friend, friendship.getFriendshipId(), classes);
                })
                .filter(entry -> !available || entry.classes().stream().noneMatch(item -> isInClass(item, time)))
                .map(entry -> FriendListResponse.FriendItem.of(entry.friend(), entry.friendshipId(),
                        entry.classes().stream()
                                .map(item -> new FriendListResponse.ClassTime(item.getStartTime(), item.getEndTime()))
                                .toList()))
                .sorted(Comparator.comparing(FriendListResponse.FriendItem::nickname))
                .toList();
        return new FriendListResponse(friends);
    }

    private static boolean isInClass(TimetableItem item, LocalTime time) {
        return !time.isBefore(item.getStartTime()) && time.isBefore(item.getEndTime());
    }

    private record FriendWithClasses(User friend, Long friendshipId, List<TimetableItem> classes) {
    }

    // 친구 관계 삭제는 반드시 이 메서드(또는 탈퇴 정리)를 거쳐야 한다.
    // friendships 행만 지우고 요청의 activePairKey를 비우지 않으면 그 쌍은 다시 친구 요청을 보낼 수 없게 된다.
    @Transactional
    public void deleteFriend(Long userId, Long friendId) {
        Friendship friendship = findFriendship(userId, friendId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "친구 관계를 찾을 수 없습니다."));
        friendRequestRepository.findByActivePairKey(FriendRequest.pairKey(userId, friendId))
                .ifPresent(FriendRequest::endFriendship);
        friendshipRepository.delete(friendship);
    }

    // 친구의 학기별 시간표. 친구가 아니면(자기 자신, 없는 사용자 포함) 403이다.
    @Transactional(readOnly = true)
    public TimetableResponse getFriendTimetable(Long userId, Long friendId, Integer academicYear, Semester semester) {
        if (!areFriends(userId, friendId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "친구의 시간표만 볼 수 있습니다.");
        }
        return timetableService.getTimetableOf(friendId, academicYear, semester);
    }

    // 다른 도메인에서 친구에게만 허용하는 기능(시간표 조회 등)의 권한 확인에 쓴다.
    @Transactional(readOnly = true)
    public boolean areFriends(Long userId, Long otherUserId) {
        return friendshipRepository.existsByUser1UserIdAndUser2UserId(
                Math.min(userId, otherUserId), Math.max(userId, otherUserId));
    }

    // 알림 창이 FRIEND_REQUEST 알림의 처리 상태(대기·수락·거절)를 표시할 때 쓴다. 없는 ID는 결과에서 빠진다.
    @Transactional(readOnly = true)
    public Map<Long, FriendRequestStatus> getRequestStatuses(Collection<Long> friendRequestIds) {
        if (friendRequestIds.isEmpty()) {
            return Map.of();
        }
        return friendRequestRepository.findAllById(friendRequestIds).stream()
                .collect(Collectors.toMap(FriendRequest::getFriendRequestId, FriendRequest::getRequestStatus));
    }

    // 탈퇴 트랜잭션 안에서 사용자 행을 지우기 전에 실행된다. DB 정리만 하고 예외를 삼키지 않는다.
    @EventListener
    @Transactional
    public void deleteAllOnWithdraw(UserWithdrawEvent event) {
        friendRequestRepository.deleteAllByUserId(event.userId());
        friendshipRepository.deleteAllByUserId(event.userId());
    }

    private Optional<Friendship> findFriendship(Long userId, Long otherUserId) {
        return friendshipRepository.findByUser1UserIdAndUser2UserId(
                Math.min(userId, otherUserId), Math.max(userId, otherUserId));
    }

    private FriendSearchResponse toSearchResponse(Long userId, User target) {
        if (areFriends(userId, target.getUserId())) {
            return FriendSearchResponse.of(target, FriendshipStatus.FRIEND, null);
        }
        return friendRequestRepository.findByActivePairKey(FriendRequest.pairKey(userId, target.getUserId()))
                .filter(FriendRequest::isPending)
                .map(pending -> FriendSearchResponse.of(
                        target,
                        pending.isSentBy(userId) ? FriendshipStatus.REQUEST_SENT : FriendshipStatus.REQUEST_RECEIVED,
                        pending.getFriendRequestId()))
                .orElseGet(() -> FriendSearchResponse.of(target, FriendshipStatus.NONE, null));
    }

    private ApiException retryLater(DataIntegrityViolationException e) {
        return new ApiException(HttpStatus.CONFLICT, "요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.", e);
    }

    // 상대가 먼저 보낸 요청이 있으면 그 요청을 수락하도록 안내한다.
    private ApiException activePairConflict(FriendRequest active, Long userId) {
        if (!active.isPending()) {
            return new ApiException(HttpStatus.CONFLICT, "이미 친구입니다.");
        }
        if (active.isSentBy(userId)) {
            return new ApiException(HttpStatus.CONFLICT, "이미 친구 요청을 보냈습니다.");
        }
        return new ApiException(HttpStatus.CONFLICT, "상대가 보낸 친구 요청이 있습니다. 받은 요청에서 수락해 주세요.");
    }
}
