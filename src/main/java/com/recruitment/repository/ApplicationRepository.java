package com.recruitment.repository;

import com.recruitment.model.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {

    List<Application> findAllByOrganizationId(Long organizationId);

    List<Application> findByJobPostId(Long jobPostId);

    List<Application> findByCandidateId(Long candidateId);
}
