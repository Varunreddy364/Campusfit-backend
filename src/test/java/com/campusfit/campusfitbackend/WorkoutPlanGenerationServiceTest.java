package com.campusfit.campusfitbackend;

import com.campusfit.campusfitbackend.entity.AcademicSchedule;
import com.campusfit.campusfitbackend.entity.FitnessProfile;
import com.campusfit.campusfitbackend.entity.StudyPlan;
import com.campusfit.campusfitbackend.entity.WorkoutPlan;
import com.campusfit.campusfitbackend.repository.AcademicScheduleRepository;
import com.campusfit.campusfitbackend.repository.FitnessProfileRepository;
import com.campusfit.campusfitbackend.repository.StudyPlanRepository;
import com.campusfit.campusfitbackend.repository.WorkoutPlanRepository;
import com.campusfit.campusfitbackend.service.WorkoutPlanGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkoutPlanGenerationServiceTest {

    @Mock
    private AcademicScheduleRepository academicScheduleRepository;

    @Mock
    private StudyPlanRepository studyPlanRepository;

    @Mock
    private FitnessProfileRepository fitnessProfileRepository;

    @Mock
    private WorkoutPlanRepository workoutPlanRepository;

    @InjectMocks
    private WorkoutPlanGenerationService service;

    private Long userId = 101L;
    private LocalDate targetDate;
    private String dayName;

    @BeforeEach
    void setUp() {
        targetDate = LocalDate.now();
        dayName = targetDate.getDayOfWeek().name();
    }

    @Test
    void testValidation_MissingAcademicSchedule() {
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.generateWorkoutPlan(userId, targetDate)
        );
        assertEquals("Please add your academic schedule first.", ex.getMessage());
    }

    @Test
    void testValidation_MissingStudyPlan() {
        AcademicSchedule s = new AcademicSchedule(1L, userId, "DBMS", dayName, LocalTime.of(9, 0), LocalTime.of(10, 0));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.singletonList(s));
        when(studyPlanRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.generateWorkoutPlan(userId, targetDate)
        );
        assertEquals("Please generate a study plan first.", ex.getMessage());
    }

    @Test
    void testValidation_MissingFitnessProfile() {
        AcademicSchedule s = new AcademicSchedule(1L, userId, "DBMS", dayName, LocalTime.of(9, 0), LocalTime.of(10, 0));
        StudyPlan sp = new StudyPlan(1L, userId, "Study DBMS", LocalDateTime.of(targetDate, LocalTime.of(12, 0)), LocalDateTime.of(targetDate, LocalTime.of(13, 0)), "PENDING");

        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.singletonList(s));
        when(studyPlanRepository.findByUserId(userId)).thenReturn(Collections.singletonList(sp));
        when(fitnessProfileRepository.findByUserID(userId.intValue())).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () ->
                service.generateWorkoutPlan(userId, targetDate)
        );
        assertEquals("Please complete your fitness profile first.", ex.getMessage());
    }

    @Test
    void testSuccessfulWorkoutPlanGeneration() {
        // Classes: 09:00 - 10:00 & 11:00 - 12:00
        AcademicSchedule s1 = new AcademicSchedule(1L, userId, "DBMS", dayName, LocalTime.of(9, 0), LocalTime.of(10, 0));
        AcademicSchedule s2 = new AcademicSchedule(2L, userId, "OS", dayName, LocalTime.of(11, 0), LocalTime.of(12, 0));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Arrays.asList(s1, s2));

        // Study Sessions: 12:00 - 13:00 & 15:00 - 16:00
        StudyPlan sp1 = new StudyPlan(1L, userId, "Study DBMS", LocalDateTime.of(targetDate, LocalTime.of(12, 0)), LocalDateTime.of(targetDate, LocalTime.of(13, 0)), "PENDING");
        StudyPlan sp2 = new StudyPlan(2L, userId, "Study OS", LocalDateTime.of(targetDate, LocalTime.of(15, 0)), LocalDateTime.of(targetDate, LocalTime.of(16, 0)), "PENDING");
        when(studyPlanRepository.findByUserId(userId)).thenReturn(Arrays.asList(sp1, sp2));

        FitnessProfile profile = new FitnessProfile();
        profile.setUserID(userId.intValue());
        profile.setFitnessGoal("Muscle Gain");
        profile.setFitnessLevel("Intermediate");
        profile.setPreferredWorkout("45 mins");
        when(fitnessProfileRepository.findByUserID(userId.intValue())).thenReturn(profile);

        when(workoutPlanRepository.findByUserIdAndWorkoutDate(userId, targetDate)).thenReturn(Optional.empty());
        when(workoutPlanRepository.save(any(WorkoutPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkoutPlan plan = service.generateWorkoutPlan(userId, targetDate);

        assertNotNull(plan);
        assertEquals(userId, plan.getUserId());
        assertEquals("Muscle Gain", plan.getWorkoutGoal());
        assertEquals("Intermediate", plan.getFitnessLevel());
        assertEquals("PENDING", plan.getStatus());
        assertTrue(plan.getDuration() >= 20 && plan.getDuration() <= 60);
        assertTrue(plan.getCaloriesEstimated() > 0);
        assertNotNull(plan.getWorkoutDetails());
        assertTrue(plan.getWorkoutDetails().contains("warmup"));
        assertTrue(plan.getWorkoutDetails().contains("mainWorkout"));
        assertTrue(plan.getWorkoutDetails().contains("cooldown"));

        // Verify timing does not overlap any class or study session
        LocalDateTime wStart = plan.getStartTime();
        LocalDateTime wEnd = plan.getEndTime();

        assertFalse(wStart.isBefore(LocalDateTime.of(targetDate, LocalTime.of(10, 0))) &&
                    wEnd.isAfter(LocalDateTime.of(targetDate, LocalTime.of(9, 0))));
        assertFalse(wStart.isBefore(LocalDateTime.of(targetDate, LocalTime.of(13, 0))) &&
                    wEnd.isAfter(LocalDateTime.of(targetDate, LocalTime.of(11, 0))));
        assertFalse(wStart.isBefore(LocalDateTime.of(targetDate, LocalTime.of(16, 0))) &&
                    wEnd.isAfter(LocalDateTime.of(targetDate, LocalTime.of(15, 0))));
    }

    @Test
    void testAdaptiveReplanning_WorkloadIncreased() {
        AcademicSchedule s1 = new AcademicSchedule(1L, userId, "DBMS", dayName, LocalTime.of(9, 0), LocalTime.of(10, 0));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.singletonList(s1));

        StudyPlan sp1 = new StudyPlan(1L, userId, "Study Session", LocalDateTime.of(targetDate, LocalTime.of(10, 0)), LocalDateTime.of(targetDate, LocalTime.of(21, 30)), "PENDING");
        when(studyPlanRepository.findByUserId(userId)).thenReturn(Collections.singletonList(sp1));

        FitnessProfile profile = new FitnessProfile();
        profile.setUserID(userId.intValue());
        profile.setFitnessGoal("Weight Loss");
        profile.setFitnessLevel("Beginner");
        profile.setPreferredWorkout("45 mins");
        when(fitnessProfileRepository.findByUserID(userId.intValue())).thenReturn(profile);

        // Previous plan had 45 minutes
        WorkoutPlan prev = new WorkoutPlan();
        prev.setWorkoutId(99L);
        prev.setDuration(45);
        prev.setStartTime(LocalDateTime.of(targetDate, LocalTime.of(18, 0)));
        when(workoutPlanRepository.findByUserIdAndWorkoutDate(userId, targetDate)).thenReturn(Optional.of(prev));
        when(workoutPlanRepository.save(any(WorkoutPlan.class))).thenAnswer(invocation -> invocation.getArgument(0));

        WorkoutPlan regenerated = service.generateWorkoutPlan(userId, targetDate);

        assertNotNull(regenerated);
        // Only slot remaining is 8:00 - 9:00 (60 mins) or 21:30 - 22:00 (30 mins).
        // Since duration is adjusted or timing moved:
        assertNotNull(regenerated.getAdjustmentReason());
    }

    @Test
    void testWorkoutHistory() {
        WorkoutPlan p1 = new WorkoutPlan();
        p1.setWorkoutId(1L);
        p1.setUserId(userId);
        p1.setWorkoutDate(LocalDate.now());
        p1.setStatus("COMPLETED");

        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(java.time.DayOfWeek.MONDAY);
        LocalDate weekEnd = today.with(java.time.DayOfWeek.SUNDAY);

        when(workoutPlanRepository.findByUserIdAndWorkoutDateBetweenOrderByWorkoutDateAsc(userId, weekStart, weekEnd))
                .thenReturn(Collections.singletonList(p1));
        when(workoutPlanRepository.countByUserId(userId)).thenReturn(1L);
        when(workoutPlanRepository.countByUserIdAndStatus(userId, "COMPLETED")).thenReturn(1L);
        when(workoutPlanRepository.findByUserIdAndWorkoutDate(userId, today)).thenReturn(Optional.of(p1));

        Map<String, Object> history = service.getWorkoutHistory(userId);

        assertEquals(1, history.get("completedThisWeek"));
        assertEquals(1L, history.get("totalWorkoutsCompleted"));
        assertEquals(1L, history.get("totalWorkouts"));
        assertEquals(100.0, history.get("completionPercentage"));
        assertTrue((int) history.get("workoutStreak") >= 1);
    }
}
