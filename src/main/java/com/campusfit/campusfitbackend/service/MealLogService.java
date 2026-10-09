package com.campusfit.campusfitbackend.service;

import com.campusfit.campusfitbackend.entity.MealLog;
import com.campusfit.campusfitbackend.repository.MealLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class MealLogService {

    @Autowired
    private MealLogRepository repository;

    public MealLog addMeal(MealLog meal) {

        if (meal.getMealDate() == null) {
            meal.setMealDate(LocalDate.now());
        }

        return repository.save(meal);
    }

    public List<MealLog> getMealsByUser(Integer userid) {
        return repository.findByUserid(userid);
    }

    public List<MealLog> getTodaysMeals(Integer userid) {
        return repository.findByUseridAndMealDate(
                userid,
                LocalDate.now()
        );
    }

    public void deleteMeal(Integer mealid) {
        repository.deleteById(mealid);
    }

    public MealLog updateMeal(Integer mealid, MealLog updatedMeal) {
        return repository.findById(mealid).map(meal -> {
            if (updatedMeal.getFoodName() != null) meal.setFoodName(updatedMeal.getFoodName());
            if (updatedMeal.getQuantity() != null) meal.setQuantity(updatedMeal.getQuantity());
            if (updatedMeal.getMealType() != null) meal.setMealType(updatedMeal.getMealType());
            if (updatedMeal.getCalories() != null) meal.setCalories(updatedMeal.getCalories());
            if (updatedMeal.getProtein() != null) meal.setProtein(updatedMeal.getProtein());
            if (updatedMeal.getCarbs() != null) meal.setCarbs(updatedMeal.getCarbs());
            if (updatedMeal.getFat() != null) meal.setFat(updatedMeal.getFat());
            if (updatedMeal.getMealDate() != null) meal.setMealDate(updatedMeal.getMealDate());
            return repository.save(meal);
        }).orElse(null);
    }

    public List<MealLog> getWeeklyMeals(Integer userid) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(6);
        return repository.findByUseridAndMealDateBetweenOrderByMealDateAsc(userid, start, end);
    }
}