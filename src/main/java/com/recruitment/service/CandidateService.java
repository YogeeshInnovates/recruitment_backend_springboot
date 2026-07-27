package com.recruitment.service;

import com.recruitment.dto.CandidateRequest;
import com.recruitment.exception.ResourceNotFoundException;
import com.recruitment.model.Candidate;
import com.recruitment.model.Organization;
import com.recruitment.repository.CandidateRepository;
import com.recruitment.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CandidateService {

    private final CandidateRepository candidateRepository;
    private final OrganizationRepository organizationRepository;

    public Candidate createCandidate(Long orgId, CandidateRequest request) {
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + orgId));

        Candidate candidate = Candidate.builder()
                .organization(organization)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .resumeText(request.getResumeText())
                .skills(request.getSkills())
                .experience(request.getExperience())
                .education(request.getEducation())
                .build();
        return candidateRepository.save(candidate);
    }

    @Transactional(readOnly = true)
    public List<Candidate> getCandidatesByOrg(Long orgId) {
        return candidateRepository.findAllByOrganizationId(orgId);
    }

    @Transactional(readOnly = true)
    public Candidate getCandidateById(Long id) {
        return candidateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found with id: " + id));
    }

    public List<Candidate> uploadResumes(Long orgId, List<MultipartFile> files) {
        Organization organization = organizationRepository.findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + orgId));

        List<Candidate> createdCandidates = new ArrayList<>();

        for (MultipartFile file : files) {
            try {
                String resumeText = extractTextFromFile(file);
                String filename = file.getOriginalFilename();
                String firstName = "Candidate";
                String lastName = "Uploaded";

                if (filename != null && filename.contains(".")) {
                    String nameWithoutExt = filename.substring(0, filename.lastIndexOf('.'));
                    String[] nameParts = nameWithoutExt.split("_|-|\\s+");
                    if (nameParts.length >= 2) {
                        firstName = nameParts[0];
                        lastName = nameParts[1];
                    } else if (nameParts.length == 1) {
                        firstName = nameParts[0];
                    }
                }

                Candidate candidate = Candidate.builder()
                        .organization(organization)
                        .firstName(firstName)
                        .lastName(lastName)
                        .email(firstName.toLowerCase() + "." + lastName.toLowerCase() + "@" + organization.getName().toLowerCase().replaceAll("\\s+", "") + ".com")
                        .resumeText(resumeText)
                        .build();

                createdCandidates.add(candidateRepository.save(candidate));
                log.info("Successfully uploaded resume: {}", filename);
            } catch (Exception e) {
                log.error("Failed to process file: {}", file.getOriginalFilename(), e);
            }
        }
        return createdCandidates;
    }

    private String extractTextFromFile(MultipartFile file) throws Exception {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line).append("\n");
            }
        }
        return text.toString();
    }

    public Candidate updateCandidate(Long id, CandidateRequest request) {
        Candidate existing = getCandidateById(id);
        existing.setFirstName(request.getFirstName());
        existing.setLastName(request.getLastName());
        existing.setEmail(request.getEmail());
        existing.setPhone(request.getPhone());
        existing.setResumeText(request.getResumeText());
        existing.setSkills(request.getSkills());
        existing.setExperience(request.getExperience());
        existing.setEducation(request.getEducation());
        return candidateRepository.save(existing);
    }

    public void deleteCandidate(Long id) {
        Candidate existing = getCandidateById(id);
        candidateRepository.delete(existing);
    }
}
