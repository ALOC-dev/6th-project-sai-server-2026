package com.aloc.sai.timetable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BuildingRepository extends JpaRepository<Building, Long> {
    List<Building> findAllByOrderByBuildingNameAsc();

    @Query("""
            select b from Building b
            where b.buildingName like :pattern escape '\\'
            order by b.buildingName
            """)
    List<Building> searchByName(@Param("pattern") String pattern);
}
