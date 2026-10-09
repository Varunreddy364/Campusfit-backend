package com.campusfit.campusfitbackend.dto;

import com.campusfit.campusfitbackend.entity.StudyPlan;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReplanPreviewResponse {
    private boolean success;
    private String message;
    private List<MissedSessionInfo> affectedSessions = new ArrayList<>();
    private List<ProposedSessionInfo> proposedReplacements = new ArrayList<>();
    private List<UnscheduledTaskInfo> unscheduledTasks = new ArrayList<>();
    private List<StudyPlan> candidatePlans = new ArrayList<>();
}
