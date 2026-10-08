package com.campusfit.campusfitbackend.repository;

import com.campusfit.campusfitbackend.entity.AcademicTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AcademicTaskRepository extends JpaRepository<AcademicTask, Long> {
    List<AcademicTask> findByUserId(Long userId);
    List<AcademicTask> findByUserIdOrderByDeadlineAsc(Long userId);
    List<AcademicTask> findByUserIdAndStatus(Long userId, String status);
}
