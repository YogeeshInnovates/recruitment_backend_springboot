package com.recruitment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {

    private Long userId;
    private String name;
    private String email;
    private String token;
    private List<MembershipInfo> memberships;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MembershipInfo {
        private Long membershipId;
        private Long orgId;
        private String orgName;
        private String role;
    }
}
