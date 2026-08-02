package com.recruitment.service;

import com.recruitment.exception.ResourceNotFoundException;
import com.recruitment.model.OrgMembership;
import com.recruitment.model.TenantRole;
import com.recruitment.model.User;
import com.recruitment.repository.OrgMembershipRepository;
import com.recruitment.repository.UserRepository;
import com.recruitment.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final JwtUtil jwtUtil;

    @Value("${super.admin.emails:}")
    private String superAdminEmails;

    /**
     * A freshly signed-up user has NO memberships -> their effective role is ROLE_USER.
     * (Organization creation is handled by OrganizationService, which maps the creator
     * to ROLE_ORG_ADMIN.)
     */
    public TenantRole defaultRole(User user) {
        return isSuperAdmin(user.getId())
                ? TenantRole.ROLE_SUPER_ADMIN
                : TenantRole.ROLE_USER;
    }

    /**
     * Strict check: only this organization's ROLE_ORG_ADMIN (or a SUPER_ADMIN) may
     * assign ROLE_HR / ROLE_RECRUITER to another user inside that organization.
     */
    public OrgMembership assignRole(Long actingUserId, Long orgId, String targetEmail, TenantRole role) {
        if (role == TenantRole.ROLE_SUPER_ADMIN || role == TenantRole.ROLE_ORG_ADMIN) {
            throw new IllegalArgumentException("Only the platform owner can assign SUPER_ADMIN or ORG_ADMIN roles");
        }

        if (!isSuperAdmin(actingUserId) && !isOrgAdmin(actingUserId, orgId)) {
            throw new IllegalArgumentException("Only this organization's ORG_ADMIN can assign roles within it");
        }

        User target = userRepository.findByEmail(targetEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + targetEmail));

        if (orgMembershipRepository.existsByUserIdAndOrgIdAndRole(
                target.getId(), orgId, TenantRole.ROLE_ORG_ADMIN.name())) {
            throw new IllegalArgumentException("Cannot change the role of the organization admin");
        }

        OrgMembership membership = orgMembershipRepository.findByUserIdAndOrgId(target.getId(), orgId)
                .orElseGet(() -> OrgMembership.builder()
                        .userId(target.getId())
                        .orgId(orgId)
                        .role(role.name())
                        .build());
        membership.setRole(role.name());
        return orgMembershipRepository.save(membership);
    }

    public boolean isOrgAdmin(Long userId, Long orgId) {
        return orgMembershipRepository.existsByUserIdAndOrgIdAndRole(
                userId, orgId, TenantRole.ROLE_ORG_ADMIN.name());
    }

    /**
     * SUPER_ADMIN placeholder: either has a ROLE_SUPER_ADMIN membership or is listed
     * in the super.admin.emails config. Grants bypass of organization checks.
     */
    public boolean isSuperAdmin(Long userId) {
        if (orgMembershipRepository.existsByUserIdAndRole(userId, TenantRole.ROLE_SUPER_ADMIN.name())) {
            return true;
        }
        if (superAdminEmails == null || superAdminEmails.isBlank()) {
            return false;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return false;
        for (String email : superAdminEmails.split(",")) {
            if (email.trim().equalsIgnoreCase(user.getEmail())) return true;
        }
        return false;
    }

    public List<OrgMembership> getRolesForUser(Long userId) {
        return orgMembershipRepository.findByUserId(userId);
    }

    /**
     * Builds a JWT embedding user_id, organization_id ("NONE" for ROLE_USER,
     * "ALL" for SUPER_ADMIN, otherwise the org id) and the active role.
     */
    public String generateToken(User user) {
        if (isSuperAdmin(user.getId())) {
            return jwtUtil.generateToken(user.getId(), null, TenantRole.ROLE_SUPER_ADMIN);
        }
        List<OrgMembership> memberships = orgMembershipRepository.findByUserId(user.getId());
        if (memberships.isEmpty()) {
            return jwtUtil.generateToken(user.getId(), null, TenantRole.ROLE_USER);
        }
        OrgMembership active = memberships.get(0);
        return jwtUtil.generateToken(user.getId(), active.getOrgId(), resolveRole(active.getRole()));
    }

    public TenantRole resolveRole(String role) {
        if (role == null || role.isBlank()) return TenantRole.ROLE_USER;
        String normalized = role.trim().toUpperCase();
        if (!normalized.startsWith("ROLE_")) normalized = "ROLE_" + normalized;
        try {
            return TenantRole.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            return TenantRole.ROLE_USER;
        }
    }
}
