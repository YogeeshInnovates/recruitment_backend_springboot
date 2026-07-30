package com.recruitment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "org_memberships")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long orgId;

    @Column(nullable = false)
    private String role;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
