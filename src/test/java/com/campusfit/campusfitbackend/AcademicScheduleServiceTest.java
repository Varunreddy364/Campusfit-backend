package com.campusfit.campusfitbackend;

import com.campusfit.campusfitbackend.entity.AcademicSchedule;
import com.campusfit.campusfitbackend.repository.AcademicScheduleRepository;
import com.campusfit.campusfitbackend.service.AcademicScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AcademicScheduleServiceTest {

    @Mock
    private AcademicScheduleRepository repository;

    @InjectMocks
    private AcademicScheduleService service;

    private AcademicSchedule validSchedule;

    @BeforeEach
    void setUp() {
        validSchedule = new AcademicSchedule();
        validSchedule.setScheduleId(1L);
        validSchedule.setUserId(101L);
        validSchedule.setSubjectName("DBMS");
        validSchedule.setDayOfWeek("Monday");
        validSchedule.setStartTime(LocalTime.of(9, 0));
        validSchedule.setEndTime(LocalTime.of(10, 0));
    }

    @Test
    void testAddScheduleSuccess() {
        when(repository.save(any(AcademicSchedule.class))).thenReturn(validSchedule);

        AcademicSchedule saved = service.addSchedule(validSchedule);

        assertNotNull(saved);
        assertEquals("DBMS", saved.getSubjectName());
        verify(repository, times(1)).save(validSchedule);
    }

    @Test
    void testAddScheduleMissingSubject() {
        validSchedule.setSubjectName(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            service.addSchedule(validSchedule);
        });
        assertEquals("Subject name is required.", ex.getMessage());

        validSchedule.setSubjectName("   ");
        ex = assertThrows(IllegalArgumentException.class, () -> {
            service.addSchedule(validSchedule);
        });
        assertEquals("Subject name is required.", ex.getMessage());
    }

    @Test
    void testAddScheduleMissingDayOfWeek() {
        validSchedule.setDayOfWeek(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            service.addSchedule(validSchedule);
        });
        assertEquals("Day of week is required.", ex.getMessage());

        validSchedule.setDayOfWeek(" ");
        assertThrows(IllegalArgumentException.class, () -> {
            service.addSchedule(validSchedule);
        });
    }

    @Test
    void testAddScheduleMissingStartTime() {
        validSchedule.setStartTime(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            service.addSchedule(validSchedule);
        });
        assertEquals("Start time is required.", ex.getMessage());
    }

    @Test
    void testAddScheduleMissingEndTime() {
        validSchedule.setEndTime(null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            service.addSchedule(validSchedule);
        });
        assertEquals("End time is required.", ex.getMessage());
    }

    @Test
    void testAddScheduleEndTimeNotAfterStartTime() {
        // Same time
        validSchedule.setStartTime(LocalTime.of(10, 0));
        validSchedule.setEndTime(LocalTime.of(10, 0));
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            service.addSchedule(validSchedule);
        });
        assertEquals("End time must be after start time.", ex.getMessage());

        // End time before start time
        validSchedule.setStartTime(LocalTime.of(11, 0));
        validSchedule.setEndTime(LocalTime.of(10, 0));
        ex = assertThrows(IllegalArgumentException.class, () -> {
            service.addSchedule(validSchedule);
        });
        assertEquals("End time must be after start time.", ex.getMessage());
    }

    @Test
    void testGetSchedulesByUserId() {
        when(repository.findByUserIdOrderByStartTimeAsc(101L)).thenReturn(Arrays.asList(validSchedule));

        List<AcademicSchedule> schedules = service.getSchedulesByUserId(101L);

        assertEquals(1, schedules.size());
        assertEquals("DBMS", schedules.get(0).getSubjectName());
    }

    @Test
    void testDeleteSchedule() {
        when(repository.existsById(1L)).thenReturn(true);
        doNothing().when(repository).deleteById(1L);

        boolean result = service.deleteSchedule(1L);

        assertTrue(result);
        verify(repository, times(1)).deleteById(1L);
    }

    @Test
    void testAddScheduleClashDetected() {
        AcademicSchedule existing = new AcademicSchedule(2L, 101L, "DBMS", "Monday", LocalTime.of(9, 0), LocalTime.of(10, 0));
        when(repository.findByUserIdAndDayOfWeekIgnoreCase(101L, "Monday"))
                .thenReturn(Collections.singletonList(existing));

        AcademicSchedule newSchedule = new AcademicSchedule();
        newSchedule.setUserId(101L);
        newSchedule.setSubjectName("OS");
        newSchedule.setDayOfWeek("Monday");
        newSchedule.setStartTime(LocalTime.of(9, 30));
        newSchedule.setEndTime(LocalTime.of(10, 30));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
            service.addSchedule(newSchedule);
        });

        assertEquals("The class overlaps with DBMS (09:00 - 10:00). Please choose another time.", ex.getMessage());
        verify(repository, never()).save(newSchedule);
    }

    @Test
    void testAddScheduleBackToBackAllowed() {
        AcademicSchedule existing = new AcademicSchedule(2L, 101L, "DBMS", "Monday", LocalTime.of(9, 0), LocalTime.of(10, 0));
        when(repository.findByUserIdAndDayOfWeekIgnoreCase(101L, "Monday"))
                .thenReturn(Collections.singletonList(existing));

        AcademicSchedule newSchedule = new AcademicSchedule();
        newSchedule.setUserId(101L);
        newSchedule.setSubjectName("OS");
        newSchedule.setDayOfWeek("Monday");
        newSchedule.setStartTime(LocalTime.of(10, 0));
        newSchedule.setEndTime(LocalTime.of(11, 0));

        when(repository.save(any(AcademicSchedule.class))).thenReturn(newSchedule);

        AcademicSchedule saved = service.addSchedule(newSchedule);
        assertNotNull(saved);
        assertEquals("OS", saved.getSubjectName());
        verify(repository, times(1)).save(newSchedule);
    }
}
