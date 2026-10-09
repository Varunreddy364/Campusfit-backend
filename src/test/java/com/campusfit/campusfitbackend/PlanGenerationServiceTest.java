package com.campusfit.campusfitbackend;

import com.campusfit.campusfitbackend.entity.AcademicSchedule;
import com.campusfit.campusfitbackend.entity.AcademicTask;
import com.campusfit.campusfitbackend.entity.StudyPlan;
import com.campusfit.campusfitbackend.repository.AcademicScheduleRepository;
import com.campusfit.campusfitbackend.repository.AcademicTaskRepository;
import com.campusfit.campusfitbackend.repository.StudyPlanRepository;
import com.campusfit.campusfitbackend.service.PlanGenerationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanGenerationServiceTest {

    @Mock
    private AcademicScheduleRepository academicScheduleRepository;

    @Mock
    private AcademicTaskRepository academicTaskRepository;

    @Mock
    private StudyPlanRepository studyPlanRepository;

    @InjectMocks
    private PlanGenerationService service;

    private Long userId = 101L;

    @BeforeEach
    void setUp() {
    }

    @Test
    void testGenerateStudyPlanAllocatesIntoFreeSlots() {
        String todayName = LocalDate.now().getDayOfWeek().name();

        AcademicSchedule class1 = new AcademicSchedule(1L, userId, "DBMS", todayName, LocalTime.of(9, 0), LocalTime.of(10, 0));
        AcademicSchedule class2 = new AcademicSchedule(2L, userId, "OS", todayName, LocalTime.of(11, 0), LocalTime.of(12, 0));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Arrays.asList(class1, class2));

        AcademicTask task = new AcademicTask(
                1L, userId, "DBMS Assignment", "ASSIGNMENT",
                LocalDateTime.now().plusDays(3), 3.0, "HIGH", "PENDING"
        );
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.singletonList(task));
        when(studyPlanRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<StudyPlan> generated = service.generateStudyPlan(userId);

        assertNotNull(generated);
        assertFalse(generated.isEmpty());

        List<StudyPlan> studySessions = generated.stream()
                .filter(p -> !"BREAK".equalsIgnoreCase(p.getStatus()))
                .collect(Collectors.toList());
        assertFalse(studySessions.isEmpty());

        for (StudyPlan plan : studySessions) {
            assertEquals("DBMS Assignment", plan.getTitle());
            assertEquals("PENDING", plan.getStatus());
            assertEquals(userId, plan.getUserId());
        }

        // Verify break sessions are also generated
        List<StudyPlan> breakSessions = generated.stream()
                .filter(p -> "BREAK".equalsIgnoreCase(p.getStatus()))
                .collect(Collectors.toList());
        assertFalse(breakSessions.isEmpty());

        // Verify deleteByUserIdAndStatus was called to clean prior pending and breaks
        verify(studyPlanRepository, times(1)).deleteByUserIdAndStatus(eq(userId), eq("PENDING"));
        verify(studyPlanRepository, times(1)).deleteByUserIdAndStatus(eq(userId), eq("BREAK"));
        verify(studyPlanRepository, times(1)).saveAll(any());
    }

    @Test
    void testGenerateStudyPlanNoTasks() {
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.emptyList());

        List<StudyPlan> result = service.generateStudyPlan(userId);

        assertTrue(result.isEmpty());
        verify(studyPlanRepository, times(1)).deleteByUserIdAndStatus(eq(userId), eq("PENDING"));
        verify(studyPlanRepository, times(1)).deleteByUserIdAndStatus(eq(userId), eq("BREAK"));
    }

    @Test
    void testGetStudyPlansByUserId() {
        StudyPlan plan = new StudyPlan(1L, userId, "DBMS Prep", LocalDateTime.now(), LocalDateTime.now().plusHours(1), "PENDING");
        when(studyPlanRepository.findByUserIdOrderByStartTimeAsc(userId)).thenReturn(Collections.singletonList(plan));

        List<StudyPlan> plans = service.getStudyPlansByUserId(userId);

        assertEquals(1, plans.size());
        assertEquals("DBMS Prep", plans.get(0).getTitle());
    }

    @Test
    void testDeletePlan() {
        when(studyPlanRepository.existsById(1L)).thenReturn(true);
        doNothing().when(studyPlanRepository).deleteById(1L);

        boolean deleted = service.deletePlan(1L);

        assertTrue(deleted);
        verify(studyPlanRepository, times(1)).deleteById(1L);
    }

    @Test
    void testSmartDistributionInterleavesTasksAndSetsExplanation() {
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        AcademicTask task1 = new AcademicTask(1L, userId, "DBMS Assignment", "ASSIGNMENT",
                LocalDateTime.now().plusDays(2), 3.0, "HIGH", "PENDING");
        AcademicTask task2 = new AcademicTask(2L, userId, "ML Assignment", "ASSIGNMENT",
                LocalDateTime.now().plusDays(2), 3.0, "HIGH", "PENDING");
        AcademicTask task3 = new AcademicTask(3L, userId, "OS Assignment", "ASSIGNMENT",
                LocalDateTime.now().plusDays(4), 4.0, "MEDIUM", "PENDING");

        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Arrays.asList(task1, task2, task3));
        when(studyPlanRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<StudyPlan> generated = service.generateStudyPlan(userId);

        assertNotNull(generated);
        assertTrue(generated.size() >= 3);

        for (StudyPlan plan : generated) {
            assertNotNull(plan.getExplanation());
        }

        List<StudyPlan> academicSessions = generated.stream()
                .filter(p -> !"BREAK".equalsIgnoreCase(p.getStatus()))
                .collect(Collectors.toList());
        assertTrue(academicSessions.size() >= 3);

        String first = academicSessions.get(0).getTitle();
        String second = academicSessions.get(1).getTitle();
        assertNotEquals(first, second, "Smart distribution should interleave different subjects!");
    }

    @Test
    void testExamNearBoostsAllocation() {
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        AcademicTask examTask = new AcademicTask(1L, userId, "Algorithms Exam", "EXAM",
                LocalDateTime.now().plusDays(1), 2.0, "HIGH", "PENDING");
        AcademicTask regularTask = new AcademicTask(2L, userId, "Web Assignment", "ASSIGNMENT",
                LocalDateTime.now().plusDays(5), 3.0, "LOW", "PENDING");

        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Arrays.asList(examTask, regularTask));
        when(studyPlanRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<StudyPlan> generated = service.generateStudyPlan(userId);

        assertNotNull(generated);
        assertFalse(generated.isEmpty());

        List<StudyPlan> academicSessions = generated.stream()
                .filter(p -> !"BREAK".equalsIgnoreCase(p.getStatus()))
                .collect(Collectors.toList());
        assertFalse(academicSessions.isEmpty());
        assertEquals("Algorithms Exam", academicSessions.get(0).getTitle());
        assertTrue(academicSessions.get(0).getExplanation().contains("exam is in 1 day"));
    }

    @Test
    void testPreviewReplan_SingleCompleteSessionMissed() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime sessionStart = LocalDateTime.of(tomorrow, LocalTime.of(10, 0));
        LocalDateTime sessionEnd = LocalDateTime.of(tomorrow, LocalTime.of(11, 0));

        StudyPlan existingPlan = new StudyPlan(10L, userId, "Compiler Design", sessionStart, sessionEnd, "PENDING");
        existingPlan.setExplanation("Original plan");
        when(studyPlanRepository.findByUserIdOrderByStartTimeAsc(userId)).thenReturn(Collections.singletonList(existingPlan));

        AcademicTask task = new AcademicTask(1L, userId, "Compiler Design", "ASSIGNMENT",
                sessionStart.plusDays(3), 2.0, "HIGH", "PENDING");
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.singletonList(task));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        com.campusfit.campusfitbackend.dto.ReplanRequest request = new com.campusfit.campusfitbackend.dto.ReplanRequest(
                tomorrow, LocalTime.of(9, 30), LocalTime.of(11, 30)
        );

        com.campusfit.campusfitbackend.dto.ReplanPreviewResponse preview = service.previewReplan(userId, request);

        assertTrue(preview.isSuccess());
        assertEquals(1, preview.getAffectedSessions().size());
        assertEquals("Compiler Design", preview.getAffectedSessions().get(0).getTaskTitle());
        assertFalse(preview.getAffectedSessions().get(0).isCompleted());
        assertEquals(1.0, preview.getAffectedSessions().get(0).getMissedDurationHours());

        assertFalse(preview.getProposedReplacements().isEmpty());
        for (com.campusfit.campusfitbackend.dto.ProposedSessionInfo prop : preview.getProposedReplacements()) {
            assertEquals("Compiler Design", prop.getTaskTitle());
            boolean inMissed = prop.getNewStartTime().isBefore(LocalDateTime.of(tomorrow, LocalTime.of(11, 30)))
                    && prop.getNewEndTime().isAfter(LocalDateTime.of(tomorrow, LocalTime.of(9, 30)));
            assertFalse(inMissed, "Replacement must not be inside missed interval!");
        }
    }

    @Test
    void testPreviewReplan_PartialSessionMissed() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime sessionStart = LocalDateTime.of(tomorrow, LocalTime.of(10, 0));
        LocalDateTime sessionEnd = LocalDateTime.of(tomorrow, LocalTime.of(11, 0));

        StudyPlan existingPlan = new StudyPlan(11L, userId, "Network Security", sessionStart, sessionEnd, "PENDING");
        when(studyPlanRepository.findByUserIdOrderByStartTimeAsc(userId)).thenReturn(Collections.singletonList(existingPlan));

        AcademicTask task = new AcademicTask(2L, userId, "Network Security", "PROJECT",
                sessionStart.plusDays(4), 2.0, "MEDIUM", "PENDING");
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.singletonList(task));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        com.campusfit.campusfitbackend.dto.ReplanRequest request = new com.campusfit.campusfitbackend.dto.ReplanRequest(
                tomorrow, LocalTime.of(10, 0), LocalTime.of(10, 30)
        );

        com.campusfit.campusfitbackend.dto.ReplanPreviewResponse preview = service.previewReplan(userId, request);

        assertTrue(preview.isSuccess());
        assertEquals(1, preview.getAffectedSessions().size());
        assertEquals(0.5, preview.getAffectedSessions().get(0).getMissedDurationHours());
        assertEquals(1.0, preview.getAffectedSessions().get(0).getTotalDurationHours());

        boolean retainedUnmissed = preview.getCandidatePlans().stream().anyMatch(
                p -> p.getStartTime().equals(LocalDateTime.of(tomorrow, LocalTime.of(10, 30)))
                        && p.getEndTime().equals(sessionEnd)
        );
        assertTrue(retainedUnmissed, "Unmissed 30 mins should be retained in the candidate schedule!");
    }

    @Test
    void testPreviewReplan_CompletedWorkIsPreservedAndNotRescheduled() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime sessionStart = LocalDateTime.of(tomorrow, LocalTime.of(14, 0));
        LocalDateTime sessionEnd = LocalDateTime.of(tomorrow, LocalTime.of(15, 0));

        StudyPlan completedPlan = new StudyPlan(12L, userId, "Maths Review", sessionStart, sessionEnd, "COMPLETED");
        when(studyPlanRepository.findByUserIdOrderByStartTimeAsc(userId)).thenReturn(Collections.singletonList(completedPlan));

        com.campusfit.campusfitbackend.dto.ReplanRequest request = new com.campusfit.campusfitbackend.dto.ReplanRequest(
                tomorrow, LocalTime.of(14, 0), LocalTime.of(15, 0)
        );

        com.campusfit.campusfitbackend.dto.ReplanPreviewResponse preview = service.previewReplan(userId, request);

        assertTrue(preview.isSuccess());
        assertEquals(1, preview.getAffectedSessions().size());
        assertTrue(preview.getAffectedSessions().get(0).isCompleted());
        assertTrue(preview.getProposedReplacements().isEmpty());
    }

    @Test
    void testApplyReplan_PersistsChangesAndDeletesOldPending() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime sessionStart = LocalDateTime.of(tomorrow, LocalTime.of(16, 0));
        LocalDateTime sessionEnd = LocalDateTime.of(tomorrow, LocalTime.of(17, 0));

        StudyPlan existingPlan = new StudyPlan(15L, userId, "AI Lab Prep", sessionStart, sessionEnd, "PENDING");
        when(studyPlanRepository.findByUserIdOrderByStartTimeAsc(userId)).thenReturn(Collections.singletonList(existingPlan));

        AcademicTask task = new AcademicTask(5L, userId, "AI Lab Prep", "LAB",
                sessionStart.plusDays(3), 1.0, "HIGH", "PENDING");
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.singletonList(task));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        com.campusfit.campusfitbackend.dto.ReplanRequest request = new com.campusfit.campusfitbackend.dto.ReplanRequest(
                tomorrow, LocalTime.of(16, 0), LocalTime.of(17, 0)
        );

        when(studyPlanRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<StudyPlan> result = service.applyReplan(userId, request);

        assertNotNull(result);
        verify(studyPlanRepository, times(1)).deleteAll(any());
        verify(studyPlanRepository, times(1)).saveAll(any());
    }

    // ==========================================================
    // 9 BREAK-AWARE SCHEDULING SPECIFICATION TEST SCENARIOS
    // ==========================================================

    @Test
    void testScenario1_OneClassFollowedByStudySessionHasBreak() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String tomorrowName = tomorrow.getDayOfWeek().name();
        AcademicSchedule class1 = new AcademicSchedule(1L, userId, "Software Engineering", tomorrowName, LocalTime.of(8, 0), LocalTime.of(10, 0));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.singletonList(class1));

        AcademicTask task = new AcademicTask(1L, userId, "SE Assignment", "ASSIGNMENT",
                tomorrow.atTime(23, 59), 1.0, "HIGH", "PENDING");
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.singletonList(task));
        when(studyPlanRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<StudyPlan> generated = service.generateStudyPlan(userId, tomorrow.atTime(8, 0));

        // Find the first academic study session
        StudyPlan studySession = generated.stream()
                .filter(p -> !"BREAK".equalsIgnoreCase(p.getStatus()))
                .findFirst()
                .orElse(null);
        assertNotNull(studySession);

        // Study session must NOT start immediately at class end (10:00 AM)
        assertTrue(studySession.getStartTime().toLocalTime().isAfter(class1.getEndTime()),
                "Study session must not start immediately when class ends!");
        assertEquals(LocalTime.of(10, 15), studySession.getStartTime().toLocalTime(),
                "Earliest study session must start at 10:15 AM after 15 min break!");

        // Protected post-class break session must exist from 10:00 to 10:15
        StudyPlan breakSession = generated.stream()
                .filter(p -> "BREAK".equalsIgnoreCase(p.getStatus()))
                .findFirst()
                .orElse(null);
        assertNotNull(breakSession);
        assertEquals(LocalTime.of(10, 0), breakSession.getStartTime().toLocalTime());
        assertEquals(LocalTime.of(10, 15), breakSession.getEndTime().toLocalTime());
    }

    @Test
    void testScenario2_MultipleConsecutiveClassesHaveLongerBreak() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String tomorrowName = tomorrow.getDayOfWeek().name();
        // 2 consecutive classes: 08:00 - 09:30 and 09:30 - 10:30 (total 2.5 hrs)
        AcademicSchedule class1 = new AcademicSchedule(1L, userId, "Data Structures", tomorrowName, LocalTime.of(8, 0), LocalTime.of(9, 30));
        AcademicSchedule class2 = new AcademicSchedule(2L, userId, "DS Lab", tomorrowName, LocalTime.of(9, 30), LocalTime.of(10, 30));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Arrays.asList(class1, class2));

        AcademicTask task = new AcademicTask(1L, userId, "DS Lab Report", "LAB",
                tomorrow.atTime(23, 59), 1.0, "HIGH", "PENDING");
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.singletonList(task));
        when(studyPlanRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<StudyPlan> generated = service.generateStudyPlan(userId, tomorrow.atTime(8, 0));

        StudyPlan studySession = generated.stream()
                .filter(p -> !"BREAK".equalsIgnoreCase(p.getStatus()))
                .findFirst()
                .orElse(null);
        assertNotNull(studySession);

        // Earliest study session after 2 consecutive classes must start after 30 min break (11:00 AM)
        assertTrue(studySession.getStartTime().toLocalTime().isAfter(LocalTime.of(10, 30)));
        assertEquals(LocalTime.of(11, 0), studySession.getStartTime().toLocalTime(),
                "Study session must start at 11:00 AM after 30-min break following consecutive classes!");

        StudyPlan breakSession = generated.stream()
                .filter(p -> "BREAK".equalsIgnoreCase(p.getStatus()))
                .findFirst()
                .orElse(null);
        assertNotNull(breakSession);
        assertEquals(LocalTime.of(10, 30), breakSession.getStartTime().toLocalTime());
        assertEquals(LocalTime.of(11, 0), breakSession.getEndTime().toLocalTime());
    }

    @Test
    void testScenario3_LongAcademicDayWithMealBreak() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String tomorrowName = tomorrow.getDayOfWeek().name();
        // Long academic block spanning 4.5 hours: 08:00 - 12:30 (crosses midday)
        AcademicSchedule class1 = new AcademicSchedule(1L, userId, "Embedded Systems", tomorrowName, LocalTime.of(8, 0), LocalTime.of(12, 30));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.singletonList(class1));

        AcademicTask task = new AcademicTask(1L, userId, "Embedded Project", "PROJECT",
                tomorrow.atTime(23, 59), 1.0, "HIGH", "PENDING");
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.singletonList(task));
        when(studyPlanRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<StudyPlan> generated = service.generateStudyPlan(userId, tomorrow.atTime(8, 0));

        StudyPlan studySession = generated.stream()
                .filter(p -> !"BREAK".equalsIgnoreCase(p.getStatus()))
                .findFirst()
                .orElse(null);
        assertNotNull(studySession);

        // Post-class meal break of 45-60 minutes: 12:30 to 13:15
        assertTrue(studySession.getStartTime().toLocalTime().isAfter(LocalTime.of(12, 30)));
        assertEquals(LocalTime.of(13, 15), studySession.getStartTime().toLocalTime(),
                "Study session must start at 1:15 PM after 45-min meal and recovery break!");

        StudyPlan mealBreak = generated.stream()
                .filter(p -> "BREAK".equalsIgnoreCase(p.getStatus()) && p.getTitle().toLowerCase().contains("lunch"))
                .findFirst()
                .orElse(null);
        assertNotNull(mealBreak);
        assertEquals(LocalTime.of(12, 30), mealBreak.getStartTime().toLocalTime());
        assertEquals(LocalTime.of(13, 15), mealBreak.getEndTime().toLocalTime());
    }

    @Test
    void testScenario4_LongFreeIntervalProvidesSufficientRest() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String tomorrowName = tomorrow.getDayOfWeek().name();
        // Class 1 ends at 10:00 AM, Class 2 starts at 3:00 PM (5-hour gap)
        AcademicSchedule class1 = new AcademicSchedule(1L, userId, "Maths", tomorrowName, LocalTime.of(8, 0), LocalTime.of(10, 0));
        AcademicSchedule class2 = new AcademicSchedule(2L, userId, "Physics", tomorrowName, LocalTime.of(15, 0), LocalTime.of(16, 0));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Arrays.asList(class1, class2));

        AcademicTask task = new AcademicTask(1L, userId, "Maths Homework", "ASSIGNMENT",
                tomorrow.atTime(23, 59), 1.0, "HIGH", "PENDING");
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.singletonList(task));
        when(studyPlanRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<StudyPlan> generated = service.generateStudyPlan(userId, tomorrow.atTime(8, 0));

        StudyPlan studySession = generated.stream()
                .filter(p -> !"BREAK".equalsIgnoreCase(p.getStatus()))
                .findFirst()
                .orElse(null);
        assertNotNull(studySession);

        // Starts at 10:15 AM after normal post-class break
        assertEquals(LocalTime.of(10, 15), studySession.getStartTime().toLocalTime());
        // No redundant pre-study breaks inserted in the long free interval
        long postClassBreaks = generated.stream()
                .filter(p -> "BREAK".equalsIgnoreCase(p.getStatus()) && p.getStartTime().toLocalTime().equals(LocalTime.of(10, 0)))
                .count();
        assertEquals(1, postClassBreaks);
    }

    @Test
    void testScenario5_MultipleStudySessionsHaveInterStudyBreaks() {
        String todayName = LocalDate.now().getDayOfWeek().name();
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        // Heavy task requiring multiple sessions
        AcademicTask task1 = new AcademicTask(1L, userId, "OS Kernel", "ASSIGNMENT",
                LocalDateTime.now().plusDays(2), 2.0, "HIGH", "PENDING");
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.singletonList(task1));
        when(studyPlanRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<StudyPlan> generated = service.generateStudyPlan(userId);

        List<StudyPlan> studySessions = generated.stream()
                .filter(p -> !"BREAK".equalsIgnoreCase(p.getStatus()))
                .collect(Collectors.toList());
        assertTrue(studySessions.size() >= 2, "Multiple study sessions should be scheduled!");

        // Consecutive study sessions MUST NOT be back-to-back: there must be a break between them
        for (int i = 0; i < studySessions.size() - 1; i++) {
            StudyPlan s1 = studySessions.get(i);
            StudyPlan s2 = studySessions.get(i + 1);
            if (s1.getStartTime().toLocalDate().equals(s2.getStartTime().toLocalDate())) {
                long gapMinutes = Duration.between(s1.getEndTime(), s2.getStartTime()).toMinutes();
                assertTrue(gapMinutes >= 15, "Consecutive study sessions must be separated by at least 15 min break!");
            }
        }
    }

    @Test
    void testScenario6_ShortAvailableTimeWindowAvoidsCramming() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String tomorrowName = tomorrow.getDayOfWeek().name();
        // Class 1 ends at 10:00, Class 2 starts at 10:20 (20 min gap)
        AcademicSchedule class1 = new AcademicSchedule(1L, userId, "Chemistry", tomorrowName, LocalTime.of(9, 0), LocalTime.of(10, 0));
        AcademicSchedule class2 = new AcademicSchedule(2L, userId, "Biology", tomorrowName, LocalTime.of(10, 20), LocalTime.of(11, 20));
        // Class 3 ends at 11:20, and next free window is after 11:20
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Arrays.asList(class1, class2));

        AcademicTask task = new AcademicTask(1L, userId, "Chem Review", "ASSIGNMENT",
                tomorrow.atTime(23, 59), 1.0, "HIGH", "PENDING");
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.singletonList(task));
        when(studyPlanRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<StudyPlan> generated = service.generateStudyPlan(userId, tomorrow.atTime(8, 0));

        // Verify that NO study session was squeezed into the tiny 20-min window (10:00 - 10:20)
        boolean studyInShortWindow = generated.stream()
                .filter(p -> !"BREAK".equalsIgnoreCase(p.getStatus()))
                .anyMatch(p -> p.getStartTime().toLocalTime().isBefore(LocalTime.of(10, 20))
                        && p.getEndTime().toLocalTime().isAfter(LocalTime.of(10, 0)));
        assertFalse(studyInShortWindow, "Algorithm must not squeeze a study session into a 20-minute gap with class transition!");
    }

    @Test
    void testScenario7_HighPriorityTaskWithApproachingDeadline() {
        String todayName = LocalDate.now().getDayOfWeek().name();
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        AcademicTask urgentTask = new AcademicTask(1L, userId, "Urgent Exam Prep", "EXAM",
                LocalDateTime.now().plusDays(1), 1.0, "HIGH", "PENDING");
        AcademicTask regularTask = new AcademicTask(2L, userId, "Low Priority Lab", "LAB",
                LocalDateTime.now().plusDays(5), 1.0, "LOW", "PENDING");
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Arrays.asList(urgentTask, regularTask));
        when(studyPlanRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<StudyPlan> generated = service.generateStudyPlan(userId);

        List<StudyPlan> academicSessions = generated.stream()
                .filter(p -> !"BREAK".equalsIgnoreCase(p.getStatus()))
                .collect(Collectors.toList());
        assertFalse(academicSessions.isEmpty());
        // First scheduled academic session must be the urgent high priority task
        assertEquals("Urgent Exam Prep", academicSessions.get(0).getTitle());
    }

    @Test
    void testScenario8_ReplanningAfterMissedIntervalRespectsBreaks() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime sessionStart = LocalDateTime.of(tomorrow, LocalTime.of(10, 15));
        LocalDateTime sessionEnd = LocalDateTime.of(tomorrow, LocalTime.of(11, 0));

        StudyPlan existingPlan = new StudyPlan(20L, userId, "Cloud Computing", sessionStart, sessionEnd, "PENDING");
        when(studyPlanRepository.findByUserIdOrderByStartTimeAsc(userId)).thenReturn(Collections.singletonList(existingPlan));

        AcademicTask task = new AcademicTask(1L, userId, "Cloud Computing", "ASSIGNMENT",
                sessionStart.plusDays(3), 1.0, "HIGH", "PENDING");
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.singletonList(task));

        // Fixed class from 14:00 to 15:00 tomorrow
        String dayName = tomorrow.getDayOfWeek().name();
        AcademicSchedule afternoonClass = new AcademicSchedule(1L, userId, "DBMS", dayName, LocalTime.of(14, 0), LocalTime.of(15, 0));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.singletonList(afternoonClass));

        // Missed interval: 10:00 to 11:30
        com.campusfit.campusfitbackend.dto.ReplanRequest request = new com.campusfit.campusfitbackend.dto.ReplanRequest(
                tomorrow, LocalTime.of(10, 0), LocalTime.of(11, 30)
        );

        com.campusfit.campusfitbackend.dto.ReplanPreviewResponse preview = service.previewReplan(userId, request);

        assertTrue(preview.isSuccess());
        assertFalse(preview.getProposedReplacements().isEmpty());

        for (com.campusfit.campusfitbackend.dto.ProposedSessionInfo prop : preview.getProposedReplacements()) {
            // Replacement must NOT start directly at 15:00 (when afternoon class ends); must respect 15 min break (start >= 15:15)
            if (prop.getNewStartTime().toLocalDate().equals(tomorrow) && prop.getNewStartTime().toLocalTime().isAfter(LocalTime.of(14, 0))) {
                assertFalse(prop.getNewStartTime().toLocalTime().equals(LocalTime.of(15, 0)),
                        "Replacement session must not start immediately when class ends!");
                assertTrue(prop.getNewStartTime().toLocalTime().isAfter(LocalTime.of(15, 14)),
                        "Replacement session must respect post-class break of at least 15 mins!");
            }
        }
    }

    @Test
    void testScenario9_InsufficientTimeReportsUnscheduledWithoutSacrificingBreaks() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime sessionStart = LocalDateTime.of(tomorrow, LocalTime.of(10, 0));
        LocalDateTime sessionEnd = LocalDateTime.of(tomorrow, LocalTime.of(14, 0));

        StudyPlan existingPlan = new StudyPlan(30L, userId, "Huge Project", sessionStart, sessionEnd, "PENDING");
        when(studyPlanRepository.findByUserIdOrderByStartTimeAsc(userId)).thenReturn(Collections.singletonList(existingPlan));

        // Task with approaching deadline at 14:45 requiring 4 hours
        AcademicTask hugeTask = new AcademicTask(1L, userId, "Huge Project", "PROJECT",
                sessionStart.plusHours(4).plusMinutes(45), 4.0, "HIGH", "PENDING");
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.singletonList(hugeTask));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        com.campusfit.campusfitbackend.dto.ReplanRequest request = new com.campusfit.campusfitbackend.dto.ReplanRequest(
                tomorrow, LocalTime.of(10, 0), LocalTime.of(14, 0)
        );

        com.campusfit.campusfitbackend.dto.ReplanPreviewResponse preview = service.previewReplan(userId, request);

        assertTrue(preview.isSuccess());
        // Unscheduled tasks must be reported with diagnostic reason, rather than sacrificing breaks
        assertFalse(preview.getUnscheduledTasks().isEmpty(),
                "Unscheduled tasks should be reported when time is insufficient while preserving breaks!");
        assertTrue(preview.getUnscheduledTasks().get(0).getReason().contains("breaks")
                || preview.getUnscheduledTasks().get(0).getReason().contains("deadline"));
    }
}
