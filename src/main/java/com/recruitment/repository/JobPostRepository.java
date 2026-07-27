package com.recruitment.repository;

import com.recruitment.model.JobPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobPostRepository extends JpaRepository<JobPost, Long> {

    List<JobPost> findAllByOrganizationId(Long organizationId);

    List<JobPost> findByOrganizationIdAndStatus(Long organizationId, JobPost.JobStatus status);
}
