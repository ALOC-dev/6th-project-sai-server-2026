package com.aloc.sai.timetable;

import com.aloc.sai.auth.RefreshTokenRepository;
import com.aloc.sai.auth.kakao.KakaoClient;
import com.aloc.sai.auth.kakao.KakaoUserInfo;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 시간표 이미지 인식. 인식기는 가짜로 바꾸고, 이미지 검증과 학교 과목·건물 매칭을 확인한다.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimetableRecognitionTest {
    private static final AcademicTerm TERM = new AcademicTerm(2026, Semester.SECOND);
    private static final String TERM_QUERY = "?academicYear=2026&semester=SECOND";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};

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

    @MockitoBean
    private TimetableImageRecognizer recognizer;

    private String token;
    private Building engineering;
    private Building science;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        given(kakaoClient.getUserInfo("user-1")).willReturn(new KakaoUserInfo(1L, "지윤", null));
        token = login("user-1");
        engineering = buildingRepository.save(new Building("제1공학관", "B01", null, null));
        science = buildingRepository.save(new Building("과학기술관", "B02", null, null));
    }

    @AfterEach
    void cleanUp() {
        courseRepository.deleteAll();
        buildingRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void 과목명과_요일_시간이_맞는_분반이_하나면_학교_데이터로_보정한다() throws Exception {
        Course course = saveCourse(TERM, "01234", "01", "자료구조", Weekday.MON, "09:00", "10:15", engineering, "326호");
        // 띄어쓰기가 틀리고 끝나는 시각을 잘못 읽어도 같은 요일에 시간이 겹치면 매칭한다.
        recognizes(recognized("자료 구조", Weekday.MON, "09:00", "10:00", "공학관", "32B호", 0.81));

        recognize(TERM_QUERY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recognizedItems", hasSize(1)))
                .andExpect(jsonPath("$.recognizedItems[0].courseId").value(course.getCourseId()))
                .andExpect(jsonPath("$.recognizedItems[0].subjectNo").value("01234"))
                .andExpect(jsonPath("$.recognizedItems[0].dvclNo").value("01"))
                .andExpect(jsonPath("$.recognizedItems[0].courseName").value("자료구조"))
                .andExpect(jsonPath("$.recognizedItems[0].startTime").value("09:00"))
                .andExpect(jsonPath("$.recognizedItems[0].endTime").value("10:15"))
                .andExpect(jsonPath("$.recognizedItems[0].buildingId").value(engineering.getBuildingId()))
                .andExpect(jsonPath("$.recognizedItems[0].buildingName").value("공학관"))
                .andExpect(jsonPath("$.recognizedItems[0].room").value("326호"))
                .andExpect(jsonPath("$.recognizedItems[0].confidence").value(0.81));
    }

    @Test
    void 같은_이름의_분반은_요일_시간으로_골라낸다() throws Exception {
        saveCourse(TERM, "01234", "01", "자료구조", Weekday.MON, "09:00", "10:15", engineering, "326호");
        Course afternoon = saveCourse(TERM, "01234", "02", "자료구조", Weekday.MON, "13:00", "14:15", engineering, "327호");
        recognizes(recognized("자료구조", Weekday.MON, "13:00", "14:15", null, null, 0.9));

        recognize(TERM_QUERY)
                .andExpect(jsonPath("$.recognizedItems[0].courseId").value(afternoon.getCourseId()))
                .andExpect(jsonPath("$.recognizedItems[0].dvclNo").value("02"))
                .andExpect(jsonPath("$.recognizedItems[0].buildingName").value("제1공학관"));
    }

    @Test
    void 같은_시간의_분반이_여럿이면_잘못_연결하지_않도록_매칭하지_않는다() throws Exception {
        saveCourse(TERM, "01234", "01", "자료구조", Weekday.MON, "09:00", "10:15", engineering, "326호");
        saveCourse(TERM, "01234", "02", "자료구조", Weekday.MON, "09:00", "10:15", science, "101호");
        recognizes(recognized("자료구조", Weekday.MON, "09:00", "10:15", null, "326호", 0.9));

        recognize(TERM_QUERY)
                .andExpect(jsonPath("$.recognizedItems[0].courseId").value(nullValue()))
                .andExpect(jsonPath("$.recognizedItems[0].room").value("326호"));
    }

    @Test
    void 이름이_같아도_요일이나_시간이_다르면_매칭하지_않는다() throws Exception {
        saveCourse(TERM, "01234", "01", "자료구조", Weekday.MON, "09:00", "10:15", engineering, "326호");
        recognizes(
                recognized("자료구조", Weekday.TUE, "09:00", "10:15", null, null, 0.9),
                // 끝나는 시각에 시작하면 겹치지 않는다.
                recognized("자료구조", Weekday.MON, "10:15", "11:30", null, null, 0.9));

        recognize(TERM_QUERY)
                .andExpect(jsonPath("$.recognizedItems", hasSize(2)))
                .andExpect(jsonPath("$.recognizedItems[0].courseId").value(nullValue()))
                .andExpect(jsonPath("$.recognizedItems[1].courseId").value(nullValue()));
    }

    @Test
    void 매칭되지_않으면_읽은_값을_주고_건물은_이름으로_찾는다() throws Exception {
        recognizes(
                recognized("  캡스톤디자인 ", Weekday.WED, "15:00", "17:50", " 과학 기술관 ", " 501호 ", 0.6),
                recognized("동아리", Weekday.THU, "18:00", "20:00", "없는관", "  ", 0.4));

        recognize(TERM_QUERY)
                .andExpect(jsonPath("$.recognizedItems[0].courseId").value(nullValue()))
                .andExpect(jsonPath("$.recognizedItems[0].courseName").value("캡스톤디자인"))
                .andExpect(jsonPath("$.recognizedItems[0].startTime").value("15:00"))
                .andExpect(jsonPath("$.recognizedItems[0].buildingId").value(science.getBuildingId()))
                .andExpect(jsonPath("$.recognizedItems[0].buildingName").value("과학 기술관"))
                .andExpect(jsonPath("$.recognizedItems[0].room").value("501호"))
                .andExpect(jsonPath("$.recognizedItems[1].buildingId").value(nullValue()))
                .andExpect(jsonPath("$.recognizedItems[1].buildingName").value("없는관"))
                .andExpect(jsonPath("$.recognizedItems[1].room").value(nullValue()));
    }

    @Test
    void 요청한_학기의_과목과만_매칭하고_생략하면_현재_학기다() throws Exception {
        AcademicTerm current = AcademicTerm.current();
        AcademicTerm other = new AcademicTerm(2020, Semester.FIRST);
        Course currentCourse = saveCourse(current, "01234", "01", "자료구조", Weekday.MON, "09:00", "10:15", engineering, "326호");
        Course otherCourse = saveCourse(other, "01234", "01", "자료구조", Weekday.MON, "09:00", "10:15", engineering, "326호");
        recognizes(recognized("자료구조", Weekday.MON, "09:00", "10:15", null, null, 0.9));

        recognize("").andExpect(jsonPath("$.recognizedItems[0].courseId").value(currentCourse.getCourseId()));
        recognize("?academicYear=2020&semester=FIRST")
                .andExpect(jsonPath("$.recognizedItems[0].courseId").value(otherCourse.getCourseId()));
    }

    @Test
    void 필수값이_빠졌거나_시간이_잘못된_수업은_버리고_요일_시작_시각순으로_준다() throws Exception {
        recognizes(
                recognized("운영체제", Weekday.WED, "13:00", "14:15", null, null, 0.9),
                recognized(null, Weekday.MON, "09:00", "10:15", null, null, 0.9),
                recognized("  ", Weekday.MON, "09:00", "10:15", null, null, 0.9),
                recognized("알고리즘", null, "09:00", "10:15", null, null, 0.9),
                recognized("컴퓨터구조", Weekday.MON, "11:00", "10:00", null, null, 0.9),
                recognized("이산수학", Weekday.MON, "13:00", "14:15", null, null, 0.9),
                recognized("선형대수", Weekday.MON, "09:00", "10:15", null, null, 0.9));

        recognize(TERM_QUERY)
                .andExpect(jsonPath("$.recognizedItems", hasSize(3)))
                .andExpect(jsonPath("$.recognizedItems[0].courseName").value("선형대수"))
                .andExpect(jsonPath("$.recognizedItems[1].courseName").value("이산수학"))
                .andExpect(jsonPath("$.recognizedItems[2].courseName").value("운영체제"));
    }

    @Test
    void 인식된_수업이_없으면_빈_배열이다() throws Exception {
        recognizes();

        recognize(TERM_QUERY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recognizedItems", hasSize(0)));
    }

    @Test
    void 이미지가_없거나_지원하지_않는_형식이면_400이고_인식기를_부르지_않는다() throws Exception {
        mockMvc.perform(multipart("/api/v1/timetables/recognition").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("시간표 이미지를 첨부해 주세요."));
        recognize(TERM_QUERY, new MockMultipartFile("image", "a.png", "image/png", new byte[0]))
                .andExpect(status().isBadRequest());
        // 파일명·Content-Type이 PNG여도 내용이 이미지가 아니면 받지 않는다.
        recognize(TERM_QUERY, new MockMultipartFile("image", "a.png", "image/png", "hello".getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("JPG, PNG, HEIC 이미지만 인식할 수 있습니다."));
        // WEBP는 시간표 인식에서 받지 않는다.
        byte[] webp = Arrays.copyOf("RIFF\0\0\0\0WEBP".getBytes(StandardCharsets.US_ASCII), 16);
        recognize(TERM_QUERY, new MockMultipartFile("image", "a.webp", "image/webp", webp))
                .andExpect(status().isBadRequest());

        verify(recognizer, never()).recognize(any(), anyString());
    }

    @Test
    void 학기를_하나만_보내면_400이다() throws Exception {
        recognize("?academicYear=2026").andExpect(status().isBadRequest());

        verify(recognizer, never()).recognize(any(), anyString());
    }

    @Test
    void 인식기에는_판별한_이미지_형식을_넘긴다() throws Exception {
        recognizes();

        recognize(TERM_QUERY, new MockMultipartFile("image", "photo.jpg", "image/jpeg", PNG)).andExpect(status().isOk());

        verify(recognizer).recognize(any(), eq("image/png"));
    }

    @Test
    void 로그인하지_않으면_401() throws Exception {
        mockMvc.perform(multipart("/api/v1/timetables/recognition").file(new MockMultipartFile("image", "a.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 인식_기능_준비_전에는_503이다() {
        assertThatThrownBy(() -> new UnavailableTimetableImageRecognizer().recognize(PNG, "image/png"))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private void recognizes(RecognizedClass... classes) {
        given(recognizer.recognize(any(), anyString())).willReturn(List.of(classes));
    }

    private static RecognizedClass recognized(String name, Weekday day, String start, String end,
                                              String buildingName, String room, double confidence) {
        return new RecognizedClass(name, day, LocalTime.parse(start), LocalTime.parse(end), buildingName, room, confidence);
    }

    private ResultActions recognize(String query) throws Exception {
        return recognize(query, new MockMultipartFile("image", "timetable.png", "image/png", PNG));
    }

    private ResultActions recognize(String query, MockMultipartFile image) throws Exception {
        return mockMvc.perform(multipart("/api/v1/timetables/recognition" + query)
                .file(image)
                .header("Authorization", "Bearer " + token));
    }

    private Course saveCourse(AcademicTerm term, String subjectNo, String dvclNo, String name,
                              Weekday day, String start, String end, Building building, String room) {
        Course course = new Course(term, subjectNo, dvclNo, name, LocalDateTime.now());
        course.addSchedule(new CourseSchedule(course, day, LocalTime.parse(start), LocalTime.parse(end), building, room));
        return courseRepository.save(course);
    }

    private String login(String kakaoAccessToken) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/kakao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kakaoAccessToken\":\"" + kakaoAccessToken + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
    }
}
