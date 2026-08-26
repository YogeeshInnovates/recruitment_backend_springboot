package com.recruitment.repository;

import com.recruitment.model.Interview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface InterviewRepository extends JpaRepository<Interview, Long> {

    List<Interview> findAllByOrganizationId(Long organizationId);

    List<Interview> findByStatus(Interview.InterviewStatus status);

    List<Interview> findByStatusAndScheduledAtBefore(Interview.InterviewStatus status, LocalDateTime dateTime);

    List<Interview> findByApplicationId(Long applicationId);

    boolean existsByOrganizationIdAndStatus(Long organizationId, Interview.InterviewStatus status);

    List<Interview> findByOrganizationIdAndStatus(Long organizationId, Interview.InterviewStatus status);
}
