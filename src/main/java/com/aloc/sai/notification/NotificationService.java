package com.aloc.sai.notification;

import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.friend.FriendRequestStatus;
import com.aloc.sai.notification.dto.NotificationListResponse;
import com.aloc.sai.notification.dto.UnreadCountResponse;
import com.aloc.sai.user.UserService;
import com.aloc.sai.user.UserWithdrawEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final UserService userService;
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    // 각 도메인이 알림을 보낼 때 호출하는 공통 인터페이스. 호출한 쪽은 "언제·누구에게·무슨 알림"만 정한다.
    // 호출한 쪽의 트랜잭션에 참여해 알림 창 기록을 저장하고, 그 트랜잭션이 커밋된 뒤 푸시를 보낸다.
    // 호출한 쪽이 롤백되면 기록도 푸시도 남지 않고, 푸시가 실패해도 호출한 쪽은 실패하지 않는다.
    // actorId는 시스템 알림(발생시킨 사람 없음)이면 null로 보낸다.
    @Transactional
    public void send(Long receiverId, Long actorId, NotificationType type, Long referenceId) {
        validateReference(type, referenceId);

        Notification notification = notificationRepository.save(new Notification(
                userService.getUser(receiverId),
                actorId == null ? null : userService.getUser(actorId),
                type,
                referenceId));
        eventPublisher.publishEvent(NotificationCreatedEvent.from(notification));
    }

    // 알림 창 목록. 최신순(id 내림차순) 커서 페이징이며, 조회만 하고 읽음 처리는 하지 않는다.
    // size+1개를 읽어 다음 페이지가 있는지 판단한다.
    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long userId, Long cursor, Integer size, boolean unreadOnly) {
        int limit = size == null ? DEFAULT_SIZE : size;
        if (limit < 1 || limit > MAX_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "size는 1 이상 50 이하여야 합니다.");
        }

        List<Object[]> rows = notificationRepository.findPage(
                userId, cursor, unreadOnly, NotificationType.FRIEND_REQUEST, PageRequest.of(0, limit + 1));
        boolean hasNext = rows.size() > limit;
        List<NotificationListResponse.NotificationItem> items = rows.stream()
                .limit(limit)
                .map(row -> NotificationListResponse.NotificationItem.from(
                        (Notification) row[0], (FriendRequestStatus) row[1]))
                .toList();
        Long nextCursor = hasNext ? items.getLast().notificationId() : null;
        return new NotificationListResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByReceiverUserIdAndIsReadFalse(userId));
    }

    @EventListener
    @Transactional
    public void deleteAllOnWithdraw(UserWithdrawEvent event) {
        notificationRepository.deleteAllByUserId(event.userId());
    }

    // referenceId를 빠뜨리거나 잘못 넣는 건 호출하는 코드의 실수라 사용자 메시지 없이 실패시킨다.
    private void validateReference(NotificationType type, Long referenceId) {
        if (type.requiresReference() && referenceId == null) {
            throw new IllegalArgumentException(type + " 알림에는 referenceId가 필요합니다.");
        }
        if (!type.requiresReference() && referenceId != null) {
            throw new IllegalArgumentException(type + " 알림에는 referenceId를 넣지 않습니다.");
        }
    }
}
