package com.recruitment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPostRequest {

    private String title;
    private String description;
    private String company;
    private String location;
    private Double salaryMin;
    private Double salaryMax;
    private String requiredSkills;
    private String experienceRequired;
    private String employmentType;
    private String status;
}
