package com.campusfit.campusfitbackend;

import com.campusfit.campusfitbackend.entity.MealLog;
import com.campusfit.campusfitbackend.repository.MealLogRepository;
import com.campusfit.campusfitbackend.service.MealLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MealLogServiceTest {

    @Mock
    private MealLogRepository repository;

    @InjectMocks
    private MealLogService service;

    private MealLog sampleMeal;
    private Integer userId = 101;

    @BeforeEach
    void setUp() {
        sampleMeal = new MealLog();
        sampleMeal.setMealid(1);
        sampleMeal.setUserid(userId);
        sampleMeal.setFoodName("Oatmeal with Blueberries");
        sampleMeal.setQuantity("200g");
        sampleMeal.setMealType("Breakfast");
        sampleMeal.setCalories(320.0);
        sampleMeal.setProtein(14.0);
        sampleMeal.setCarbs(52.0);
        sampleMeal.setFat(6.0);
        sampleMeal.setMealDate(LocalDate.now());
    }

    @Test
    void testAddMeal_SetsCurrentDateIfNull() {
        MealLog mealWithoutDate = new MealLog();
        mealWithoutDate.setUserid(userId);
        mealWithoutDate.setFoodName("Grilled Chicken Salad");
        mealWithoutDate.setCalories(450.0);

        when(repository.save(any(MealLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MealLog saved = service.addMeal(mealWithoutDate);

        assertNotNull(saved);
        assertNotNull(saved.getMealDate());
        assertEquals(LocalDate.now(), saved.getMealDate());
        verify(repository, times(1)).save(mealWithoutDate);
    }

    @Test
    void testGetTodaysMeals() {
        when(repository.findByUseridAndMealDate(userId, LocalDate.now()))
                .thenReturn(Arrays.asList(sampleMeal));

        List<MealLog> results = service.getTodaysMeals(userId);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("Oatmeal with Blueberries", results.get(0).getFoodName());
        assertEquals(320.0, results.get(0).getCalories());
    }

    @Test
    void testUpdateMeal_ExistingRecord() {
        when(repository.findById(1)).thenReturn(Optional.of(sampleMeal));
        when(repository.save(any(MealLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MealLog updatePayload = new MealLog();
        updatePayload.setFoodName("Protein Oatmeal & Berries");
        updatePayload.setCalories(380.0);
        updatePayload.setProtein(25.0);

        MealLog updated = service.updateMeal(1, updatePayload);

        assertNotNull(updated);
        assertEquals("Protein Oatmeal & Berries", updated.getFoodName());
        assertEquals(380.0, updated.getCalories());
        assertEquals(25.0, updated.getProtein());
        verify(repository, times(1)).save(sampleMeal);
    }

    @Test
    void testUpdateMeal_NonExistentRecord() {
        when(repository.findById(999)).thenReturn(Optional.empty());

        MealLog updated = service.updateMeal(999, sampleMeal);

        assertNull(updated);
        verify(repository, never()).save(any(MealLog.class));
    }

    @Test
    void testGetWeeklyMeals() {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(6);

        when(repository.findByUseridAndMealDateBetweenOrderByMealDateAsc(userId, start, end))
                .thenReturn(Arrays.asList(sampleMeal));

        List<MealLog> weekly = service.getWeeklyMeals(userId);

        assertNotNull(weekly);
        assertEquals(1, weekly.size());
        verify(repository, times(1)).findByUseridAndMealDateBetweenOrderByMealDateAsc(userId, start, end);
    }

    @Test
    void testDeleteMeal() {
        doNothing().when(repository).deleteById(1);

        service.deleteMeal(1);

        verify(repository, times(1)).deleteById(1);
    }
}
