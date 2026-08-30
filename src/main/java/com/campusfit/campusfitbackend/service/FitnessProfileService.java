package com.campusfit.campusfitbackend.service;

import com.campusfit.campusfitbackend.entity.FitnessProfile;
import com.campusfit.campusfitbackend.repository.FitnessProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FitnessProfileService {

    @Autowired
    private FitnessProfileRepository repository;

    public FitnessProfile saveProfile(FitnessProfile profile) {

        FitnessProfile existingProfile =
                repository.findByUserID(profile.getUserID());

        if (existingProfile != null) {

            existingProfile.setFitnessLevel(
                    profile.getFitnessLevel());

            existingProfile.setPreferredWorkout(
                    profile.getPreferredWorkout());

            existingProfile.setTargetWeight(
                    profile.getTargetWeight());

            existingProfile.setDailyCalorieGoal(
                    profile.getDailyCalorieGoal());

            existingProfile.setDailyProteinGoal(
                    profile.getDailyProteinGoal());

            existingProfile.setMedicalConditions(
                    profile.getMedicalConditions());

            existingProfile.setFitnessGoal(
                    profile.getFitnessGoal());

            return repository.save(existingProfile);
        }

        return repository.save(profile);
    }

    public FitnessProfile getProfile(Integer userId) {
        return repository.findByUserID(userId);
    }
}