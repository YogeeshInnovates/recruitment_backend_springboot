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
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

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
                .city(request.getCity())
                .state(request.getState())
                .postalCode(request.getPostalCode())
                .gstNumber(request.getGstNumber())
                .cinNumber(request.getCinNumber())
                .legalEntityType(request.getLegalEntityType())
                .companySize(request.getCompanySize())
                .foundedYear(request.getFoundedYear())
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

    public Map<String, Object> registerRecruiter(Long orgId, String name, String email) {
        String normalizedEmail = email.toLowerCase().trim();
        User user = userRepository.findByEmail(normalizedEmail).orElse(null);

        String tempPassword = null;
        boolean accountCreated = false;

        if (user == null) {
            tempPassword = "Recruit@" + (10000 + (int) (Math.random() * 90000));
            user = User.builder()
                    .name(name.trim())
                    .email(normalizedEmail)
                    .password(BCrypt.hashpw(tempPassword, BCrypt.gensalt()))
                    .build();
            user = userRepository.save(user);
            accountCreated = true;
        } else if (orgMembershipRepository.findByUserIdAndOrgId(user.getId(), orgId).isPresent()) {
            throw new RuntimeException("User is already a recruiter of this organization");
        }

        OrgMembership membership = OrgMembership.builder()
                .userId(user.getId())
                .orgId(orgId)
                .role(TenantRole.ROLE_RECRUITER.name())
                .build();
        membership = orgMembershipRepository.save(membership);

        Map<String, Object> result = new HashMap<>();
        result.put("membershipId", membership.getId());
        result.put("userId", user.getId());
        result.put("userName", user.getName());
        result.put("userEmail", user.getEmail());
        result.put("role", membership.getRole());
        result.put("accountCreated", accountCreated);
        result.put("tempPassword", tempPassword);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getOrgRecruiters(Long orgId) {
        List<OrgMembership> memberships = orgMembershipRepository.findByOrgId(orgId);
        Map<Long, User> usersById = userRepository
                .findAllById(memberships.stream().map(OrgMembership::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return memberships.stream().map(m -> {
            Map<String, Object> mm = new HashMap<>();
            mm.put("id", m.getId());
            mm.put("userId", m.getUserId());
            mm.put("orgId", m.getOrgId());
            mm.put("role", m.getRole());
            mm.put("createdAt", m.getCreatedAt());
            User u = usersById.get(m.getUserId());
            mm.put("name", u != null ? u.getName() : null);
            mm.put("email", u != null ? u.getEmail() : null);
            return mm;
        }).toList();
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
        existing.setCity(request.getCity());
        existing.setState(request.getState());
        existing.setPostalCode(request.getPostalCode());
        existing.setGstNumber(request.getGstNumber());
        existing.setCinNumber(request.getCinNumber());
        existing.setLegalEntityType(request.getLegalEntityType());
        existing.setCompanySize(request.getCompanySize());
        existing.setFoundedYear(request.getFoundedYear());
        return organizationRepository.save(existing);
    }

    public void deleteOrg(Long id) {
        Organization existing = getOrgById(id);
        organizationRepository.delete(existing);
    }
}
