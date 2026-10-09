package com.campusfit.campusfitbackend.service;

import com.campusfit.campusfitbackend.dto.*;
import com.campusfit.campusfitbackend.entity.AcademicSchedule;
import com.campusfit.campusfitbackend.entity.AcademicTask;
import com.campusfit.campusfitbackend.entity.StudyPlan;
import com.campusfit.campusfitbackend.repository.AcademicScheduleRepository;
import com.campusfit.campusfitbackend.repository.AcademicTaskRepository;
import com.campusfit.campusfitbackend.repository.StudyPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@Service
public class PlanGenerationService {

    @Autowired
    private AcademicScheduleRepository academicScheduleRepository;

    @Autowired
    private AcademicTaskRepository academicTaskRepository;

    @Autowired
    private StudyPlanRepository studyPlanRepository;

    // Study window bounds (8 AM to 8 PM)
    private static final LocalTime DAY_START = LocalTime.of(8, 0);
    private static final LocalTime DAY_END = LocalTime.of(20, 0);

    // Configurable Break Constants (in minutes)
    public static final int SINGLE_CLASS_BREAK_MINUTES = 15;        // 10-15 mins after 1 class
    public static final int CONSECUTIVE_CLASSES_BREAK_MINUTES = 30; // 20-30 mins after 2-3 consecutive classes
    public static final int MEAL_BREAK_MINUTES = 45;               // 30-60 mins for meal / long academic period
    public static final int STUDY_BREAK_MINUTES = 15;              // 5-15 mins between study sessions
    public static final int LONG_STUDY_BREAK_MINUTES = 25;         // 15-30 mins after >= 90 mins of study
    public static final int PRE_CLASS_BUFFER_MINUTES = 10;         // 5-10 mins buffer before class
    public static final int MIN_STUDY_SESSION_MINUTES = 30;
    public static final int DEFAULT_STUDY_SESSION_MINUTES = 45;

    @Transactional
    public List<StudyPlan> generateStudyPlan(Long userId) {
        return generateStudyPlan(userId, LocalDateTime.now());
    }

    @Transactional
    public List<StudyPlan> generateStudyPlan(Long userId, LocalDateTime referenceNow) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }
        if (referenceNow == null) {
            referenceNow = LocalDateTime.now();
        }

        // 1. Read Academic Schedule
        List<AcademicSchedule> schedules = academicScheduleRepository.findByUserId(userId);

        // 2. Read Academic Tasks (Pending only) and sort by nearest deadline
        List<AcademicTask> allTasks = academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId);
        List<AcademicTask> pendingTasks = new ArrayList<>();
        for (AcademicTask t : allTasks) {
            if ("PENDING".equalsIgnoreCase(t.getStatus())) {
                pendingTasks.add(t);
            }
        }

        if (pendingTasks.isEmpty()) {
            studyPlanRepository.deleteByUserIdAndStatus(userId, "PENDING");
            studyPlanRepository.deleteByUserIdAndStatus(userId, "BREAK");
            return Collections.emptyList();
        }

        // 3. Check completed study sessions for adaptive replanning (ignoring breaks)
        Map<String, Double> completedHoursByTitle = new HashMap<>();
        List<StudyPlan> existingPlans = studyPlanRepository.findByUserIdOrderByStartTimeAsc(userId);
        for (StudyPlan sp : existingPlans) {
            boolean isBreak = "BREAK".equalsIgnoreCase(sp.getStatus())
                    || (sp.getTitle() != null && sp.getTitle().toLowerCase().startsWith("break"));
            if ("COMPLETED".equalsIgnoreCase(sp.getStatus()) && !isBreak && sp.getTitle() != null
                    && sp.getStartTime() != null && sp.getEndTime() != null) {
                double hours = Duration.between(sp.getStartTime(), sp.getEndTime()).toMinutes() / 60.0;
                String key = sp.getTitle().trim().toLowerCase();
                completedHoursByTitle.put(key, completedHoursByTitle.getOrDefault(key, 0.0) + hours);
            }
        }

        // 4. Find break-aware free slots across the upcoming 7 days
        List<ScheduleSlot> allSlots = new ArrayList<>();
        LocalDate today = referenceNow.toLocalDate();
        LocalDateTime now = referenceNow;

        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate date = today.plusDays(dayOffset);
            String dayName = date.getDayOfWeek().name();

            // Filter classes for this day
            List<AcademicSchedule> dayClasses = new ArrayList<>();
            for (AcademicSchedule s : schedules) {
                if (s.getDayOfWeek() != null && s.getDayOfWeek().equalsIgnoreCase(dayName)) {
                    dayClasses.add(s);
                }
            }
            dayClasses.sort(Comparator.comparing(AcademicSchedule::getStartTime));

            List<ScheduleSlot> daySlots = buildBreakAwareSlotsForDay(date, dayClasses, now);
            allSlots.addAll(daySlots);
        }

        // 5. Build Smart Task Trackers
        class TaskTracker {
            final AcademicTask task;
            double remainingHours;
            int consecutiveSessions = 0;
            int sessionsScheduledToday = 0;
            LocalDate lastScheduledDate = null;

            TaskTracker(AcademicTask task, double remainingHours) {
                this.task = task;
                this.remainingHours = remainingHours;
            }
        }

        List<TaskTracker> trackers = new ArrayList<>();
        for (AcademicTask t : pendingTasks) {
            double origHours = (t.getEstimatedHours() != null && t.getEstimatedHours() > 0) ? t.getEstimatedHours() : 1.0;
            double done = completedHoursByTitle.getOrDefault(t.getTitle().trim().toLowerCase(), 0.0);
            double rem = origHours - done;
            if (rem <= 0.05) {
                // If task is still marked pending, give at least 1 hour for adaptive replanning
                rem = 1.0;
            }
            trackers.add(new TaskTracker(t, rem));
        }

        List<StudyPlan> generatedSessions = new ArrayList<>();
        List<ScheduleSlot> assignedStudySlots = new ArrayList<>();
        LocalDate currentTrackingDate = null;

        for (ScheduleSlot slot : allSlots) {
            if (slot.isBreak()) {
                continue; // Break slots are not candidates for task assignment
            }

            LocalDate slotDate = slot.getStart().toLocalDate();

            // Reset daily counters when moving to a new day
            if (!slotDate.equals(currentTrackingDate)) {
                currentTrackingDate = slotDate;
                for (TaskTracker tr : trackers) {
                    tr.sessionsScheduledToday = 0;
                    tr.consecutiveSessions = 0;
                }
            }

            // Find candidates with remaining hours
            List<TaskTracker> activeCandidates = new ArrayList<>();
            for (TaskTracker tr : trackers) {
                if (tr.remainingHours > 0.05) {
                    activeCandidates.add(tr);
                }
            }

            if (activeCandidates.isEmpty()) {
                break; // All task workloads fulfilled
            }

            // Score each candidate for this slot
            TaskTracker bestCandidate = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (TaskTracker tr : activeCandidates) {
                AcademicTask task = tr.task;
                long hoursUntil = (task.getDeadline() != null)
                        ? Duration.between(slot.getStart(), task.getDeadline()).toHours()
                        : 999;
                long daysUntil = (task.getDeadline() != null)
                        ? Math.max(0, Duration.between(slot.getStart(), task.getDeadline()).toDays())
                        : 99;

                // Base score from deadline urgency
                double score = 0.0;
                if (hoursUntil <= 24 || daysUntil <= 1) {
                    score += 75.0; // Due tomorrow or today: highest urgency
                } else if (daysUntil <= 2) {
                    score += 45.0;
                } else if (daysUntil <= 3) {
                    score += 32.0;
                } else if (daysUntil <= 5) {
                    score += 18.0;
                } else if (daysUntil <= 7) {
                    score += 10.0;
                } else {
                    score += 5.0;
                }

                // Priority bonus
                if ("HIGH".equalsIgnoreCase(task.getPriority())) {
                    score += 12.0;
                } else if ("MEDIUM".equalsIgnoreCase(task.getPriority())) {
                    score += 6.0;
                }

                // Exam within 2 days rule: increase exam study allocation
                if ("EXAM".equalsIgnoreCase(task.getTaskType())) {
                    if (daysUntil <= 2) {
                        score += 35.0;
                    } else {
                        score += 10.0;
                    }
                }

                // Workload demand weight
                if (task.getEstimatedHours() != null) {
                    score += Math.min(8.0, task.getEstimatedHours() * 1.5);
                }

                // Interleaving & Balance: Avoid 3-4 consecutive hours of same subject
                if (tr.consecutiveSessions == 1) {
                    score -= 28.0; // Moderate penalty to encourage interleaving when deadlines are close
                } else if (tr.consecutiveSessions >= 2) {
                    score -= 60.0; // Heavy penalty to prevent 3+ consecutive hours
                }

                // Daily variety penalty
                score -= (tr.sessionsScheduledToday * 16.0);

                if (score > bestScore) {
                    bestScore = score;
                    bestCandidate = tr;
                }
            }

            if (bestCandidate == null) {
                bestCandidate = activeCandidates.get(0);
            }

            // Calculate slot duration
            double slotDurationHours = Duration.between(slot.getStart(), slot.getEnd()).toMinutes() / 60.0;
            int durationMins = (int) Duration.between(slot.getStart(), slot.getEnd()).toMinutes();
            String durationStr = (durationMins == 60) ? "1 hour" : (durationMins + " mins");

            // Generate "Why this plan was generated" explanation
            AcademicTask chosenTask = bestCandidate.task;
            long chosenDaysUntil = (chosenTask.getDeadline() != null)
                    ? Math.max(0, Duration.between(slot.getStart(), chosenTask.getDeadline()).toDays())
                    : 99;

            String explanation;
            if ("EXAM".equalsIgnoreCase(chosenTask.getTaskType()) && chosenDaysUntil <= 2) {
                explanation = "Allocated " + durationStr + " because exam is in " + (chosenDaysUntil <= 1 ? "1 day." : chosenDaysUntil + " days.");
            } else if (chosenDaysUntil <= 1) {
                explanation = "Allocated " + durationStr + " because deadline is tomorrow.";
            } else if (chosenDaysUntil <= 3) {
                explanation = "Allocated " + durationStr + " because deadline is in " + chosenDaysUntil + " days.";
            } else if (chosenTask.getEstimatedHours() != null && chosenTask.getEstimatedHours() >= 3.0) {
                int totalHoursInt = (int) Math.round(chosenTask.getEstimatedHours());
                explanation = "Allocated " + durationStr + " because task requires " + totalHoursInt + " total hours.";
            } else if ("HIGH".equalsIgnoreCase(chosenTask.getPriority())) {
                explanation = "Allocated " + durationStr + " due to high priority.";
            } else {
                explanation = "Allocated " + durationStr + " because deadline is in " + chosenDaysUntil + " days.";
            }

            StudyPlan plan = new StudyPlan();
            plan.setUserId(userId);
            plan.setTitle(chosenTask.getTitle());
            plan.setStartTime(slot.getStart());
            plan.setEndTime(slot.getEnd());
            plan.setStatus("PENDING");
            plan.setExplanation(explanation);

            generatedSessions.add(plan);
            assignedStudySlots.add(slot);

            // Update tracker states
            bestCandidate.remainingHours -= slotDurationHours;
            bestCandidate.sessionsScheduledToday++;
            bestCandidate.consecutiveSessions++;

            // Reset consecutive sessions for all other trackers
            for (TaskTracker tr : trackers) {
                if (tr != bestCandidate) {
                    tr.consecutiveSessions = 0;
                }
            }
        }

        // Include break sessions that precede or separate assigned study sessions on that day
        for (ScheduleSlot b : allSlots) {
            if (b.isBreak()) {
                boolean hasStudySessionAfter = assignedStudySlots.stream().anyMatch(s ->
                        s.getStart().toLocalDate().equals(b.getStart().toLocalDate())
                                && !s.getStart().isBefore(b.getEnd())
                );
                if (hasStudySessionAfter) {
                    StudyPlan breakPlan = new StudyPlan();
                    breakPlan.setUserId(userId);
                    breakPlan.setTitle(b.getBreakTitle());
                    breakPlan.setStartTime(b.getStart());
                    breakPlan.setEndTime(b.getEnd());
                    breakPlan.setStatus("BREAK");
                    breakPlan.setExplanation(b.getBreakReason());
                    generatedSessions.add(breakPlan);
                }
            }
        }

        // Sort chronologically
        generatedSessions.sort(Comparator.comparing(StudyPlan::getStartTime));

        // 6. Clear old PENDING sessions & old BREAK sessions, then store new generated sessions
        studyPlanRepository.deleteByUserIdAndStatus(userId, "PENDING");
        studyPlanRepository.deleteByUserIdAndStatus(userId, "BREAK");
        return studyPlanRepository.saveAll(generatedSessions);
    }


    public List<StudyPlan> getStudyPlansByUserId(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }
        return studyPlanRepository.findByUserIdOrderByStartTimeAsc(userId);
    }

    public Optional<StudyPlan> markPlanCompleted(Long planId) {
        if (planId == null) {
            throw new IllegalArgumentException("Plan ID is required.");
        }
        return studyPlanRepository.findById(planId).map(plan -> {
            plan.setStatus("COMPLETED");
            return studyPlanRepository.save(plan);
        });
    }

    public Optional<StudyPlan> updatePlanStatus(Long planId, String status) {
        if (planId == null) {
            throw new IllegalArgumentException("Plan ID is required.");
        }
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be empty.");
        }
        return studyPlanRepository.findById(planId).map(plan -> {
            plan.setStatus(status.trim().toUpperCase());
            return studyPlanRepository.save(plan);
        });
    }

    public boolean deletePlan(Long planId) {
        if (planId == null) {
            throw new IllegalArgumentException("Plan ID is required.");
        }
        if (studyPlanRepository.existsById(planId)) {
            studyPlanRepository.deleteById(planId);
            return true;
        }
        return false;
    }

    // ==========================================================
    // REPLANNING ENGINE (BASED ON MISSED TIME INTERVAL)
    // ==========================================================

    @Transactional(readOnly = true)
    public ReplanPreviewResponse previewReplan(Long userId, ReplanRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }
        if (request == null) {
            throw new IllegalArgumentException("Replan request is required.");
        }
        if (request.getMissedDate() == null) {
            throw new IllegalArgumentException("Missed date is required.");
        }
        if (request.getMissedStartTime() == null || request.getMissedEndTime() == null) {
            throw new IllegalArgumentException("Missed start and end times are required.");
        }
        if (!request.getMissedStartTime().isBefore(request.getMissedEndTime())) {
            throw new IllegalArgumentException("Missed start time must be before end time.");
        }

        LocalDateTime missedStart = LocalDateTime.of(request.getMissedDate(), request.getMissedStartTime());
        LocalDateTime missedEnd = LocalDateTime.of(request.getMissedDate(), request.getMissedEndTime());

        // 1. Fetch all existing plans for user
        List<StudyPlan> existingPlans = studyPlanRepository.findByUserIdOrderByStartTimeAsc(userId);

        // 2. Identify overlapping sessions
        List<MissedSessionInfo> affectedSessionInfos = new ArrayList<>();
        List<StudyPlan> affectedPendingPlans = new ArrayList<>();
        Map<String, Double> missedHoursByTaskTitle = new LinkedHashMap<>();

        for (StudyPlan sp : existingPlans) {
            if (sp.getStartTime() == null || sp.getEndTime() == null) continue;

            // Check temporal overlap: start < missedEnd && end > missedStart
            if (sp.getStartTime().isBefore(missedEnd) && sp.getEndTime().isAfter(missedStart)) {
                LocalDateTime oStart = sp.getStartTime().isAfter(missedStart) ? sp.getStartTime() : missedStart;
                LocalDateTime oEnd = sp.getEndTime().isBefore(missedEnd) ? sp.getEndTime() : missedEnd;
                double overlapMinutes = Duration.between(oStart, oEnd).toMinutes();
                double overlapHours = Math.max(0.0, overlapMinutes / 60.0);
                double totalHours = Math.max(0.0, Duration.between(sp.getStartTime(), sp.getEndTime()).toMinutes() / 60.0);

                boolean isCompleted = "COMPLETED".equalsIgnoreCase(sp.getStatus());
                MissedSessionInfo info = new MissedSessionInfo(
                        sp.getPlanId(),
                        sp.getTitle(),
                        sp.getStartTime(),
                        sp.getEndTime(),
                        Math.round(overlapHours * 10.0) / 10.0,
                        Math.round(totalHours * 10.0) / 10.0,
                        sp.getStatus(),
                        isCompleted
                );
                affectedSessionInfos.add(info);

                boolean isBreak = "BREAK".equalsIgnoreCase(sp.getStatus())
                        || (sp.getTitle() != null && sp.getTitle().toLowerCase().startsWith("break"));

                if (!isCompleted && !isBreak && overlapHours > 0.05) {
                    affectedPendingPlans.add(sp);
                    String titleKey = sp.getTitle() != null ? sp.getTitle().trim() : "Study Session";
                    missedHoursByTaskTitle.put(titleKey, missedHoursByTaskTitle.getOrDefault(titleKey, 0.0) + overlapHours);
                }
            }
        }

        if (affectedSessionInfos.isEmpty()) {
            return new ReplanPreviewResponse(
                    false,
                    "No study sessions were scheduled during the specified interval (" + request.getMissedDate() + " " + request.getMissedStartTime() + " - " + request.getMissedEndTime() + ").",
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    existingPlans
            );
        }

        if (affectedPendingPlans.isEmpty()) {
            return new ReplanPreviewResponse(
                    true,
                    "All study sessions in the selected interval were already marked as COMPLETED or scheduled rest breaks. No replacement work needed.",
                    affectedSessionInfos,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    existingPlans
            );
        }

        // 3. Retrieve Pending Tasks
        List<AcademicTask> allPendingTasks = academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId);
        Map<String, AcademicTask> taskMap = new HashMap<>();
        for (AcademicTask t : allPendingTasks) {
            if ("PENDING".equalsIgnoreCase(t.getStatus()) && t.getTitle() != null) {
                taskMap.put(t.getTitle().trim().toLowerCase(), t);
            }
        }

        // 4. Find free future slots for replacement sessions
        List<AcademicSchedule> schedules = academicScheduleRepository.findByUserId(userId);
        List<TimeSlot> candidateSlots = findFutureFreeSlotsExcluding(
                userId,
                request.getMissedDate(),
                missedStart,
                missedEnd,
                schedules,
                existingPlans,
                affectedPendingPlans
        );

        // 5. Schedule replacement sessions for the missed tasks using EDF + priority
        List<ProposedSessionInfo> proposedReplacements = new ArrayList<>();
        List<StudyPlan> proposedStudyPlans = new ArrayList<>();
        List<UnscheduledTaskInfo> unscheduledTasks = new ArrayList<>();

        // Build trackers for affected tasks
        class ReplanTaskTracker {
            final String title;
            final AcademicTask task;
            double remainingHoursToSchedule;

            ReplanTaskTracker(String title, AcademicTask task, double hours) {
                this.title = title;
                this.task = task;
                this.remainingHoursToSchedule = hours;
            }
        }

        List<ReplanTaskTracker> trackers = new ArrayList<>();
        for (Map.Entry<String, Double> entry : missedHoursByTaskTitle.entrySet()) {
            String title = entry.getKey();
            AcademicTask task = taskMap.get(title.toLowerCase());
            trackers.add(new ReplanTaskTracker(title, task, entry.getValue()));
        }

        // Sort trackers: nearer deadlines and high priority first
        trackers.sort((a, b) -> {
            if (a.task != null && b.task != null) {
                if (a.task.getDeadline() != null && b.task.getDeadline() != null) {
                    int c = a.task.getDeadline().compareTo(b.task.getDeadline());
                    if (c != 0) return c;
                }
                String pA = a.task.getPriority() != null ? a.task.getPriority() : "LOW";
                String pB = b.task.getPriority() != null ? b.task.getPriority() : "LOW";
                return pB.compareTo(pA);
            }
            return 0;
        });

        int slotIdx = 0;
        for (ReplanTaskTracker tr : trackers) {
            LocalDateTime taskDeadline = (tr.task != null) ? tr.task.getDeadline() : null;
            while (tr.remainingHoursToSchedule > 0.1 && slotIdx < candidateSlots.size()) {
                TimeSlot slot = candidateSlots.get(slotIdx);
                if (taskDeadline != null && slot.getStart().isAfter(taskDeadline)) {
                    // Slot is past deadline; stop trying to schedule this task into future slots
                    break;
                }
                slotIdx++;

                double slotHours = Duration.between(slot.getStart(), slot.getEnd()).toMinutes() / 60.0;
                double allocatedHours = Math.min(tr.remainingHoursToSchedule, slotHours);

                LocalDateTime actualSlotEnd = slot.getStart().plusMinutes((long) (allocatedHours * 60));

                String priority = (tr.task != null && tr.task.getPriority() != null) ? tr.task.getPriority() : "NORMAL";
                LocalDateTime deadline = (tr.task != null) ? tr.task.getDeadline() : null;

                String reason = "Rescheduled replacement for missed session on " + request.getMissedDate()
                        + " (" + String.format(Locale.US, "%.1f", allocatedHours) + " hr)"
                        + (deadline != null ? " before deadline" : "");

                ProposedSessionInfo proposedInfo = new ProposedSessionInfo(
                        tr.title,
                        slot.getStart(),
                        actualSlotEnd,
                        Math.round(allocatedHours * 10.0) / 10.0,
                        priority,
                        deadline,
                        reason
                );
                proposedReplacements.add(proposedInfo);

                StudyPlan newPlan = new StudyPlan();
                newPlan.setUserId(userId);
                newPlan.setTitle(tr.title);
                newPlan.setStartTime(slot.getStart());
                newPlan.setEndTime(actualSlotEnd);
                newPlan.setStatus("PENDING");
                newPlan.setExplanation(reason);
                proposedStudyPlans.add(newPlan);

                tr.remainingHoursToSchedule -= allocatedHours;
            }

            if (tr.remainingHoursToSchedule > 0.1) {
                unscheduledTasks.add(new UnscheduledTaskInfo(
                        tr.title,
                        Math.round(tr.remainingHoursToSchedule * 10.0) / 10.0,
                        "Not enough free daytime slots before deadline to fit remaining "
                                + String.format(Locale.US, "%.1f", tr.remainingHoursToSchedule)
                                + " hours while preserving required class rest and study breaks."
                ));
            }
        }

        // 6. Build Candidate Plans:
        List<StudyPlan> candidatePlans = new ArrayList<>();
        Set<Long> affectedPendingIds = new HashSet<>();
        for (StudyPlan sp : affectedPendingPlans) {
            affectedPendingIds.add(sp.getPlanId());
        }

        for (StudyPlan sp : existingPlans) {
            if (!affectedPendingIds.contains(sp.getPlanId())) {
                candidatePlans.add(sp);
            } else {
                // If this session only partially overlapped the missed interval, keep unmissed portion
                if (sp.getStartTime().isBefore(missedStart) && sp.getEndTime().isAfter(missedStart)) {
                    long nonMissedMins = Duration.between(sp.getStartTime(), missedStart).toMinutes();
                    if (nonMissedMins >= 20) {
                        StudyPlan partial = new StudyPlan();
                        partial.setPlanId(sp.getPlanId());
                        partial.setUserId(userId);
                        partial.setTitle(sp.getTitle());
                        partial.setStartTime(sp.getStartTime());
                        partial.setEndTime(missedStart);
                        partial.setStatus("PENDING");
                        partial.setExplanation("Retained unmissed portion of session.");
                        candidatePlans.add(partial);
                    }
                } else if (sp.getStartTime().isBefore(missedEnd) && sp.getEndTime().isAfter(missedEnd)) {
                    long nonMissedMins = Duration.between(missedEnd, sp.getEndTime()).toMinutes();
                    if (nonMissedMins >= 20) {
                        StudyPlan partial = new StudyPlan();
                        partial.setPlanId(sp.getPlanId());
                        partial.setUserId(userId);
                        partial.setTitle(sp.getTitle());
                        partial.setStartTime(missedEnd);
                        partial.setEndTime(sp.getEndTime());
                        partial.setStatus("PENDING");
                        partial.setExplanation("Retained unmissed portion of session.");
                        candidatePlans.add(partial);
                    }
                }
            }
        }
        candidatePlans.addAll(proposedStudyPlans);
        candidatePlans.sort(Comparator.comparing(StudyPlan::getStartTime));

        String message = proposedReplacements.isEmpty()
                ? "No replacement slots could be allocated."
                : "Successfully generated " + proposedReplacements.size() + " replacement session(s) in upcoming available slots.";

        return new ReplanPreviewResponse(
                true,
                message,
                affectedSessionInfos,
                proposedReplacements,
                unscheduledTasks,
                candidatePlans
        );
    }

    @Transactional
    public List<StudyPlan> applyReplan(Long userId, ReplanRequest request) {
        ReplanPreviewResponse preview = previewReplan(userId, request);
        if (!preview.isSuccess()) {
            throw new IllegalArgumentException(preview.getMessage());
        }

        // 1. Delete or adjust affected pending sessions
        LocalDateTime missedStart = LocalDateTime.of(request.getMissedDate(), request.getMissedStartTime());
        LocalDateTime missedEnd = LocalDateTime.of(request.getMissedDate(), request.getMissedEndTime());
        List<StudyPlan> existingPlans = studyPlanRepository.findByUserIdOrderByStartTimeAsc(userId);

        List<StudyPlan> toDelete = new ArrayList<>();
        List<StudyPlan> toUpdate = new ArrayList<>();

        for (StudyPlan sp : existingPlans) {
            if ("PENDING".equalsIgnoreCase(sp.getStatus()) && sp.getStartTime() != null && sp.getEndTime() != null) {
                if (sp.getStartTime().isBefore(missedEnd) && sp.getEndTime().isAfter(missedStart)) {
                    // Check if entire session was missed
                    if (!sp.getStartTime().isBefore(missedStart) && !sp.getEndTime().isAfter(missedEnd)) {
                        toDelete.add(sp);
                    } else if (sp.getStartTime().isBefore(missedStart) && sp.getEndTime().isAfter(missedStart)) {
                        long nonMissedMins = Duration.between(sp.getStartTime(), missedStart).toMinutes();
                        if (nonMissedMins >= 20) {
                            sp.setEndTime(missedStart);
                            toUpdate.add(sp);
                        } else {
                            toDelete.add(sp);
                        }
                    } else if (sp.getStartTime().isBefore(missedEnd) && sp.getEndTime().isAfter(missedEnd)) {
                        long nonMissedMins = Duration.between(missedEnd, sp.getEndTime()).toMinutes();
                        if (nonMissedMins >= 20) {
                            sp.setStartTime(missedEnd);
                            toUpdate.add(sp);
                        } else {
                            toDelete.add(sp);
                        }
                    } else {
                        toDelete.add(sp);
                    }
                }
            }
        }

        if (!toDelete.isEmpty()) {
            studyPlanRepository.deleteAll(toDelete);
        }
        if (!toUpdate.isEmpty()) {
            studyPlanRepository.saveAll(toUpdate);
        }

        // 2. Save proposed replacement plans
        List<StudyPlan> toSave = new ArrayList<>();
        for (ProposedSessionInfo prop : preview.getProposedReplacements()) {
            StudyPlan newPlan = new StudyPlan();
            newPlan.setUserId(userId);
            newPlan.setTitle(prop.getTaskTitle());
            newPlan.setStartTime(prop.getNewStartTime());
            newPlan.setEndTime(prop.getNewEndTime());
            newPlan.setStatus("PENDING");
            newPlan.setExplanation(prop.getReason());
            toSave.add(newPlan);
        }

        if (!toSave.isEmpty()) {
            studyPlanRepository.saveAll(toSave);
        }

        return studyPlanRepository.findByUserIdOrderByStartTimeAsc(userId);
    }

    private List<TimeSlot> findFutureFreeSlotsExcluding(
            Long userId,
            LocalDate missedDate,
            LocalDateTime missedStart,
            LocalDateTime missedEnd,
            List<AcademicSchedule> schedules,
            List<StudyPlan> existingPlans,
            List<StudyPlan> affectedPlans) {

        List<TimeSlot> freeSlots = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        LocalDate startDate = now.toLocalDate();

        // Collect all busy intervals across the next 7 days
        Set<Long> affectedIds = new HashSet<>();
        for (StudyPlan a : affectedPlans) {
            affectedIds.add(a.getPlanId());
        }

        List<TimeSlot> busyIntervals = new ArrayList<>();
        for (StudyPlan sp : existingPlans) {
            if (!affectedIds.contains(sp.getPlanId()) && sp.getStartTime() != null && sp.getEndTime() != null) {
                busyIntervals.add(new TimeSlot(sp.getStartTime(), sp.getEndTime()));
            }
        }

        // Missed interval itself is BUSY (never schedule replacement work in the missed interval!)
        busyIntervals.add(new TimeSlot(missedStart, missedEnd));

        for (int dayOffset = 0; dayOffset < 7; dayOffset++) {
            LocalDate date = startDate.plusDays(dayOffset);
            String dayName = date.getDayOfWeek().name();

            // Classes for this day
            List<AcademicSchedule> dayClasses = new ArrayList<>();
            for (AcademicSchedule s : schedules) {
                if (s.getDayOfWeek() != null && s.getDayOfWeek().equalsIgnoreCase(dayName)) {
                    dayClasses.add(s);
                }
            }
            dayClasses.sort(Comparator.comparing(AcademicSchedule::getStartTime));

            // Generate break-aware slots for this day
            List<ScheduleSlot> daySlots = buildBreakAwareSlotsForDay(date, dayClasses, now);
            for (ScheduleSlot s : daySlots) {
                if (!s.isBreak() && !overlapsAny(s.getStart(), s.getEnd(), busyIntervals)) {
                    freeSlots.add(new TimeSlot(s.getStart(), s.getEnd()));
                }
            }
        }

        freeSlots.sort(Comparator.comparing(TimeSlot::getStart));
        return freeSlots;
    }

    private boolean overlapsAny(LocalDateTime start, LocalDateTime end, List<TimeSlot> busyIntervals) {
        for (TimeSlot b : busyIntervals) {
            if (start.isBefore(b.getEnd()) && end.isAfter(b.getStart())) {
                return true;
            }
        }
        return false;
    }

    // ==========================================================
    // BREAK-AWARE SCHEDULING HEURISTICS & HELPER METHODS
    // ==========================================================

    public List<ScheduleSlot> buildBreakAwareSlotsForDay(
            LocalDate date,
            List<AcademicSchedule> dayClasses,
            LocalDateTime notBeforeTime) {

        List<ScheduleSlot> slots = new ArrayList<>();
        List<AcademicBlock> blocks = buildAcademicBlocks(dayClasses);

        boolean mealBreakPlaced = false;
        LocalTime dayStartTime = DAY_START; // 08:00
        LocalTime dayEndTime = DAY_END;     // 20:00

        if (blocks.isEmpty()) {
            // Free day with no classes!
            sliceStudyWindowWithBreaks(date, dayStartTime, dayEndTime, notBeforeTime, mealBreakPlaced, slots);
            return slots;
        }

        // Window before the first class block
        AcademicBlock firstBlock = blocks.get(0);
        if (firstBlock.getStart().isAfter(dayStartTime)) {
            // Study must finish at least 10 minutes before class to allow travel/preparation
            LocalTime windowEnd = firstBlock.getStart().minusMinutes(PRE_CLASS_BUFFER_MINUTES);
            if (windowEnd.isAfter(dayStartTime)) {
                mealBreakPlaced = sliceStudyWindowWithBreaks(date, dayStartTime, windowEnd, notBeforeTime, mealBreakPlaced, slots);
            }
        }

        // Windows between consecutive blocks
        for (int i = 0; i < blocks.size(); i++) {
            AcademicBlock currentBlock = blocks.get(i);
            BreakInfo postBreak = determinePostBlockBreak(currentBlock);

            LocalTime postBreakStart = currentBlock.getEnd();
            LocalTime postBreakEnd = postBreakStart.plusMinutes(postBreak.getDurationMinutes());

            LocalTime nextConstraint;
            if (i < blocks.size() - 1) {
                AcademicBlock nextBlock = blocks.get(i + 1);
                nextConstraint = nextBlock.getStart();
            } else {
                nextConstraint = dayEndTime;
            }

            // Cap post-class break end at next constraint
            if (postBreakEnd.isAfter(nextConstraint)) {
                postBreakEnd = nextConstraint;
            }

            // Record protected post-class / meal break slot if duration >= 10 mins
            if (Duration.between(postBreakStart, postBreakEnd).toMinutes() >= 10) {
                LocalDateTime bStartDt = LocalDateTime.of(date, postBreakStart);
                LocalDateTime bEndDt = LocalDateTime.of(date, postBreakEnd);
                if (bEndDt.isAfter(notBeforeTime)) {
                    slots.add(new ScheduleSlot(bStartDt, bEndDt, postBreak.getTitle(), postBreak.getReason()));
                }
                if (postBreak.getTitle().toLowerCase().contains("lunch") || postBreak.getTitle().toLowerCase().contains("meal")) {
                    mealBreakPlaced = true;
                }
            }

            // Available study window after the post-class break
            LocalTime studyWindowStart = postBreakEnd;
            LocalTime studyWindowEnd;
            if (i < blocks.size() - 1) {
                // Buffer of 10 minutes before next class block
                studyWindowEnd = nextConstraint.minusMinutes(PRE_CLASS_BUFFER_MINUTES);
            } else {
                studyWindowEnd = nextConstraint;
            }

            if (studyWindowEnd.isAfter(studyWindowStart)) {
                mealBreakPlaced = sliceStudyWindowWithBreaks(date, studyWindowStart, studyWindowEnd, notBeforeTime, mealBreakPlaced, slots);
            }
        }

        return slots;
    }

    private boolean sliceStudyWindowWithBreaks(
            LocalDate date,
            LocalTime windowStart,
            LocalTime windowEnd,
            LocalDateTime notBeforeTime,
            boolean mealBreakPlaced,
            List<ScheduleSlot> outSlots) {

        LocalTime current = windowStart;
        int consecutiveStudySessions = 0;

        while (Duration.between(current, windowEnd).toMinutes() >= MIN_STUDY_SESSION_MINUTES) {
            // Check for midday meal break if crossing 12:00 PM - 1:30 PM and not placed yet
            if (!mealBreakPlaced && current.isAfter(LocalTime.of(11, 59)) && current.isBefore(LocalTime.of(13, 1))) {
                LocalTime mealEnd = current.plusMinutes(MEAL_BREAK_MINUTES);
                if (mealEnd.isAfter(windowEnd)) mealEnd = windowEnd;
                if (Duration.between(current, mealEnd).toMinutes() >= 25) {
                    LocalDateTime mStartDt = LocalDateTime.of(date, current);
                    LocalDateTime mEndDt = LocalDateTime.of(date, mealEnd);
                    if (mEndDt.isAfter(notBeforeTime)) {
                        outSlots.add(new ScheduleSlot(mStartDt, mEndDt, "Break: Lunch & Recovery", "45-minute midday meal and rest break."));
                    }
                    mealBreakPlaced = true;
                    current = mealEnd;
                    consecutiveStudySessions = 0;
                    continue;
                }
            }

            int availableMinutes = (int) Duration.between(current, windowEnd).toMinutes();
            if (availableMinutes < MIN_STUDY_SESSION_MINUTES) {
                break;
            }

            // Focused study sessions: 45 minutes default, or full window if between 45 and 59 minutes
            int studyMinutes = Math.min(DEFAULT_STUDY_SESSION_MINUTES, availableMinutes);
            if (availableMinutes >= 50 && availableMinutes < 60) {
                studyMinutes = availableMinutes;
            }

            LocalTime studyStart = current;
            LocalTime studyEnd = current.plusMinutes(studyMinutes);

            LocalDateTime sStartDt = LocalDateTime.of(date, studyStart);
            LocalDateTime sEndDt = LocalDateTime.of(date, studyEnd);

            if (sEndDt.isAfter(notBeforeTime)) {
                outSlots.add(new ScheduleSlot(sStartDt, sEndDt));
            }

            current = studyEnd;
            consecutiveStudySessions++;

            // Inter-study break: needed if there's enough time left in window for break + next session
            int remainingAfterStudy = (int) Duration.between(current, windowEnd).toMinutes();
            if (remainingAfterStudy >= (MIN_STUDY_SESSION_MINUTES + 10)) {
                int breakMins = (consecutiveStudySessions >= 2) ? LONG_STUDY_BREAK_MINUTES : STUDY_BREAK_MINUTES;
                String bTitle = (consecutiveStudySessions >= 2) ? "Break: Extended Rest & Recovery" : "Break: Study Refreshment";
                String bReason = (consecutiveStudySessions >= 2)
                        ? "25-minute extended recovery break after 2 focused study sessions."
                        : "15-minute pause between study sessions to maintain cognitive focus.";

                LocalTime bEnd = current.plusMinutes(breakMins);
                if (bEnd.isAfter(windowEnd)) bEnd = windowEnd;

                LocalDateTime bStartDt = LocalDateTime.of(date, current);
                LocalDateTime bEndDt = LocalDateTime.of(date, bEnd);

                if (bEndDt.isAfter(notBeforeTime)) {
                    outSlots.add(new ScheduleSlot(bStartDt, bEndDt, bTitle, bReason));
                }

                current = bEnd;
                if (consecutiveStudySessions >= 2) {
                    consecutiveStudySessions = 0;
                }
            }
        }

        return mealBreakPlaced;
    }

    public List<AcademicBlock> buildAcademicBlocks(List<AcademicSchedule> dayClasses) {
        List<AcademicBlock> blocks = new ArrayList<>();
        if (dayClasses == null || dayClasses.isEmpty()) {
            return blocks;
        }

        AcademicSchedule currentClass = dayClasses.get(0);
        LocalTime blockStart = currentClass.getStartTime();
        LocalTime blockEnd = currentClass.getEndTime();
        int count = 1;
        int totalMinutes = (int) Duration.between(blockStart, blockEnd).toMinutes();
        StringBuilder desc = new StringBuilder(currentClass.getSubjectName());

        for (int i = 1; i < dayClasses.size(); i++) {
            AcademicSchedule nextClass = dayClasses.get(i);
            // If next class starts within 15 minutes of current block end (consecutive or overlapping)
            if (!nextClass.getStartTime().isAfter(blockEnd.plusMinutes(15))) {
                if (nextClass.getEndTime().isAfter(blockEnd)) {
                    blockEnd = nextClass.getEndTime();
                }
                count++;
                totalMinutes += (int) Duration.between(nextClass.getStartTime(), nextClass.getEndTime()).toMinutes();
                desc.append(" & ").append(nextClass.getSubjectName());
            } else {
                blocks.add(new AcademicBlock(blockStart, blockEnd, count, totalMinutes, desc.toString()));
                currentClass = nextClass;
                blockStart = currentClass.getStartTime();
                blockEnd = currentClass.getEndTime();
                count = 1;
                totalMinutes = (int) Duration.between(blockStart, blockEnd).toMinutes();
                desc = new StringBuilder(currentClass.getSubjectName());
            }
        }
        blocks.add(new AcademicBlock(blockStart, blockEnd, count, totalMinutes, desc.toString()));
        return blocks;
    }

    public BreakInfo determinePostBlockBreak(AcademicBlock block) {
        // Rule: Long/dense academic period (>= 180 min) OR block ends between 11:30 AM and 1:30 PM (midday meal)
        boolean isMiddayMeal = (block.getEnd().isAfter(LocalTime.of(11, 29)) && block.getEnd().isBefore(LocalTime.of(13, 31)));
        boolean isDenseOrLong = block.getTotalDurationMinutes() >= 180;

        if (isDenseOrLong || isMiddayMeal) {
            int mins = MEAL_BREAK_MINUTES;
            return new BreakInfo(
                    mins,
                    "Break: Lunch & Recovery",
                    mins + "-minute meal and rest break following " + block.getClassCount() + " class(es) (" + block.getBlockDescription() + ")."
            );
        } else if (block.getClassCount() >= 2) {
            return new BreakInfo(
                    CONSECUTIVE_CLASSES_BREAK_MINUTES,
                    "Break: Post-Class Rest",
                    "30-minute rest after " + block.getClassCount() + " consecutive classes (" + block.getBlockDescription() + ")."
            );
        } else {
            return new BreakInfo(
                    SINGLE_CLASS_BREAK_MINUTES,
                    "Break: Post-Class Transition",
                    "15-minute transition and rest after " + block.getBlockDescription() + " lecture."
            );
        }
    }

    // ==========================================================
    // HELPER CLASSES
    // ==========================================================

    public static class AcademicBlock {
        private final LocalTime start;
        private final LocalTime end;
        private final int classCount;
        private final int totalDurationMinutes;
        private final String blockDescription;

        public AcademicBlock(LocalTime start, LocalTime end, int classCount, int totalDurationMinutes, String blockDescription) {
            this.start = start;
            this.end = end;
            this.classCount = classCount;
            this.totalDurationMinutes = totalDurationMinutes;
            this.blockDescription = blockDescription;
        }

        public LocalTime getStart() { return start; }
        public LocalTime getEnd() { return end; }
        public int getClassCount() { return classCount; }
        public int getTotalDurationMinutes() { return totalDurationMinutes; }
        public String getBlockDescription() { return blockDescription; }
    }

    public static class BreakInfo {
        private final int durationMinutes;
        private final String title;
        private final String reason;

        public BreakInfo(int durationMinutes, String title, String reason) {
            this.durationMinutes = durationMinutes;
            this.title = title;
            this.reason = reason;
        }

        public int getDurationMinutes() { return durationMinutes; }
        public String getTitle() { return title; }
        public String getReason() { return reason; }
    }

    public static class ScheduleSlot {
        private final boolean aBreak;
        private final LocalDateTime start;
        private final LocalDateTime end;
        private final String breakTitle;
        private final String breakReason;

        public ScheduleSlot(LocalDateTime start, LocalDateTime end) {
            this.aBreak = false;
            this.start = start;
            this.end = end;
            this.breakTitle = null;
            this.breakReason = null;
        }

        public ScheduleSlot(LocalDateTime start, LocalDateTime end, String breakTitle, String breakReason) {
            this.aBreak = true;
            this.start = start;
            this.end = end;
            this.breakTitle = breakTitle;
            this.breakReason = breakReason;
        }

        public boolean isBreak() { return aBreak; }
        public LocalDateTime getStart() { return start; }
        public LocalDateTime getEnd() { return end; }
        public String getBreakTitle() { return breakTitle; }
        public String getBreakReason() { return breakReason; }
        public double getDurationHours() { return Duration.between(start, end).toMinutes() / 60.0; }
        public int getDurationMinutes() { return (int) Duration.between(start, end).toMinutes(); }
    }

    public static class TimeSlot {
        private final LocalDateTime start;
        private final LocalDateTime end;

        public TimeSlot(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }

        public LocalDateTime getStart() {
            return start;
        }

        public LocalDateTime getEnd() {
            return end;
        }
    }
}

