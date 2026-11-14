package com.aloc.sai.timetable.dto;

import com.aloc.sai.timetable.Building;

// 과목 수업 시간·시간표 항목 응답에 붙는 건물 정보. 건물이 없으면 null로 내려간다.
public record BuildingSummary(Long buildingId, String buildingName) {
    public static BuildingSummary from(Building building) {
        return building == null ? null : new BuildingSummary(building.getBuildingId(), building.getBuildingName());
    }
}
