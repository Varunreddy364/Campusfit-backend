package com.campusfit.campusfitbackend.repository;

import com.campusfit.campusfitbackend.entity.StudyPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface StudyPlanRepository extends JpaRepository<StudyPlan, Long> {
    List<StudyPlan> findByUserId(Long userId);
    List<StudyPlan> findByUserIdOrderByStartTimeAsc(Long userId);

    @Modifying
    @Transactional
    void deleteByUserId(Long userId);

    @Modifying
    @Transactional
    void deleteByUserIdAndStatus(Long userId, String status);

    @Modifying
    @Transactional
    void deleteByUserIdAndStatusIn(Long userId, List<String> statuses);
}

