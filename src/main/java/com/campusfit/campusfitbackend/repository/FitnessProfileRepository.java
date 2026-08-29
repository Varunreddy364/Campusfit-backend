package com.campusfit.campusfitbackend.repository;

import com.campusfit.campusfitbackend.entity.FitnessProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FitnessProfileRepository extends JpaRepository<FitnessProfile, Integer> {

    FitnessProfile findByUserID(Integer userId);

}