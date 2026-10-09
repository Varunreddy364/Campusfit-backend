package com.campusfit.campusfitbackend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "student_feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "mood")
    private String mood; // e.g., "Great", "Okay", "Stressed", "Exhausted"

    @Column(name = "academic_load_score")
    private Integer academicLoadScore; // 1-10

    @Column(name = "workout_difficulty_score")
    private Integer workoutDifficultyScore; // 1-10

    @Column(name = "planner_satisfaction_score")
    private Integer plannerSatisfactionScore; // 1-10

    @Column(name = "missed_activity_reason", columnDefinition = "TEXT")
    private String missedActivityReason;

    @Column(name = "improvement_suggestions", columnDefinition = "TEXT")
    private String improvementSuggestions;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Module-specific feedback fields (transient to avoid database schema alteration)
    @Transient
    private Integer studyPlanScore; // 1-5 star rating

    @Transient
    private Integer replanScore; // 1-5 star rating

    @Transient
    private Integer workoutScore; // 1-5 star rating

    @Transient
    private Integer nutritionScore; // 1-5 star rating
}
