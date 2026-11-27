package com.aloc.sai.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// AI 명세 그대로의 수업 한 줄. dayOfWeek는 "MON"~"SUN", 시각은 "HH:mm" 문자열이다.
// buildingName·room은 못 읽었으면 null.
@JsonIgnoreProperties(ignoreUnknown = true)
public record AiTimetableRecognitionItem(
        String courseName,
        String dayOfWeek,
        String startTime,
        String endTime,
        String buildingName,
        String room,
        Double confidence
) {
}
