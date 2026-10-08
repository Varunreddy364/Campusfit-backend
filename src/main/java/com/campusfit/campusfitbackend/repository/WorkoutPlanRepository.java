package com.campusfit.campusfitbackend.repository;

import com.campusfit.campusfitbackend.entity.WorkoutPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkoutPlanRepository extends JpaRepository<WorkoutPlan, Long> {

    List<WorkoutPlan> findByUserIdOrderByWorkoutDateDescStartTimeAsc(Long userId);

    Optional<WorkoutPlan> findByUserIdAndWorkoutDate(Long userId, LocalDate workoutDate);

    List<WorkoutPlan> findByUserIdAndStatus(Long userId, String status);

    List<WorkoutPlan> findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(Long userId, LocalDate start, LocalDate end);

    @Modifying
    @Transactional
    void deleteByUserIdAndWorkoutDateAndStatus(Long userId, LocalDate workoutDate, String status);

    long countByUserId(Long userId);

    long countByUserIdAndStatus(Long userId, String status);
}
