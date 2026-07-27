package com.recruitment.controller;

import com.recruitment.dto.ApiResponse;
import com.recruitment.dto.OrganizationRequest;
import com.recruitment.model.Organization;
import com.recruitment.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<ApiResponse<Organization>> createOrganization(
            @Valid @RequestBody OrganizationRequest request) {
        Organization org = organizationService.createOrg(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Organization created successfully", org));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Organization>>> getAllOrganizations() {
        List<Organization> orgs = organizationService.getAllOrgs();
        return ResponseEntity.ok(ApiResponse.success("Organizations retrieved successfully", orgs));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Organization>> getOrganizationById(@PathVariable Long id) {
        Organization org = organizationService.getOrgById(id);
        return ResponseEntity.ok(ApiResponse.success("Organization retrieved successfully", org));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Organization>> updateOrganization(
            @PathVariable Long id,
            @Valid @RequestBody OrganizationRequest request) {
        Organization org = organizationService.updateOrg(id, request);
        return ResponseEntity.ok(ApiResponse.success("Organization updated successfully", org));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteOrganization(@PathVariable Long id) {
        organizationService.deleteOrg(id);
        return ResponseEntity.ok(ApiResponse.success("Organization deleted successfully"));
    }
}
