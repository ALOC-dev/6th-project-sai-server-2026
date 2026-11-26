package com.aloc.sai.notification;

import com.aloc.sai.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 알림을 저장한 트랜잭션이 커밋된 뒤에만 푸시를 보낸다. 롤백되면 보내지 않는다.
// 단순 버전(고도화 전 기준선): 요청 스레드에서 동기로 Expo를 부르고, 재시도·큐·receipt 확인·만료 토큰 정리는 하지 않는다.
// 기기별 실패(예: DeviceNotRegistered)는 로그만 남긴다.
// 알려진 위험: AFTER_COMMIT 시점에도 원래 트랜잭션의 DB 커넥션이 아직 반납되지 않아, Expo가 멈추면 요청마다 최대 약 5초
// 커넥션을 쥔다. 동시 요청이 풀 크기를 넘으면 푸시와 무관한 API도 멈출 수 있어 부하 측정 항목으로 둔다(고도화 때 비동기로 분리).
// 배너 문구는 앱이 꺼져 있어도 OS가 그리므로 서버가 만든다. 앱 안 알림 창 문구는 FE가 type·actor로 만든다.
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationPushSender {
    static final String TITLE = "사이";

    private final UserDeviceRepository userDeviceRepository;
    private final UserService userService;
    private final ExpoPushClient expoPushClient;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotificationCreated(NotificationCreatedEvent event) {
        // 푸시 실패가 이미 커밋된 비즈니스 로직에 영향을 주지 않도록 여기서 끝낸다.
        try {
            push(event);
        } catch (RuntimeException e) {
            log.warn("푸시 발송 실패: notificationId={}", event.notificationId(), e);
        }
    }

    void push(NotificationCreatedEvent event) {
        List<String> tokens = userDeviceRepository.findPushTokensByUserId(event.receiverId());
        if (tokens.isEmpty()) {
            return;
        }

        String body = body(event.notificationType(), actorNickname(event.actorId()));
        Map<String, Object> data = new HashMap<>();
        data.put("notificationId", event.notificationId());
        data.put("type", event.notificationType().name());
        data.put("referenceId", event.referenceId());
        List<ExpoPushClient.Message> messages = tokens.stream()
                .map(token -> new ExpoPushClient.Message(token, TITLE, body, data))
                .toList();

        List<ExpoPushClient.Ticket> tickets = expoPushClient.send(messages);
        for (int i = 0; i < tickets.size(); i++) {
            ExpoPushClient.Ticket ticket = tickets.get(i);
            if (!ticket.isOk()) {
                // 토큰 원문은 남기지 않는다. 같은 토큰이 반복 실패하는지는 끝 6자리로 구분한다.
                log.warn("푸시 기기 발송 실패: notificationId={}, token=...{}, error={}, message={}",
                        event.notificationId(), tail(messages.get(i).to()), ticket.error(), ticket.message());
            }
        }
    }

    static String body(NotificationType type, String actorNickname) {
        if (actorNickname == null) {
            return "새 알림이 있어요";
        }
        return switch (type) {
            case FRIEND_REQUEST -> actorNickname + "님이 친구 요청을 보냈어요";
            case FRIEND_ACCEPTED -> actorNickname + "님이 친구 요청을 수락했어요";
            case POKE -> actorNickname + "님이 콕 찔렀어요";
            case CHAT_INVITE -> actorNickname + "님이 약속방에 초대했어요";
        };
    }

    private String actorNickname(Long actorId) {
        return actorId == null ? null : userService.getUser(actorId).getNickname();
    }

    private static String tail(String token) {
        return token.length() <= 6 ? token : token.substring(token.length() - 6);
    }
}
