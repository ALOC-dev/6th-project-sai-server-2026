package com.aloc.sai.timetable.dto;

import java.util.List;

// 과목 선택 등록이면 수업 시간 수만큼, 직접 입력이면 1개가 만들어진다.
public record TimetableItemCreateResponse(Long timetableId, List<TimetableItemResponse> createdItems) {
}
