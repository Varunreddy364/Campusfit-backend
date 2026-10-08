package com.campusfit.campusfitbackend.repository;

import com.campusfit.campusfitbackend.entity.BmiRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BmiRecordRepository extends JpaRepository<BmiRecord, Long> {

    List<BmiRecord> findByUserIdOrderByRecordedAtAsc(Long userId);

    List<BmiRecord> findByUserIdOrderByRecordedAtDesc(Long userId);

    Optional<BmiRecord> findFirstByUserIdOrderByRecordedAtDesc(Long userId);
}
