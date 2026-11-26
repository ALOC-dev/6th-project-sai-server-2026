package com.aloc.sai.notification;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.friend.FriendRequestRepository;
import com.aloc.sai.friend.FriendService;
import com.aloc.sai.friend.FriendshipRepository;
import com.aloc.sai.friend.dto.FriendRequestAction;
import com.aloc.sai.notification.dto.PokeResponse;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 찌르기: 친구에게만, 같은 친구에게 10분에 한 번
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PokeTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PokeService pokeService;

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
    private Long friend2;
    private Long stranger;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = userRepository.save(new User(1L, "가영", null, "ME000001")).getUserId();
        friend = userRepository.save(new User(2L, "나연", null, "FRIEND01")).getUserId();
        friend2 = userRepository.save(new User(3L, "다현", null, "FRIEND02")).getUserId();
        stranger = userRepository.save(new User(4L, "라희", null, "STRANGE1")).getUserId();
        befriend(me, friend);
        befriend(me, friend2);
        // 친구 맺기 과정에서 생긴 알림은 찌르기 확인에 방해되지 않도록 지운다.
        notificationRepository.deleteAll();
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
    void 친구에게_찌르면_받은_친구에게_POKE_알림이_남는다() {
        PokeResponse response = pokeService.poke(me, friend);

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        Notification poke = notifications.getFirst();
        assertThat(poke.getNotificationId()).isEqualTo(response.notificationId());
        assertThat(poke.getNotificationType()).isEqualTo(NotificationType.POKE);
        assertThat(poke.getReceiver().getUserId()).isEqualTo(friend);
        assertThat(poke.getActor().getUserId()).isEqualTo(me);
        assertThat(poke.getReferenceId()).isNull();
        assertThat(response.targetFriendId()).isEqualTo(friend);
        assertThat(response.sentAt()).isNotNull();
    }

    @Test
    void 같은_친구에게_10분_안에_다시_찌르면_429이고_알림이_늘지_않는다() {
        pokeService.poke(me, friend);

        assertStatus(() -> pokeService.poke(me, friend), HttpStatus.TOO_MANY_REQUESTS);
        assertThat(notificationRepository.count()).isEqualTo(1);
    }

    // 정상 앱의 연타가 아니라 API를 직접 동시에 호출해도 쿨다운이 지켜지는지 확인한다.
    @Test
    void 같은_친구에게_동시에_5번_찔러도_알림은_1개만_생긴다() throws Exception {
        int threads = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<HttpStatus>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        pokeService.poke(me, friend);
                        return HttpStatus.CREATED;
                    } catch (ApiException e) {
                        return e.getStatus();
                    }
                }));
            }
            ready.await();
            start.countDown();

            List<HttpStatus> results = new ArrayList<>();
            for (Future<HttpStatus> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }

            assertThat(results).filteredOn(HttpStatus.CREATED::equals).hasSize(1);
            assertThat(results).filteredOn(HttpStatus.TOO_MANY_REQUESTS::equals).hasSize(threads - 1);
            assertThat(notificationRepository.count()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void 쿨다운은_친구별_방향별이다() {
        pokeService.poke(me, friend);

        // 다른 친구, 반대 방향은 바로 보낼 수 있다.
        pokeService.poke(me, friend2);
        pokeService.poke(friend, me);

        assertThat(notificationRepository.count()).isEqualTo(3);
    }

    @Test
    void 십분이_지나면_다시_찌를_수_있다() {
        Long first = pokeService.poke(me, friend).notificationId();
        moveCreatedAt(first, LocalDateTime.now().minusMinutes(10).minusSeconds(1));

        pokeService.poke(me, friend);

        assertThat(notificationRepository.count()).isEqualTo(2);
    }

    @Test
    void 다른_종류의_알림은_쿨다운에_걸리지_않는다() {
        // 내가 친구에게 보낸 친구 요청 알림 같은 다른 타입은 무관하다.
        transactionTemplate.executeWithoutResult(status -> entityManager.persist(new Notification(
                userRepository.getReferenceById(friend), userRepository.getReferenceById(me),
                NotificationType.FRIEND_ACCEPTED, 1L)));

        pokeService.poke(me, friend);

        assertThat(notificationRepository.count()).isEqualTo(2);
    }

    @Test
    void 친구가_아니거나_없는_사용자면_403이다() {
        assertStatus(() -> pokeService.poke(me, stranger), HttpStatus.FORBIDDEN);
        assertStatus(() -> pokeService.poke(me, 999_999L), HttpStatus.FORBIDDEN);
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    void 자기_자신에게는_400이다() {
        assertStatus(() -> pokeService.poke(me, me), HttpStatus.BAD_REQUEST);
    }

    @Test
    void API로_찌르면_201과_응답_본문을_준다() throws Exception {
        mockMvc.perform(post("/api/v1/pokes")
                        .header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetFriendId\": " + friend + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificationId").isNumber())
                .andExpect(jsonPath("$.targetFriendId").value(friend))
                .andExpect(jsonPath("$.sentAt").isString());

        mockMvc.perform(post("/api/v1/pokes")
                        .header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetFriendId\": " + friend + "}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.message").value("같은 친구에게는 10분 후에 다시 찌를 수 있습니다."));
    }

    @Test
    void targetFriendId가_없으면_400_토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(post("/api/v1/pokes")
                        .header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("targetFriendId는 필수입니다."));

        mockMvc.perform(post("/api/v1/pokes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetFriendId\": " + friend + "}"))
                .andExpect(status().isUnauthorized());
    }

    private void befriend(Long requester, Long receiver) {
        Long requestId = friendService.sendRequest(requester, receiver).friendRequestId();
        friendService.respond(receiver, requestId, FriendRequestAction.ACCEPT);
    }

    private void moveCreatedAt(Long notificationId, LocalDateTime createdAt) {
        transactionTemplate.executeWithoutResult(status -> entityManager
                .createQuery("update Notification n set n.createdAt = :createdAt where n.notificationId = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", notificationId)
                .executeUpdate());
    }

    private static void assertStatus(Runnable action, HttpStatus expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(expected));
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.createToken(userId);
    }
}
