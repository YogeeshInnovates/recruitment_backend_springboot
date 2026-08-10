package com.recruitment.repository;

import com.recruitment.model.InterviewSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewSnapshotRepository extends JpaRepository<InterviewSnapshot, Long> {

    List<InterviewSnapshot> findAllByInterviewIdOrderByCapturedAtDesc(Long interviewId);

    long countByInterviewId(Long interviewId);
}
