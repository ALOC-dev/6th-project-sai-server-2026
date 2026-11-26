package com.aloc.sai.notification;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.friend.FriendRequestRepository;
import com.aloc.sai.friend.FriendRequestStatus;
import com.aloc.sai.friend.FriendService;
import com.aloc.sai.friend.FriendshipRepository;
import com.aloc.sai.friend.dto.FriendRequestAction;
import com.aloc.sai.notification.dto.NotificationListResponse;
import com.aloc.sai.notification.dto.NotificationListResponse.NotificationItem;
import com.aloc.sai.security.JwtTokenProvider;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 알림 목록(커서 페이징, 친구 요청 처리 상태)과 안 읽은 알림 수
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationListTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationQueryService notificationQueryService;

    @Autowired
    private FriendService friendService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private FriendRequestRepository friendRequestRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Long me;
    private Long friend;
    private Long other;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = userRepository.save(new User(1L, "가영", "https://img/ga.png", "ME000001")).getUserId();
        friend = userRepository.save(new User(2L, "나연", "https://img/na.png", "FRIEND01")).getUserId();
        other = userRepository.save(new User(3L, "다현", null, "OTHER001")).getUserId();
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        friendRequestRepository.deleteAll();
        friendshipRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 최신순으로_주고_nextCursor로_이어서_조회한다() {
        for (int i = 0; i < 5; i++) {
            notificationService.send(me, friend, NotificationType.POKE, null);
        }
        List<Long> allIds = notificationRepository.findAll().stream()
                .map(Notification::getNotificationId)
                .sorted((a, b) -> Long.compare(b, a))
                .toList();

        List<Long> collected = new ArrayList<>();
        NotificationListResponse page = notificationQueryService.getNotifications(me, null, 2, false);
        List<Integer> pageSizes = new ArrayList<>();
        while (true) {
            pageSizes.add(page.notifications().size());
            page.notifications().forEach(item -> collected.add(item.notificationId()));
            if (page.nextCursor() == null) {
                break;
            }
            page = notificationQueryService.getNotifications(me, page.nextCursor(), 2, false);
        }

        assertThat(pageSizes).containsExactly(2, 2, 1);
        assertThat(collected).containsExactlyElementsOf(allIds);
    }

    @Test
    void 개수가_size와_같으면_nextCursor는_null이다() {
        notificationService.send(me, friend, NotificationType.POKE, null);
        notificationService.send(me, friend, NotificationType.POKE, null);

        NotificationListResponse page = notificationQueryService.getNotifications(me, null, 2, false);

        assertThat(page.notifications()).hasSize(2);
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void FRIEND_REQUEST_알림에는_요청_처리_상태가_들어가고_다른_타입은_null이다() {
        Long requestId = friendService.sendRequest(friend, me).friendRequestId();

        NotificationItem pending = only(notificationQueryService.getNotifications(me, null, null, false));
        assertThat(pending.notificationType()).isEqualTo(NotificationType.FRIEND_REQUEST);
        assertThat(pending.referenceId()).isEqualTo(requestId);
        assertThat(pending.requestStatus()).isEqualTo(FriendRequestStatus.PENDING);
        assertThat(pending.actor()).isEqualTo(new NotificationListResponse.Actor(friend, "나연", "https://img/na.png"));

        friendService.respond(me, requestId, FriendRequestAction.ACCEPT);

        // 수락해도 알림은 읽음 처리되지 않고, 상태만 ACCEPTED로 바뀐다.
        NotificationItem accepted = only(notificationQueryService.getNotifications(me, null, null, false));
        assertThat(accepted.requestStatus()).isEqualTo(FriendRequestStatus.ACCEPTED);
        assertThat(accepted.isRead()).isFalse();

        // 요청자가 받은 FRIEND_ACCEPTED 알림은 requestStatus가 없다.
        NotificationItem acceptedNotice = only(notificationQueryService.getNotifications(friend, null, null, false));
        assertThat(acceptedNotice.notificationType()).isEqualTo(NotificationType.FRIEND_ACCEPTED);
        assertThat(acceptedNotice.requestStatus()).isNull();
    }

    @Test
    void 거절한_요청은_REJECTED로_보인다() {
        Long requestId = friendService.sendRequest(friend, me).friendRequestId();
        friendService.respond(me, requestId, FriendRequestAction.REJECT);

        assertThat(only(notificationQueryService.getNotifications(me, null, null, false)).requestStatus())
                .isEqualTo(FriendRequestStatus.REJECTED);
    }

    @Test
    void 다른_사람의_알림은_보이지_않는다() {
        notificationService.send(other, friend, NotificationType.POKE, null);

        assertThat(notificationQueryService.getNotifications(me, null, null, false).notifications()).isEmpty();
        assertThat(notificationQueryService.getUnreadCount(me).unreadCount()).isZero();
    }

    @Test
    void 발생시킨_사람이_없는_알림은_actor가_null이다() {
        notificationService.send(me, null, NotificationType.POKE, null);

        assertThat(only(notificationQueryService.getNotifications(me, null, null, false)).actor()).isNull();
    }

    @Test
    void unreadOnly와_안_읽은_알림_수는_읽은_알림을_뺀다() {
        notificationService.send(me, friend, NotificationType.POKE, null);
        notificationService.send(me, other, NotificationType.POKE, null);
        notificationService.send(me, friend, NotificationType.POKE, null);
        Long oldest = notificationRepository.findAll().stream()
                .map(Notification::getNotificationId).min(Long::compare).orElseThrow();
        markRead(oldest);

        assertThat(notificationQueryService.getUnreadCount(me).unreadCount()).isEqualTo(2);
        assertThat(notificationQueryService.getNotifications(me, null, null, true).notifications())
                .extracting(NotificationItem::notificationId)
                .doesNotContain(oldest)
                .hasSize(2);
        assertThat(notificationQueryService.getNotifications(me, null, null, false).notifications()).hasSize(3);
    }

    @Test
    void 알림이_없으면_빈_목록과_0이다() {
        NotificationListResponse page = notificationQueryService.getNotifications(me, null, null, false);

        assertThat(page.notifications()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        assertThat(notificationQueryService.getUnreadCount(me).unreadCount()).isZero();
    }

    @Test
    void size가_1_미만이거나_50_초과면_400이다() {
        for (int size : new int[]{0, 51}) {
            assertThatThrownBy(() -> notificationQueryService.getNotifications(me, null, size, false))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    // 응답 JSON 키가 노션 명세와 같은지(record의 boolean isRead가 "read"로 바뀌지 않는지) 확인한다.
    @Test
    void 로그인한_사용자의_알림을_명세와_같은_JSON으로_준다() throws Exception {
        Long requestId = friendService.sendRequest(friend, me).friendRequestId();
        notificationService.send(other, friend, NotificationType.POKE, null);
        String token = "Bearer " + jwtTokenProvider.createToken(me);

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(1))
                .andExpect(jsonPath("$.notifications[0].notificationId").isNumber())
                .andExpect(jsonPath("$.notifications[0].notificationType").value("FRIEND_REQUEST"))
                .andExpect(jsonPath("$.notifications[0].actor.userId").value(friend))
                .andExpect(jsonPath("$.notifications[0].actor.nickname").value("나연"))
                .andExpect(jsonPath("$.notifications[0].actor.profileImageUrl").value("https://img/na.png"))
                .andExpect(jsonPath("$.notifications[0].referenceId").value(requestId))
                .andExpect(jsonPath("$.notifications[0].requestStatus").value("PENDING"))
                .andExpect(jsonPath("$.notifications[0].isRead").value(false))
                .andExpect(jsonPath("$.notifications[0].read").doesNotExist())
                .andExpect(jsonPath("$.notifications[0].createdAt").isString())
                // jsonPath의 exists()는 null 값을 없는 것으로 보므로, 키가 null로 직렬화되는지는 본문으로 확인한다.
                .andExpect(content().string(containsString("\"nextCursor\":null")));

        mockMvc.perform(get("/api/v1/notifications/unread-count").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));

        mockMvc.perform(get("/api/v1/notifications").param("size", "51").header("Authorization", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size는 1 이상 50 이하여야 합니다."));
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
    }

    private static NotificationItem only(NotificationListResponse response) {
        assertThat(response.notifications()).hasSize(1);
        return response.notifications().getFirst();
    }

    // 읽음 처리 API는 아직 없어서 테스트에서 직접 바꾼다.
    private void markRead(Long notificationId) {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createQuery("update Notification n set n.isRead = true where n.notificationId = :id")
                .setParameter("id", notificationId)
                .executeUpdate());
    }
}
