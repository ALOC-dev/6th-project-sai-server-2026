package com.aloc.sai.timetable;

import java.util.List;

// 시간표 이미지에서 수업을 읽는다. 학교 과목·건물과 맞추는 일은 하지 않는다(TimetableRecognitionService.matchRecognized).
// 인식을 어디서 할지(AI 서버 등)가 정해지면 이 인터페이스의 구현체만 바꾼다.
public interface TimetableImageRecognizer {

    List<RecognizedClass> recognize(byte[] image, String contentType);
}
