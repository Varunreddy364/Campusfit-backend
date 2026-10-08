package com.campusfit.campusfitbackend.service;

import com.campusfit.campusfitbackend.entity.AcademicSchedule;
import com.campusfit.campusfitbackend.entity.FitnessProfile;
import com.campusfit.campusfitbackend.entity.StudyPlan;
import com.campusfit.campusfitbackend.entity.WorkoutPlan;
import com.campusfit.campusfitbackend.repository.AcademicScheduleRepository;
import com.campusfit.campusfitbackend.repository.FitnessProfileRepository;
import com.campusfit.campusfitbackend.repository.StudyPlanRepository;
import com.campusfit.campusfitbackend.repository.WorkoutPlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class WorkoutPlanGenerationService {

    @Autowired
    private AcademicScheduleRepository academicScheduleRepository;

    @Autowired
    private StudyPlanRepository studyPlanRepository;

    @Autowired
    private FitnessProfileRepository fitnessProfileRepository;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    private static final LocalTime DAY_START = LocalTime.of(8, 0);  // 8:00 AM
    private static final LocalTime DAY_END = LocalTime.of(22, 0);   // 10:00 PM

    @Transactional
    public WorkoutPlan generateWorkoutPlan(Long userId, LocalDate targetDate) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }
        if (targetDate == null) {
            targetDate = LocalDate.now();
        }

        // STEP 1 & 2: Validations
        List<AcademicSchedule> schedules = academicScheduleRepository.findByUserId(userId);
        if (schedules == null || schedules.isEmpty()) {
            throw new IllegalStateException("Please add your academic schedule first.");
        }

        List<StudyPlan> studyPlans = studyPlanRepository.findByUserId(userId);
        if (studyPlans == null || studyPlans.isEmpty()) {
            throw new IllegalStateException("Please generate a study plan first.");
        }

        FitnessProfile profile = fitnessProfileRepository.findByUserID(userId.intValue());
        if (profile == null) {
            throw new IllegalStateException("Please complete your fitness profile first.");
        }

        // STEP 3: Combine schedules and identify occupied time
        List<Interval> occupied = new ArrayList<>();
        String dayName = targetDate.getDayOfWeek().name();

        // 3a. Academic Schedule for this day of week
        for (AcademicSchedule s : schedules) {
            if (s.getDayOfWeek() != null && s.getDayOfWeek().equalsIgnoreCase(dayName)) {
                occupied.add(new Interval(
                        LocalDateTime.of(targetDate, s.getStartTime()),
                        LocalDateTime.of(targetDate, s.getEndTime())
                ));
            }
        }

        // 3b. Study Plan sessions for this specific date
        for (StudyPlan sp : studyPlans) {
            if (sp.getStartTime() != null && sp.getStartTime().toLocalDate().equals(targetDate)) {
                occupied.add(new Interval(sp.getStartTime(), sp.getEndTime()));
            }
        }

        // Merge overlapping occupied intervals
        List<Interval> mergedOccupied = mergeIntervals(occupied);

        // STEP 4: Find Remaining Free Time Slots (between 8 AM and 10 PM)
        List<Interval> freeSlots = findFreeSlots(targetDate, mergedOccupied);

        if (freeSlots.isEmpty()) {
            throw new IllegalStateException("No suitable workout slot available today.");
        }

        // Select the optimal free slot:
        // Priority 1: Evening slot (starts between 4:00 PM / 16:00 and 9:00 PM / 21:00)
        // Priority 2: Longest free slot
        // Priority 3: Time before 10 PM
        Interval chosenSlot = selectOptimalSlot(freeSlots);
        long availableMinutes = Duration.between(chosenSlot.start, chosenSlot.end).toMinutes();
        if (availableMinutes < 20) {
            throw new IllegalStateException("No suitable workout slot available today.");
        }

        // STEP 5: Read Fitness Profile
        String goal = profile.getFitnessGoal() != null ? profile.getFitnessGoal().trim() : "General Fitness";
        String level = profile.getFitnessLevel() != null ? profile.getFitnessLevel().trim() : "Beginner";
        int preferredDuration = parsePreferredDuration(profile.getPreferredWorkout(), level);

        // STEP 6: Fit Workout into free slot
        int actualDuration = (int) Math.min(preferredDuration, availableMinutes);
        if (actualDuration > 60) actualDuration = 60;
        if (actualDuration < 20) actualDuration = 20;

        LocalDateTime workoutStart = chosenSlot.start;
        LocalDateTime workoutEnd = workoutStart.plusMinutes(actualDuration);

        // Workout variety by day of week
        String workoutFocus = getFocusForDay(targetDate.getDayOfWeek());
        String workoutTitle = workoutFocus + " Routine";

        // Calories estimate
        int calories = estimateCalories(actualDuration, level, goal);

        // Build workout structure (Warmup + Main + Cooldown)
        Map<String, Object> detailsMap = buildWorkoutDetails(goal, level, workoutFocus, actualDuration);
        String detailsJson = toJson(detailsMap);

        // Check if an existing plan already existed (adaptive check)
        Optional<WorkoutPlan> existingOpt = workoutPlanRepository.findByUserIdAndWorkoutDate(userId, targetDate);
        String adjustmentReason = null;
        if (existingOpt.isPresent()) {
            WorkoutPlan existing = existingOpt.get();
            if (existing.getDuration() != null && actualDuration < existing.getDuration()) {
                adjustmentReason = "Workout adjusted because academic workload increased.";
            } else if (!existing.getStartTime().equals(workoutStart)) {
                adjustmentReason = "Workout adjusted to accommodate updated academic schedule.";
            }
            workoutPlanRepository.delete(existing);
        }

        WorkoutPlan plan = new WorkoutPlan();
        plan.setUserId(userId);
        plan.setWorkoutDate(targetDate);
        plan.setWorkoutTitle(workoutTitle);
        plan.setWorkoutGoal(goal);
        plan.setWorkoutFocus(workoutFocus);
        plan.setFitnessLevel(level);
        plan.setStartTime(workoutStart);
        plan.setEndTime(workoutEnd);
        plan.setDuration(actualDuration);
        plan.setCaloriesEstimated(calories);
        plan.setWorkoutDetails(detailsJson);
        plan.setAdjustmentReason(adjustmentReason);
        plan.setStatus("PENDING");

        return workoutPlanRepository.save(plan);
    }

    public Optional<WorkoutPlan> getTodayWorkout(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }
        return workoutPlanRepository.findByUserIdAndWorkoutDate(userId, LocalDate.now());
    }

    public List<WorkoutPlan> getWorkoutPlansByUser(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }
        return workoutPlanRepository.findByUserIdOrderByWorkoutDateDescStartTimeAsc(userId);
    }

    public Optional<WorkoutPlan> markWorkoutCompleted(Long workoutId) {
        if (workoutId == null) {
            throw new IllegalArgumentException("Workout ID is required.");
        }
        return workoutPlanRepository.findById(workoutId).map(plan -> {
            plan.setStatus("COMPLETED");
            return workoutPlanRepository.save(plan);
        });
    }

    public Map<String, Object> getWorkoutHistory(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required.");
        }

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = today.with(DayOfWeek.SUNDAY);

        List<WorkoutPlan> weekPlans = workoutPlanRepository
                .findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(userId, weekStart, weekEnd);

        int completedThisWeek = 0;
        for (WorkoutPlan p : weekPlans) {
            if ("COMPLETED".equalsIgnoreCase(p.getStatus())) {
                completedThisWeek++;
            }
        }

        long totalCount = workoutPlanRepository.countByUserId(userId);
        long totalCompleted = workoutPlanRepository.countByUserIdAndStatus(userId, "COMPLETED");

        double completionPercentage = totalCount > 0
                ? ((double) totalCompleted / totalCount) * 100.0
                : 0.0;

        // Calculate workout streak
        int streak = calculateStreak(userId, today);

        Map<String, Object> stats = new HashMap<>();
        stats.put("completedThisWeek", completedThisWeek);
        stats.put("workoutStreak", streak);
        stats.put("totalWorkoutsCompleted", totalCompleted);
        stats.put("totalWorkouts", totalCount);
        stats.put("completionPercentage", Math.round(completionPercentage * 10.0) / 10.0);
        return stats;
    }

    public boolean deleteWorkoutPlan(Long workoutId) {
        if (workoutId == null) {
            throw new IllegalArgumentException("Workout ID is required.");
        }
        if (workoutPlanRepository.existsById(workoutId)) {
            workoutPlanRepository.deleteById(workoutId);
            return true;
        }
        return false;
    }

    // ==========================================
    // HELPER METHODS & ALGORITHMS
    // ==========================================

    private List<Interval> mergeIntervals(List<Interval> intervals) {
        if (intervals.isEmpty()) return Collections.emptyList();
        intervals.sort(Comparator.comparing(a -> a.start));

        List<Interval> merged = new ArrayList<>();
        Interval current = intervals.get(0);

        for (int i = 1; i < intervals.size(); i++) {
            Interval next = intervals.get(i);
            if (!next.start.isAfter(current.end)) {
                // Overlapping or contiguous
                if (next.end.isAfter(current.end)) {
                    current.end = next.end;
                }
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

    private List<Interval> findFreeSlots(LocalDate date, List<Interval> occupied) {
        LocalDateTime dayStart = LocalDateTime.of(date, DAY_START);
        LocalDateTime dayEnd = LocalDateTime.of(date, DAY_END);

        List<Interval> freeSlots = new ArrayList<>();
        LocalDateTime pointer = dayStart;

        for (Interval occ : occupied) {
            if (occ.end.isBefore(dayStart) || occ.start.isAfter(dayEnd)) {
                continue;
            }
            LocalDateTime effectiveStart = occ.start.isBefore(dayStart) ? dayStart : occ.start;
            LocalDateTime effectiveEnd = occ.end.isAfter(dayEnd) ? dayEnd : occ.end;

            if (effectiveStart.isAfter(pointer)) {
                long gap = Duration.between(pointer, effectiveStart).toMinutes();
                if (gap >= 20) {
                    freeSlots.add(new Interval(pointer, effectiveStart));
                }
            }
            if (effectiveEnd.isAfter(pointer)) {
                pointer = effectiveEnd;
            }
        }

        if (pointer.isBefore(dayEnd)) {
            long gap = Duration.between(pointer, dayEnd).toMinutes();
            if (gap >= 20) {
                freeSlots.add(new Interval(pointer, dayEnd));
            }
        }

        return freeSlots;
    }

    private Interval selectOptimalSlot(List<Interval> slots) {
        // Preference 1: Evening slot (between 4:00 PM and 9:00 PM)
        List<Interval> eveningSlots = new ArrayList<>();
        for (Interval slot : slots) {
            int startHour = slot.start.getHour();
            if (startHour >= 16 && startHour <= 20) {
                eveningSlots.add(slot);
            }
        }

        if (!eveningSlots.isEmpty()) {
            // Pick longest evening slot
            eveningSlots.sort((a, b) -> Long.compare(
                    Duration.between(b.start, b.end).toMinutes(),
                    Duration.between(a.start, a.end).toMinutes()
            ));
            return eveningSlots.get(0);
        }

        // Preference 2: Longest free slot overall
        slots.sort((a, b) -> Long.compare(
                Duration.between(b.start, b.end).toMinutes(),
                Duration.between(a.start, a.end).toMinutes()
        ));
        return slots.get(0);
    }

    private String getFocusForDay(DayOfWeek day) {
        switch (day) {
            case MONDAY:
                return "Upper Body";
            case TUESDAY:
                return "Cardio";
            case WEDNESDAY:
                return "Lower Body";
            case THURSDAY:
                return "Core";
            case FRIDAY:
                return "Full Body";
            case SATURDAY:
                return "Mobility & Recovery";
            case SUNDAY:
            default:
                return "Light Activity or Rest";
        }
    }

    private int parsePreferredDuration(String preferredStr, String level) {
        if (preferredStr != null) {
            String lower = preferredStr.toLowerCase();
            if (lower.contains("60")) return 60;
            if (lower.contains("45")) return 45;
            if (lower.contains("30")) return 30;
            if (lower.contains("20")) return 20;
        }
        if ("Advanced".equalsIgnoreCase(level)) return 50;
        if ("Intermediate".equalsIgnoreCase(level)) return 45;
        return 35; // Beginner default
    }

    private int estimateCalories(int durationMinutes, String level, String goal) {
        double calPerMin = 6.0;
        if ("Advanced".equalsIgnoreCase(level)) {
            calPerMin = 9.0;
        } else if ("Intermediate".equalsIgnoreCase(level)) {
            calPerMin = 7.5;
        }

        if ("Weight Loss".equalsIgnoreCase(goal)) {
            calPerMin += 1.5;
        } else if ("Muscle Gain".equalsIgnoreCase(goal)) {
            calPerMin += 1.0;
        }

        return (int) Math.round(durationMinutes * calPerMin);
    }

    private int calculateStreak(Long userId, LocalDate today) {
        int streak = 0;
        LocalDate checkDate = today;

        // Check if today is completed
        Optional<WorkoutPlan> todayPlan = workoutPlanRepository.findByUserIdAndWorkoutDate(userId, checkDate);
        if (todayPlan.isPresent() && "COMPLETED".equalsIgnoreCase(todayPlan.get().getStatus())) {
            streak++;
            checkDate = checkDate.minusDays(1);
        } else {
            // If today is not completed yet, streak might be from yesterday
            checkDate = checkDate.minusDays(1);
        }

        while (true) {
            Optional<WorkoutPlan> plan = workoutPlanRepository.findByUserIdAndWorkoutDate(userId, checkDate);
            if (plan.isPresent() && "COMPLETED".equalsIgnoreCase(plan.get().getStatus())) {
                streak++;
                checkDate = checkDate.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    // ==========================================
    // EXERCISE DETAILS & POOLS
    // ==========================================

    private Map<String, Object> buildWorkoutDetails(String goal, String level, String focus, int duration) {
        Map<String, Object> structure = new LinkedHashMap<>();

        // Warmup (3-5 min)
        List<Map<String, Object>> warmups = new ArrayList<>();
        warmups.add(createExercise("Arm Circles", "2", "30 sec",
                "1. Stand tall with feet shoulder-width apart.\n2. Extend arms to sides.\n3. Rotate arms forward in controlled circles.",
                "Shoulder mobility, Rotator cuff warmup",
                "Shrugging shoulders, Moving too quickly"));
        warmups.add(createExercise("Marching in Place", "2", "45 sec",
                "1. Stand with core braced.\n2. Drive knees upward alternatively toward chest.\n3. Pump arms synchronously.",
                "Hip flexor activation, Heart rate elevation",
                "Leaning backward"));
        warmups.add(createExercise("Leg Swings", "2", "15 per leg",
                "1. Support yourself against a wall.\n2. Swing leg smoothly forward and backward.\n3. Switch to lateral swings.",
                "Hamstring and hip dynamic flexibility",
                "Arching lower back excessively"));
        structure.put("warmup", warmups);

        // Main Exercises tailored by Focus, Goal, and Level
        List<Map<String, Object>> mainExercises = selectMainExercises(goal, level, focus);
        structure.put("mainWorkout", mainExercises);

        // Cooldown (3-5 min)
        List<Map<String, Object>> cooldowns = new ArrayList<>();
        cooldowns.add(createExercise("Hamstring Stretch", "2", "30 sec hold",
                "1. Extend one leg forward with heel on floor.\n2. Hinge at hips until stretch is felt in hamstring.\n3. Keep back flat.",
                "Posterior chain recovery, Hamstring flexibility",
                "Rounding spine"));
        cooldowns.add(createExercise("Quad Stretch", "2", "30 sec per leg",
                "1. Stand upright.\n2. Pull foot toward glute.\n3. Keep knees close together.",
                "Quadriceps relaxation, Hip flexor stretch",
                "Flaring knee outward"));
        cooldowns.add(createExercise("Deep Diaphragmatic Breathing", "1", "1 min",
                "1. Inhale deeply through nose for 4 seconds.\n2. Hold for 2 seconds.\n3. Exhale slowly through mouth for 6 seconds.",
                "Parasympathetic nervous system recovery, Heart rate lowering",
                "Shallow chest breathing"));
        structure.put("cooldown", cooldowns);

        return structure;
    }

    private List<Map<String, Object>> selectMainExercises(String goal, String level, String focus) {
        int sets = "Advanced".equalsIgnoreCase(level) ? 5 : "Intermediate".equalsIgnoreCase(level) ? 4 : 3;
        int reps = "Advanced".equalsIgnoreCase(level) ? 20 : "Intermediate".equalsIgnoreCase(level) ? 15 : 10;
        int plankSec = "Advanced".equalsIgnoreCase(level) ? 60 : "Intermediate".equalsIgnoreCase(level) ? 45 : 30;

        List<Map<String, Object>> exercises = new ArrayList<>();

        if ("Upper Body".equalsIgnoreCase(focus)) {
            exercises.add(createExercise("Pushups", String.valueOf(sets), String.valueOf(reps),
                    "1. Keep body in a straight plank.\n2. Place hands shoulder-width apart.\n3. Lower chest slowly until near floor.\n4. Push upward forcefully.",
                    "Chest, Shoulders, Triceps, Core",
                    "Sagging hips, Half repetitions, Flaring elbows outward"));
            exercises.add(createExercise("Incline Pushups", String.valueOf(sets), String.valueOf(reps),
                    "1. Place hands on elevated sturdy surface.\n2. Keep core tight.\n3. Lower chest and press back up.",
                    "Lower chest, Anterior deltoid",
                    "Bending at the hips"));
            exercises.add(createExercise("Diamond Pushups", String.valueOf(sets - 1), String.valueOf(Math.max(6, reps - 4)),
                    "1. Place index fingers and thumbs together under chest.\n2. Lower chest toward hands.\n3. Press back up.",
                    "Triceps overload, Inner chest activation",
                    "Elbow pain, Loss of plank posture"));
            exercises.add(createExercise("Plank to Shoulder Taps", String.valueOf(sets), reps + " taps",
                    "1. High plank position.\n2. Tap opposite shoulder with hand.\n3. Keep hips stable without rocking.",
                    "Anti-rotational core, Shoulder stabilizers",
                    "Rotating hips from side to side"));

        } else if ("Cardio".equalsIgnoreCase(focus)) {
            exercises.add(createExercise("Jumping Jacks", String.valueOf(sets), (reps * 2) + " reps",
                    "1. Stand straight.\n2. Jump feet outward while clapping hands overhead.\n3. Return softly to starting stance.",
                    "Cardiovascular endurance, Full body warming",
                    "Landing heavily on heels"));
            exercises.add(createExercise("High Knees", String.valueOf(sets), (reps * 2) + " reps",
                    "1. Run in place bringing knees to hip level.\n2. Pump arms vigorously.\n3. Maintain fast cadence.",
                    "Calorie expenditure, Hip flexor endurance",
                    "Leaning backwards"));
            exercises.add(createExercise("Mountain Climbers", String.valueOf(sets), (reps * 2) + " reps",
                    "1. High plank position.\n2. Drive knees alternatively toward chest rapidly.\n3. Keep back flat.",
                    "Cardiovascular conditioning, Core stamina",
                    "Bouncing hips in air"));
            exercises.add(createExercise("Burpees", String.valueOf(sets), (reps - 2) + " reps",
                    "1. Drop into squat, place hands down.\n2. Kick feet back to plank.\n3. Hop feet back in and jump upward.",
                    "Maximal anaerobic conditioning, Total body burn",
                    "Skipping hip extension at top"));

        } else if ("Lower Body".equalsIgnoreCase(focus)) {
            exercises.add(createExercise("Bodyweight Squats", String.valueOf(sets), String.valueOf(reps + 5),
                    "1. Stand shoulder-width apart.\n2. Push hips back and bend knees to 90 degrees.\n3. Press through heels to stand.",
                    "Quadriceps, Glutes, Hamstrings",
                    "Knees caving inward, Heels lifting off floor"));
            exercises.add(createExercise("Walking Lunges", String.valueOf(sets), reps + " per leg",
                    "1. Step forward into lunge with back knee near floor.\n2. Drive through front heel into next step.",
                    "Unilateral leg strength, Balance & Glute development",
                    "Front knee tracking past toes"));
            exercises.add(createExercise("Wall Sit", String.valueOf(sets), plankSec + " sec",
                    "1. Back against wall with thighs parallel to floor.\n2. Knees at 90-degree angle.\n3. Hold steady.",
                    "Isometric quad endurance, Knee joint stability",
                    "Resting hands on knees"));
            exercises.add(createExercise("Glute Bridges", String.valueOf(sets), String.valueOf(reps + 5),
                    "1. Lie on back with knees bent.\n2. Squeeze glutes and raise hips toward ceiling.\n3. Pause and lower slowly.",
                    "Gluteus maximus, Hamstring activation",
                    "Hyperextending lower back"));

        } else if ("Core".equalsIgnoreCase(focus)) {
            exercises.add(createExercise("Standard Plank", String.valueOf(sets), plankSec + " sec",
                    "1. Forearms on ground, elbows under shoulders.\n2. Body in one straight line.\n3. Brace abs tightly.",
                    "Deep transverse abdominis, Spinal stability",
                    "Dropping hips, Looking upward"));
            exercises.add(createExercise("Russian Twists", String.valueOf(sets), (reps * 2) + " twists",
                    "1. Sit with knees bent, torso leaned back 45 degrees.\n2. Rotate torso from left to right smoothly.",
                    "Internal and external obliques",
                    "Moving only arms instead of rotating torso"));
            exercises.add(createExercise("Side Plank", String.valueOf(sets), (plankSec - 10) + " sec / side",
                    "1. Lie on side supported by forearm.\n2. Lift hips off ground forming straight line.\n3. Hold.",
                    "Lateral core stability, Quadratus lumborum",
                    "Sagging bottom hip"));
            exercises.add(createExercise("Bicycle Crunches", String.valueOf(sets), (reps * 2) + " reps",
                    "1. Lie back with hands behind head.\n2. Touch opposite elbow to knee in pedaling motion.",
                    "Rectus abdominis, Obliques",
                    "Pulling neck with hands"));

        } else if ("Full Body".equalsIgnoreCase(focus)) {
            exercises.add(createExercise("Pushups", String.valueOf(sets), String.valueOf(reps),
                    "1. Plank position, hands shoulder-width.\n2. Lower chest and press upward.",
                    "Chest, Deltoids, Triceps, Core",
                    "Sagging hips"));
            exercises.add(createExercise("Squats", String.valueOf(sets), String.valueOf(reps + 5),
                    "1. Hips back, knees bent to 90 degrees.\n2. Drive up through heels.",
                    "Quads, Glutes, Hamstrings",
                    "Knees collapsing inward"));
            exercises.add(createExercise("Burpees", String.valueOf(sets), (reps - 2) + " reps",
                    "1. Squat, kick back to plank, return and jump.",
                    "Full body metabolic conditioning",
                    "Incomplete hip extension"));
            exercises.add(createExercise("Plank", String.valueOf(sets), plankSec + " sec",
                    "1. Forearms on floor, straight spine, tight core.",
                    "Core stability and endurance",
                    "Hips too high or too low"));

        } else if ("Mobility & Recovery".equalsIgnoreCase(focus)) {
            exercises.add(createExercise("World's Greatest Stretch", "3", "5 per side",
                    "1. Step into deep lunge.\n2. Place inside elbow to floor then rotate arm to ceiling.",
                    "Thoracic spine, Hip flexors, Groin",
                    "Rushing through the movement"));
            exercises.add(createExercise("Cat-Cow Stretch", "3", "10 cycles",
                    "1. Hands and knees.\n2. Inhale arching back, exhale rounding spine.",
                    "Spinal articulation and decompression",
                    "Forcing range of motion"));
            exercises.add(createExercise("Deep Bodyweight Squat Hold", "3", "45 sec hold",
                    "1. Sink into deep squat with chest up.\n2. Use elbows to gently press knees out.",
                    "Ankle dorsiflexion, Hip mobility",
                    "Excessive spinal rounding"));
            exercises.add(createExercise("Cobra to Child's Pose", "3", "8 reps",
                    "1. Flow between gentle backbend into relaxed Child's Pose.",
                    "Back decompression, Shoulder relaxation",
                    "Holding breath"));

        } else {
            // Light Activity or Rest
            exercises.add(createExercise("Brisk Walking", "1", "20-30 min",
                    "1. Walk at comfortable brisk pace outdoors or on treadmill.\n2. Keep shoulders relaxed.",
                    "Active recovery, Caloric expenditure, Mental reset",
                    "Slouching posture"));
            exercises.add(createExercise("Full Body Static Stretching", "1", "10 min",
                    "1. Hold gentle stretches for calves, hamstrings, chest, and neck for 30s each.",
                    "Muscle relaxation, Joint recovery",
                    "Bouncing during stretches"));
        }

        return exercises;
    }

    private Map<String, Object> createExercise(String name, String sets, String reps,
                                              String howTo, String benefits, String mistakes) {
        Map<String, Object> ex = new LinkedHashMap<>();
        ex.put("name", name);
        ex.put("sets", sets);
        ex.put("reps", reps);
        ex.put("howTo", howTo);
        ex.put("benefits", benefits);
        ex.put("commonMistakes", mistakes);
        return ex;
    }

    // Helper Interval class
    private static class Interval {
        LocalDateTime start;
        LocalDateTime end;

        Interval(LocalDateTime start, LocalDateTime end) {
            this.start = start;
            this.end = end;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) {
            return "\"" + ((String) obj).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":").append(toJson(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : ((List<?>) obj)) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJson(item));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + obj.toString() + "\"";
    }
}
