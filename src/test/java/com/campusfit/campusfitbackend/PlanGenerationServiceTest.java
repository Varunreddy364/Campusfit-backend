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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
        // Today's day of week name
        String todayName = LocalDate.now().getDayOfWeek().name();

        // 1. Classes: 09:00 - 10:00 and 11:00 - 12:00
        AcademicSchedule class1 = new AcademicSchedule(1L, userId, "DBMS", todayName, LocalTime.of(9, 0), LocalTime.of(10, 0));
        AcademicSchedule class2 = new AcademicSchedule(2L, userId, "OS", todayName, LocalTime.of(11, 0), LocalTime.of(12, 0));
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Arrays.asList(class1, class2));

        // 2. Task: "DBMS Assignment", 3 hours, deadline in 3 days
        AcademicTask task = new AcademicTask(
                1L, userId, "DBMS Assignment", "ASSIGNMENT",
                LocalDateTime.now().plusDays(3), 3.0, "HIGH", "PENDING"
        );
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.singletonList(task));

        when(studyPlanRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<StudyPlan> generated = service.generateStudyPlan(userId);

        assertNotNull(generated);
        assertFalse(generated.isEmpty());

        // Verify that all generated sessions have title "DBMS Assignment"
        for (StudyPlan plan : generated) {
            assertEquals("DBMS Assignment", plan.getTitle());
            assertEquals("PENDING", plan.getStatus());
            assertEquals(userId, plan.getUserId());
        }

        // Verify deleteByUserIdAndStatus was called to clean prior pending
        verify(studyPlanRepository, times(1)).deleteByUserIdAndStatus(eq(userId), eq("PENDING"));
        verify(studyPlanRepository, times(1)).saveAll(any());
    }

    @Test
    void testGenerateStudyPlanNoTasks() {
        when(academicScheduleRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(academicTaskRepository.findByUserIdOrderByDeadlineAsc(userId)).thenReturn(Collections.emptyList());

        List<StudyPlan> result = service.generateStudyPlan(userId);

        assertTrue(result.isEmpty());
        verify(studyPlanRepository, times(1)).deleteByUserIdAndStatus(eq(userId), eq("PENDING"));
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
        String todayName = LocalDate.now().getDayOfWeek().name();
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

        // Verify explanations are populated
        for (StudyPlan plan : generated) {
            assertNotNull(plan.getExplanation());
            assertTrue(plan.getExplanation().startsWith("Allocated"));
        }

        // Verify that the first 3 sessions are not all the same task (interleaved!)
        String first = generated.get(0).getTitle();
        String second = generated.get(1).getTitle();
        assertNotEquals(first, second, "Smart distribution should interleave different subjects instead of 3 continuous hours of one!");
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
        // First session should be the urgent Exam
        assertEquals("Algorithms Exam", generated.get(0).getTitle());
        assertTrue(generated.get(0).getExplanation().contains("exam is in 1 day"));
    }
}
