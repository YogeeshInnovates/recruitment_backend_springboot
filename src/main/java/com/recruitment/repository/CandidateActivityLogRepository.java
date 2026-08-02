package com.recruitment.repository;

import com.recruitment.model.CandidateActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CandidateActivityLogRepository extends JpaRepository<CandidateActivityLog, Long> {

    List<CandidateActivityLog> findAllByInterviewIdOrderByOccurredAt(Long interviewId);

    long countByInterviewId(Long interviewId);
}
