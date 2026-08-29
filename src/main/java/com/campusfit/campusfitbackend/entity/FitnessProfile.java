package com.campusfit.campusfitbackend.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "FitnessProfile")
public class FitnessProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer profileID;

    private Integer userID;

    private String fitnessLevel;

    private String preferredWorkout;

    private Integer targetWeight;

    private Integer dailyCalorieGoal;

    private Integer dailyProteinGoal;

    private String medicalConditions;

    private String fitnessGoal;

    public FitnessProfile() {
    }

    public Integer getProfileID() {
        return profileID;
    }

    public void setProfileID(Integer profileID) {
        this.profileID = profileID;
    }

    public Integer getUserID() {
        return userID;
    }

    public void setUserID(Integer userID) {
        this.userID = userID;
    }

    public String getFitnessLevel() {
        return fitnessLevel;
    }

    public void setFitnessLevel(String fitnessLevel) {
        this.fitnessLevel = fitnessLevel;
    }

    public String getPreferredWorkout() {
        return preferredWorkout;
    }

    public void setPreferredWorkout(String preferredWorkout) {
        this.preferredWorkout = preferredWorkout;
    }

    public Integer getTargetWeight() {
        return targetWeight;
    }

    public void setTargetWeight(Integer targetWeight) {
        this.targetWeight = targetWeight;
    }

    public Integer getDailyCalorieGoal() {
        return dailyCalorieGoal;
    }

    public void setDailyCalorieGoal(Integer dailyCalorieGoal) {
        this.dailyCalorieGoal = dailyCalorieGoal;
    }

    public Integer getDailyProteinGoal() {
        return dailyProteinGoal;
    }

    public void setDailyProteinGoal(Integer dailyProteinGoal) {
        this.dailyProteinGoal = dailyProteinGoal;
    }

    public String getMedicalConditions() {
        return medicalConditions;
    }

    public void setMedicalConditions(String medicalConditions) {
        this.medicalConditions = medicalConditions;
    }

    public String getFitnessGoal() {
        return fitnessGoal;
    }

    public void setFitnessGoal(String fitnessGoal) {
        this.fitnessGoal = fitnessGoal;
    }
}