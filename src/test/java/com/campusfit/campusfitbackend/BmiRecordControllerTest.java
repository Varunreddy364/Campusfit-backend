package com.campusfit.campusfitbackend;

import com.campusfit.campusfitbackend.controller.BmiRecordController;
import com.campusfit.campusfitbackend.entity.BmiRecord;
import com.campusfit.campusfitbackend.service.BmiRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BmiRecordControllerTest {

    @Mock
    private BmiRecordService service;

    @InjectMocks
    private BmiRecordController controller;

    private Long userId = 101L;

    @BeforeEach
    void setUp() {}

    @Test
    void testLogBmiEndpointSuccess() {
        BmiRecord rec = new BmiRecord(1L, userId, 175.0, 70.0, 22.9, "Optimal Health Zone", 95, LocalDateTime.now());
        when(service.logBmi(eq(userId), eq(175.0), eq(70.0))).thenReturn(rec);

        Map<String, Object> body = Map.of("userId", userId, "height", 175.0, "weight", 70.0);
        ResponseEntity<?> resp = controller.logBmi(body);

        assertEquals(200, resp.getStatusCode().value());
        assertTrue(resp.getBody() instanceof BmiRecord);
    }

    @Test
    void testLogBmiMissingFields() {
        Map<String, Object> body = Map.of("userId", userId);
        ResponseEntity<?> resp = controller.logBmi(body);
        assertEquals(400, resp.getStatusCode().value());
    }

    @Test
    void testGetHistory() {
        when(service.getBmiHistory(userId)).thenReturn(Collections.emptyList());
        ResponseEntity<?> resp = controller.getHistory(userId);
        assertEquals(200, resp.getStatusCode().value());
    }

    @Test
    void testGetLatest() {
        BmiRecord rec = new BmiRecord(1L, userId, 175.0, 70.0, 22.9, "Optimal Health Zone", 95, LocalDateTime.now());
        when(service.getLatestBmi(userId)).thenReturn(Optional.of(rec));
        ResponseEntity<?> resp = controller.getLatest(userId);
        assertEquals(200, resp.getStatusCode().value());
    }
}
