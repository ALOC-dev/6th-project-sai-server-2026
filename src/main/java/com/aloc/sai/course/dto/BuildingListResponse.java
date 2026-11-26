package com.aloc.sai.course.dto;

import com.aloc.sai.course.Building;

import java.util.List;

public record BuildingListResponse(List<BuildingItem> buildings) {

    public record BuildingItem(Long buildingId, String buildingName, Double latitude, Double longitude) {
        public static BuildingItem from(Building building) {
            return new BuildingItem(building.getBuildingId(), building.getBuildingName(),
                    building.getLatitude(), building.getLongitude());
        }
    }
}
