package com.recruitment.service;

import com.recruitment.dto.OrganizationRequest;
import com.recruitment.exception.ResourceNotFoundException;
import com.recruitment.model.Organization;
import com.recruitment.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public Organization createOrg(OrganizationRequest request) {
        Organization org = Organization.builder()
                .name(request.getName())
                .description(request.getDescription())
                .industry(request.getIndustry())
                .website(request.getWebsite())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .build();
        return organizationRepository.save(org);
    }

    @Transactional(readOnly = true)
    public Organization getOrgById(Long id) {
        return organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Organization> getAllOrgs() {
        return organizationRepository.findAll();
    }

    public Organization updateOrg(Long id, OrganizationRequest request) {
        Organization existing = getOrgById(id);
        existing.setName(request.getName());
        existing.setDescription(request.getDescription());
        existing.setIndustry(request.getIndustry());
        existing.setWebsite(request.getWebsite());
        existing.setEmail(request.getEmail());
        existing.setPhone(request.getPhone());
        existing.setAddress(request.getAddress());
        return organizationRepository.save(existing);
    }

    public void deleteOrg(Long id) {
        Organization existing = getOrgById(id);
        organizationRepository.delete(existing);
    }
}
