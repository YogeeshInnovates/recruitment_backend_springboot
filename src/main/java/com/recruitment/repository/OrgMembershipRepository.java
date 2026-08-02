package com.recruitment.repository;

import com.recruitment.model.OrgMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrgMembershipRepository extends JpaRepository<OrgMembership, Long> {
    List<OrgMembership> findByUserId(Long userId);
    Optional<OrgMembership> findByUserIdAndOrgId(Long userId, Long orgId);
    boolean existsByUserIdAndRole(Long userId, String role);
    List<OrgMembership> findByOrgId(Long orgId);
    boolean existsByUserIdAndOrgIdAndRole(Long userId, Long orgId, String role);
    List<OrgMembership> findByUserIdAndRole(Long userId, String role);
}
