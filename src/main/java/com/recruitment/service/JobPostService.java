package com.recruitment.service;

import com.recruitment.dto.JobPostRequest;
import com.recruitment.exception.ResourceNotFoundException;
import com.recruitment.model.JobPost;
import com.recruitment.model.Organization;
import com.recruitment.repository.JobPostRepository;
import com.recruitment.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class JobPostService {

    private final JobPostRepository jobPostRepository;
    private final OrganizationRepository organizationRepository;

    public JobPost createJob(Long orgId, JobPostRequest request) {
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + orgId));

        JobPost.JobStatus status = request.getStatus() != null
                ? JobPost.JobStatus.valueOf(request.getStatus())
                : JobPost.JobStatus.ACTIVE;

        JobPost job = JobPost.builder()
                .organization(organization)
                .title(request.getTitle())
                .description(request.getDescription())
                .company(request.getCompany())
                .location(request.getLocation())
                .salaryMin(request.getSalaryMin())
                .salaryMax(request.getSalaryMax())
                .requiredSkills(request.getRequiredSkills())
                .experienceRequired(request.getExperienceRequired())
                .employmentType(JobPost.EmploymentType.valueOf(request.getEmploymentType()))
                .status(status)
                .build();
        return jobPostRepository.save(job);
    }

    @Transactional(readOnly = true)
    public List<JobPost> getJobsByOrg(Long orgId) {
        return jobPostRepository.findAllByOrganizationId(orgId);
    }

    @Transactional(readOnly = true)
    public JobPost getJobById(Long id) {
        return jobPostRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Job post not found with id: " + id));
    }

    public JobPost updateJob(Long id, JobPostRequest request) {
        JobPost existing = getJobById(id);
        existing.setTitle(request.getTitle());
        existing.setDescription(request.getDescription());
        existing.setCompany(request.getCompany());
        existing.setLocation(request.getLocation());
        existing.setSalaryMin(request.getSalaryMin());
        existing.setSalaryMax(request.getSalaryMax());
        existing.setRequiredSkills(request.getRequiredSkills());
        existing.setExperienceRequired(request.getExperienceRequired());
        existing.setEmploymentType(JobPost.EmploymentType.valueOf(request.getEmploymentType()));
        if (request.getStatus() != null) {
            existing.setStatus(JobPost.JobStatus.valueOf(request.getStatus()));
        }
        return jobPostRepository.save(existing);
    }

    public void deleteJob(Long id) {
        JobPost existing = getJobById(id);
        jobPostRepository.delete(existing);
    }
}
