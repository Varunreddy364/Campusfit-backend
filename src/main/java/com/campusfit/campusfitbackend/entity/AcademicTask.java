package com.campusfit.campusfitbackend.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "academic_task")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AcademicTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "task_type", nullable = false)
    private String taskType; // ASSIGNMENT, EXAM, PROJECT, LAB

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    @Column(name = "deadline", nullable = false)
    private LocalDateTime deadline;

    @Column(name = "estimated_hours")
    private Double estimatedHours;

    @Column(name = "priority")
    private String priority; // HIGH, MEDIUM, LOW

    @Column(name = "status", nullable = false)
    private String status = "PENDING"; // PENDING, COMPLETED
}
