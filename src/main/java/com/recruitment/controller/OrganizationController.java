package com.recruitment.controller;

import com.recruitment.dto.AddRecruiterRequest;
import com.recruitment.dto.ApiResponse;
import com.recruitment.dto.AssignRoleRequest;
import com.recruitment.dto.OrganizationRequest;
import com.recruitment.model.OrgMembership;
import com.recruitment.model.Organization;
import com.recruitment.model.TenantRole;
import com.recruitment.service.AuthService;
import com.recruitment.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;
    private final AuthService authService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Organization>> createOrganization(
            @Valid @RequestBody OrganizationRequest request,
            @RequestParam(defaultValue = "1") Long userId) {
        Organization org = organizationService.createOrg(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Organization created successfully", org));
    }

    @GetMapping("/mine/{userId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Organization>>> getMyOrganizations(@PathVariable Long userId) {
        List<Organization> orgs = organizationService.getOrgsByUserId(userId);
        return ResponseEntity.ok(ApiResponse.success("Organizations retrieved successfully", orgs));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<Organization>>> getAllOrganizations() {
        List<Organization> orgs = organizationService.getAllOrgs();
        return ResponseEntity.ok(ApiResponse.success("Organizations retrieved successfully", orgs));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Organization>> getOrganizationById(@PathVariable Long id) {
        Organization org = organizationService.getOrgById(id);
        return ResponseEntity.ok(ApiResponse.success("Organization retrieved successfully", org));
    }

    @PostMapping("/{orgId}/recruiters")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrgMembership>> addRecruiter(
            @PathVariable Long orgId,
            @Valid @RequestBody AddRecruiterRequest request) {
        OrgMembership membership = organizationService.addRecruiterToOrg(orgId, request.getEmail());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Recruiter added successfully", membership));
    }

    @GetMapping("/{orgId}/recruiters")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'HR', 'RECRUITER', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<List<OrgMembership>>> getRecruiters(@PathVariable Long orgId) {
        List<OrgMembership> members = organizationService.getOrgRecruiters(orgId);
        return ResponseEntity.ok(ApiResponse.success("Recruiters retrieved successfully", members));
    }

    @PostMapping("/{orgId}/roles")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<OrgMembership>> assignRole(
            @PathVariable Long orgId,
            @Valid @RequestBody AssignRoleRequest request) {
        TenantRole role = authService.resolveRole(request.getRole());
        OrgMembership membership = authService.assignRole(request.getActingUserId(), orgId, request.getEmail(), role);
        return ResponseEntity.ok(ApiResponse.success("Role assigned successfully", membership));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORG_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Organization>> updateOrganization(
            @PathVariable Long id,
            @Valid @RequestBody OrganizationRequest request) {
        Organization org = organizationService.updateOrg(id, request);
        return ResponseEntity.ok(ApiResponse.success("Organization updated successfully", org));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteOrganization(@PathVariable Long id) {
        organizationService.deleteOrg(id);
        return ResponseEntity.ok(ApiResponse.success("Organization deleted successfully"));
    }
}
