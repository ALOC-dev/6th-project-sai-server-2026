package com.aloc.sai.friend;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
import com.aloc.sai.friend.dto.FriendListResponse;
import com.aloc.sai.friend.dto.FriendRequestAction;
import com.aloc.sai.notification.NotificationRepository;
import com.aloc.sai.timetable.Semester;
import com.aloc.sai.timetable.TimetableItemRepository;
import com.aloc.sai.timetable.TimetableRepository;
import com.aloc.sai.timetable.TimetableService;
import com.aloc.sai.timetable.Weekday;
import com.aloc.sai.timetable.dto.TimetableItemCreateRequest;
import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 친구 목록의 오늘 수업(todayClasses)과 공강 필터(available). 기준 시각은 2026-11-17(화), 2026년 2학기다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FriendListTest {
    private static final LocalDateTime TUESDAY = LocalDateTime.of(2026, 11, 17, 0, 0);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FriendService friendService;

    @Autowired
    private TimetableService timetableService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private FriendRequestRepository friendRequestRepository;

    @Autowired
    private FriendshipRepository friendshipRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TimetableRepository timetableRepository;

    @Autowired
    private TimetableItemRepository timetableItemRepository;

    @MockitoBean
    private KakaoClient kakaoClient;

    private Long me;
    private Long inClass;
    private Long afternoon;
    private Long noTimetable;
    private Long stranger;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        me = signUp(1L, "가영");
        inClass = signUp(2L, "나연");
        afternoon = signUp(3L, "다현");
        noTimetable = signUp(4L, "라희");
        stranger = signUp(5L, "마루");
        befriend(me, inClass);
        befriend(me, afternoon);
        befriend(me, noTimetable);

        // 화요일 10:00~11:50 수업. 다른 요일·다른 학기 수업은 오늘 수업이 아니다.
        addClass(inClass, 2026, Semester.SECOND, Weekday.TUE, "10:00", "11:50");
        addClass(inClass, 2026, Semester.SECOND, Weekday.WED, "09:00", "09:50");
        addClass(inClass, 2026, Semester.FIRST, Weekday.TUE, "15:00", "15:50");
        // 늦은 수업을 먼저 넣어도 시작 시각순으로 나와야 한다.
        addClass(afternoon, 2026, Semester.SECOND, Weekday.TUE, "13:00", "14:50");
        addClass(afternoon, 2026, Semester.SECOND, Weekday.TUE, "09:00", "09:50");
        // 친구가 아닌 사람의 수업은 보이지 않는다.
        addClass(stranger, 2026, Semester.SECOND, Weekday.TUE, "10:00", "11:50");
    }

    @AfterEach
    void cleanUp() {
        timetableItemRepository.deleteAll();
        timetableRepository.deleteAll();
        notificationRepository.deleteAll();
        friendRequestRepository.deleteAll();
        friendshipRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 친구마다_현재_학기_오늘_요일_수업을_시작_시각순으로_준다() {
        Map<Long, List<FriendListResponse.ClassTime>> classes = todayClasses(friendService.getFriends(me, false, at("10:30")));

        assertThat(classes).containsOnlyKeys(inClass, afternoon, noTimetable);
        assertThat(classes.get(inClass)).containsExactly(classTime("10:00", "11:50"));
        assertThat(classes.get(afternoon)).containsExactly(classTime("09:00", "09:50"), classTime("13:00", "14:50"));
        assertThat(classes.get(noTimetable)).isEmpty();
    }

    @Test
    void available이면_지금_수업_중인_친구를_뺀다() {
        FriendListResponse response = friendService.getFriends(me, true, at("10:30"));

        assertThat(response.friends()).extracting(FriendListResponse.FriendItem::friendId)
                .containsExactly(afternoon, noTimetable);
    }

    @Test
    void 수업_시작_시각에는_수업_중이고_종료_시각에는_공강이다() {
        assertThat(friendIds(friendService.getFriends(me, true, at("10:00")))).doesNotContain(inClass);
        assertThat(friendIds(friendService.getFriends(me, true, at("11:49")))).doesNotContain(inClass);
        assertThat(friendIds(friendService.getFriends(me, true, at("11:50")))).contains(inClass);
        assertThat(friendIds(friendService.getFriends(me, true, at("09:59")))).contains(inClass);
    }

    @Test
    void available이_아니면_수업_중이어도_모두_준다() {
        assertThat(friendIds(friendService.getFriends(me, false, at("10:30"))))
                .containsExactly(inClass, afternoon, noTimetable);
    }

    @Test
    void 다른_요일에는_그_요일_수업만_준다() {
        LocalDateTime wednesday = LocalDateTime.of(2026, 11, 18, 9, 30);
        FriendListResponse response = friendService.getFriends(me, true, wednesday);

        assertThat(friendIds(response)).containsExactly(afternoon, noTimetable);
        assertThat(todayClasses(response).get(afternoon)).isEmpty();
    }

    @Test
    void 친구가_없으면_빈_목록이다() {
        assertThat(friendService.getFriends(stranger, true, at("10:30")).friends()).isEmpty();
    }

    private LocalDateTime at(String time) {
        return TUESDAY.with(LocalTime.parse(time));
    }

    private static FriendListResponse.ClassTime classTime(String start, String end) {
        return new FriendListResponse.ClassTime(LocalTime.parse(start), LocalTime.parse(end));
    }

    private static List<Long> friendIds(FriendListResponse response) {
        return response.friends().stream().map(FriendListResponse.FriendItem::friendId).toList();
    }

    private static Map<Long, List<FriendListResponse.ClassTime>> todayClasses(FriendListResponse response) {
        return response.friends().stream().collect(Collectors.toMap(
                FriendListResponse.FriendItem::friendId, FriendListResponse.FriendItem::todayClasses));
    }

    private void addClass(Long userId, int year, Semester semester, Weekday day, String start, String end) {
        timetableService.addItem(userId, new TimetableItemCreateRequest(null, year, semester, "수업",
                day, LocalTime.parse(start), LocalTime.parse(end), null, null));
    }

    private void befriend(Long requester, Long receiver) {
        Long requestId = friendService.sendRequest(requester, receiver).friendRequestId();
        friendService.respond(receiver, requestId, FriendRequestAction.ACCEPT);
    }

    private Long signUp(Long kakaoId, String nickname) throws Exception {
        given(kakaoClient.getUserInfo("user-" + kakaoId)).willReturn(new KakaoUserInfo(kakaoId, nickname, null));
        mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"user-" + kakaoId + "\"}"))
                .andExpect(status().isOk());
        return userRepository.findByKakaoId(kakaoId).orElseThrow().getUserId();
    }
}
