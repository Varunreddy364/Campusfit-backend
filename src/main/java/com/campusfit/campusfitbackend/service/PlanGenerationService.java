package com.campusfit.campusfitbackend.service;

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

    @Transactional
    public List<StudyPlan> generateStudyPlan(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
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
            return Collections.emptyList();
        }

        // 3. Check completed study sessions for adaptive replanning
        Map<String, Double> completedHoursByTitle = new HashMap<>();
        List<StudyPlan> existingPlans = studyPlanRepository.findByUserIdOrderByStartTimeAsc(userId);
        for (StudyPlan sp : existingPlans) {
            if ("COMPLETED".equalsIgnoreCase(sp.getStatus()) && sp.getTitle() != null
                    && sp.getStartTime() != null && sp.getEndTime() != null) {
                double hours = Duration.between(sp.getStartTime(), sp.getEndTime()).toMinutes() / 60.0;
                String key = sp.getTitle().trim().toLowerCase();
                completedHoursByTitle.put(key, completedHoursByTitle.getOrDefault(key, 0.0) + hours);
            }
        }

        // 4. Find free slots across the upcoming 7 days (45-60 min focused sessions)
        List<TimeSlot> allFreeSlots = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

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
            // Sort classes by start time
            dayClasses.sort(Comparator.comparing(AcademicSchedule::getStartTime));

            // Compute free 1-hour slots between classes
            LocalTime current = DAY_START;
            for (AcademicSchedule c : dayClasses) {
                LocalTime classStart = c.getStartTime();
                if (classStart.isAfter(current)) {
                    LocalTime slotCur = current;
                    while (slotCur.isBefore(classStart)) {
                        LocalTime slotNext = slotCur.plusHours(1);
                        if (slotNext.isAfter(classStart) || slotNext.isBefore(slotCur)) {
                            slotNext = classStart;
                        }
                        if (Duration.between(slotCur, slotNext).toMinutes() >= 30) {
                            LocalDateTime slotStartDt = LocalDateTime.of(date, slotCur);
                            LocalDateTime slotEndDt = LocalDateTime.of(date, slotNext);
                            if (slotEndDt.isAfter(now)) {
                                allFreeSlots.add(new TimeSlot(slotStartDt, slotEndDt));
                            }
                        }
                        slotCur = slotNext;
                    }
                }
                if (c.getEndTime().isAfter(current)) {
                    current = c.getEndTime();
                }
            }

            // Time after last class until DAY_END
            if (current.isBefore(DAY_END)) {
                LocalTime slotCur = current;
                while (slotCur.isBefore(DAY_END)) {
                    LocalTime slotNext = slotCur.plusHours(1);
                    if (slotNext.isAfter(DAY_END) || slotNext.isBefore(slotCur)) {
                        slotNext = DAY_END;
                    }
                    if (Duration.between(slotCur, slotNext).toMinutes() >= 30) {
                        LocalDateTime slotStartDt = LocalDateTime.of(date, slotCur);
                        LocalDateTime slotEndDt = LocalDateTime.of(date, slotNext);
                        if (slotEndDt.isAfter(now)) {
                            allFreeSlots.add(new TimeSlot(slotStartDt, slotEndDt));
                        }
                    }
                    slotCur = slotNext;
                }
            }
        }

        // Sort free slots chronologically
        allFreeSlots.sort(Comparator.comparing(TimeSlot::getStart));

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
        LocalDate currentTrackingDate = null;

        for (TimeSlot slot : allFreeSlots) {
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

        // 6. Clear old PENDING sessions and store new generated sessions
        studyPlanRepository.deleteByUserIdAndStatus(userId, "PENDING");
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

    // Helper class for free slot intervals
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
