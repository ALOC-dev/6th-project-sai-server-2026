package com.aloc.sai.timetable;

import com.aloc.sai.ai.AiException;
import com.aloc.sai.ai.AiTimetableClient;
import com.aloc.sai.ai.dto.AiTimetableRecognitionItem;
import com.aloc.sai.common.exception.ApiException;
import com.aloc.sai.course.Weekday;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;

// AI 서버 인식 결과를 시간표 도메인 값(RecognizedClass)으로 바꾸고, AI 실패를 사용자에게 보여 줄 오류로 바꾼다.
@Component
@RequiredArgsConstructor
public class AiTimetableImageRecognizer implements TimetableImageRecognizer {
    private static final String FAILED_MESSAGE = "시간표 이미지 인식에 실패했습니다. 수업을 직접 등록해 주세요.";

    private final AiTimetableClient aiTimetableClient;

    @Override
    public List<RecognizedClass> recognize(byte[] image, String contentType) {
        try {
            return aiTimetableClient.recognize(image, contentType).stream()
                    .map(AiTimetableImageRecognizer::toRecognizedClass)
                    .toList();
        } catch (AiException e) {
            throw toApiException(e);
        }
    }

    private static ApiException toApiException(AiException e) {
        return switch (e.getStatus()) {
            case 400 -> new ApiException(HttpStatus.BAD_REQUEST, "이미지를 읽을 수 없습니다. 시간표 캡처 이미지를 올려 주세요.", e);
            case 413 -> new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "이미지 용량이 너무 큽니다.", e);
            // 내부 키 설정 오류는 사용자 잘못이 아니므로 서버 오류로 알린다.
            case 401 -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "시간표 이미지 인식 중 서버 오류가 발생했습니다.", e);
            default -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, FAILED_MESSAGE, e);
        };
    }

    // 읽을 수 없는 요일·시각은 null로 두면 매칭 단계(TimetableRecognitionService.isUsable)에서 그 수업만 버려진다.
    private static RecognizedClass toRecognizedClass(AiTimetableRecognitionItem item) {
        return new RecognizedClass(item.courseName(), parseWeekday(item.dayOfWeek()),
                parseTime(item.startTime()), parseTime(item.endTime()),
                item.buildingName(), item.room(), item.confidence());
    }

    private static Weekday parseWeekday(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Weekday.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static LocalTime parseTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
