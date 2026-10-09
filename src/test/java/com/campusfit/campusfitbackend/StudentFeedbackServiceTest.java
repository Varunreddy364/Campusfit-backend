package com.campusfit.campusfitbackend;

import com.campusfit.campusfitbackend.entity.StudentFeedback;
import com.campusfit.campusfitbackend.repository.StudentFeedbackRepository;
import com.campusfit.campusfitbackend.service.StudentFeedbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentFeedbackServiceTest {

    @Mock
    private StudentFeedbackRepository repository;

    @InjectMocks
    private StudentFeedbackService service;

    private StudentFeedback sampleFeedback;

    @BeforeEach
    void setUp() {
        sampleFeedback = new StudentFeedback();
        sampleFeedback.setId(1L);
        sampleFeedback.setUserId(101L);
        sampleFeedback.setMood("Great");
        sampleFeedback.setAcademicLoadScore(5);
        sampleFeedback.setPlannerSatisfactionScore(4);
        sampleFeedback.setWorkoutDifficultyScore(4);
        sampleFeedback.setMissedActivityReason("Nutrition: 5/5 stars");
        sampleFeedback.setImprovementSuggestions("Love the break-aware study planning!");
        sampleFeedback.setCreatedAt(LocalDateTime.now());
    }

    @Test
    void testSaveFeedback_Success() {
        when(repository.save(any(StudentFeedback.class))).thenReturn(sampleFeedback);

        StudentFeedback saved = service.saveFeedback(sampleFeedback);

        assertNotNull(saved);
        assertEquals(101L, saved.getUserId());
        assertEquals("Great", saved.getMood());
        assertEquals(5, saved.getAcademicLoadScore());
        verify(repository, times(1)).save(sampleFeedback);
    }

    @Test
    void testSaveFeedback_NullFeedback_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> service.saveFeedback(null));
        verify(repository, never()).save(any());
    }

    @Test
    void testSaveFeedback_InvalidUserId_ThrowsException() {
        sampleFeedback.setUserId(null);
        assertThrows(IllegalArgumentException.class, () -> service.saveFeedback(sampleFeedback));

        sampleFeedback.setUserId(0L);
        assertThrows(IllegalArgumentException.class, () -> service.saveFeedback(sampleFeedback));

        verify(repository, never()).save(any());
    }

    @Test
    void testGetFeedbackByUserId_Success() {
        when(repository.findByUserIdOrderByCreatedAtDesc(101L))
                .thenReturn(Collections.singletonList(sampleFeedback));

        List<StudentFeedback> list = service.getFeedbackByUserId(101L);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("Great", list.get(0).getMood());
        verify(repository, times(1)).findByUserIdOrderByCreatedAtDesc(101L);
    }

    @Test
    void testGetFeedbackByUserId_InvalidUserId_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> service.getFeedbackByUserId(null));
        assertThrows(IllegalArgumentException.class, () -> service.getFeedbackByUserId(-1L));
        verify(repository, never()).findByUserIdOrderByCreatedAtDesc(any());
    }
}
