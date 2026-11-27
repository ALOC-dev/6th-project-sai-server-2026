package com.aloc.sai.timetable;

import com.aloc.sai.ai.AiException;
import com.aloc.sai.ai.AiTimetableClient;
import com.aloc.sai.ai.dto.AiTimetableRecognitionItem;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.course.Weekday;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class AiTimetableImageRecognizerTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private final AiTimetableClient aiTimetableClient = mock(AiTimetableClient.class);
    private final AiTimetableImageRecognizer recognizer = new AiTimetableImageRecognizer(aiTimetableClient);

    @Test
    void AI_결과를_시간표_도메인_값으로_바꾼다() {
        given(aiTimetableClient.recognize(PNG, "image/png")).willReturn(List.of(
                new AiTimetableRecognitionItem("자료구조", "MON", "09:00", "10:15", "정보기술관", "19-326,27", 0.91),
                new AiTimetableRecognitionItem("근로", "TUE", "13:00", "17:00", null, null, 0.8)));

        assertThat(recognizer.recognize(PNG, "image/png")).containsExactly(
                new RecognizedClass("자료구조", Weekday.MON, LocalTime.of(9, 0), LocalTime.of(10, 15),
                        "정보기술관", "19-326,27", 0.91),
                new RecognizedClass("근로", Weekday.TUE, LocalTime.of(13, 0), LocalTime.of(17, 0),
                        null, null, 0.8));
    }

    @Test
    void 읽을_수_없는_요일과_시각은_null로_두어_매칭에서_버려지게_한다() {
        given(aiTimetableClient.recognize(PNG, "image/png")).willReturn(List.of(
                new AiTimetableRecognitionItem("자료구조", "월", "9시", "10:15", null, null, 0.5)));

        RecognizedClass recognized = recognizer.recognize(PNG, "image/png").getFirst();

        assertThat(recognized.dayOfWeek()).isNull();
        assertThat(recognized.startTime()).isNull();
        assertThat(recognized.endTime()).isEqualTo(LocalTime.of(10, 15));
    }

    @Test
    void AI가_이미지를_못_읽으면_400() {
        failsWith(400, HttpStatus.BAD_REQUEST);
    }

    @Test
    void 용량_초과는_413() {
        failsWith(413, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void 내부_키_인증_실패는_사용자_잘못이_아니므로_500() {
        failsWith(401, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void AI_타임아웃_응답은_503() {
        failsWith(504, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void 연결_실패는_503() {
        failsWith(AiException.UNAVAILABLE_STATUS, HttpStatus.SERVICE_UNAVAILABLE);
    }

    private void failsWith(int aiStatus, HttpStatus expected) {
        given(aiTimetableClient.recognize(PNG, "image/png"))
                .willThrow(new AiException(aiStatus, null, "AI 오류", null));

        assertThatThrownBy(() -> recognizer.recognize(PNG, "image/png"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getStatus()).isEqualTo(expected));
    }
}
