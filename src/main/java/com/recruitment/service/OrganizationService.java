package com.recruitment.service;

import com.recruitment.dto.OrganizationRequest;
import com.recruitment.exception.ResourceNotFoundException;
import com.recruitment.model.OrgMembership;
import com.recruitment.model.Organization;
import com.recruitment.model.TenantRole;
import com.recruitment.model.User;
import com.recruitment.repository.OrgMembershipRepository;
import com.recruitment.repository.OrganizationRepository;
import com.recruitment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final UserRepository userRepository;

    public Organization createOrg(OrganizationRequest request, Long userId) {
        Organization org = Organization.builder()
                .name(request.getName())
                .description(request.getDescription())
                .industry(request.getIndustry())
                .website(request.getWebsite())
                .email(request.getEmail())
                .phone(request.getPhone())
                .address(request.getAddress())
                .createdByUserId(userId)
                .build();
        org = organizationRepository.save(org);

        OrgMembership membership = OrgMembership.builder()
                .userId(userId)
                .orgId(org.getId())
                .role(TenantRole.ROLE_ORG_ADMIN.name())
                .build();
        orgMembershipRepository.save(membership);

        return org;
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

    @Transactional(readOnly = true)
    public List<Organization> getOrgsByUserId(Long userId) {
        List<OrgMembership> memberships = orgMembershipRepository.findByUserId(userId);
        List<Long> orgIds = memberships.stream().map(OrgMembership::getOrgId).toList();
        return organizationRepository.findAllById(orgIds);
    }

    public OrgMembership addRecruiterToOrg(Long orgId, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        if (orgMembershipRepository.findByUserIdAndOrgId(user.getId(), orgId).isPresent()) {
            throw new RuntimeException("User is already a member of this organization");
        }

        OrgMembership membership = OrgMembership.builder()
                .userId(user.getId())
                .orgId(orgId)
                .role(TenantRole.ROLE_RECRUITER.name())
                .build();
        return orgMembershipRepository.save(membership);
    }

    @Transactional(readOnly = true)
    public List<OrgMembership> getOrgRecruiters(Long orgId) {
        return orgMembershipRepository.findByOrgId(orgId);
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
