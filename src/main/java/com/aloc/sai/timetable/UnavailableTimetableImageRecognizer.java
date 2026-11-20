package com.aloc.sai.timetable;

import com.aloc.sai.common.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;

// 인식 방식(AI 서버 연동)이 정해지기 전까지 쓰는 임시 구현체. 항상 503으로 준비 중임을 알린다.
// AI 서버 클라이언트를 만들면 그 구현체로 바꾸고 이 클래스는 지운다.
@Component
public class UnavailableTimetableImageRecognizer implements TimetableImageRecognizer {

    @Override
    public List<RecognizedClass> recognize(byte[] image, String contentType) {
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "시간표 이미지 인식은 아직 준비 중입니다. 수업을 직접 등록해 주세요.");
    }
}
