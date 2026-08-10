package com.recruitment.repository;

import com.recruitment.model.MalpracticeReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MalpracticeReportRepository extends JpaRepository<MalpracticeReport, Long> {
    Optional<MalpracticeReport> findByInterviewId(Long interviewId);
}
