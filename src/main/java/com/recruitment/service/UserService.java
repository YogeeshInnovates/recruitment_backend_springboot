package com.recruitment.service;

import com.recruitment.dto.AuthResponse;
import com.recruitment.dto.AuthResponse.MembershipInfo;
import com.recruitment.dto.LoginRequest;
import com.recruitment.dto.SignupRequest;
import com.recruitment.exception.ResourceNotFoundException;
import com.recruitment.model.OrgMembership;
import com.recruitment.model.Organization;
import com.recruitment.model.User;
import com.recruitment.repository.OrgMembershipRepository;
import com.recruitment.repository.OrganizationRepository;
import com.recruitment.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final OrganizationRepository organizationRepository;

    public AuthResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail().toLowerCase().trim())
                .password(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()))
                .build();
        user = userRepository.save(user);

        return buildAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail().toLowerCase().trim())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!BCrypt.checkpw(request.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse getUserById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return buildAuthResponse(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        List<OrgMembership> memberships = orgMembershipRepository.findByUserId(user.getId());
        List<MembershipInfo> membershipInfos = memberships.stream()
                .map(m -> {
                    String orgName = organizationRepository.findById(m.getOrgId())
                            .map(Organization::getName)
                            .orElse("Unknown");
                    return MembershipInfo.builder()
                            .membershipId(m.getId())
                            .orgId(m.getOrgId())
                            .orgName(orgName)
                            .role(m.getRole())
                            .build();
                })
                .toList();

        return AuthResponse.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .memberships(membershipInfos)
                .build();
    }
}
