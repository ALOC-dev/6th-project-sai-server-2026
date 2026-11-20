package com.aloc.sai.timetable.dto;

import com.aloc.sai.timetable.Weekday;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalTime;
import java.util.List;

// 인식 결과는 저장하지 않는다. 사용자가 고친 뒤 일괄 등록 API로 저장한다.
public record TimetableRecognitionResponse(List<RecognizedItem> recognizedItems) {

    // 안쪽 record 이름은 API 문서의 스키마 이름이 되므로 다른 DTO와 겹치지 않게 짓는다.
    // 학교 과목과 매칭되면 courseId·subjectNo·dvclNo가 있고, 과목명·시간·강의실은 학교 데이터 값이다.
    // buildingId는 매칭된 과목의 강의 건물 또는 읽은 건물명과 이름이 같은 건물이다.
    // 건물이 정해지면 buildingName은 그 건물의 이름이고, 정해지지 않으면 이미지에서 읽은 값이다(ID와 이름이 어긋나지 않게).
    public record RecognizedItem(
            Long courseId,
            String subjectNo,
            String dvclNo,
            String courseName,
            Weekday dayOfWeek,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime,
            Long buildingId,
            String buildingName,
            String room,
            Double confidence
    ) {
    }
}
