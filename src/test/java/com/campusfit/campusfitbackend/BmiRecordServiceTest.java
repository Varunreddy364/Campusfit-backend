package com.campusfit.campusfitbackend;

import com.campusfit.campusfitbackend.entity.BmiRecord;
import com.campusfit.campusfitbackend.entity.User;
import com.campusfit.campusfitbackend.repository.BmiRecordRepository;
import com.campusfit.campusfitbackend.repository.UserRepository;
import com.campusfit.campusfitbackend.service.BmiRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BmiRecordServiceTest {

    @Mock
    private BmiRecordRepository bmiRecordRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BmiRecordService service;

    private Long userId = 101L;

    @BeforeEach
    void setUp() {}

    @Test
    void testLogBmiSuccess() {
        User user = new User();
        user.setUserID(userId.intValue());
        when(userRepository.findById(userId.intValue())).thenReturn(Optional.of(user));
        when(bmiRecordRepository.save(any(BmiRecord.class))).thenAnswer(i -> i.getArgument(0));

        BmiRecord record = service.logBmi(userId, 175.0, 68.0);

        assertNotNull(record);
        assertEquals(userId, record.getUserId());
        assertEquals(175.0, record.getHeight());
        assertEquals(68.0, record.getWeight());
        // 68 / (1.75 * 1.75) = 22.2
        assertEquals(22.2, record.getBmi(), 0.1);
        assertEquals("Optimal Health Zone", record.getCategory());
        assertTrue(record.getHealthScore() >= 90);

        verify(userRepository, times(1)).save(user);
        assertEquals(175, user.getHeight());
        assertEquals(68, user.getWeight());
    }

    @Test
    void testLogBmiInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> service.logBmi(null, 170.0, 70.0));
        assertThrows(IllegalArgumentException.class, () -> service.logBmi(userId, 0.0, 70.0));
        assertThrows(IllegalArgumentException.class, () -> service.logBmi(userId, 170.0, -5.0));
    }

    @Test
    void testClassifyBmiAllCategories() {
        assertEquals("Severely Underweight", BmiRecordService.classifyBmi(15.5));
        assertEquals("Moderately Underweight", BmiRecordService.classifyBmi(16.5));
        assertEquals("Mildly Underweight", BmiRecordService.classifyBmi(17.8));
        assertEquals("Healthy Lean", BmiRecordService.classifyBmi(19.2));
        assertEquals("Ideal Fitness Zone", BmiRecordService.classifyBmi(21.5));
        assertEquals("Optimal Health Zone", BmiRecordService.classifyBmi(23.5));
        assertEquals("Slightly Above Optimal", BmiRecordService.classifyBmi(26.0));
        assertEquals("Early Weight Management Zone", BmiRecordService.classifyBmi(28.5));
        assertEquals("High Risk Weight Zone", BmiRecordService.classifyBmi(32.0));
        assertEquals("Critical Health Improvement Zone", BmiRecordService.classifyBmi(36.5));
    }

    @Test
    void testGetBmiHistoryAndLatest() {
        BmiRecord rec = new BmiRecord(1L, userId, 170.0, 65.0, 22.5, "Optimal Health Zone", 95, LocalDateTime.now());
        when(bmiRecordRepository.findByUserIdOrderByRecordedAtAsc(userId)).thenReturn(Collections.singletonList(rec));
        when(bmiRecordRepository.findFirstByUserIdOrderByRecordedAtDesc(userId)).thenReturn(Optional.of(rec));

        List<BmiRecord> history = service.getBmiHistory(userId);
        assertEquals(1, history.size());

        Optional<BmiRecord> latest = service.getLatestBmi(userId);
        assertTrue(latest.isPresent());
        assertEquals(22.5, latest.get().getBmi());
    }
}
