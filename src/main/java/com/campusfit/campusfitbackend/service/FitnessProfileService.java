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
        return repository.save(profile);
    }

    public FitnessProfile getProfile(Integer userId) {
        return repository.findByUserID(userId);
    }
}