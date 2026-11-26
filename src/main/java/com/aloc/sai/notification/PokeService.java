package com.aloc.sai.notification;

import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.friend.FriendService;
import com.aloc.sai.notification.dto.PokeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

// 공강 친구에게 찌르기. 알림 창에 POKE 기록을 남기고, 커밋 후 푸시를 보낸다(NotificationService.send).
// 친구 관계 확인에 FriendService가 필요한데, FriendService가 알림 발송을 위해 NotificationService를 부르므로
// 순환 참조가 생기지 않도록 별도 클래스로 둔다.
// 수업 중인지는 확인하지 않는다. 홈 화면이 공강 친구만 보여 주고, 학교 시간표 밖 일정은 어차피 알 수 없다.
@Service
@RequiredArgsConstructor
public class PokeService {
    static final int COOLDOWN_MINUTES = 10;

    private final FriendService friendService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;

    // 쿨다운은 최근 POKE 알림 기록으로 판단한다. 거의 동시에 들어온 두 요청은 함께 통과할 수 있어,
    // 연타는 FE가 요청 중 버튼을 막는 것으로 처리한다.
    @Transactional
    public PokeResponse poke(Long userId, Long targetFriendId) {
        if (userId.equals(targetFriendId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "자기 자신은 찌를 수 없습니다.");
        }
        if (!friendService.areFriends(userId, targetFriendId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "친구에게만 찌르기를 보낼 수 있습니다.");
        }
        LocalDateTime now = LocalDateTime.now();
        if (notificationRepository.existsByReceiverUserIdAndActorUserIdAndNotificationTypeAndCreatedAtAfter(
                targetFriendId, userId, NotificationType.POKE, now.minusMinutes(COOLDOWN_MINUTES))) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "같은 친구에게는 " + COOLDOWN_MINUTES + "분 후에 다시 찌를 수 있습니다.");
        }

        Long notificationId = notificationService.send(targetFriendId, userId, NotificationType.POKE, null);
        return new PokeResponse(notificationId, targetFriendId, now);
    }
}
