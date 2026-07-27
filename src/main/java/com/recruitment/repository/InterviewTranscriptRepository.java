package com.recruitment.repository;

import com.recruitment.model.InterviewTranscript;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InterviewTranscriptRepository extends JpaRepository<InterviewTranscript, Long> {

    List<InterviewTranscript> findAllByInterviewIdOrderByQuestionNumberAsc(Long interviewId);
}
