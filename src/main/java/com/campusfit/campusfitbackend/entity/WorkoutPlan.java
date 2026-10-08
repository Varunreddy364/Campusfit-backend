package com.campusfit.campusfitbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "workout_plan")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "workout_id")
    private Long workoutId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @Column(name = "workout_date", nullable = false)
    private LocalDate workoutDate;

    @Column(name = "workout_title", nullable = false)
    private String workoutTitle;

    @Column(name = "workout_goal", nullable = false)
    private String workoutGoal;

    @Column(name = "workout_focus", nullable = false)
    private String workoutFocus;

    @Column(name = "fitness_level", nullable = false)
    private String fitnessLevel;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "duration", nullable = false)
    private Integer duration; // in minutes

    @Column(name = "calories_estimated", nullable = false)
    private Integer caloriesEstimated;

    @Column(name = "workout_details", columnDefinition = "LONGTEXT")
    private String workoutDetails; // JSON containing warmup, main workout, and cooldown details

    @Column(name = "adjustment_reason")
    private String adjustmentReason;

    @Column(name = "status", nullable = false)
    private String status = "PENDING"; // PENDING, COMPLETED
}
