package com.aloc.sai.notification;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.security.JwtTokenProvider;
import com.aloc.sai.user.User;
import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 알림 전체 읽음 처리: lastNotificationId 이하만 읽음, 남은 안 읽은 수 반환
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationReadAllTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private Long me;
    private Long friend;
    private Long other;

    @BeforeEach
    void setUp() {
        cleanUp();
        me = userRepository.save(new User(1L, "가영", null, "ME000001")).getUserId();
        friend = userRepository.save(new User(2L, "나연", null, "FRIEND01")).getUserId();
        other = userRepository.save(new User(3L, "다현", null, "OTHER001")).getUserId();
    }

    @AfterEach
    void cleanUp() {
        notificationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void lastNotificationId_이하만_읽음_처리하고_이후_도착한_알림은_남긴다() {
        Long first = poke(me);
        Long seen = poke(me);
        // 목록을 받은 뒤(최신 = seen) 도착한 알림
        Long arrivedLater = poke(me);

        long unreadCount = notificationService.markAllRead(me, seen).unreadCount();

        assertThat(unreadCount).isEqualTo(1);
        Map<Long, Boolean> read = readStates();
        assertThat(read.get(first)).isTrue();
        assertThat(read.get(seen)).isTrue();
        assertThat(read.get(arrivedLater)).isFalse();
    }

    @Test
    void 다른_사람의_알림은_건드리지_않는다() {
        Long othersOld = poke(other);
        Long mine = poke(me);

        notificationService.markAllRead(me, mine);

        assertThat(readStates().get(othersOld)).isFalse();
        assertThat(notificationRepository.countByReceiverUserIdAndIsReadFalse(other)).isEqualTo(1);
    }

    @Test
    void 남의_알림_id를_보내도_내_알림_중_그_이하만_처리한다() {
        Long mine = poke(me);
        Long othersNewer = poke(other);

        assertThat(notificationService.markAllRead(me, othersNewer).unreadCount()).isZero();
        assertThat(readStates().get(mine)).isTrue();
        assertThat(readStates().get(othersNewer)).isFalse();
    }

    @Test
    void 읽을_알림이_없어도_실패하지_않고_0을_준다() {
        Long id = poke(me);
        notificationService.markAllRead(me, id);

        assertThat(notificationService.markAllRead(me, id).unreadCount()).isZero();
        assertThat(notificationService.markAllRead(other, id).unreadCount()).isZero();
    }

    @Test
    void API로_호출하면_남은_안_읽은_수를_준다() throws Exception {
        Long seen = poke(me);
        poke(me);

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastNotificationId\": " + seen + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void lastNotificationId가_없으면_400이고_아무것도_바꾸지_않는다() throws Exception {
        Long id = poke(me);

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .header("Authorization", bearer(me))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("lastNotificationId는 필수입니다."));

        assertThat(readStates().get(id)).isFalse();
    }

    @Test
    void 토큰이_없으면_401이다() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lastNotificationId\": 1}"))
                .andExpect(status().isUnauthorized());
    }

    private Long poke(Long receiverId) {
        notificationService.send(receiverId, friend, NotificationType.POKE, null);
        return notificationRepository.findAll().stream()
                .map(Notification::getNotificationId).max(Long::compare).orElseThrow();
    }

    private Map<Long, Boolean> readStates() {
        return notificationRepository.findAll().stream()
                .collect(Collectors.toMap(Notification::getNotificationId, Notification::isRead));
    }

    private String bearer(Long userId) {
        return "Bearer " + jwtTokenProvider.createToken(userId);
    }
}
