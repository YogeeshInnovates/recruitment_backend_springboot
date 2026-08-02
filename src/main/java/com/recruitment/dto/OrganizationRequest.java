package com.recruitment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationRequest {

    @NotBlank(message = "Organization name is required")
    private String name;

    private String description;
    private String industry;
    private String website;
    private String email;
    private String phone;
    private String address;
    private String city;
    private String state;
    private String postalCode;
    private String gstNumber;
    private String cinNumber;
    private String legalEntityType;
    private String companySize;
    private Integer foundedYear;
}
