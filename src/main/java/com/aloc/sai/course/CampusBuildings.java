package com.aloc.sai.course;

import java.util.LinkedHashMap;
import java.util.Map;

// 캠퍼스 공식 건물 번호표(https://www.uos.ac.kr/kor/html/auos/guide/map/map.do).
// 학교 시간표 API의 강의실 표기("19-B102")는 이 번호를 쓴다. 학교 건물 API(ApiBldg)는 번호 체계가 달라 쓰지 않는다.
public final class CampusBuildings {
    public static final Map<String, String> NAME_BY_NUMBER;

    static {
        Map<String, String> buildings = new LinkedHashMap<>();
        buildings.put("1", "전농관");
        buildings.put("3", "건설공학관");
        buildings.put("4", "창공관");
        buildings.put("5", "인문학관");
        buildings.put("6", "배봉관");
        buildings.put("7", "대학본부");
        buildings.put("8", "자연과학관");
        buildings.put("10", "경농관");
        buildings.put("11", "창의혁신관");
        buildings.put("12", "학생회관");
        buildings.put("13", "시대인재관");
        buildings.put("14", "과학기술관");
        buildings.put("15", "21세기관");
        buildings.put("16", "조형관");
        buildings.put("18", "자작마루");
        buildings.put("19", "정보기술관");
        buildings.put("20", "법학관");
        buildings.put("21", "중앙도서관");
        buildings.put("22", "생활관");
        buildings.put("23", "건축구조실험동");
        buildings.put("24", "토목구조실험동");
        buildings.put("25", "미디어관");
        buildings.put("27", "대강당");
        buildings.put("28", "운동장");
        buildings.put("29", "박물관");
        buildings.put("32", "웰니스센터");
        buildings.put("33", "미래관");
        buildings.put("34", "국제학사");
        buildings.put("35", "음악관");
        buildings.put("36", "어린이집");
        buildings.put("37", "100주년 기념관");
        buildings.put("38", "스마트연구동");
        buildings.put("39", "시대융합관");
        buildings.put("41", "실외테니스장");
        buildings.put("81", "자동화온실");
        NAME_BY_NUMBER = Map.copyOf(buildings);
    }

    private CampusBuildings() {
    }
}
