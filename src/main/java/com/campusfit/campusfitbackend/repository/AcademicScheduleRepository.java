package com.campusfit.campusfitbackend.repository;

import com.campusfit.campusfitbackend.entity.AcademicSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcademicScheduleRepository extends JpaRepository<AcademicSchedule, Long> {
    List<AcademicSchedule> findByUserId(Long userId);
    List<AcademicSchedule> findByUserIdOrderByStartTimeAsc(Long userId);
    List<AcademicSchedule> findByUserIdAndDayOfWeekIgnoreCase(Long userId, String dayOfWeek);
}
