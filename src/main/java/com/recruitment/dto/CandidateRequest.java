package com.recruitment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateRequest {

    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private String resumeText;
    private String skills;
    private String experience;
    private String education;
}
