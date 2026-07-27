package com.recruitment.service;

import com.recruitment.dto.ApplicationRequest;
import com.recruitment.exception.ResourceNotFoundException;
import com.recruitment.model.Application;
import com.recruitment.model.Candidate;
import com.recruitment.model.JobPost;
import com.recruitment.model.Organization;
import com.recruitment.repository.ApplicationRepository;
import com.recruitment.repository.CandidateRepository;
import com.recruitment.repository.JobPostRepository;
import com.recruitment.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final OrganizationRepository organizationRepository;
    private final JobPostRepository jobPostRepository;
    private final CandidateRepository candidateRepository;

    public Application createApplication(Long orgId, ApplicationRequest request) {
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + orgId));
        JobPost jobPost = jobPostRepository.findById(request.getJobPostId())
                .orElseThrow(() -> new ResourceNotFoundException("Job post not found with id: " + request.getJobPostId()));
        Candidate candidate = candidateRepository.findById(request.getCandidateId())
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found with id: " + request.getCandidateId()));

        Application application = Application.builder()
                .organization(organization)
                .jobPost(jobPost)
                .candidate(candidate)
                .coverLetter(request.getCoverLetter())
                .status(Application.ApplicationStatus.SUBMITTED)
                .build();
        return applicationRepository.save(application);
    }

    @Transactional(readOnly = true)
    public List<Application> getApplicationsByOrg(Long orgId) {
        return applicationRepository.findAllByOrganizationId(orgId);
    }

    @Transactional(readOnly = true)
    public List<Application> getApplicationsByJob(Long jobId) {
        return applicationRepository.findByJobPostId(jobId);
    }

    @Transactional(readOnly = true)
    public Application getApplicationById(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found with id: " + id));
    }

    public Application updateApplicationStatus(Long id, String status) {
        Application existing = getApplicationById(id);
        existing.setStatus(Application.ApplicationStatus.valueOf(status));
        return applicationRepository.save(existing);
    }
}
