package com.aloc.sai.timetable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 학교 건물. 캠퍼스 공식 건물 번호표(CampusBuildings)로 채우고, 위도·경도는 따로 입력한다.
@Entity
@Table(name = "buildings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Building {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long buildingId;

    @Column(nullable = false, unique = true, length = 50)
    private String buildingName;

    // 학교 API에 없어서 직접 입력하기 전까지는 비어 있을 수 있다.
    private Double latitude;

    private Double longitude;

    // 캠퍼스 건물 번호(예: "19"). 강의실 표기 "19-B102"의 앞부분과 같고, 동기화할 때 같은 건물을 찾는 기준이다.
    @Column(unique = true, length = 20)
    private String buildingCode;

    public Building(String buildingName, String buildingCode, Double latitude, Double longitude) {
        this.buildingName = buildingName;
        this.buildingCode = buildingCode;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public void syncFromSchool(String buildingName, String buildingCode) {
        this.buildingName = buildingName;
        this.buildingCode = buildingCode;
    }
}
