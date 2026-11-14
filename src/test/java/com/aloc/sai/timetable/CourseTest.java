package com.aloc.sai.timetable;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
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

import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CourseTest {
    private static final AcademicTerm CURRENT = AcademicTerm.current();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private BuildingRepository buildingRepository;

    @Autowired
    private CourseRepository courseRepository;

    @MockitoBean
    private KakaoClient kakaoClient;

    private String token;
    private Building engineering;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        given(kakaoClient.getUserInfo("user-1")).willReturn(new KakaoUserInfo(1L, "지윤", null));
        token = login();

        engineering = buildingRepository.save(new Building("공학관", "B01", 37.5509, 126.9410));
        buildingRepository.save(new Building("자연과학관", "B02", null, null));
        buildingRepository.save(new Building("100%관", "B03", null, null));
    }

    @AfterEach
    void cleanUp() {
        courseRepository.deleteAll();
        buildingRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 건물을_이름순으로_조회한다() throws Exception {
        buildings(null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buildings", hasSize(3)))
                .andExpect(jsonPath("$.buildings[0].buildingName").value("100%관"))
                .andExpect(jsonPath("$.buildings[1].buildingName").value("공학관"))
                .andExpect(jsonPath("$.buildings[1].latitude").value(37.5509))
                .andExpect(jsonPath("$.buildings[2].latitude").value(nullValue()));
    }

    @Test
    void 건물명으로_부분_검색한다() throws Exception {
        buildings("공학")
                .andExpect(jsonPath("$.buildings", hasSize(1)))
                .andExpect(jsonPath("$.buildings[0].buildingId").value(engineering.getBuildingId()));
    }

    @Test
    void 검색어의_퍼센트는_와일드카드가_아니라_글자로_찾는다() throws Exception {
        buildings("%")
                .andExpect(jsonPath("$.buildings", hasSize(1)))
                .andExpect(jsonPath("$.buildings[0].buildingName").value("100%관"));
    }

    @Test
    void 과목명으로_검색하면_분반별_수업_시간을_함께_준다() throws Exception {
        Course course = saveCourse(CURRENT, "01234", "01", "자료구조");
        course.addSchedule(new CourseSchedule(course, Weekday.WED, LocalTime.of(9, 0), LocalTime.of(10, 15), engineering, "301호"));
        course.addSchedule(new CourseSchedule(course, Weekday.MON, LocalTime.of(9, 0), LocalTime.of(10, 15), null, null));
        courseRepository.save(course);

        courses("keyword=자료")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courses", hasSize(1)))
                .andExpect(jsonPath("$.courses[0].courseId").value(course.getCourseId()))
                .andExpect(jsonPath("$.courses[0].subjectNo").value("01234"))
                .andExpect(jsonPath("$.courses[0].dvclNo").value("01"))
                .andExpect(jsonPath("$.courses[0].schedules", hasSize(2)))
                .andExpect(jsonPath("$.courses[0].schedules[0].dayOfWeek").value("MON"))
                .andExpect(jsonPath("$.courses[0].schedules[0].startTime").value("09:00"))
                .andExpect(jsonPath("$.courses[0].schedules[0].building").value(nullValue()))
                .andExpect(jsonPath("$.courses[0].schedules[1].dayOfWeek").value("WED"))
                .andExpect(jsonPath("$.courses[0].schedules[1].endTime").value("10:15"))
                .andExpect(jsonPath("$.courses[0].schedules[1].building.buildingName").value("공학관"))
                .andExpect(jsonPath("$.courses[0].schedules[1].room").value("301호"));
    }

    @Test
    void 교과목번호로도_검색한다() throws Exception {
        saveCourse(CURRENT, "01234", "01", "자료구조");
        saveCourse(CURRENT, "99999", "01", "운영체제");

        courses("keyword=0123")
                .andExpect(jsonPath("$.courses", hasSize(1)))
                .andExpect(jsonPath("$.courses[0].courseName").value("자료구조"));
    }

    @Test
    void 원격_과목은_수업_시간이_빈_배열이다() throws Exception {
        saveCourse(CURRENT, "01234", "01", "원격 자료구조");

        courses("keyword=자료구조").andExpect(jsonPath("$.courses[0].schedules", hasSize(0)));
    }

    @Test
    void 학기를_보내지_않으면_현재_학기에서만_찾는다() throws Exception {
        AcademicTerm other = new AcademicTerm(CURRENT.academicYear() - 1, CURRENT.semester());
        saveCourse(CURRENT, "01234", "01", "자료구조");
        saveCourse(other, "01234", "01", "자료구조");

        courses("keyword=자료구조").andExpect(jsonPath("$.courses", hasSize(1)));
        courses("keyword=자료구조&academicYear=" + other.academicYear() + "&semester=" + other.semester())
                .andExpect(jsonPath("$.courses", hasSize(1)));
    }

    @Test
    void 분반은_과목명_교과목번호_분반_순으로_정렬하고_size만큼_준다() throws Exception {
        saveCourse(CURRENT, "01234", "02", "자료구조");
        saveCourse(CURRENT, "01234", "01", "자료구조");
        saveCourse(CURRENT, "01234", "03", "자료구조");

        courses("keyword=자료구조&size=2")
                .andExpect(jsonPath("$.courses", hasSize(2)))
                .andExpect(jsonPath("$.courses[0].dvclNo").value("01"))
                .andExpect(jsonPath("$.courses[1].dvclNo").value("02"));
    }

    @Test
    void 결과가_없으면_빈_배열이다() throws Exception {
        courses("keyword=없는과목").andExpect(status().isOk()).andExpect(jsonPath("$.courses", hasSize(0)));
    }

    @Test
    void 검색어가_없으면_400을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/courses").param("keyword", "  ").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("검색어를 입력해 주세요."));
        courses("").andExpect(status().isBadRequest());
    }

    @Test
    void size가_범위를_벗어나면_400을_반환한다() throws Exception {
        courses("keyword=자료&size=0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("size는 1 이상 50 이하여야 합니다."));
        courses("keyword=자료&size=51").andExpect(status().isBadRequest());
    }

    @Test
    void 연도와_학기_중_하나만_보내면_400을_반환한다() throws Exception {
        courses("keyword=자료&academicYear=2026").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("academicYear와 semester는 함께 보내야 합니다."));
        courses("keyword=자료&semester=FIRST").andExpect(status().isBadRequest());
    }

    @Test
    void 학기_값이_잘못되면_400을_반환한다() throws Exception {
        courses("keyword=자료&academicYear=2026&semester=SUMMER").andExpect(status().isBadRequest());
    }

    @Test
    void 인증_없이_요청하면_401을_반환한다() throws Exception {
        mockMvc.perform(get("/api/v1/buildings")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/courses?keyword=자료")).andExpect(status().isUnauthorized());
    }

    private Course saveCourse(AcademicTerm term, String subjectNo, String dvclNo, String name) {
        return courseRepository.save(new Course(term, subjectNo, dvclNo, name, LocalDateTime.now()));
    }

    private ResultActions buildings(String keyword) throws Exception {
        String query = keyword == null ? "" : "?keyword=" + keyword;
        return mockMvc.perform(get("/api/v1/buildings" + query).header("Authorization", "Bearer " + token));
    }

    private ResultActions courses(String query) throws Exception {
        return mockMvc.perform(get("/api/v1/courses?" + query).header("Authorization", "Bearer " + token));
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"user-1\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
