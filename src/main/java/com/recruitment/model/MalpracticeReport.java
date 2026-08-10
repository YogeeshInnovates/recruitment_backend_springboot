package com.recruitment.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "malpractice_reports")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MalpracticeReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "interview_id", nullable = false, unique = true)
    private Long interviewId;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String eventBreakdown;

    private String severity;

    private Integer suspiciousEventCount;

    private Integer evidenceCount;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
