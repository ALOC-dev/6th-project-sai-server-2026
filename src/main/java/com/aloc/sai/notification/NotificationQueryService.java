package com.aloc.sai.notification;

import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.friend.FriendRequestStatus;
import com.aloc.sai.friend.FriendService;
import com.aloc.sai.notification.dto.NotificationListResponse;
import com.aloc.sai.notification.dto.UnreadCountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

// 알림 창 조회 전용. FriendService가 알림 발송을 위해 NotificationService를 부르므로,
// 친구 요청 상태를 FriendService로 가져오는 조회는 순환 참조가 생기지 않도록 이 클래스로 분리했다.
@Service
@RequiredArgsConstructor
public class NotificationQueryService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final FriendService friendService;

    // 최신순(id 내림차순) 커서 페이징이며, 조회만 하고 읽음 처리는 하지 않는다.
    // size+1개를 읽어 다음 페이지가 있는지 판단한다.
    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long userId, Long cursor, Integer size, boolean unreadOnly) {
        int limit = size == null ? DEFAULT_SIZE : size;
        if (limit < 1 || limit > MAX_SIZE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "size는 1 이상 50 이하여야 합니다.");
        }

        List<Notification> rows = notificationRepository.findPage(userId, cursor, unreadOnly, PageRequest.of(0, limit + 1));
        boolean hasNext = rows.size() > limit;
        List<Notification> page = rows.subList(0, Math.min(limit, rows.size()));

        // FRIEND_REQUEST 알림의 요청 상태를 IN 쿼리 한 번으로 가져온다. 다른 타입은 null이다.
        Map<Long, FriendRequestStatus> statuses = friendService.getRequestStatuses(page.stream()
                .filter(n -> n.getNotificationType() == NotificationType.FRIEND_REQUEST)
                .map(Notification::getReferenceId)
                .toList());
        List<NotificationListResponse.NotificationItem> items = page.stream()
                .map(n -> NotificationListResponse.NotificationItem.from(n,
                        n.getNotificationType() == NotificationType.FRIEND_REQUEST ? statuses.get(n.getReferenceId()) : null))
                .toList();
        Long nextCursor = hasNext ? items.getLast().notificationId() : null;
        return new NotificationListResponse(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId) {
        return new UnreadCountResponse(notificationRepository.countByReceiverUserIdAndIsReadFalse(userId));
    }
}
