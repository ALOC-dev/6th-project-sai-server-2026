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

// 학교 건물. 학교 API(ApiBldg)로 채우고, 학교 API에 없는 위도·경도는 따로 입력한다.
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

    // 학교 건물코드. 동기화할 때 같은 건물을 찾는 기준이다.
    @Column(unique = true, length = 20)
    private String buildingCode;

    public Building(String buildingName, String buildingCode, Double latitude, Double longitude) {
        this.buildingName = buildingName;
        this.buildingCode = buildingCode;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
