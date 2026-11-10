package com.aloc.sai.friend;

import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.friend.dto.FriendListResponse;
import com.aloc.sai.friend.dto.FriendRequestAction;
import com.aloc.sai.friend.dto.FriendRequestCreateResponse;
import com.aloc.sai.friend.dto.FriendRequestListResponse;
import com.aloc.sai.friend.dto.FriendRequestRespondResponse;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserService;
import com.aloc.sai.user.UserWithdrawEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendService {
    private final UserService userService;
    private final FriendRequestRepository friendRequestRepository;
    private final FriendshipRepository friendshipRepository;

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
        try {
            FriendRequest request = friendRequestRepository.saveAndFlush(new FriendRequest(requester, receiver));
            return FriendRequestCreateResponse.from(request);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 친구이거나 대기 중인 친구 요청이 있습니다.", e);
        }
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

        LocalDateTime now = LocalDateTime.now();
        if (action == FriendRequestAction.REJECT) {
            request.reject(now);
            return FriendRequestRespondResponse.of(request, null);
        }

        request.accept(now);
        try {
            Friendship friendship = friendshipRepository.saveAndFlush(
                    new Friendship(request.getRequester(), request.getReceiver()));
            return FriendRequestRespondResponse.of(request, friendship.getFriendshipId());
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 친구입니다.", e);
        }
    }

    // 공강 필터(available)는 시간표 도메인이 생기면 적용한다. 지금은 모든 친구의 오늘 수업이 비어 있어 전원이 공강이다.
    @Transactional(readOnly = true)
    public FriendListResponse getFriends(Long userId, boolean available) {
        List<FriendListResponse.Item> friends = friendshipRepository.findAllWithUsersByUserId(userId).stream()
                .map(friendship -> FriendListResponse.Item.of(
                        friendship.getFriendOf(userId), friendship.getFriendshipId(), List.of()))
                .sorted(Comparator.comparing(FriendListResponse.Item::nickname))
                .toList();
        return new FriendListResponse(friends);
    }

    @Transactional
    public void deleteFriend(Long userId, Long friendId) {
        Friendship friendship = findFriendship(userId, friendId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "친구 관계를 찾을 수 없습니다."));
        friendRequestRepository.findByActivePairKey(FriendRequest.pairKey(userId, friendId))
                .ifPresent(FriendRequest::endFriendship);
        friendshipRepository.delete(friendship);
    }

    // 다른 도메인에서 친구에게만 허용하는 기능(시간표 조회 등)의 권한 확인에 쓴다.
    @Transactional(readOnly = true)
    public boolean areFriends(Long userId, Long otherUserId) {
        return friendshipRepository.existsByUser1UserIdAndUser2UserId(
                Math.min(userId, otherUserId), Math.max(userId, otherUserId));
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
