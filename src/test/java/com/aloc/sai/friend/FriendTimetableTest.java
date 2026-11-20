package com.aloc.sai.friend;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
import com.aloc.sai.friend.dto.FriendRequestAction;
import com.aloc.sai.notification.NotificationRepository;
import com.aloc.sai.timetable.AcademicTerm;
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
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 친구 시간표 조회. 친구일 때만 볼 수 있고 응답 구조는 내 시간표 조회와 같다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FriendTimetableTest {
    private static final AcademicTerm CURRENT = AcademicTerm.current();
    private static final String FORBIDDEN_MESSAGE = "친구의 시간표만 볼 수 있습니다.";

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

    private String myToken;
    private Long me;
    private Long friend;
    private Long stranger;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        myToken = login(1L, "가영");
        me = userId(1L);
        login(2L, "나연");
        friend = userId(2L);
        login(3L, "다현");
        stranger = userId(3L);
        befriend(me, friend);
    }

    // 시간표·친구가 users를 참조하므로, 다른 테스트가 사용자를 지울 수 있게 정리하고 끝낸다.
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
    void 친구의_현재_학기_시간표를_요일_시작_시각순으로_준다() throws Exception {
        addClass(friend, CURRENT, Weekday.WED, "09:00", "10:15", "자료구조");
        addClass(friend, CURRENT, Weekday.MON, "13:00", "14:15", "운영체제");
        addClass(friend, CURRENT, Weekday.MON, "09:00", "10:15", "알고리즘");
        // 다른 학기 수업은 보이지 않는다.
        addClass(friend, new AcademicTerm(2020, Semester.FIRST), Weekday.MON, "11:00", "12:15", "지난 학기");

        getFriendTimetable(friend, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetableId").value(notNullValue()))
                .andExpect(jsonPath("$.academicYear").value(CURRENT.academicYear()))
                .andExpect(jsonPath("$.semester").value(CURRENT.semester().name()))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].courseName").value("알고리즘"))
                .andExpect(jsonPath("$.items[1].courseName").value("운영체제"))
                .andExpect(jsonPath("$.items[2].courseName").value("자료구조"));
    }

    @Test
    void 학기를_지정하면_그_학기_시간표를_준다() throws Exception {
        addClass(friend, CURRENT, Weekday.MON, "09:00", "10:15", "이번 학기");
        addClass(friend, new AcademicTerm(2020, Semester.FIRST), Weekday.TUE, "11:00", "12:15", "지난 학기");

        getFriendTimetable(friend, "?academicYear=2020&semester=FIRST")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.academicYear").value(2020))
                .andExpect(jsonPath("$.semester").value("FIRST"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].courseName").value("지난 학기"));
    }

    @Test
    void 친구가_시간표를_만들지_않았으면_빈_시간표를_준다() throws Exception {
        getFriendTimetable(friend, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.timetableId").value(nullValue()))
                .andExpect(jsonPath("$.updatedAt").value(nullValue()))
                .andExpect(jsonPath("$.academicYear").value(CURRENT.academicYear()))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void 친구가_아니면_403이다() throws Exception {
        addClass(stranger, CURRENT, Weekday.MON, "09:00", "10:15", "비공개");

        expectForbidden(getFriendTimetable(stranger, ""));
    }

    @Test
    void 친구_요청을_수락하기_전에는_403이다() throws Exception {
        friendService.sendRequest(me, stranger);

        expectForbidden(getFriendTimetable(stranger, ""));
    }

    @Test
    void 친구를_삭제하면_더_이상_볼_수_없다() throws Exception {
        friendService.deleteFriend(me, friend);

        expectForbidden(getFriendTimetable(friend, ""));
    }

    @Test
    void 자기_자신이나_없는_사용자는_403이다() throws Exception {
        expectForbidden(getFriendTimetable(me, ""));
        expectForbidden(getFriendTimetable(999999L, ""));
    }

    @Test
    void 연도와_학기_중_하나만_보내면_400이다() throws Exception {
        getFriendTimetable(friend, "?academicYear=2026")
                .andExpect(status().isBadRequest());
    }

    @Test
    void 로그인하지_않으면_401이다() throws Exception {
        mockMvc.perform(get("/api/v1/friends/" + friend + "/timetable"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions getFriendTimetable(Long friendId, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/friends/" + friendId + "/timetable" + query)
                .header("Authorization", "Bearer " + myToken));
    }

    private static void expectForbidden(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(FORBIDDEN_MESSAGE));
    }

    private void addClass(Long userId, AcademicTerm term, Weekday day, String start, String end, String name) {
        timetableService.addItem(userId, new TimetableItemCreateRequest(null, term.academicYear(), term.semester(), name,
                day, LocalTime.parse(start), LocalTime.parse(end), null, null));
    }

    private void befriend(Long requester, Long receiver) {
        Long requestId = friendService.sendRequest(requester, receiver).friendRequestId();
        friendService.respond(receiver, requestId, FriendRequestAction.ACCEPT);
    }

    private String login(Long kakaoId, String nickname) throws Exception {
        given(kakaoClient.getUserInfo("user-" + kakaoId)).willReturn(new KakaoUserInfo(kakaoId, nickname, null));
        String response = mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"user-" + kakaoId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }

    private Long userId(Long kakaoId) {
        return userRepository.findByKakaoId(kakaoId).orElseThrow().getUserId();
    }
}
