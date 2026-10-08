package com.campusfit.campusfitbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "study_plan")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false)
    private String title;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "status", nullable = false)
    private String status = "PENDING"; // PENDING, COMPLETED

    @Column(name = "explanation")
    private String explanation;

    public StudyPlan(Long planId, Long userId, String title, LocalDateTime startTime, LocalDateTime endTime, String status) {
        this.planId = planId;
        this.userId = userId;
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
    }
}
