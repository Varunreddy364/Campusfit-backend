package com.campusfit.campusfitbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnscheduledTaskInfo {
    private String taskTitle;
    private double remainingHoursUnscheduled;
    private String reason;
}
