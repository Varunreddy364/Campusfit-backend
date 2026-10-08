package com.campusfit.campusfitbackend;

import com.campusfit.campusfitbackend.controller.AcademicScheduleController;
import com.campusfit.campusfitbackend.entity.AcademicSchedule;
import com.campusfit.campusfitbackend.service.AcademicScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AcademicScheduleControllerTest {

    @Mock
    private AcademicScheduleService service;

    @InjectMocks
    private AcademicScheduleController controller;

    private AcademicSchedule sampleSchedule;

    @BeforeEach
    void setUp() {
        sampleSchedule = new AcademicSchedule();
        sampleSchedule.setScheduleId(1L);
        sampleSchedule.setUserId(1L);
        sampleSchedule.setSubjectName("Operating Systems");
        sampleSchedule.setDayOfWeek("Monday");
        sampleSchedule.setStartTime(LocalTime.of(11, 0));
        sampleSchedule.setEndTime(LocalTime.of(12, 0));
    }

    @Test
    void testAddScheduleSuccess() {
        when(service.addSchedule(any(AcademicSchedule.class))).thenReturn(sampleSchedule);

        ResponseEntity<?> response = controller.addSchedule(sampleSchedule);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(sampleSchedule, response.getBody());
    }

    @Test
    void testAddScheduleValidationError() {
        when(service.addSchedule(any(AcademicSchedule.class)))
                .thenThrow(new IllegalArgumentException("End time must be after start time."));

        ResponseEntity<?> response = controller.addSchedule(sampleSchedule);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertTrue(response.getBody() instanceof Map);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("End time must be after start time.", body.get("error"));
    }

    @Test
    void testGetSchedulesByUserSuccess() {
        when(service.getSchedulesByUserId(1L)).thenReturn(Collections.singletonList(sampleSchedule));

        ResponseEntity<?> response = controller.getSchedulesByUser(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody() instanceof List);
        List<?> list = (List<?>) response.getBody();
        assertEquals(1, list.size());
    }

    @Test
    void testDeleteScheduleSuccess() {
        when(service.deleteSchedule(1L)).thenReturn(true);

        ResponseEntity<?> response = controller.deleteSchedule(1L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("Academic schedule deleted successfully", body.get("message"));
    }

    @Test
    void testDeleteScheduleNotFound() {
        when(service.deleteSchedule(99L)).thenReturn(false);

        ResponseEntity<?> response = controller.deleteSchedule(99L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("Academic schedule not found with ID: 99", body.get("error"));
    }
}
