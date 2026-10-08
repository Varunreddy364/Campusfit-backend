package com.campusfit.campusfitbackend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "bmi_record")
public class BmiRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long recordId;

    @Column(nullable = false)
    private Long userId;

    private Double height; // in cm
    private Double weight; // in kg
    private Double bmi;
    private String category;
    private Integer healthScore;

    private LocalDateTime recordedAt;

    public BmiRecord() {
    }

    public BmiRecord(Long recordId, Long userId, Double height, Double weight, Double bmi, String category, Integer healthScore, LocalDateTime recordedAt) {
        this.recordId = recordId;
        this.userId = userId;
        this.height = height;
        this.weight = weight;
        this.bmi = bmi;
        this.category = category;
        this.healthScore = healthScore;
        this.recordedAt = recordedAt;
    }

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Double getHeight() {
        return height;
    }

    public void setHeight(Double height) {
        this.height = height;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Double getBmi() {
        return bmi;
    }

    public void setBmi(Double bmi) {
        this.bmi = bmi;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getHealthScore() {
        return healthScore;
    }

    public void setHealthScore(Integer healthScore) {
        this.healthScore = healthScore;
    }

    public LocalDateTime getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(LocalDateTime recordedAt) {
        this.recordedAt = recordedAt;
    }
}
