package com.recruitment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignRoleRequest {

    @NotNull(message = "actingUserId is required")
    private Long actingUserId;

    @NotBlank(message = "email is required")
    private String email;

    @NotBlank(message = "role is required")
    private String role;
}
